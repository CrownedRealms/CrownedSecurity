package io.github.eylexlive.discord2fa.manager;

import io.github.eylexlive.discord2fa.Discord2FA;
import io.github.eylexlive.discord2fa.data.PlayerData;
import io.github.eylexlive.discord2fa.event.AuthCompleteEvent;
import io.github.eylexlive.discord2fa.event.AuthFailEvent;
import io.github.eylexlive.discord2fa.task.Cancel2FAReqTask;
import io.github.eylexlive.discord2fa.task.CountdownTask;
import io.github.eylexlive.discord2fa.util.ConfigUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONObject;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.OffsetDateTime;
import java.util.*;

/*
 *	Created by EylexLive on Feb 23, 2020.
 *	Currently version: 3.5
 */

public class Discord2FAManager {

    private final Discord2FA plugin;

    private final Map<UUID, PlayerData> players = new HashMap<>();

    private final Map<Player, ArmorStand> armorStands = new HashMap<>();

    private final Set<Player> checkPlayers = new HashSet<>();

    public Discord2FAManager(Discord2FA plugin) {
        this.plugin = plugin;
    }

    public PlayerData loadData(Player player) {
        final UUID uuid = player.getUniqueId();
        if (getPlayerData(uuid) != null)
            return getPlayerData(uuid);

        final PlayerData data = new PlayerData(uuid);
        players.put(
                data.getUuid(), data
        );

        return data;
    }

    public void unloadData(Player player) {
        players.remove(
                player.getUniqueId()
        );
    }

    public PlayerData getPlayerData(Player player) {
      if (getPlayerData(player.getUniqueId()) == null)
          return loadData(player);
      return players.get(player.getUniqueId());
    }

    public PlayerData getPlayerData(UUID uuid) {
        return players.get(uuid);
    }

    public void addPlayerToCheck(Player player) {
        if (isInCheck(player))
            return;

        checkPlayers.add(player);

        final String code = getRandomCode(
                ConfigUtil.getInt(
                        "code-lenght"
                )
        );
        final PlayerData playerData = loadData(player);

        if (!ConfigUtil.getBoolean("generate-new-code-always")) {
            if (playerData.getCheckCode() == null)
                sendCode(
                        player, code
                );
        } else {
            sendCode(
                    player, code
            );
        }

        /*
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                sitPlayer(player), 13L
        );
         */

        player.sendMessage(
                getAuthMessage(
                        true, -1
                )
        );

        // Get Config blind-on-auth

        if (ConfigUtil.getBoolean("blind-on-auth")) {

                // Blind player
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1000, 1));

        }

        final CountdownTask task = new CountdownTask(
                this,  player
        );
        task.runTaskTimer(
                plugin, 0L, 20L
        );
    }

    public void removePlayerFromCheck(Player player) {

        if (!isInCheck(player))
            return;

        checkPlayers.remove(player);

        /*
        unSitPlayer(player);

        if (plugin.isBlindOnAuthEnabled()) {

                // Unblind player
                p.getActivePotionEffects().clear();
                p.sendMessage("§4§l[lDiscord2FA] §aYou are now unblinded.");

        } else {
                        
                p.sendMessage("§4§l[lDiscord2FA] §a'Blind on Auth' is disabled, so you are not blinded.");
        
        }
        */

        final PlayerData playerData = getPlayerData(player);

        if (playerData.getCheckCode() != null
                && ConfigUtil.getBoolean("generate-new-code-always")) {
            playerData.setCheckCode(null);
        }
    }

    public void checkPlayer(Player player) {
        final boolean playerExits = plugin.getProvider().playerExits(player);
        if (!plugin.isConnected()) {
            if (playerExits)
                player.sendMessage(
                        "§4§l[lDiscord2FA] §cHey! The bot connection must be provided to we send a code."
                );

            plugin.getLogger().warning(
                    "Oops, the bot connect failed. Please provide the bot connection."
            );
            player.kickPlayer("§4§l[lDiscord2FA] §cOops, the bot connect failed. Please provide the bot connection.");
            return;
        }

        // Si el jugador tiene op y su cuenta no está vinculada a discord, se le expulsa del servidor.
        else if (ConfigUtil.getBoolean("anti-op")) {
                if (player.isOp()) {
                        player.sendMessage(
                                "§4§l[Discord2FA] §cHey! You can't be OP to use this server because your account is not linked to Discord."
                        );
                        //player.kickPlayer("§4§l[Discord2FA] §cHey! You can't be OP to use this server because your account is not linked to Discord.");
                        return;
                }
                return;
        }

        else if (!playerExits) {
            return;
        }

        else if (ConfigUtil.getBoolean("auto-verification")) {
            final String currentlyIp = player.getAddress().getAddress().getHostAddress();
            final String lastIp = plugin.getProvider().getIP(player);

            if (currentlyIp.equals(lastIp)) {
                player.sendMessage(
                        ConfigUtil.getString(
                                "messages.auto-verify-success-message"
                        )
                );
                return;
            }
        }

        addPlayerToCheck(player);
    }

    public void completeAuth(Player player) {
        final PlayerData playerData = getPlayerData(player);
        String admin_message_sended = null;
    
        if (!isInCheck(player))
            return;
    
        checkPlayers.remove(player);
    
        
        unloadData(player);


        if (ConfigUtil.getBoolean("logs.enabled")) {
    
            plugin.getLogger().info("Logs enabled, checking if discord embed logs are enabled...");
    
            if (ConfigUtil.getBoolean("logs.enable-admin-discord-embed")) {
    
                plugin.getLogger().info("Trying to send the embed message to the Discord Admins...");
    
                List<String> adminIds = ConfigUtil.getStringList("logs.admin-ids");
                boolean sendEmbed = sendLogEmbed(adminIds,
                        ConfigUtil.getString("logs.embed-player-authenticated.title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                        ConfigUtil.getString("logs.embed-player-authenticated.description", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                        player,
                        ConfigUtil.getString("logs.embed-player-authenticated.first-field-title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                        ConfigUtil.getString("logs.embed-player-authenticated.first-field-subtitle", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                        ConfigUtil.getString("logs.embed-player-authenticated.second-field-title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                        ConfigUtil.getString("logs.embed-player-authenticated.second-field-subtitle", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()))
                );
    
                if (sendEmbed) {
                    plugin.getLogger().info(player.getName() + " was authenticated, and the embed message was sent.");
                    admin_message_sended = "STATUS: SENDED, TYPE: EMBED.";
                } else {
                    player.sendMessage(ConfigUtil.getString("messages.msg-send-failed"));
                    plugin.getLogger().info(player.getName() + " was authenticated, but the embed message could not be sent.");
                    admin_message_sended = "STATUS: FAILED, TYPE: EMBED.";
                }
    
            } else {
                sendLog(
                        ConfigUtil.getStringList("logs.admin-ids"),
                        ConfigUtil.getString("logs.player-authenticated", "player:" + player.getName()),
                        player
                );
                plugin.getLogger().info(player.getName() + " was authenticated, and the normal message was sent.");
                admin_message_sended = "STATUS: SENDED, TYPE: NORMAL.";
            }
    
        } else {
            plugin.getLogger().info("Logs are disabled, so the private message was not sent.");
            admin_message_sended = "STATUS: DISABLED, TYPE: NONE.";
        }


        plugin.getLogger().info(
                player.getName() + "'s account was authenticated. Log " + admin_message_sended
        );
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getServer().getPluginManager().callEvent(
                        new AuthCompleteEvent(player)
                )
        );

    }

    private String getCity(String ip) {
        
        // Creamos la peticion a la API
        String url = "http://ip-api.com/json/" + ip;
        String json = null;

        if (ip == null || ip.equals("127.0.0.1") || ip.equals("localhost") || ip.equals("0.0.0.0")) {
                return ConfigUtil.getString("messages.unknown-location-placeholder");
            }

        // Obtenemos la respuesta de la API y la guardamos en un String
        try {
                URL api = new URL(url);
                URLConnection connection = api.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                json = in.readLine();
                in.close();
        } catch (Exception e) {
                e.printStackTrace();

                plugin.getLogger().info(
                        "The URL " + url + " API could not be reached, so the City is unknown."
                );
                return ConfigUtil.getString("messages.unknown-location-placeholder");

        }
        
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        JsonElement city = jsonObject.get("city"); 

        if (city != null) {
                String str_city = city.toString().replaceAll("\"", "");
                return str_city;
        } else {
                return ConfigUtil.getString(
                "messages.unknown-location-placeholder"
                );
        }

    }

    private String getISP(String ip) {
        
        // Creamos la peticion a la API
        String url = "http://ip-api.com/json/" + ip;
        String json = null;

        if (ip == null || ip.equals("127.0.0.1") || ip.equals("localhost") || ip.equals("0.0.0.0")) {
                return ConfigUtil.getString("messages.unknown-location-placeholder");
            }

        // Obtenemos la respuesta de la API y la guardamos en un String
        try {
                URL api = new URL(url);
                URLConnection connection = api.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                json = in.readLine();
                in.close();
        } catch (Exception e) {
                e.printStackTrace();

                plugin.getLogger().info(
                        "The URL " + url + " API could not be reached, so the ISP is unknown."
                );
                return ConfigUtil.getString("messages.unknown-location-placeholder");

        }
        
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        JsonElement isp = jsonObject.get("isp"); 

        if (isp != null) {
                String str_isp = isp.toString().replaceAll("\"", "");
                return str_isp;
        } else {
                return ConfigUtil.getString(
                "messages.unknown-location-placeholder"
                );
        }

    }

    private String getCountry(String ip) {
        
        // Creamos la peticion a la API
        String url = "http://ip-api.com/json/" + ip;
        String json = null;

        if (ip == null || ip.equals("127.0.0.1") || ip.equals("localhost") || ip.equals("0.0.0.0")) {
                return ConfigUtil.getString("messages.unknown-location-placeholder");
            }

        // Obtenemos la respuesta de la API y la guardamos en un String
        try {
                URL api = new URL(url);
                URLConnection connection = api.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                json = in.readLine();
                in.close();
        } catch (Exception e) {


                e.printStackTrace();

                plugin.getLogger().info(
                        "The URL " + url + " API could not be reached, so the country is unknown."
                );
                return ConfigUtil.getString("messages.unknown-location-placeholder");

        }
        
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        JsonElement country = jsonObject.get("country"); 

        if (country != null) {
                String str_country = country.toString().replaceAll("\"", "");
                return str_country;
        } else {
                return ConfigUtil.getString(
                "messages.unknown-location-placeholder"
                );
        }

    }

    public void sendCode(Player player, String code) {
        final PlayerData playerData = getPlayerData(player);
        final String memberId = plugin.getProvider().getMemberID(player);

        playerData.setCheckCode(code);

        if (memberId == null) {
            plugin.getLogger().warning(
                    "We're cannot get player's Discord ID?"
            );
            return;
        }

        if (playerData.getLeftRights() == 0)
            playerData.setLeftRights(
                ConfigUtil.getInt(
                        "number-of-rights"
                )
        );

    
        if (ConfigUtil.getBoolean("messages.enable-discord-embed")) {

                if (!sendLogEmbed(
                        Collections.singletonList(memberId),
                        // Title
                        ConfigUtil.getString(
                                "messages.embed.title",
                                "code:" + playerData.getCheckCode(), 
                                "ip:" + player.getAddress().getAddress().getHostAddress(),
                                "player:" + player.getName(),
                                "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()),
                                "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()),
                                "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())
                        ),
                        // Description
                        ConfigUtil.getString(
                                "messages.embed.description",
                                "code:" + playerData.getCheckCode(), 
                                "ip:" + player.getAddress().getAddress().getHostAddress(),
                                "player:" + player.getName(),
                                "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()),
                                "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()),
                                "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())
                        ),
                        //player
                        player,
                        // first field
                        ConfigUtil.getString(
                                "messages.embed.first-field-title",
                                "code:" + playerData.getCheckCode(), 
                                "ip:" + player.getAddress().getAddress().getHostAddress(),
                                "player:" + player.getName(),
                                "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()),
                                "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()),
                                "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())
                        ),
                        // first field value
                        ConfigUtil.getString(
                                "messages.embed.first-field-subtitle",
                                "code:" + playerData.getCheckCode(), 
                                "ip:" + player.getAddress().getAddress().getHostAddress(),
                                "player:" + player.getName(),
                                "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()),
                                "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()),
                                "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())
                        ),
                        // second field
                        ConfigUtil.getString(
                                "messages.embed.second-field-title",
                                "code:" + playerData.getCheckCode(), 
                                "ip:" + player.getAddress().getAddress().getHostAddress(),
                                "player:" + player.getName(),
                                "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()),
                                "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()),
                                "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())
                        ),
                        // second field value
                        ConfigUtil.getString(
                                "messages.embed.second-field-subtitle",
                                "code:" + playerData.getCheckCode(), 
                                "ip:" + player.getAddress().getAddress().getHostAddress(),
                                "player:" + player.getName(),
                                "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()),
                                "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()),
                                "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())
                        )

                )) player.sendMessage(ConfigUtil.getString("messages.msg-send-failed"));
                
        } else {

                if (!sendLog(
                        Collections.singletonList(memberId),
                        ConfigUtil.getString(
                                "messages.discord-message",
                                "code:" + playerData.getCheckCode(), 
                                "ip:" + player.getAddress().getAddress().getHostAddress(),
                                "player:" + player.getName(),
                                "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()),
                                "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()),
                                "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())
                        ),
                        player
                )) player.sendMessage(ConfigUtil.getString("messages.msg-send-failed"));

        }

    }

    public void failPlayer(Player player) {
        final Server server = plugin.getServer();
        String admin_message_sended = "";

        getPlayerData(player).setLeftRights(
                ConfigUtil.getInt("number-of-rights")
        );
        server.dispatchCommand(
                server.getConsoleSender(),
                ConfigUtil.getString(
                        "rights-reached-console-command",
                        "player:" + player.getName()
                )
        );

        if (ConfigUtil.getBoolean("logs.enabled")) {

                if (ConfigUtil.getBoolean("logs.enable-admin-discord-embed")) {
    
                        plugin.getLogger().info("Trying to send the embed message to the Discord Admins...");
            
                        List<String> adminIds = ConfigUtil.getStringList("logs.admin-ids");
                        boolean sendEmbed = sendLogEmbed(adminIds,
                                ConfigUtil.getString("logs.embed-reached-limit.title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                                ConfigUtil.getString("logs.embed-reached-limit.description", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                                player,
                                ConfigUtil.getString("logs.embed-reached-limit.first-field-title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                                ConfigUtil.getString("logs.embed-reached-limit.first-field-subtitle", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                                ConfigUtil.getString("logs.embed-reached-limit.second-field-title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress())),
                                ConfigUtil.getString("logs.embed-reached-limit.second-field-subtitle", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()))
                        );
            
                        if (sendEmbed) {
                            plugin.getLogger().info(player.getName() + " reached the limit, and the embed message was sent.");
                            admin_message_sended = "STATUS: SENDED, TYPE: EMBED.";
                        } else {
                            player.sendMessage(ConfigUtil.getString("messages.msg-send-failed"));
                            plugin.getLogger().info(player.getName() + " reached the limit, but the embed message could not be sent.");
                            admin_message_sended = "STATUS: FAILED, TYPE: EMBED.";
                        }
            
                    } else {

                        if (!sendLog(
                                ConfigUtil.getStringList(
                                        "logs.admin-ids"
                                ),
                                ConfigUtil.getString(
                                        "logs.player-reached-limit",
                                        "player:" + player.getName()
                                ),
                                player
                        )) player.sendMessage(ConfigUtil.getString("messages.msg-send-failed"));

                    }


                    // Enviamos log a la consola con la string de admin_message_sended
                    plugin.getLogger().info("Player " + player.getName() + " reached the limit of failed attempts. " + admin_message_sended);
        
        }

        plugin.getServer().getPluginManager().callEvent(
                new AuthFailEvent(player)
        );
    }

    public void failPlayer(Player player, int i) {
        final PlayerData playerData = getPlayerData(player);
        String admin_message_sended = "";

        playerData.setLeftRights(
                playerData.getLeftRights() - i
        );

        player.sendMessage(
                ConfigUtil.getString(
                        "messages.auth-command.invalid-code-message",
                        "rights:" + playerData.getLeftRights()
                )
        );

        if (ConfigUtil.getBoolean("logs.enabled")) {

                if (ConfigUtil.getBoolean("logs.enable-admin-discord-embed")) {
    
                        plugin.getLogger().info("Trying to send the embed message to the Discord Admins...");
            
                        List<String> adminIds = ConfigUtil.getStringList("logs.admin-ids");
                        boolean sendEmbed = sendLogEmbed(
                                adminIds,
                                ConfigUtil.getString("logs.embed-player-entered-wrong-code.title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()), "left:" + playerData.getLeftRights()),
                                ConfigUtil.getString("logs.embed-player-entered-wrong-code.description", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()), "left:" + playerData.getLeftRights()),
                                player,
                                ConfigUtil.getString("logs.embed-player-entered-wrong-code.first-field-title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()), "left:" + playerData.getLeftRights()),
                                ConfigUtil.getString("logs.embed-player-entered-wrong-code.first-field-subtitle", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()), "left:" + playerData.getLeftRights()),
                                ConfigUtil.getString("logs.embed-player-entered-wrong-code.second-field-title", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()), "left:" + playerData.getLeftRights()),
                                ConfigUtil.getString("logs.embed-player-entered-wrong-code.second-field-subtitle", "ip:" + player.getAddress().getAddress().getHostAddress(), "player:" + player.getName(), "country:" + this.getCountry(player.getAddress().getAddress().getHostAddress()), "city:" + this.getCity(player.getAddress().getAddress().getHostAddress()), "isp:" + this.getISP(player.getAddress().getAddress().getHostAddress()), "left:" + playerData.getLeftRights())
                        );
                    
                        if (sendEmbed) {
                            plugin.getLogger().info(player.getName() + " entered wrong code, and the embed message was sent.");
                            admin_message_sended = "STATUS: SENDED, TYPE: EMBED.";
                        } else {
                            player.sendMessage(ConfigUtil.getString("messages.msg-send-failed"));
                            plugin.getLogger().info(player.getName() + " entered wrong code, but the embed message could not be sent.");
                            admin_message_sended = "STATUS: FAILED, TYPE: EMBED.";
                        }
            
                    } else {
                        if (!sendLog(
                                ConfigUtil.getStringList(
                                        "logs.admin-ids"),
                                ConfigUtil.getString(
                                        "logs.player-entered-wrong-code",
                                        "player:" + player.getName(),
                                        "left:" + playerData.getLeftRights()
                                ),
                                player
                        )) player.sendMessage(ConfigUtil.getString("messages.msg-send-failed"));
                        admin_message_sended = "STATUS: SENDED, TYPE: NORMAL.";
                    }

                    // Enviamos un log con admin_message_sended
                    plugin.getLogger().info("Player " + player.getName() + " entered wrong code. " + admin_message_sended);
        }
    }

    public String[] getAuthMessage(boolean state, int i) {
        final boolean bool = state && i == -1;
        final int format = bool ? 1 : 2;

        final String replaceKey = bool ? "countdown" : "seconds";
        final String replacementKey = bool ? String.valueOf(
                ConfigUtil.getInt(
                        "auth-countdown"
                )
        ) : i + " second" + (i > 1 ? "s" : "");

        return ConfigUtil.getString(
                "messages.auth-message.format-" + format,
                replaceKey + ":" + replacementKey
        ).split("%nl%");
    }


    private static final String NUMERIC_CHARS = "0123456789";
    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String ALPHABETIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static Random random = new Random();

    public static String randomNumeric(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            int randomIndex = random.nextInt(NUMERIC_CHARS.length());
            char randomChar = NUMERIC_CHARS.charAt(randomIndex);
            sb.append(randomChar);
        }
        return sb.toString();
    }

    public static String randomAlphanumeric(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            int randomIndex = random.nextInt(ALPHANUMERIC_CHARS.length());
            char randomChar = ALPHANUMERIC_CHARS.charAt(randomIndex);
            sb.append(randomChar);
        }
        return sb.toString();
    }

    public static String randomAlphabetic(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            int randomIndex = random.nextInt(ALPHABETIC_CHARS.length());
            char randomChar = ALPHABETIC_CHARS.charAt(randomIndex);
            sb.append(randomChar);
        }
        return sb.toString();
    }

    public String getRandomCode(int count) {
        final CodeType codeType;
        try {
            codeType = CodeType.valueOf(
                    ConfigUtil.getString(
                            "code-type"
                    )
            );
        } catch (IllegalArgumentException e) {
            return "Invalid code type. **Please check it in config.yml** (Write it in upper case)";
        }

        switch (codeType) {
            case NUMERIC:
                //return RandomStringUtils.randomNumeric(count);
                return Discord2FAManager.randomNumeric(count);
            case ALPHANUMERIC:
                //return RandomStringUtils.randomAlphanumeric(count);
                return Discord2FAManager.randomAlphanumeric(count);
            case ALPHABETIC:
                //return RandomStringUtils.randomAlphabetic(count);
                return Discord2FAManager.randomAlphabetic(count);


        }

        return null;
    }

    public void enable2FA(Player player, String str) {
        final PlayerData playerData = getPlayerData(player);
        final String code = playerData.getConfirmCode();

        if (!str.equals(code))
            return;

        final User user = playerData.getConfirmUser();

        plugin.getProvider().addToVerifyList(
                player, user.getId()
        );
        player.sendMessage(
                ConfigUtil.getString(
                        "messages.discord2fa-command.player-auth-enabled"
                )
        );

        if (!sendLog(
                ConfigUtil.getString(
                        "authentication-for-players.successfully-confirmed"
                ),
                user
        )) player.sendMessage(
                ConfigUtil.getString(
                        "messages.msg-send-failed"
                )
        );

        final int taskID = playerData.getPlayerTaskID();
        if (taskID != 0) Bukkit.getScheduler().cancelTask(taskID);

        unloadData(player);
    }

    public void disable2FA(Player player) {
        if (!plugin.getProvider().playerExits(player)) {
            player.sendMessage(
                    ConfigUtil.getString(
                            "messages.discord2fa-command.player-auth-already-disabled"
                    )
            );
            return;
        }

        plugin.getProvider().removeFromVerifyList(player);
        player.sendMessage(
                ConfigUtil.getString(
                        "messages.discord2fa-command.player-auth-disabled"
                )
        );
    }

    public void sendEnabling2FARequest(Player player) {
        if (plugin.getProvider().playerExits(player)) {
            player.sendMessage(
                    ConfigUtil.getString(
                            "messages.discord2fa-command.player-auth-already-enabled"
                    )
            );
            return;
        }

        final PlayerData playerData = getPlayerData(player);
        final String confCode = playerData.getConfirmCode();

        if (confCode != null && confCode.equals("§"))
            return;

        playerData.setConfirmCode("§");
        player.sendMessage(
                ConfigUtil.getString(
                        "messages.discord2fa-command.player-auth-enter-discord"
                )
        );

        final BukkitTask task = new Cancel2FAReqTask(
                this, player, true
        ).runTaskLater(plugin, 20 * 30L);
        playerData.setPlayerTaskID(task.getTaskId());
    }

    public void sendConfirmCode(Player player, String str) {
        final String[] split = str.split("#");
        final User user;

        if (split.length == 2)
            user = plugin.getBot()
                    .getJDA()
                    .getUserByTag(
                            (split[0].length() >= 2 && split[0].length() <= 32 ? split[0] : "§§"),
                            (split[1].length() == 4 ? split[1] : "0000")
                    );
        else
            user = null;

        if (user == null) {
            player.sendMessage(
                    ConfigUtil.getString(
                            "messages.discord2fa-command.player-auth-discord-acc-not-found"
                    )
            );
            return;
        }

        final PlayerData playerData = getPlayerData(player);
        final String code = getRandomCode(
                ConfigUtil.getInt(
                        "code-lenght"
                )
        );
        playerData.setConfirmCode(code);

        if (!sendLog(
                ConfigUtil.getString(
                        "authentication-for-players.confirm-your-account",
                        "nl:" + "\n",
                        "code:" + code,
                        "player:" + player.getName()
                ),
                user
        )) player.sendMessage(
                ConfigUtil.getString(
                        "messages.msg-send-failed"
                )
        );

        playerData.setConfirmUser(user);
        player.sendMessage(
                ConfigUtil.getString(
                        "messages.discord2fa-command.player-auth-confirm-code-sent"
                )
        );

        final int taskID = playerData.getPlayerTaskID();
        if (taskID != 0) Bukkit.getScheduler().cancelTask(taskID);

        final BukkitTask task = new Cancel2FAReqTask(
                this, player, false
        ).runTaskLater(plugin, 20 * 30L);
        playerData.setPlayerTaskID(task.getTaskId());
    }

    public void cancel2FAReq(Player player) {
        unloadData(player);
        player.sendMessage(
                ConfigUtil.getString(
                        "messages.discord2fa-command.player-auth-timeout"
                )
        );
        
    }

    public boolean sendLog(String path, User user) {
        final boolean[] bool = {true};
        if (user == null)
            return false;
        user.openPrivateChannel()
                .submit()
                .thenCompose(channel ->
                        channel.sendMessage(path).submit()
                ).whenComplete((message, error) -> {
                    if (error != null)
                        bool[0] = false;
                });
        return bool[0];
    }


    public boolean sendLogEmbed(List<String> stringList, String title, String description, Player player,
    String first_field_title, String first_field_subtitle, String second_field_title,
    String second_field_subtitle) {

        final boolean[] bool = {true};
        final String memberId = plugin.getProvider().getMemberID(player);

        stringList.forEach(id -> {

                final User user = plugin.getBot().getJDA().getUserById(id);

                if (user == null) {
                        return; // Evitar enviar mensajes al propio usuario
                }

                EmbedBuilder embedBuilder = new EmbedBuilder();
                
                embedBuilder.setTitle(title);
                embedBuilder.setDescription(description);

                // Agregar campo con una imagen como URL
                embedBuilder.addField(first_field_title, first_field_subtitle, true);
                
                embedBuilder.setThumbnail(ConfigUtil.getString(
                        "discord-embed-image",
                        "player:" + player.getName()
                ));

                // Agregar campo con una imagen como URL
                embedBuilder.addField(second_field_title, second_field_subtitle, true);

                embedBuilder.setTimestamp(OffsetDateTime.now());

                MessageEmbed embed = embedBuilder.build();

                user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(embed))
                .queue(
                        success -> {
                                plugin.getLogger().info("[EMBED] Mensaje enviado correctamente");
                        },
                        error -> {
                                // Ocurrió un error al enviar el mensaje
                                plugin.getLogger().info("[EMBED] Ocurrió un error al enviar el mensaje");
                                if (error != null)
                                bool[0] = false;
                        }  
                );
                

        });

        return bool[0];

    }

    public boolean sendLog(List<String> stringList, String path, Player player) {
        final boolean[] bool = {true};
        stringList.forEach(id ->  {
            final User user = plugin.getBot()
                    .getJDA()
                    .getUserById(id);

            if (user == null)
                return;
            user.openPrivateChannel()
                    .submit()
                    .thenCompose(channel ->
                            channel.sendMessage(path).submit()
                    ).whenComplete((message, error) -> {
                        if (error != null)
                            bool[0] = false;
                    });
        });
        return bool[0];
    }

    public void sitPlayer(Player player) {
        final ArmorStand armorStand = (ArmorStand) player.getLocation()
                .getWorld()
                .spawnEntity(
                        player.getLocation(), EntityType.ARMOR_STAND
                );
        armorStand.setVisible(false);
        armorStand.setPassenger(player);
        armorStands.put(player,armorStand);
    }

    public void unSitPlayer(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (armorStands.containsKey(player))
                        armorStands.get(player).remove();;
        });
    }

    public void reSitPlayer(Player player) {
        if (armorStands.containsKey(player))
            armorStands.get(player).setPassenger(player);
    }

    public boolean isInCheck(Player player) {
        return checkPlayers.contains(player);
    }

    public Set<Player> getCheckPlayers() {
        return checkPlayers;
    }

    public Map<Player, ArmorStand> getArmorStands() {
        return armorStands;
    }

    private enum CodeType {
        NUMERIC,
        ALPHANUMERIC,
        ALPHABETIC
    }
}

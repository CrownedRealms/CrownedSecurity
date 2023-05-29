package io.github.eylexlive.discord2fa.command;

import io.github.eylexlive.discord2fa.Discord2FA;
import io.github.eylexlive.discord2fa.manager.Discord2FAManager;
import io.github.eylexlive.discord2fa.provider.Provider;
import io.github.eylexlive.discord2fa.util.ConfigUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/*
 *	Created by EylexLive on Feb 23, 2020.
 *  Recoded by myc0de on May 29, 2023.
 *	Currently version: 3.5
 */

public class Discord2FACommand implements CommandExecutor {

    private final Discord2FA plugin;
    private final String[] mainMessage;

    public Discord2FACommand(Discord2FA plugin) {
        this.plugin = plugin;
        mainMessage = new String[]
                {
                "§6§lDiscord2FA (Fork Version) running on the server. §f§lVersion: §6§lv"+
                        plugin.getDescription().getVersion(),
                "§fRecoded by:§6§l myc0de",
                        "",
                        "https://www.spigotmc.org/resources/%E2%9C%A8-discord2fa-2-fork-version-%E2%9C%A8.110132/"
        };
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        final Discord2FAManager manager = plugin.getDiscord2FAManager();
        final Provider provider = plugin.getProvider();

        Player onlinePlayer = null;
        if (args.length >= 2) {
            onlinePlayer = plugin.getServer().getPlayer(args[1]);
            if (onlinePlayer == null) {
                sender.sendMessage(
                        "§cPlayer not found: " + args[1]
                );
                return true;
            }
        }

        if (args.length == 0) {
            sender.sendMessage(mainMessage);
            if (sender.hasPermission("discord2fa.admin")) {
                sender.sendMessage(
                        "§fBot status: §"+ (plugin.isConnected() ? "aConnected." : "cConnect failed!")
                );
                sender.sendMessage(
                        ConfigUtil.getString(
                                "messages.discord2fa-command.help-message"
                        ).split("%nl%")
                );
            }
        }

        else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("verifylist")) {
                if (!sender.hasPermission("discord2fa.admin")) {
                    sender.sendMessage(
                            "§cYou do not have permission to use that command."
                    );
                    return true;
                }

                sender.sendMessage(
                        ConfigUtil.getString(
                                "messages.discord2fa-command.verifyList-message",
                                "list:" + provider.getListMessage()
                        ).split("%nl%")
                );
            }

            else if (args[0].equalsIgnoreCase("reloadconfig")) {
                if (!sender.hasPermission("discord2fa.admin")) {
                    sender.sendMessage(
                            "§cYou do not have permission to use that command."
                    );
                    return true;
                }

                plugin.getConfig().reload();
                sender.sendMessage(
                        ConfigUtil.getString(
                                "messages.discord2fa-command.reload-success"
                        )
                );
            }

            else if (args[0].equalsIgnoreCase("enable")) {
                if (!ConfigUtil.getBoolean("authentication-for-players.enabled")) {
                    sender.sendMessage(
                            "§cYou can do it when 2FA enabled for players."
                    );
                    return true;
                }

                manager.sendEnabling2FARequest((Player) sender);
            }

            else if (args[0].equalsIgnoreCase("disable")) {
                if (!ConfigUtil.getBoolean("authentication-for-players.enabled")) {
                    sender.sendMessage(
                            "§cYou can do it when 2FA enabled for players."
                    );
                    return true;
                }

                manager.disable2FA((Player) sender);
            }
        }

        else if (args.length == 2) {
            if (!sender.hasPermission("discord2fa.admin")) {
                sender.sendMessage(
                        "§cYou do not have permission to use that command."
                );
                return true;
            }

            if (args[0].equalsIgnoreCase("removefromcheck")) {
                manager.removePlayerFromCheck(onlinePlayer);
            }

            else if (args[0].equalsIgnoreCase("generatebackupcodes")) {
                final List<String> codes = provider.generateBackupCodes(onlinePlayer);
                sender.sendMessage(
                        ConfigUtil.getString(
                                "messages.discord2fa-command.backup-codes-generated",
                                "player:" + onlinePlayer.getName(),
                                "codes:" + codes.toString()
                        )
                );
            }
        }

        else if (args.length == 3) {
            if (!sender.hasPermission("discord2fa.admin")) {
                sender.sendMessage(
                        "§cYou do not have permission to use that command."
                );
                return true;
            }

            final String discord = args[2];
            if (args[0].equalsIgnoreCase("addtoverifylist")) {
                if (discord.length() != 18) {
                    sender.sendMessage(
                            ConfigUtil.getString(
                                    "messages.discord2fa-command.invalid-discord-id"
                            )
                    );
                    return true;
                }

                provider.addToVerifyList(onlinePlayer, discord);
                sender.sendMessage(
                        ConfigUtil.getString(
                                "messages.discord2fa-command.added-to-verifyList-message"
                                , "player:" + onlinePlayer.getName(),
                                "id:" + discord
                        )
                );
            }

            else if (args[0].equalsIgnoreCase("removefromverifylist")) {
                provider.removeFromVerifyList(onlinePlayer);
                sender.sendMessage(
                        ConfigUtil.getString(
                                "messages.discord2fa-command.removed-from-verifyList-message",
                                "player:" + onlinePlayer.getName(),
                                "id:" + discord
                        )
                );
            }
        }
        return true;
    }
}
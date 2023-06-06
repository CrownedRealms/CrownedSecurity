package io.github.eylexlive.discord2fa;

import io.github.eylexlive.discord2fa.bot.Bot;
import io.github.eylexlive.discord2fa.command.*;
import io.github.eylexlive.discord2fa.listener.*;
import io.github.eylexlive.discord2fa.manager.*;
import io.github.eylexlive.discord2fa.provider.MySQLProvider;
import io.github.eylexlive.discord2fa.provider.Provider;
import io.github.eylexlive.discord2fa.provider.YamlProvider;
import io.github.eylexlive.discord2fa.util.Config;
import io.github.eylexlive.discord2fa.util.ConfigUtil;
import io.github.eylexlive.discord2fa.util.Metrics;
import io.github.eylexlive.discord2fa.util.UpdateCheck;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.time.Duration;
import java.util.Arrays;

/*
 *	Created by EylexLive on Feb 23, 2020.
 *	Currently version: 3.5
 */

public class Discord2FA extends JavaPlugin {

    private static Discord2FA instance;

    private Bot bot;

    private Config config;

    private Discord2FAManager discord2FAManager;
    private HookManager hookManager;

    private Provider provider;

    private boolean validateEdm;

    @Override
    public void onEnable() {
        if (instance != null)
            throw new IllegalStateException(
                    "Discord2FA already enabled!"
            );

        instance = this;

        config = new Config("config");

        getCommand("auth").setExecutor(
                new AuthCommand(this)
        );
        getCommand("discord2fa").setExecutor(
                new Discord2FACommand(this)
        );

        registerListeners();

        discord2FAManager = new Discord2FAManager(this);
        hookManager = new HookManager(this);

        provider = (
                isMYSQLEnabled() ? new MySQLProvider(this)
                        :
                        new YamlProvider(this)
        );
        provider.setupDatabase();

        validateEdm = checkEdm();

        new Metrics(this);
        new UpdateCheck(this);

        bot = new Bot(this);
        bot.login();

    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);

        discord2FAManager.getArmorStands()
                .values()
                .forEach(
                        Entity::remove
                );

        discord2FAManager.getCheckPlayers()
                .forEach(player ->
                        player.kickPlayer(
                                "§cServer closed or Discord2FA reloaded!"
                        )
                );

        if (provider != null) provider.saveDatabase();

        if (bot != null) {
            
            bot.logout();
            bot.jda.shutdownNow();
        
        }

        bot.jda.shutdown();
        bot.jda.getHttpClient().connectionPool().evictAll();
        bot.jda.getHttpClient().dispatcher().executorService().shutdown();
        
        // Esperamos 10 segundos y volvemos a comprobar si se han cerrado las conexiones.

        try {
            Thread.sleep(Duration.ofSeconds(5).toMillis());
            bot.jda.shutdown();
            bot.jda.getHttpClient().connectionPool().evictAll();
            bot.jda.getHttpClient().dispatcher().executorService().shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        

        getLogger().info("Conexiones del JDA cerradas correctamente.");

    }

    private void registerListeners() {
        final PluginManager pluginManager = getServer().getPluginManager();
        Arrays.asList(
                new AsyncPlayerChatListener(this),
                new BlockBreakListener(this),
                new BlockPlaceListener(this),
                new EntityDamageByEntityListener(this),
                new InventoryClickListener(this),
                new InventoryOpenListener(this),
                new PlayerCommandUseListener(this),
                new PlayerDropItemListener(this),
                new PlayerInteractListener(this),
                new ConnectionListener(this),
                new EntityDismountListener(this)
        ).forEach(listener ->
                pluginManager.registerEvents(
                        listener, this
                )
        );
    }

    private boolean checkEdm() {
        try {
            EntityDismountEvent.class.getMethod("isCancelled");
        } catch (NoSuchMethodException e) {
            return false;
        }
        return true;
    }

    @NotNull
    public static Discord2FA getInstance() {
        return instance;
    }

    @NotNull
    public Bot getBot() {
        return bot;
    }

    @NotNull
    public Config getConfig() {
        return config;
    }

    @NotNull
    public Discord2FAManager getDiscord2FAManager() {
        return discord2FAManager;
    }

    @NotNull
    public HookManager getHookManager() {
        return hookManager;
    }

    @NotNull
    public Provider getProvider() {
        return provider;
    }

    public boolean isConnected() {
        return bot.getJDA() != null;
    }

    public boolean isMYSQLEnabled() {
        return ConfigUtil.getBoolean(
                "mysql.enabled"
        );
    }

    public boolean isBlindOnAuthEnabled() {
        return ConfigUtil.getBoolean(
                "blind-on-auth"
        );
    }

    public boolean isValidateEdm() {
        return validateEdm;
    }
}

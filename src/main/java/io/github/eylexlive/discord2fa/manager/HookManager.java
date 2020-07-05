package io.github.eylexlive.discord2fa.manager;

import io.github.eylexlive.discord2fa.Main;
import io.github.eylexlive.discord2fa.hook.PluginHook;

import java.util.Arrays;

/*
 *	Created by EylexLive on Feb 23, 2020.
 *	Currently version: 2.5
 */

public class HookManager {
    private Main plugin;
    public HookManager(Main plugin) {
        this.plugin = plugin;
    }
    public void hook() {
        Arrays.asList("Authme", "LoginSecurity").forEach(hookPl ->  {
            PluginHook pluginHook = new PluginHook(hookPl, this.plugin, this.plugin.getConfig().getBoolean(hookPl.toLowerCase() + "-support"));
            pluginHook.hook();
        });
    }
}
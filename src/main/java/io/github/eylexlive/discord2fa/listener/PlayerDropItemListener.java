package io.github.eylexlive.discord2fa.listener;

import io.github.eylexlive.discord2fa.Main;
import io.github.eylexlive.discord2fa.util.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

/*
 *	Created by EylexLive on Feb 23, 2020.
 *	Currently version: 2.5
 */

public class PlayerDropItemListener implements Listener {
    private Main plugin;
    public PlayerDropItemListener(Main plugin) {
        this.plugin = plugin;
    }
    @EventHandler
    public void handleItemDrop(PlayerDropItemEvent event) {
        String settingsPrefix = "canceled-events.";
        if (!this.plugin.getConfig().getBoolean(settingsPrefix+"item-drop.cancel"))
            return;
        Player player= event.getPlayer();
        if (this.plugin.getDiscord2FAManager().isInCheck(player)) {
            this.plugin.getConfig().getStringList(settingsPrefix+"item-drop.whitelisted-materials").stream().filter(whitelistedMaterial -> event.getItemDrop().getItemStack().getType().getId() != Material.getMaterial(whitelistedMaterial).getId()).forEach(whitelistedMaterial -> {
                event.setCancelled(true);
                player.sendMessage(Color.translate(this.plugin.getConfig().getString("messages.event-messages.item-drop-message")));
            });
        }
    }
}
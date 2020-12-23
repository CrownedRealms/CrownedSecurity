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
 *	Currently version: 3.2
 */

public class PlayerDropItemListener implements Listener {

    private final Main plugin;

    public PlayerDropItemListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void handleItemDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("canceled-events.item-drop.cancel"))
            return;
        final Player player= event.getPlayer();
        if (plugin.getDiscord2FAManager().isInCheck(player)) {
            final boolean cancelled = !plugin.getConfig().getStringList("canceled-events.item-drop.whitelisted-materials")
                    .contains(event.getItemDrop().getItemStack().getType().name());
            event.setCancelled(cancelled);
            if (cancelled) player.sendMessage(Color.translate(plugin.getConfig().getString("messages.event-messages.item-drop-message")));
        }
    }
}

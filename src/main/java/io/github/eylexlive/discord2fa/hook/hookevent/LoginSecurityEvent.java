package io.github.eylexlive.discord2fa.hook.hookevent;

import com.lenis0012.bukkit.loginsecurity.events.AuthActionEvent;
import io.github.eylexlive.discord2fa.Discord2FA;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/*
 *	Created by EylexLive on Feb 23, 2020.
 *	Currently version: 3.5
 */

@SuppressWarnings("unused")
public class LoginSecurityEvent implements Listener {

    private final Discord2FA plugin;

    public LoginSecurityEvent(){
        this.plugin = Discord2FA.getInstance();
    }

    @EventHandler
    public void handleLoginSecurityLogin(AuthActionEvent event) {
        plugin.getDiscord2FAManager().checkPlayer(
                event.getPlayer()
        );
    }
}

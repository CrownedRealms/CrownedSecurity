package io.github.eylexlive.discord2fa.provider;

import org.bukkit.entity.Player;

import java.util.List;

/*
 *	Created by EylexLive on Feb 23, 2020.
 *	Currently version: 3.5
 */

public abstract class Provider {

    public abstract void setupDatabase();

    public abstract void saveDatabase();

    public abstract void addToVerifyList(Player player, String discord);

    public abstract void removeFromVerifyList(Player player);

    public abstract void authPlayer(Player player);

    public abstract void removeBackupCode(Player player, String code);

    public abstract boolean isBackupCode(Player player, String code);

    public abstract boolean playerExits(Player player);

    public abstract String getIP(Player player);

    public abstract String getMemberID(Player player);

    public abstract String getListMessage();

    public abstract List<String> generateBackupCodes(Player player);

}

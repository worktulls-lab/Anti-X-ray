package ru.tarkmull.antixray.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.tarkmull.antixray.TarkMullAntiXray;

/** Позиция игрока — единственный вход, по которому решается, что пора показать блок. */
public final class PlayerListener implements Listener {

    private final TarkMullAntiXray plugin;

    public PlayerListener(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        plugin.hider().updateLocation(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        plugin.hider().updateLocation(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.hider().updateLocation(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        plugin.hider().forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (plugin.cfg().exemptGameModes.contains(event.getNewGameMode())) {
            plugin.hider().revealFor(event.getPlayer());
        } else {
            plugin.hider().rescanAround(event.getPlayer(), 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.hider().updateLocation(event.getPlayer());
        plugin.hider().rescanAround(event.getPlayer(), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.hider().forget(event.getPlayer());
    }
}

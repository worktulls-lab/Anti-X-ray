package ru.tarkmull.antixray.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import ru.tarkmull.antixray.TarkMullAntiXray;

public final class BlockListener implements Listener {

    private final TarkMullAntiXray plugin;

    public BlockListener(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.tracker().handleBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        plugin.tracker().handlePlace(event.getPlayer(), event.getBlock());
    }
}

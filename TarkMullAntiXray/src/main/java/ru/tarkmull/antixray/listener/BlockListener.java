package ru.tarkmull.antixray.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ru.tarkmull.antixray.TarkMullAntiXray;

/** Ломание/установка блоков: сбрасываем кэш чанка и кормим детектор. */
public final class BlockListener implements Listener {

    private final TarkMullAntiXray plugin;

    public BlockListener(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        plugin.tracker().handleBreak(event.getPlayer(), block);
        plugin.hider().invalidate(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
        plugin.hider().forgetBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        plugin.hider().invalidate(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
        plugin.hider().forgetBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(block -> {
            plugin.hider().invalidate(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
            plugin.hider().forgetBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(block -> {
            plugin.hider().invalidate(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
            plugin.hider().forgetBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        });
    }
}

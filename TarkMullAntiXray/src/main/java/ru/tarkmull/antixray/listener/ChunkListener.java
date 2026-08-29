package ru.tarkmull.antixray.listener;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.tarkmull.antixray.TarkMullAntiXray;

/** Ловим момент, когда чанк реально ушёл игроку по сети. */
public final class ChunkListener implements Listener {

    private final TarkMullAntiXray plugin;

    public ChunkListener(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        plugin.hider().handleChunkSent(event.getPlayer(), event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(PlayerChunkUnloadEvent event) {
        plugin.hider().handleChunkUnload(event.getPlayer(),
                event.getChunk().getX(), event.getChunk().getZ());
    }
}

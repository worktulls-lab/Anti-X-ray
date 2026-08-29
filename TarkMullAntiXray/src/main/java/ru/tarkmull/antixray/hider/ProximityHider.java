package ru.tarkmull.antixray.hider;

import io.papermc.paper.math.Position;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import ru.tarkmull.antixray.TarkMullAntiXray;
import ru.tarkmull.antixray.config.AntiXrayConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Подменяет игроку руды на камень, пока он дальше radius блоков,
 * и возвращает настоящий блок, когда игрок подходит ближе.
 */
public final class ProximityHider {

    private final TarkMullAntiXray plugin;
    private final Map<UUID, PlayerView> views = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, CachedChunk>> oreCache = new ConcurrentHashMap<>();
    private volatile ScheduledTask task;

    public ProximityHider(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ жизненный цикл

    public void start() {
        AntiXrayConfig cfg = plugin.cfg();
        if (!cfg.hiderEnabled) {
            return;
        }
        long periodMs = Math.max(50L, cfg.checkIntervalTicks * 50L);
        task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> tick(),
                periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public void restart() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        start();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Возвращаем настоящие блоки синхронно, иначе у игроков останутся "призраки".
        for (PlayerView view : views.values()) {
            Player player = view.player;
            if (!player.isOnline()) {
                continue;
            }
            Map<Position, BlockData> real = collectReal(player.getWorld(), drain(view));
            if (!real.isEmpty()) {
                sendChanges(player, real);
            }
        }
        views.clear();
        oreCache.clear();
    }

    // ------------------------------------------------------------------ события

    /** Чанк отправлен игроку — надо спрятать в нём далёкие руды. */
    public void handleChunkSent(Player player, Chunk chunk) {
        AntiXrayConfig cfg = plugin.cfg();
        if (!cfg.hiderEnabled || isExempt(player) || !cfg.isWorldEnabled(chunk.getWorld())) {
            return;
        }

        World world = chunk.getWorld();
        long key = BlockPos.chunkKey(chunk.getX(), chunk.getZ());
        PlayerView view = views.computeIfAbsent(player.getUniqueId(), k -> new PlayerView(player));
        view.update(player.getLocation());

        CachedChunk cached = oreCache.getOrDefault(world.getUID(), Map.of()).get(key);
        if (cached != null && !cached.expired(cfg.cacheTtlMillis)) {
            long[] positions = cached.positions();
            Bukkit.getAsyncScheduler().runNow(plugin, t -> applyHide(view, world, key, positions));
            return;
        }

        // Снимок чанка делаем в игровом потоке, разбор — асинхронно.
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            long[] positions = scan(snapshot, chunkX, chunkZ, worldMin, worldMax, cfg);
            oreCache.computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>())
                    .put(key, new CachedChunk(positions, System.currentTimeMillis()));
            applyHide(view, world, key, positions);
        });
    }

    /**
     * Прогоняет чанки вокруг игрока вручную.
     * Нужно при входе на сервер: часть ближних чанков уходит клиенту до того,
     * как плагин успевает подписаться на событие.
     */
    public void rescanAround(Player player, long delayTicks) {
        AntiXrayConfig cfg = plugin.cfg();
        if (!cfg.hiderEnabled) {
            return;
        }
        player.getScheduler().runDelayed(plugin, t -> {
            if (!player.isOnline() || isExempt(player)) {
                return;
            }
            World world = player.getWorld();
            if (!cfg.isWorldEnabled(world)) {
                return;
            }
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            for (int x = cx - 8; x <= cx + 8; x++) {
                for (int z = cz - 8; z <= cz + 8; z++) {
                    if (world.isChunkLoaded(x, z)) {
                        handleChunkSent(player, world.getChunkAt(x, z));
                    }
                }
            }
        }, null, Math.max(1L, delayTicks));
    }

    public void handleChunkUnload(Player player, int chunkX, int chunkZ) {
        PlayerView view = views.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        Set<Long> removed = view.hidden.remove(BlockPos.chunkKey(chunkX, chunkZ));
        if (removed != null) {
            view.count.addAndGet(-removed.size());
        }
    }

    public void updateLocation(Player player) {
        PlayerView view = views.get(player.getUniqueId());
        if (view != null) {
            view.update(player.getLocation());
        }
    }

    public void forget(Player player) {
        views.remove(player.getUniqueId());
    }

    /** Блок сломан/поставлен — кэш чанка устарел. */
    public void invalidate(World world, int chunkX, int chunkZ) {
        Map<Long, CachedChunk> byChunk = oreCache.get(world.getUID());
        if (byChunk != null) {
            byChunk.remove(BlockPos.chunkKey(chunkX, chunkZ));
        }
    }

    public void forgetBlock(World world, int x, int y, int z) {
        long packed = BlockPos.asLong(x, y, z);
        long key = BlockPos.chunkKey(x >> 4, z >> 4);
        for (PlayerView view : views.values()) {
            Set<Long> set = view.hidden.get(key);
            if (set != null && set.remove(packed)) {
                view.count.decrementAndGet();
            }
        }
    }

    /** Показать всем игрокам все спрятанные блоки (перезагрузка конфига, /antixray refresh). */
    public void revealEverything() {
        for (PlayerView view : views.values()) {
            Player player = view.player;
            if (!player.isOnline()) {
                continue;
            }
            List<Long> positions = drain(view);
            if (!positions.isEmpty()) {
                scheduleReveal(player, player.getWorld().getUID(), positions);
            }
        }
    }

    public void revealFor(Player player) {
        PlayerView view = views.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        List<Long> positions = drain(view);
        if (!positions.isEmpty()) {
            scheduleReveal(player, player.getWorld().getUID(), positions);
        }
    }

    public int hiddenCount(Player player) {
        PlayerView view = views.get(player.getUniqueId());
        return view == null ? 0 : view.count.get();
    }

    // ------------------------------------------------------------------ внутреннее

    private void tick() {
        AntiXrayConfig cfg = plugin.cfg();
        int radius = cfg.hideRadius;
        long radiusSq = (long) radius * radius;
        int chunkRadius = (radius >> 4) + 1;

        for (Map.Entry<UUID, PlayerView> entry : views.entrySet()) {
            PlayerView view = entry.getValue();
            Player player = view.player;
            if (!player.isOnline()) {
                views.remove(entry.getKey());
                continue;
            }

            int px = view.x;
            int py = view.y;
            int pz = view.z;
            UUID worldId = view.world;
            if (worldId == null) {
                continue;
            }

            List<Long> reveal = new ArrayList<>();
            int pcx = px >> 4;
            int pcz = pz >> 4;

            outer:
            for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
                for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                    Set<Long> set = view.hidden.get(BlockPos.chunkKey(cx, cz));
                    if (set == null || set.isEmpty()) {
                        continue;
                    }
                    for (Iterator<Long> it = set.iterator(); it.hasNext(); ) {
                        long packed = it.next();
                        long dx = BlockPos.x(packed) - px;
                        long dy = BlockPos.y(packed) - py;
                        long dz = BlockPos.z(packed) - pz;
                        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                            it.remove();
                            view.count.decrementAndGet();
                            reveal.add(packed);
                            if (reveal.size() >= cfg.maxRevealPerTick) {
                                break outer;
                            }
                        }
                    }
                }
            }

            if (!reveal.isEmpty()) {
                scheduleReveal(player, worldId, reveal);
            }
        }
    }

    private long[] scan(ChunkSnapshot snapshot, int chunkX, int chunkZ,
                        int worldMin, int worldMax, AntiXrayConfig cfg) {
        int minY = Math.max(worldMin, cfg.scanMinY);
        int maxY = Math.min(worldMax, cfg.scanMaxY);
        if (minY > maxY || cfg.hiddenMaterials.isEmpty()) {
            return new long[0];
        }

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        List<Long> found = new ArrayList<>();

        for (int y = minY; y <= maxY; y++) {
            if (((y - worldMin) & 15) == 0 && snapshot.isSectionEmpty((y - worldMin) >> 4)) {
                y += 15;
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Material material = snapshot.getBlockType(x, y, z);
                    if (!cfg.hiddenMaterials.contains(material)) {
                        continue;
                    }
                    if (cfg.hideOnlyEnclosed && isExposed(snapshot, x, y, z, worldMin, worldMax)) {
                        continue;
                    }
                    found.add(BlockPos.asLong(baseX + x, y, baseZ + z));
                }
            }
        }

        long[] result = new long[found.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = found.get(i);
        }
        return result;
    }

    private boolean isExposed(ChunkSnapshot s, int x, int y, int z, int worldMin, int worldMax) {
        return transparent(s, x + 1, y, z, worldMin, worldMax)
                || transparent(s, x - 1, y, z, worldMin, worldMax)
                || transparent(s, x, y + 1, z, worldMin, worldMax)
                || transparent(s, x, y - 1, z, worldMin, worldMax)
                || transparent(s, x, y, z + 1, worldMin, worldMax)
                || transparent(s, x, y, z - 1, worldMin, worldMax);
    }

    private boolean transparent(ChunkSnapshot s, int x, int y, int z, int worldMin, int worldMax) {
        // За границей снимка считаем блок сплошным: лучше лишний раз спрятать, чем показать.
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < worldMin || y > worldMax) {
            return false;
        }
        return !s.getBlockType(x, y, z).isOccluding();
    }

    private void applyHide(PlayerView view, World world, long chunkKey, long[] positions) {
        if (positions.length == 0) {
            return;
        }
        Player player = view.player;
        if (!player.isOnline()) {
            return;
        }

        AntiXrayConfig cfg = plugin.cfg();
        long radiusSq = (long) cfg.hideRadius * cfg.hideRadius;
        int px = view.x;
        int py = view.y;
        int pz = view.z;

        Set<Long> set = view.hidden.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet());
        Map<Position, BlockData> changes = new HashMap<>();

        for (long packed : positions) {
            if (view.count.get() >= cfg.maxHiddenPerPlayer) {
                break;
            }
            int x = BlockPos.x(packed);
            int y = BlockPos.y(packed);
            int z = BlockPos.z(packed);
            long dx = x - px;
            long dy = y - py;
            long dz = z - pz;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                continue; // игрок рядом — пусть видит настоящий блок
            }
            if (set.add(packed)) {
                view.count.incrementAndGet();
                changes.put(Position.block(x, y, z), cfg.replacementFor(world, y));
            }
        }

        if (changes.isEmpty()) {
            return;
        }
        UUID worldId = world.getUID();
        player.getScheduler().run(plugin, t -> {
            if (player.isOnline() && player.getWorld().getUID().equals(worldId)) {
                sendChanges(player, changes);
            }
        }, null);
    }

    private void scheduleReveal(Player player, UUID worldId, List<Long> positions) {
        player.getScheduler().run(plugin, t -> {
            if (!player.isOnline()) {
                return;
            }
            World world = player.getWorld();
            if (!world.getUID().equals(worldId)) {
                return;
            }
            Map<Position, BlockData> real = collectReal(world, positions);
            if (!real.isEmpty()) {
                sendChanges(player, real);
            }
        }, null);
    }

    private Map<Position, BlockData> collectReal(World world, List<Long> positions) {
        Map<Position, BlockData> real = new HashMap<>();
        for (long packed : positions) {
            int x = BlockPos.x(packed);
            int y = BlockPos.y(packed);
            int z = BlockPos.z(packed);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            real.put(Position.block(x, y, z), world.getBlockAt(x, y, z).getBlockData());
        }
        return real;
    }

    /** Пакеты шлём посекционно — так гарантированно корректно и максимально дёшево. */
    private void sendChanges(Player player, Map<Position, BlockData> changes) {
        Map<Long, Map<Position, BlockData>> bySection = new HashMap<>();
        for (Map.Entry<Position, BlockData> entry : changes.entrySet()) {
            Position pos = entry.getKey();
            long section = BlockPos.sectionKey(pos.blockX() >> 4, pos.blockY() >> 4, pos.blockZ() >> 4);
            bySection.computeIfAbsent(section, k -> new HashMap<>()).put(entry.getKey(), entry.getValue());
        }
        for (Map<Position, BlockData> batch : bySection.values()) {
            player.sendMultiBlockChange(batch);
        }
    }

    private List<Long> drain(PlayerView view) {
        List<Long> all = new ArrayList<>();
        for (Set<Long> set : view.hidden.values()) {
            all.addAll(set);
        }
        view.hidden.clear();
        view.count.set(0);
        return all;
    }

    private boolean isExempt(Player player) {
        return player.hasPermission("antixray.bypass")
                || plugin.cfg().exemptGameModes.contains(player.getGameMode());
    }

    // ------------------------------------------------------------------ структуры данных

    private static final class PlayerView {
        private final Player player;
        private final Map<Long, Set<Long>> hidden = new ConcurrentHashMap<>();
        private final AtomicInteger count = new AtomicInteger();
        private volatile int x;
        private volatile int y;
        private volatile int z;
        private volatile UUID world;

        private PlayerView(Player player) {
            this.player = player;
            update(player.getLocation());
        }

        private void update(Location location) {
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
            this.world = location.getWorld() == null ? null : location.getWorld().getUID();
        }
    }

    private record CachedChunk(long[] positions, long createdAt) {
        private boolean expired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }
}

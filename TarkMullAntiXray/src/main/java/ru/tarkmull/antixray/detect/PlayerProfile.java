package ru.tarkmull.antixray.detect;

import org.bukkit.Material;
import ru.tarkmull.antixray.config.AntiXrayConfig;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Накопленная статистика копки одного игрока. Все методы синхронизированы. */
public final class PlayerProfile {

    private final UUID uuid;
    private final String name;
    private final Map<Material, Counter> ores = new EnumMap<>(Material.class);
    private double stone;
    private long lastMine;
    private long lastAlert;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public synchronized void addStone() {
        stone += 1.0D;
        lastMine = System.currentTimeMillis();
    }

    public synchronized void addOre(Material material, boolean enclosed) {
        Counter counter = ores.computeIfAbsent(material, k -> new Counter());
        if (enclosed) {
            counter.enclosed += 1.0D;
        } else {
            counter.exposed += 1.0D;
        }
        lastMine = System.currentTimeMillis();
    }

    /**
     * Счёт = взвешенное число "закрытых" руд на 1000 пробитых блоков породы.
     * Обычная копка держится в районе 5–20, X-Ray выдаёт сотни.
     */
    public synchronized double score(AntiXrayConfig cfg) {
        double weighted = 0.0D;
        for (Map.Entry<Material, Counter> entry : ores.entrySet()) {
            weighted += entry.getValue().enclosed * cfg.weightOf(entry.getKey());
        }
        return weighted / Math.max(1.0D, stone) * 1000.0D;
    }

    public synchronized double stone() {
        return stone;
    }

    public synchronized long lastMine() {
        return lastMine;
    }

    public synchronized boolean canAlert(long cooldownMillis) {
        long now = System.currentTimeMillis();
        if (now - lastAlert < cooldownMillis) {
            return false;
        }
        lastAlert = now;
        return true;
    }

    public synchronized void decay(double factor) {
        stone *= factor;
        ores.values().forEach(counter -> {
            counter.enclosed *= factor;
            counter.exposed *= factor;
        });
        ores.entrySet().removeIf(e -> e.getValue().enclosed < 0.05D && e.getValue().exposed < 0.05D);
    }

    public synchronized void reset() {
        ores.clear();
        stone = 0.0D;
        lastAlert = 0L;
    }

    /** Строка вида "DIAMOND_ORE x4 (скрытых 4)" для алертов и /antixray stats. */
    public synchronized String describeOres(AntiXrayConfig cfg) {
        Map<String, String> parts = new LinkedHashMap<>();
        ores.entrySet().stream()
                .filter(e -> cfg.weightOf(e.getKey()) > 0)
                .sorted((a, b) -> Double.compare(
                        b.getValue().enclosed * cfg.weightOf(b.getKey()),
                        a.getValue().enclosed * cfg.weightOf(a.getKey())))
                .limit(6)
                .forEach(e -> parts.put(e.getKey().name(),
                        String.format("%s: %.0f (скрытых %.0f)",
                                e.getKey().name().toLowerCase(),
                                e.getValue().enclosed + e.getValue().exposed,
                                e.getValue().enclosed)));
        return parts.isEmpty() ? "нет данных" : String.join(", ", parts.values());
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    private static final class Counter {
        private double enclosed;
        private double exposed;
    }
}

package ru.tarkmull.antixray.detect;

import org.bukkit.Material;
import ru.tarkmull.antixray.config.AntiXrayConfig;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * Статистика копки одного игрока.
 * Затухание считается лениво, при обращении — никаких фоновых задач.
 */
public final class PlayerProfile {

    private static final int PLACED_LIMIT = 4096;

    private final UUID uuid;
    private final String name;
    private final Map<Material, double[]> ores = new EnumMap<>(Material.class);

    /** Позиции блоков, поставленных самим игроком: защита от накрутки знаменателя. */
    private final Map<Long, Long> placed = new LinkedHashMap<>(256, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > PLACED_LIMIT;
        }
    };

    private double stone;
    private double peakScore;
    private long lastDecay;
    private long lastMine;
    private long lastAlert;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.lastDecay = System.currentTimeMillis();
    }

    public synchronized void rememberPlaced(long packedPos) {
        placed.put(packedPos, System.currentTimeMillis());
    }

    public synchronized boolean wasPlacedBySelf(long packedPos) {
        return placed.remove(packedPos) != null;
    }

    public synchronized void addStone(AntiXrayConfig cfg) {
        decay(cfg);
        stone += 1.0D;
        lastMine = System.currentTimeMillis();
    }

    public synchronized void addOre(AntiXrayConfig cfg, Material material, boolean enclosed) {
        decay(cfg);
        double[] counter = ores.computeIfAbsent(material, k -> new double[2]);
        counter[enclosed ? 0 : 1] += 1.0D;
        lastMine = System.currentTimeMillis();
    }

    /** Взвешенное число закрытых руд на 1000 блоков породы. */
    public synchronized double score(AntiXrayConfig cfg) {
        decay(cfg);
        double weighted = 0.0D;
        for (Map.Entry<Material, double[]> entry : ores.entrySet()) {
            weighted += entry.getValue()[0] * cfg.weightOf(entry.getKey());
        }
        double value = weighted / Math.max(1.0D, stone) * 1000.0D;
        peakScore = Math.max(peakScore, value);
        return value;
    }

    private void decay(AntiXrayConfig cfg) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastDecay;
        if (elapsed < cfg.decayIntervalMillis) {
            return;
        }
        double steps = (double) elapsed / cfg.decayIntervalMillis;
        double factor = Math.pow(cfg.decayFactor, steps);
        stone *= factor;
        ores.values().forEach(counter -> {
            counter[0] *= factor;
            counter[1] *= factor;
        });
        ores.entrySet().removeIf(e -> e.getValue()[0] < 0.05D && e.getValue()[1] < 0.05D);
        lastDecay = now;
    }

    public synchronized boolean canAlert(long cooldownMillis) {
        long now = System.currentTimeMillis();
        if (now - lastAlert < cooldownMillis) {
            return false;
        }
        lastAlert = now;
        return true;
    }

    public synchronized void reset() {
        ores.clear();
        placed.clear();
        stone = 0.0D;
        peakScore = 0.0D;
        lastAlert = 0L;
        lastDecay = System.currentTimeMillis();
    }

    /** Читаемая сводка по рудам для алертов и /antixray stats. */
    public synchronized String describeOres(AntiXrayConfig cfg) {
        StringBuilder sb = new StringBuilder();
        ores.entrySet().stream()
                .filter(e -> cfg.weightOf(e.getKey()) > 0)
                .sorted((a, b) -> Double.compare(
                        b.getValue()[0] * cfg.weightOf(b.getKey()),
                        a.getValue()[0] * cfg.weightOf(a.getKey())))
                .limit(6)
                .forEach(e -> {
                    if (!sb.isEmpty()) {
                        sb.append(", ");
                    }
                    sb.append(String.format("%s %.0f (закрытых %.0f)",
                            e.getKey().name().toLowerCase(Locale.ROOT),
                            e.getValue()[0] + e.getValue()[1],
                            e.getValue()[0]));
                });
        return sb.isEmpty() ? "нет данных" : sb.toString();
    }

    public synchronized double stone() {
        return stone;
    }

    public synchronized double peakScore() {
        return peakScore;
    }

    public synchronized long lastMine() {
        return lastMine;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }
}

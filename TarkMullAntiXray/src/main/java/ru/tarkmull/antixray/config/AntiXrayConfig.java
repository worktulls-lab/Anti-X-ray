package ru.tarkmull.antixray.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.tarkmull.antixray.TarkMullAntiXray;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Разбор config.yml в готовые к использованию структуры. */
public final class AntiXrayConfig {

    private final TarkMullAntiXray plugin;

    // --- скрытие руд ---
    public boolean hiderEnabled;
    public int hideRadius;
    public int checkIntervalTicks;
    public int maxHiddenPerPlayer;
    public int maxRevealPerTick;
    public boolean hideOnlyEnclosed;
    public int scanMinY;
    public int scanMaxY;
    public long cacheTtlMillis;
    public Set<Material> hiddenMaterials = EnumSet.noneOf(Material.class);
    private BlockData replacementStone;
    private BlockData replacementDeepslate;
    private BlockData replacementNether;
    private BlockData replacementEnd;
    public int deepslateY;

    // --- детектор ---
    public boolean detectorEnabled;
    public long minStoneMined;
    public double alertThreshold;
    public long alertCooldownMillis;
    public boolean consoleAlerts;
    public boolean logToFile;
    public int decayIntervalSeconds;
    public double decayFactor;
    public Set<Material> stoneMaterials = EnumSet.noneOf(Material.class);
    public Map<Material, Double> oreWeights = new EnumMap<>(Material.class);

    // --- общее ---
    public Set<GameMode> exemptGameModes = EnumSet.noneOf(GameMode.class);
    private boolean worldWhitelist;
    private Set<String> worldList = new HashSet<>();
    private String prefix;
    private String alertFormat;

    public AntiXrayConfig(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();

        hiderEnabled = c.getBoolean("hider.enabled", true);
        hideRadius = clamp(c.getInt("hider.radius", 20), 4, 64);
        checkIntervalTicks = clamp(c.getInt("hider.check-interval-ticks", 10), 1, 100);
        maxHiddenPerPlayer = clamp(c.getInt("hider.max-hidden-per-player", 40000), 1000, 500000);
        maxRevealPerTick = clamp(c.getInt("hider.max-reveal-per-tick", 512), 16, 8192);
        hideOnlyEnclosed = c.getBoolean("hider.only-enclosed", true);
        scanMinY = c.getInt("hider.scan.min-y", -64);
        scanMaxY = c.getInt("hider.scan.max-y", 96);
        cacheTtlMillis = Math.max(1000L, c.getLong("hider.scan.cache-seconds", 60) * 1000L);
        deepslateY = c.getInt("hider.replacement.deepslate-below-y", 0);

        hiddenMaterials = materials(c.getStringList("hider.hide-blocks"), "hider.hide-blocks");
        replacementStone = blockData(c.getString("hider.replacement.overworld", "STONE"));
        replacementDeepslate = blockData(c.getString("hider.replacement.deepslate", "DEEPSLATE"));
        replacementNether = blockData(c.getString("hider.replacement.nether", "NETHERRACK"));
        replacementEnd = blockData(c.getString("hider.replacement.end", "END_STONE"));

        detectorEnabled = c.getBoolean("detector.enabled", true);
        minStoneMined = Math.max(1, c.getLong("detector.min-blocks-mined", 400));
        alertThreshold = c.getDouble("detector.alert-threshold", 45.0);
        alertCooldownMillis = Math.max(0L, c.getLong("detector.alert-cooldown-seconds", 120) * 1000L);
        consoleAlerts = c.getBoolean("detector.console-alerts", true);
        logToFile = c.getBoolean("detector.log-to-file", true);
        decayIntervalSeconds = clamp(c.getInt("detector.decay.interval-seconds", 300), 30, 3600);
        decayFactor = Math.min(1.0, Math.max(0.1, c.getDouble("detector.decay.factor", 0.8)));
        stoneMaterials = materials(c.getStringList("detector.count-as-stone"), "detector.count-as-stone");

        oreWeights = new EnumMap<>(Material.class);
        ConfigurationSection weights = c.getConfigurationSection("detector.ore-weights");
        if (weights != null) {
            for (String key : weights.getKeys(false)) {
                Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (m == null || !m.isBlock()) {
                    plugin.getLogger().warning("Неизвестный блок в detector.ore-weights: " + key);
                    continue;
                }
                oreWeights.put(m, weights.getDouble(key));
            }
        }

        exemptGameModes = EnumSet.noneOf(GameMode.class);
        for (String raw : c.getStringList("general.exempt-gamemodes")) {
            try {
                exemptGameModes.add(GameMode.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Неизвестный режим игры: " + raw);
            }
        }

        worldWhitelist = "WHITELIST".equalsIgnoreCase(c.getString("general.worlds.mode", "BLACKLIST"));
        worldList = new HashSet<>();
        for (String w : c.getStringList("general.worlds.list")) {
            worldList.add(w.toLowerCase(Locale.ROOT));
        }

        prefix = c.getString("messages.prefix", "<gray>[<aqua>AntiXray</aqua>]</gray> ");
        alertFormat = c.getString("messages.alert",
                "<red>Подозрение на X-Ray:</red> <white><player></white> <gray>(счёт <yellow><score></yellow>, порог <threshold>)</gray>"
                        + "<newline><gray>Найдено: <white><ores></white></gray>"
                        + "<newline><gray>Пробито блоков: <white><stone></white>, координаты: <white><x> <y> <z></white> (<world>)</gray>");
    }

    private Set<Material> materials(List<String> raw, String path) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : raw) {
            Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (m == null || !m.isBlock()) {
                plugin.getLogger().warning("Неизвестный блок в " + path + ": " + name);
                continue;
            }
            out.add(m);
        }
        return out;
    }

    private BlockData blockData(String name) {
        Material m = name == null ? null : Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (m == null || !m.isBlock()) {
            plugin.getLogger().warning("Неизвестный блок-заменитель: " + name + ", использую STONE");
            m = Material.STONE;
        }
        return m.createBlockData();
    }

    /** Чем подменять руду в конкретном мире и на конкретной высоте. */
    public BlockData replacementFor(World world, int y) {
        return switch (world.getEnvironment()) {
            case NETHER -> replacementNether;
            case THE_END -> replacementEnd;
            default -> y < deepslateY ? replacementDeepslate : replacementStone;
        };
    }

    public boolean isWorldEnabled(World world) {
        boolean listed = worldList.contains(world.getName().toLowerCase(Locale.ROOT));
        return worldWhitelist == listed;
    }

    public double weightOf(Material material) {
        return oreWeights.getOrDefault(material, 0.0D);
    }

    public Component prefix() {
        return MiniMessage.miniMessage().deserialize(prefix);
    }

    public String alertFormat() {
        return alertFormat;
    }

    public Component msg(String miniMessage) {
        return prefix().append(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

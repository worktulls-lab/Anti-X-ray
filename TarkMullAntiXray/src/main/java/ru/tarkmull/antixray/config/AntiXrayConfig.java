package ru.tarkmull.antixray.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
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

public final class AntiXrayConfig {

    private final TarkMullAntiXray plugin;

    public boolean detectorEnabled;
    public long minStoneMined;
    public double alertThreshold;
    public long alertCooldownMillis;
    public boolean consoleAlerts;
    public boolean logToFile;
    public long decayIntervalMillis;
    public double decayFactor;
    public boolean ignoreSelfPlaced;

    public Set<Material> stoneMaterials = EnumSet.noneOf(Material.class);
    public Map<Material, Double> oreWeights = new EnumMap<>(Material.class);
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

        detectorEnabled = c.getBoolean("detector.enabled", true);
        minStoneMined = Math.max(1L, c.getLong("detector.min-blocks-mined", 400));
        alertThreshold = c.getDouble("detector.alert-threshold", 45.0);
        alertCooldownMillis = Math.max(0L, c.getLong("detector.alert-cooldown-seconds", 120) * 1000L);
        consoleAlerts = c.getBoolean("detector.console-alerts", true);
        logToFile = c.getBoolean("detector.log-to-file", true);
        ignoreSelfPlaced = c.getBoolean("detector.ignore-self-placed", true);
        decayIntervalMillis = Math.max(30_000L, c.getLong("detector.decay.interval-seconds", 300) * 1000L);
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
                        + "<newline><gray>Руды: <white><ores></white></gray>"
                        + "<newline><gray>Породы пробито: <white><stone></white> | <white><x> <y> <z></white> (<world>)</gray>");

        if (oreWeights.isEmpty()) {
            plugin.getLogger().warning("Список detector.ore-weights пуст — детектор ничего не поймает!");
        }
        if (stoneMaterials.isEmpty()) {
            plugin.getLogger().warning("Список detector.count-as-stone пуст — детектор ничего не поймает!");
        }
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

    public boolean isWorldEnabled(World world) {
        return worldWhitelist == worldList.contains(world.getName().toLowerCase(Locale.ROOT));
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
}

package ru.tarkmull.antixray.detect;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import ru.tarkmull.antixray.TarkMullAntiXray;
import ru.tarkmull.antixray.config.AntiXrayConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Статистический детектор: считает, сколько закрытых руд игрок находит на объём породы. */
public final class MiningTracker {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TarkMullAntiXray plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private volatile ScheduledTask decayTask;

    public MiningTracker(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    public void start() {
        AntiXrayConfig cfg = plugin.cfg();
        if (!cfg.detectorEnabled) {
            return;
        }
        long period = cfg.decayIntervalSeconds * 1000L;
        decayTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            double factor = plugin.cfg().decayFactor;
            profiles.values().forEach(profile -> profile.decay(factor));
            long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6);
            profiles.values().removeIf(profile -> profile.lastMine() < cutoff
                    && Bukkit.getPlayer(profile.uuid()) == null);
        }, period, period, TimeUnit.MILLISECONDS);
    }

    public void restart() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
        start();
    }

    public void shutdown() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
        profiles.clear();
    }

    /** Вызывается из BlockBreakEvent (блок ещё на месте, соседей можно проверить). */
    public void handleBreak(Player player, Block block) {
        AntiXrayConfig cfg = plugin.cfg();
        if (!cfg.detectorEnabled
                || player.hasPermission("antixray.bypass")
                || cfg.exemptGameModes.contains(player.getGameMode())
                || !cfg.isWorldEnabled(block.getWorld())) {
            return;
        }

        Material type = block.getType();
        PlayerProfile profile = profiles.computeIfAbsent(player.getUniqueId(),
                id -> new PlayerProfile(id, player.getName()));

        if (cfg.weightOf(type) > 0.0D) {
            profile.addOre(type, !isExposed(block));
            evaluate(player, profile);
        } else if (cfg.stoneMaterials.contains(type)) {
            profile.addStone();
        }
    }

    private void evaluate(Player player, PlayerProfile profile) {
        AntiXrayConfig cfg = plugin.cfg();
        if (profile.stone() < cfg.minStoneMined) {
            return;
        }
        double score = profile.score(cfg);
        if (score < cfg.alertThreshold || !profile.canAlert(cfg.alertCooldownMillis)) {
            return;
        }
        alert(player, profile, score);
    }

    private void alert(Player player, PlayerProfile profile, double score) {
        AntiXrayConfig cfg = plugin.cfg();
        Component message = cfg.prefix().append(MiniMessage.miniMessage().deserialize(
                cfg.alertFormat(),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("score", String.format("%.1f", score)),
                Placeholder.unparsed("threshold", String.format("%.1f", cfg.alertThreshold)),
                Placeholder.unparsed("ores", profile.describeOres(cfg)),
                Placeholder.unparsed("stone", String.format("%.0f", profile.stone())),
                Placeholder.unparsed("x", String.valueOf(player.getLocation().getBlockX())),
                Placeholder.unparsed("y", String.valueOf(player.getLocation().getBlockY())),
                Placeholder.unparsed("z", String.valueOf(player.getLocation().getBlockZ())),
                Placeholder.unparsed("world", player.getWorld().getName())));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("antixray.alerts") && !isMuted(staff)) {
                staff.sendMessage(message);
            }
        }
        if (cfg.consoleAlerts) {
            Bukkit.getConsoleSender().sendMessage(message);
        }
        if (cfg.logToFile) {
            writeLog(String.format("[%s] %s score=%.1f blocks=%.0f at %d %d %d (%s) | %s",
                    LocalDateTime.now().format(STAMP), player.getName(), score, profile.stone(),
                    player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ(), player.getWorld().getName(),
                    profile.describeOres(cfg)));
        }
    }

    private void writeLog(String line) {
        try {
            Path folder = plugin.getDataFolder().toPath();
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("alerts.log"), line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось записать alerts.log: " + ex.getMessage());
        }
    }

    private boolean isExposed(Block block) {
        for (BlockFace face : FACES) {
            if (!block.getRelative(face).getType().isOccluding()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ доступ для команд

    private final java.util.Set<UUID> muted = ConcurrentHashMap.newKeySet();

    public boolean toggleAlerts(Player player) {
        if (muted.remove(player.getUniqueId())) {
            return true;
        }
        muted.add(player.getUniqueId());
        return false;
    }

    public boolean isMuted(Player player) {
        return muted.contains(player.getUniqueId());
    }

    public PlayerProfile profile(UUID uuid) {
        return profiles.get(uuid);
    }

    public void reset(UUID uuid) {
        PlayerProfile profile = profiles.get(uuid);
        if (profile != null) {
            profile.reset();
        }
    }

    public List<PlayerProfile> top(int limit) {
        AntiXrayConfig cfg = plugin.cfg();
        List<PlayerProfile> list = new ArrayList<>(profiles.values());
        list.sort(Comparator.comparingDouble((PlayerProfile p) -> p.score(cfg)).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}

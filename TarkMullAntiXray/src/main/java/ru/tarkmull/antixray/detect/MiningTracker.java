package ru.tarkmull.antixray.detect;

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
import ru.tarkmull.antixray.util.BlockPos;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MiningTracker {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TarkMullAntiXray plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();

    public MiningTracker(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    /** BlockBreakEvent: блок ещё на месте, соседей можно честно проверить. */
    public void handleBreak(Player player, Block block) {
        AntiXrayConfig cfg = plugin.cfg();
        if (!cfg.detectorEnabled || isExempt(player, cfg) || !cfg.isWorldEnabled(block.getWorld())) {
            return;
        }

        PlayerProfile profile = profile(player);
        long packed = BlockPos.asLong(block.getX(), block.getY(), block.getZ());

        // Блок, который игрок сам поставил, в статистику не идёт —
        // иначе счёт можно занизить, наставив и переломав кучу камня.
        if (cfg.ignoreSelfPlaced && profile.wasPlacedBySelf(packed)) {
            return;
        }

        Material type = block.getType();
        if (cfg.weightOf(type) > 0.0D) {
            profile.addOre(cfg, type, !isExposed(block));
            evaluate(player, profile, cfg);
        } else if (cfg.stoneMaterials.contains(type)) {
            profile.addStone(cfg);
        }
    }

    public void handlePlace(Player player, Block block) {
        AntiXrayConfig cfg = plugin.cfg();
        if (!cfg.detectorEnabled || !cfg.ignoreSelfPlaced || isExempt(player, cfg)) {
            return;
        }
        profile(player).rememberPlaced(BlockPos.asLong(block.getX(), block.getY(), block.getZ()));
    }

    private void evaluate(Player player, PlayerProfile profile, AntiXrayConfig cfg) {
        if (profile.stone() < cfg.minStoneMined) {
            return;
        }
        double score = profile.score(cfg);
        if (score < cfg.alertThreshold || !profile.canAlert(cfg.alertCooldownMillis)) {
            return;
        }
        alert(player, profile, score, cfg);
    }

    private void alert(Player player, PlayerProfile profile, double score, AntiXrayConfig cfg) {
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
            if (staff.hasPermission("antixray.alerts") && !muted.contains(staff.getUniqueId())) {
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

    private boolean isExempt(Player player, AntiXrayConfig cfg) {
        return player.hasPermission("antixray.bypass")
                || cfg.exemptGameModes.contains(player.getGameMode());
    }

    private PlayerProfile profile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(),
                id -> new PlayerProfile(id, player.getName()));
    }

    // ---------------------------------------------------------------- для команд

    public PlayerProfile profile(UUID uuid) {
        return profiles.get(uuid);
    }

    public int profileCount() {
        return profiles.size();
    }

    public void reset(UUID uuid) {
        PlayerProfile profile = profiles.get(uuid);
        if (profile != null) {
            profile.reset();
        }
    }

    public boolean toggleAlerts(Player player) {
        if (muted.remove(player.getUniqueId())) {
            return true;
        }
        muted.add(player.getUniqueId());
        return false;
    }

    public List<PlayerProfile> top(int limit) {
        AntiXrayConfig cfg = plugin.cfg();
        List<PlayerProfile> list = new ArrayList<>(profiles.values());
        list.sort(Comparator.comparingDouble((PlayerProfile p) -> p.score(cfg)).reversed());
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }
}

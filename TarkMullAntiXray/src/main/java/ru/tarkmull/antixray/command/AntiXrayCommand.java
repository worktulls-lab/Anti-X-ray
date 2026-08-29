package ru.tarkmull.antixray.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.tarkmull.antixray.TarkMullAntiXray;
import ru.tarkmull.antixray.config.AntiXrayConfig;
import ru.tarkmull.antixray.detect.PlayerProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AntiXrayCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("help", "reload", "stats", "top", "reset", "refresh", "alerts");

    private final TarkMullAntiXray plugin;

    public AntiXrayCommand(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        AntiXrayConfig cfg = plugin.cfg();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(cfg.msg("<gray>Команды:</gray>"
                    + "<newline><white>/antixray reload</white> <gray>— перечитать конфиг</gray>"
                    + "<newline><white>/antixray stats [ник]</white> <gray>— статистика копки</gray>"
                    + "<newline><white>/antixray top</white> <gray>— топ подозрительных</gray>"
                    + "<newline><white>/antixray reset [ник]</white> <gray>— обнулить статистику</gray>"
                    + "<newline><white>/antixray refresh</white> <gray>— вернуть всем настоящие блоки</gray>"
                    + "<newline><white>/antixray alerts</white> <gray>— вкл/выкл алерты для себя</gray>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(cfg.msg("<green>Конфиг перезагружен.</green>"));
            }
            case "refresh" -> {
                plugin.hider().revealEverything();
                sender.sendMessage(cfg.msg("<green>Скрытые блоки возвращены игрокам.</green>"));
            }
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.msg("<red>Только для игроков.</red>"));
                    return true;
                }
                boolean enabled = plugin.tracker().toggleAlerts(player);
                sender.sendMessage(cfg.msg(enabled
                        ? "<green>Алерты включены.</green>"
                        : "<yellow>Алерты выключены.</yellow>"));
            }
            case "stats" -> {
                if (args.length < 2) {
                    sender.sendMessage(cfg.msg("<red>Укажите ник: /antixray stats [ник]</red>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage(cfg.msg("<red>Игрок не найден.</red>"));
                    return true;
                }
                PlayerProfile profile = plugin.tracker().profile(target.getUniqueId());
                if (profile == null) {
                    sender.sendMessage(cfg.msg("<yellow>Статистики по этому игроку пока нет.</yellow>"));
                    return true;
                }
                Player online = target.getPlayer();
                sender.sendMessage(cfg.msg(String.format(
                        "<white>%s</white><newline><gray>Счёт: <yellow>%.1f</yellow> (порог %.1f)</gray>"
                                + "<newline><gray>Породы пробито: <white>%.0f</white></gray>"
                                + "<newline><gray>Руды: <white>%s</white></gray>"
                                + "<newline><gray>Скрыто блоков сейчас: <white>%d</white></gray>",
                        args[1], profile.score(cfg), cfg.alertThreshold, profile.stone(),
                        profile.describeOres(cfg),
                        online == null ? 0 : plugin.hider().hiddenCount(online))));
            }
            case "top" -> {
                List<PlayerProfile> top = plugin.tracker().top(10);
                if (top.isEmpty()) {
                    sender.sendMessage(cfg.msg("<yellow>Данных пока нет.</yellow>"));
                    return true;
                }
                StringBuilder sb = new StringBuilder("<gray>Топ по счёту X-Ray:</gray>");
                int i = 1;
                for (PlayerProfile profile : top) {
                    sb.append(String.format("<newline><gray>%d.</gray> <white>%s</white> <gray>—</gray> <yellow>%.1f</yellow>",
                            i++, profile.name(), profile.score(cfg)));
                }
                sender.sendMessage(cfg.msg(sb.toString()));
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(cfg.msg("<red>Укажите ник: /antixray reset [ник]</red>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage(cfg.msg("<red>Игрок не найден.</red>"));
                    return true;
                }
                plugin.tracker().reset(target.getUniqueId());
                sender.sendMessage(cfg.msg("<green>Статистика обнулена.</green>"));
            }
            default -> sender.sendMessage(cfg.msg("<red>Неизвестная подкоманда. /antixray help</red>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String sub : SUB) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("reset"))) {
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}

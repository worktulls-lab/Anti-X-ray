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
import ru.tarkmull.antixray.util.PaperAntiXrayStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AntiXrayCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("help", "check", "reload", "stats", "top", "reset", "alerts");

    private final TarkMullAntiXray plugin;

    public AntiXrayCommand(TarkMullAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        AntiXrayConfig cfg = plugin.cfg();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(cfg.msg("<gray>Команды:</gray>"
                    + "<newline><white>/antixray check</white> <gray>— диагностика, начните с неё</gray>"
                    + "<newline><white>/antixray stats [ник]</white> <gray>— статистика копки</gray>"
                    + "<newline><white>/antixray top</white> <gray>— топ подозрительных</gray>"
                    + "<newline><white>/antixray reset [ник]</white> <gray>— обнулить статистику</gray>"
                    + "<newline><white>/antixray alerts</white> <gray>— вкл/выкл алерты для себя</gray>"
                    + "<newline><white>/antixray reload</white> <gray>— перечитать конфиг</gray>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check" -> check(sender, cfg);
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(cfg.msg("<green>Конфиг перезагружен.</green>"));
            }
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.msg("<red>Только для игроков.</red>"));
                    return true;
                }
                boolean on = plugin.tracker().toggleAlerts(player);
                sender.sendMessage(cfg.msg(on
                        ? "<green>Алерты включены.</green>"
                        : "<yellow>Алерты выключены.</yellow>"));
            }
            case "stats" -> stats(sender, cfg, args);
            case "top" -> top(sender, cfg);
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

    /** Главная диагностическая команда: показывает, что реально включено. */
    private void check(CommandSender sender, AntiXrayConfig cfg) {
        PaperAntiXrayStatus paper = plugin.paperStatus();
        String paperColor = paper.healthy() ? "green" : "red";
        StringBuilder sb = new StringBuilder("<gray>Диагностика:</gray>");

        sb.append("<newline><gray>Обфускация руд (Paper): </gray>")
                .append('<').append(paperColor).append('>')
                .append(paper.verdict())
                .append("</").append(paperColor).append('>');

        sb.append("<newline><gray>Детектор плагина: </gray>")
                .append(cfg.detectorEnabled ? "<green>включен</green>" : "<red>выключен</red>");
        sb.append("<newline><gray>Порог алерта: <white>").append(String.format("%.1f", cfg.alertThreshold))
                .append("</white>, минимум блоков: <white>").append(cfg.minStoneMined).append("</white></gray>");
        sb.append("<newline><gray>Отслеживается руд: <white>").append(cfg.oreWeights.size())
                .append("</white>, пород: <white>").append(cfg.stoneMaterials.size()).append("</white></gray>");
        sb.append("<newline><gray>Профилей в памяти: <white>").append(plugin.tracker().profileCount())
                .append("</white></gray>");

        if (sender instanceof Player player) {
            sb.append("<newline><gray>Ваш мир: </gray>")
                    .append(cfg.isWorldEnabled(player.getWorld())
                            ? "<green>под наблюдением</green>" : "<yellow>исключён</yellow>");
            boolean bypass = player.hasPermission("antixray.bypass");
            boolean exemptMode = cfg.exemptGameModes.contains(player.getGameMode());
            sb.append("<newline><gray>Вы сами: </gray>")
                    .append(bypass || exemptMode
                            ? "<yellow>не отслеживаетесь (" + (bypass ? "право bypass" : "режим " + player.getGameMode()) + ")</yellow>"
                            : "<green>отслеживаетесь</green>");
        }

        if (!paper.healthy()) {
            sb.append("<newline><red>Главное: пока обфускация Paper выключена, руды видны читерам.</red>");
            sb.append("<newline><gray>Остановите сервер, в config/paper-world-defaults.yml</gray>");
            sb.append("<newline><gray>выставьте anticheat.anti-xray.enabled: true и engine-mode: 2</gray>");
        }
        sender.sendMessage(cfg.msg(sb.toString()));
    }

    private void stats(CommandSender sender, AntiXrayConfig cfg, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(cfg.msg("<red>Укажите ник: /antixray stats [ник]</red>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            sender.sendMessage(cfg.msg("<red>Игрок не найден (ни разу не заходил на сервер).</red>"));
            return;
        }
        PlayerProfile profile = plugin.tracker().profile(target.getUniqueId());
        if (profile == null) {
            sender.sendMessage(cfg.msg("<yellow>Статистики пока нет — игрок ничего не копал.</yellow>"));
            return;
        }
        sender.sendMessage(cfg.msg(String.format(
                "<white>%s</white>"
                        + "<newline><gray>Счёт: <yellow>%.1f</yellow> (пик %.1f, порог %.1f)</gray>"
                        + "<newline><gray>Породы пробито: <white>%.0f</white></gray>"
                        + "<newline><gray>Руды: <white>%s</white></gray>",
                args[1], profile.score(cfg), profile.peakScore(), cfg.alertThreshold,
                profile.stone(), profile.describeOres(cfg))));
    }

    private void top(CommandSender sender, AntiXrayConfig cfg) {
        List<PlayerProfile> top = plugin.tracker().top(10);
        if (top.isEmpty()) {
            sender.sendMessage(cfg.msg("<yellow>Данных пока нет — никто ещё не копал.</yellow>"));
            return;
        }
        StringBuilder sb = new StringBuilder("<gray>Топ по счёту X-Ray:</gray>");
        int i = 1;
        for (PlayerProfile profile : top) {
            sb.append(String.format(
                    "<newline><gray>%d.</gray> <white>%s</white> <gray>—</gray> <yellow>%.1f</yellow> <gray>(%.0f блоков)</gray>",
                    i++, profile.name(), profile.score(cfg), profile.stone()));
        }
        sender.sendMessage(cfg.msg(sb.toString()));
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

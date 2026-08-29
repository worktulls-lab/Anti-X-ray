package ru.tarkmull.antixray;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.tarkmull.antixray.command.AntiXrayCommand;
import ru.tarkmull.antixray.config.AntiXrayConfig;
import ru.tarkmull.antixray.detect.MiningTracker;
import ru.tarkmull.antixray.listener.BlockListener;
import ru.tarkmull.antixray.util.PaperAntiXrayStatus;

/**
 * TarkMullAntiXray 2.0 — детектор X-Ray по характеру копки.
 *
 * Скрытием руд плагин НЕ занимается: обфускация должна происходить до отправки
 * пакета чанка, а туда плагины не попадают. За это отвечает встроенный
 * анти-ксрей Paper (config/paper-world-defaults.yml, engine-mode 2).
 * Плагин при старте проверяет, включён ли он, и ругается в консоль, если нет.
 */
public final class TarkMullAntiXray extends JavaPlugin {

    private AntiXrayConfig config;
    private MiningTracker tracker;
    private PaperAntiXrayStatus paperStatus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new AntiXrayConfig(this);
        this.config.load();

        this.tracker = new MiningTracker(this);
        this.paperStatus = PaperAntiXrayStatus.read();

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        PluginCommand command = getCommand("antixray");
        if (command != null) {
            AntiXrayCommand executor = new AntiXrayCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Команда /antixray не зарегистрирована — испорчен plugin.yml");
        }

        getLogger().info("Детектор X-Ray включен. Порог: " + config.alertThreshold
                + ", минимум блоков: " + config.minStoneMined);

        if (!paperStatus.enabled()) {
            getLogger().warning("=================================================");
            getLogger().warning("Встроенный анти-ксрей Paper ВЫКЛЮЧЕН.");
            getLogger().warning("Руды видны читерам напрямую, плагин это не чинит.");
            getLogger().warning("Остановите сервер и в config/paper-world-defaults.yml");
            getLogger().warning("выставьте anticheat.anti-xray.enabled: true и engine-mode: 2");
            getLogger().warning("Подробности: /antixray check");
            getLogger().warning("=================================================");
        } else if (paperStatus.engineMode() < 2) {
            getLogger().warning("Анти-ксрей Paper работает в engine-mode " + paperStatus.engineMode()
                    + ". Режим 2 надёжнее — см. /antixray check");
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        config.load();
        paperStatus = PaperAntiXrayStatus.read();
    }

    public AntiXrayConfig cfg() {
        return config;
    }

    public MiningTracker tracker() {
        return tracker;
    }

    public PaperAntiXrayStatus paperStatus() {
        return paperStatus;
    }
}

package ru.tarkmull.antixray;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.tarkmull.antixray.command.AntiXrayCommand;
import ru.tarkmull.antixray.config.AntiXrayConfig;
import ru.tarkmull.antixray.detect.MiningTracker;
import ru.tarkmull.antixray.hider.ProximityHider;
import ru.tarkmull.antixray.listener.BlockListener;
import ru.tarkmull.antixray.listener.ChunkListener;
import ru.tarkmull.antixray.listener.PlayerListener;

/**
 * TarkMullAntiXray — защита от X-Ray для Paper 26.x.
 *
 * Два независимых модуля:
 *   1) ProximityHider — подменяет руды на камень для клиента, пока игрок далеко.
 *   2) MiningTracker  — статистический детектор X-Ray по характеру копки + алерты админам.
 */
public final class TarkMullAntiXray extends JavaPlugin {

    private AntiXrayConfig config;
    private ProximityHider hider;
    private MiningTracker tracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new AntiXrayConfig(this);
        this.config.load();

        this.hider = new ProximityHider(this);
        this.tracker = new MiningTracker(this);

        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand command = getCommand("antixray");
        if (command != null) {
            AntiXrayCommand executor = new AntiXrayCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Команда /antixray не зарегистрирована — проверьте plugin.yml");
        }

        hider.start();
        tracker.start();

        getLogger().info("Включен. Скрытие руд: " + (config.hiderEnabled ? "вкл" : "выкл")
                + ", детектор: " + (config.detectorEnabled ? "вкл" : "выкл"));
    }

    @Override
    public void onDisable() {
        if (hider != null) {
            hider.shutdown();
        }
        if (tracker != null) {
            tracker.shutdown();
        }
    }

    /** Перечитывает config.yml и возвращает игрокам настоящие блоки. */
    public void reloadPlugin() {
        reloadConfig();
        config.load();
        hider.revealEverything();
        hider.restart();
        tracker.restart();
    }

    public AntiXrayConfig cfg() {
        return config;
    }

    public ProximityHider hider() {
        return hider;
    }

    public MiningTracker tracker() {
        return tracker;
    }
}

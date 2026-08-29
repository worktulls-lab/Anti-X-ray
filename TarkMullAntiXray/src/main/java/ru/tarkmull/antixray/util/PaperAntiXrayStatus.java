package ru.tarkmull.antixray.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Читает config/paper-world-defaults.yml, чтобы сказать администратору,
 * включена ли настоящая обфускация руд. API для этого Paper не даёт,
 * поэтому смотрим файл напрямую — только на чтение.
 */
public record PaperAntiXrayStatus(boolean fileFound, boolean enabled, int engineMode,
                                  int maxBlockHeight, int updateRadius) {

    private static final String PATH = "config/paper-world-defaults.yml";

    public static PaperAntiXrayStatus read() {
        File file = new File(PATH);
        if (!file.isFile()) {
            return new PaperAntiXrayStatus(false, false, -1, -1, -1);
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String base = "anticheat.anti-xray.";
            return new PaperAntiXrayStatus(
                    true,
                    yaml.getBoolean(base + "enabled", false),
                    yaml.getInt(base + "engine-mode", 1),
                    yaml.getInt(base + "max-block-height", 64),
                    yaml.getInt(base + "update-radius", 2));
        } catch (Exception ex) {
            return new PaperAntiXrayStatus(false, false, -1, -1, -1);
        }
    }

    /** Короткий вердикт для /antixray check. */
    public String verdict() {
        if (!fileFound) {
            return "файл " + PATH + " не найден — сервер точно Paper?";
        }
        if (!enabled) {
            return "ВЫКЛЮЧЕН — руды уходят клиенту как есть";
        }
        return switch (engineMode) {
            case 1 -> "режим 1: руды подменяются камнем. Работает, но режим 2 надёжнее";
            case 2 -> "режим 2: руды спрятаны среди фальшивых. Оптимально";
            case 3 -> "режим 3: как 2, но тяжелее для сервера";
            default -> "неизвестный режим " + engineMode;
        };
    }

    public boolean healthy() {
        return fileFound && enabled && engineMode >= 1;
    }
}

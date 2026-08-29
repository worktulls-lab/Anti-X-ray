package ru.tarkmull.antixray.hider;

/** Упаковка координат блока/чанка в long, чтобы не плодить объекты. */
public final class BlockPos {

    private BlockPos() {
    }

    public static long asLong(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFFL);
    }

    public static int x(long packed) {
        return (int) (packed >> 38);
    }

    public static int y(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int z(long packed) {
        return (int) (packed << 26 >> 38);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42)
                | ((long) (sz & 0x3FFFFF) << 20)
                | (sy & 0xFFFFFL);
    }
}

package ru.tarkmull.antixray.util;

/** Упаковка координат блока в long — для списка блоков, поставленных игроком. */
public final class BlockPos {

    private BlockPos() {
    }

    public static long asLong(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFFL);
    }
}

package com.yelle233.yuanliuwujin.compat;

import net.minecraftforge.fml.ModList;

/**
 * Mekanism 模组存在性检测（1.20.1 Forge 版本）。
 */
public final class MekanismChecker {

    private static final String MEKANISM_MOD_ID = "mekanism";
    private static Boolean loaded = null;

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MEKANISM_MOD_ID);
        }
        return loaded;
    }

    private MekanismChecker() {}
}

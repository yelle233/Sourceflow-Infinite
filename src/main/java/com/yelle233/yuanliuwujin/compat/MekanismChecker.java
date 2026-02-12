package com.yelle233.yuanliuwujin.compat;

import net.neoforged.fml.ModList;

/**
 * Mekanism 模组存在性检测。
 * <p>
 * 此类不引用任何 Mekanism 的类，因此即使 Mekanism 不存在也不会导致 ClassNotFoundException。
 * 所有调用 Mekanism API 的代码都应先通过 {@link #isLoaded()} 判断后再执行。
 */
public final class MekanismChecker {

    private static final String MEKANISM_MOD_ID = "mekanism";

    /** 缓存检测结果，避免重复查询 */
    private static Boolean loaded = null;

    /** 检查 Mekanism 是否已加载 */
    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MEKANISM_MOD_ID);
        }
        return loaded;
    }

    private MekanismChecker() {}
}

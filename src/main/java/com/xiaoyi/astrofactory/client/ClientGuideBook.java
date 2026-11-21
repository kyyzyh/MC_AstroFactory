package com.xiaoyi.astrofactory.client;

/**
 * 客户端手册入口：单独一个类，保证 {@link GuideBookScreen}
 * 只在客户端被类加载。
 *
 * <p>{@link com.xiaoyi.astrofactory.item.GuideBookItem} 是双端类，
 * 里面只在 {@code level.isClientSide()} 分支调用本类 —— JVM 到那一刻
 * 才会解析 {@code GuideBookScreen}，服务端永远不会碰到它。</p>
 */
public final class ClientGuideBook {
    private ClientGuideBook() {}

    /** 打开手册书本界面。 */
    public static void open() {
        GuideBookScreen.open();
    }
}

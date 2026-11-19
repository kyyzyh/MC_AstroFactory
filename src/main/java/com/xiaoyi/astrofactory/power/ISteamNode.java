package com.xiaoyi.astrofactory.power;

/**
 * 蒸汽网络节点（规格 §5.5 最小实现：不做压力、温度、损耗、电压等级）。
 *
 * <p>锅炉、管道、蒸汽机都实现本接口，用最简单的"相邻均摊"扩散蒸汽：
 * 每 tick 每个节点把自己多余的蒸汽推给相邻更空的节点。</p>
 */
public interface ISteamNode {
    /** 当前蒸汽量。 */
    int getSteam();

    /** 蒸汽容量上限。 */
    int getSteamCapacity();

    /** 直接设置蒸汽量（扩散时用）。 */
    void setSteam(int amount);

    /** 加入蒸汽，返回实际加入量。 */
    default int addSteam(int amount) {
        int space = getSteamCapacity() - getSteam();
        int moved = Math.min(space, amount);
        if (moved > 0) {
            setSteam(getSteam() + moved);
        }
        return moved;
    }

    /** 取出蒸汽，返回实际取出量。 */
    default int takeSteam(int amount) {
        int moved = Math.min(getSteam(), amount);
        if (moved > 0) {
            setSteam(getSteam() - moved);
        }
        return moved;
    }
}

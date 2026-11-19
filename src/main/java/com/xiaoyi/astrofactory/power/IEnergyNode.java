package com.xiaoyi.astrofactory.power;

/**
 * 电力节点（规格 §5.5 最小实现：不做电压等级、不做损耗）。
 *
 * <p>蒸汽机发电写入能量，电线杆作为 5 格范围的能量缓冲，电动机器从范围内取电。</p>
 */
public interface IEnergyNode {
    /** 当前能量（本模组自定义单位）。 */
    int getEnergy();

    /** 能量容量上限。 */
    int getEnergyCapacity();

    /** 直接设置能量。 */
    void setEnergy(int amount);

    /** 加入能量，返回实际加入量。 */
    default int addEnergy(int amount) {
        int space = getEnergyCapacity() - getEnergy();
        int moved = Math.min(space, amount);
        if (moved > 0) {
            setEnergy(getEnergy() + moved);
        }
        return moved;
    }

    /** 取出能量，返回实际取出量。 */
    default int takeEnergy(int amount) {
        int moved = Math.min(getEnergy(), amount);
        if (moved > 0) {
            setEnergy(getEnergy() - moved);
        }
        return moved;
    }
}

package com.astrofactory.block;

import com.mojang.serialization.MapCodec;

/** coal矿脉：世界生成产物，采矿机放在其上产出coal矿石。 */
public class CoalField extends OreFieldBlock {

    public static final MapCodec<CoalField> CODEC = simpleCodec(CoalField::new);

    public CoalField(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CoalField> codec() {
        return CODEC;
    }

    @Override
    public FieldType fieldType() {
        return FieldType.COAL;
    }
}

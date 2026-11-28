package com.astrofactory.block;

import com.mojang.serialization.MapCodec;

/** copper矿脉：世界生成产物，采矿机放在其上产出copper矿石。 */
public class CopperField extends OreFieldBlock {

    public static final MapCodec<CopperField> CODEC = simpleCodec(CopperField::new);

    public CopperField(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CopperField> codec() {
        return CODEC;
    }

    @Override
    public FieldType fieldType() {
        return FieldType.COPPER;
    }
}

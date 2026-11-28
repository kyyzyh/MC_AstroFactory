package com.astrofactory.block;

import com.mojang.serialization.MapCodec;

/** stone矿脉：世界生成产物，采矿机放在其上产出stone矿石。 */
public class StoneField extends OreFieldBlock {

    public static final MapCodec<StoneField> CODEC = simpleCodec(StoneField::new);

    public StoneField(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends StoneField> codec() {
        return CODEC;
    }

    @Override
    public FieldType fieldType() {
        return FieldType.STONE;
    }
}

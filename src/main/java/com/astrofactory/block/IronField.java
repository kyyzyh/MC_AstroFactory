package com.astrofactory.block;

import com.mojang.serialization.MapCodec;

/** iron矿脉：世界生成产物，采矿机放在其上产出iron矿石。 */
public class IronField extends OreFieldBlock {

    public static final MapCodec<IronField> CODEC = simpleCodec(IronField::new);

    public IronField(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends IronField> codec() {
        return CODEC;
    }

    @Override
    public FieldType fieldType() {
        return FieldType.IRON;
    }
}

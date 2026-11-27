package dev.viridianengineering.content.corporea;

import dev.viridianengineering.registry.VECorporea;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public class ArcaneSparkItem extends Item {
    public ArcaneSparkItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level=context.getLevel(); var pos=context.getClickedPos();
        if (!ArcaneSparkEntity.supports(level,pos) || ArcaneSparkEntity.at(level,pos)!=null) {
            if (!level.isClientSide && context.getPlayer() != null)
                context.getPlayer().displayClientMessage(Component.translatable("message.viridian_engineering.spark_target"), true);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            // Query entities as well: a just-placed spark has not reached its first tick yet.
            if (!level.getEntitiesOfClass(ArcaneSparkEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(0,2,0)).isEmpty()) return InteractionResult.FAIL;
            var spark=VECorporea.SPARK_ENTITY.get().create(level);
            if (spark==null) return InteractionResult.FAIL;
            spark.attach(pos);
            if (!level.addFreshEntity(spark)) return InteractionResult.FAIL;
            level.playSound(null, pos, dev.viridianengineering.registry.VESounds.SPARK_LINK.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.3f);
            if (context.getPlayer()==null || !context.getPlayer().isCreative()) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.viridian_engineering.relay_spark").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.viridian_engineering.relay_spark_mana").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

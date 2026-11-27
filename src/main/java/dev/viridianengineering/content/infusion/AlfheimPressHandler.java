package dev.viridianengineering.content.infusion;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEItems;
import dev.viridianengineering.registry.VERecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jetbrains.annotations.Nullable;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.api.mana.ManaReceiver;

import java.util.List;
import java.util.Optional;

/**
 * Spec 3.1 Alfheim Mana Infusion Module on Create's Mechanical Press.
 * <ul>
 *   <li>Right-click a press with the module: attaches it (saved + synced to clients).</li>
 *   <li>Right-click an upgraded press with the module again: shows its stored mana.</li>
 *   <li>Sneak-right-click with a Create Wrench: detaches and returns the module (the press stays).</li>
 *   <li>Upgraded presses accept mana from spreader bursts and Terrasteel mana pipes, and run
 *       {@code viridian_engineering:mana_infusion} recipes on belts, depots and dropped items.
 *       Items without an infusion recipe are pressed normally.</li>
 * </ul>
 */
public final class AlfheimPressHandler {

    /** Enough for any default recipe (mana diamond block = 90,000). */
    public static final int MANA_CAPACITY = 100_000;

    private AlfheimPressHandler() {}

    // ------------------------------------------------------------------ recipes

    public static Optional<RecipeHolder<ManaInfusionRecipe>> findRecipe(Level level, ItemStack stack) {
        if (level == null || stack.isEmpty()) return Optional.empty();
        return level.getRecipeManager()
                .getRecipeFor(VERecipes.MANA_INFUSION_TYPE.get(), new SingleRecipeInput(stack), level);
    }

    /**
     * Called from the mixin at the start of {@code tryProcessOnBelt}.
     *
     * @return null to let Create press the item normally, otherwise the result to return.
     */
    @Nullable
    public static Boolean processOnBelt(MechanicalPressBlockEntity press, TransportedItemStack input,
                                       @Nullable List<ItemStack> outputList, boolean simulate) {
        IAlfheimPress alfheim = (IAlfheimPress) press;
        if (!alfheim.ve$hasAlfheimModule()) return null;
        Optional<RecipeHolder<ManaInfusionRecipe>> holder = findRecipe(press.getLevel(), input.stack);
        if (holder.isEmpty()) return null;
        ManaInfusionRecipe recipe = holder.get().value();
        if (Math.abs(press.getSpeed()) < recipe.getMinRpm()) return false;

        int count = press.canProcessInBulk() ? input.stack.getCount() : 1;
        long cost = (long) recipe.getMana() * count;
        if (alfheim.ve$getMana() < cost) return false; // not enough mana: leave the item alone
        if (simulate) return true;

        alfheim.ve$setMana((int) (alfheim.ve$getMana() - cost));
        press.pressingBehaviour.particleItems.add(input.stack);

        ItemStack result = recipe.getOutput().copyWithCount(recipe.getOutput().getCount() * count);
        if (outputList != null) outputList.add(result);
        press.onItemPressed(result);
        playInfusion(press);
        press.setChanged();
        return true;
    }

    /** Same as {@link #processOnBelt} for item entities lying under the press. */
    @Nullable
    public static Boolean processInWorld(MechanicalPressBlockEntity press, ItemEntity itemEntity, boolean simulate) {
        IAlfheimPress alfheim = (IAlfheimPress) press;
        if (!alfheim.ve$hasAlfheimModule()) return null;
        ItemStack item = itemEntity.getItem();
        Optional<RecipeHolder<ManaInfusionRecipe>> holder = findRecipe(press.getLevel(), item);
        if (holder.isEmpty()) return null;
        ManaInfusionRecipe recipe = holder.get().value();
        if (Math.abs(press.getSpeed()) < recipe.getMinRpm()) return false;

        boolean bulk = press.canProcessInBulk() || item.getCount() == 1;
        int count = bulk ? item.getCount() : 1;
        long cost = (long) recipe.getMana() * count;
        if (alfheim.ve$getMana() < cost) return false;
        if (simulate) return true;

        alfheim.ve$setMana((int) (alfheim.ve$getMana() - cost));
        press.pressingBehaviour.particleItems.add(item);
        ItemStack result = recipe.getOutput().copyWithCount(recipe.getOutput().getCount() * count);

        if (bulk) {
            itemEntity.setItem(result);
        } else {
            item.shrink(1);
            ItemEntity created = new ItemEntity(press.getLevel(), itemEntity.getX(), itemEntity.getY(),
                    itemEntity.getZ(), result);
            created.setDefaultPickUpDelay();
            created.setDeltaMovement(Vec3.ZERO);
            press.getLevel().addFreshEntity(created);
        }
        press.onItemPressed(result);
        playInfusion(press);
        press.setChanged();
        return true;
    }

    public static void playInfusion(SmartBlockEntity machine) {
        Level level = machine.getLevel();
        if (level != null && !level.isClientSide)
            level.playSound(null, machine.getBlockPos(), dev.viridianengineering.registry.VESounds.ALFHEIM_INFUSE.get(), SoundSource.BLOCKS, .7f, 1.1f);
    }

    // ------------------------------------------------------------------ mana capability

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, AllBlockEntityTypes.MECHANICAL_PRESS.get(),
                (press, side) -> receiver(press));
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER, AllBlockEntityTypes.MECHANICAL_MIXER.get(),
                (mixer, side) -> receiver(mixer));
    }

    @Nullable
    private static ManaReceiver receiver(SmartBlockEntity machine) {
        IAlfheimPress module = (IAlfheimPress) machine;
        return module.ve$hasAlfheimModule() ? new MachineManaReceiver(machine, module) : null;
    }

    private static int capacity(SmartBlockEntity machine) {
        return machine instanceof MechanicalMixerBlockEntity ? VEConfig.ALFHEIM_MIXER_MANA_CAPACITY.get() : MANA_CAPACITY;
    }

    private record MachineManaReceiver(SmartBlockEntity machine, IAlfheimPress module) implements ManaReceiver {
        @Override public Level getManaReceiverLevel() { return machine.getLevel(); }
        @Override public BlockPos getManaReceiverPos() { return machine.getBlockPos(); }
        @Override public int getCurrentMana() { return module.ve$getMana(); }
        @Override public boolean isFull() { return getCurrentMana() >= capacity(machine); }
        @Override public boolean canReceiveManaFromBursts() { return module.ve$hasAlfheimModule(); }

        @Override
        public void receiveMana(int mana) {
            if (!module.ve$hasAlfheimModule()) return;
            long next = Math.clamp((long) module.ve$getMana() + mana, 0, capacity(machine));
            if (next != module.ve$getMana()) {
                module.ve$setMana((int) next);
                machine.setChanged();
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    /** Game-bus listener: attach with the module, detach with sneak + wrench. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (!(be instanceof SmartBlockEntity machine) || !(be instanceof IAlfheimPress alfheim)) return;
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();

        if (VEItems.ALFHEIM_INFUSION_MODULE.isIn(held)) {
            if (!level.isClientSide) {
                if (alfheim.ve$hasAlfheimModule()) {
                    player.displayClientMessage(Component.translatable("viridian_engineering.alfheim.status",
                            alfheim.ve$getMana(), capacity(machine)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                } else {
                    alfheim.ve$setAlfheimModule(true);
                    alfheim.ve$setMana(0);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    afterChange(machine);
                    level.playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
                    player.displayClientMessage(Component.translatable("viridian_engineering.alfheim.attached")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
                if (machine instanceof MechanicalMixerBlockEntity mixer && player instanceof ServerPlayer serverPlayer) {
                    AlfheimMixerHandler.unlockIfQualified(mixer, serverPlayer);
                    if (!((IAlfheimMixer) mixer).ve$isTerrasteelUnlocked())
                        player.displayClientMessage(Component.translatable("viridian_engineering.alfheim.alloy_locked")
                                .withStyle(ChatFormatting.GRAY), false);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            return;
        }

        if (player.isShiftKeyDown() && AllItems.WRENCH.isIn(held) && alfheim.ve$hasAlfheimModule()) {
            if (!level.isClientSide) {
                alfheim.ve$setAlfheimModule(false);
                alfheim.ve$setMana(0);
                afterChange(machine);
                ItemStack module = VEItems.ALFHEIM_INFUSION_MODULE.asStack();
                if (!player.getInventory().add(module))
                    player.drop(module, false);
                level.playSound(null, event.getPos(), dev.viridianengineering.registry.VESounds.ALFHEIM_DETACH.get(), SoundSource.BLOCKS, 0.6f, 1.4f);
                player.displayClientMessage(Component.translatable("viridian_engineering.alfheim.detached")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        }
    }

    /** Game-bus listener: a broken machine drops its module. */
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.getBlockEntity() instanceof IAlfheimPress module && module.ve$hasAlfheimModule()) {
            BlockPos pos = event.getPos();
            event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    VEItems.ALFHEIM_INFUSION_MODULE.asStack()));
        }
    }

    private static void afterChange(SmartBlockEntity machine) {
        machine.setChanged();
        machine.sendData();
        if (machine.getLevel() != null)
            machine.getLevel().invalidateCapabilities(machine.getBlockPos());
    }
}

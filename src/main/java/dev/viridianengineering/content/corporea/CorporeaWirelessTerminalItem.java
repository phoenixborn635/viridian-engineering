package dev.viridianengineering.content.corporea;

import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Corporea Wireless Terminal (decision 2A).
 * <ul>
 *   <li>Sneak-right-click a Corporea Crafting Terminal to bind to its persistent UUID.</li>
 *   <li>Right-click in the air to open the bound network from anywhere in the same dimension,
 *       as long as the terminal's chunk is loaded.</li>
 *   <li>Works only while the network is powered (at least 32 RPM). Unpowered, it refuses to open,
 *       and an already open UI goes empty until power returns.</li>
 * </ul>
 */
public class CorporeaWirelessTerminalItem extends Item {

    private static final String KEY = "BoundTerminal";
    private static final String ID = "BoundTerminalId";

    public CorporeaWirelessTerminalItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static MachineEndpointKey boundTerminal(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY)) return null;
        return MachineEndpointKey.CODEC.parse(NbtOps.INSTANCE, tag.get(KEY)).result().orElse(null);
    }

    public static void bind(ItemStack stack, MachineEndpointKey key) {
        MachineEndpointKey.CODEC.encodeStart(NbtOps.INSTANCE, key).result().ifPresent(encoded ->
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.remove(ID);
                    tag.put(KEY, encoded);
                }));
    }

    public static void bind(ItemStack stack, ServerLevel level, CorporeaTerminalBlockEntity terminal) {
        bind(stack, SableCompat.endpointKey(level, terminal.getBlockPos(), Direction.DOWN));
        UUID id = terminal.bindWireless(level);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(ID, id));
    }

    @Nullable
    private static UUID boundId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(ID) ? tag.getUUID(ID) : null;
    }

    @Nullable
    public static MachineEndpointKey currentBinding(ServerLevel level, ItemStack stack) {
        UUID id = boundId(stack);
        return id == null ? boundTerminal(stack) : TerminalBindings.get(level.getServer()).endpoint(id);
    }

    private static boolean matchesTerminal(ServerLevel level, MachineEndpointKey key, ItemStack stack) {
        UUID id = boundId(stack);
        if (id == null) return true;
        BlockPos pos = SableCompat.resolve(level, key);
        return pos != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof CorporeaTerminalBlockEntity terminal && id.equals(terminal.bindingId());
    }

    static boolean isHoldingBoundTo(Player player, MachineEndpointKey key) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof CorporeaWirelessTerminalItem && key.equals(currentBinding(level, held))
                    && matchesTerminal(level, key, held)) return true;
        }
        return false;
    }

    /** Why the bound terminal can't be opened right now, or null if it can. */
    @Nullable
    public static Component unavailableReason(ServerLevel level, @Nullable MachineEndpointKey key) {
        if (key == null) return Component.translatable("gui.viridian_engineering.wireless.unbound");
        if (!level.dimension().equals(key.dimension()))
            return Component.translatable("gui.viridian_engineering.wireless.other_dimension");
        BlockPos pos = SableCompat.resolve(level, key);
        if (pos == null || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(VEBlocks.CORPOREA_CRAFTING_TERMINAL.get()))
            return Component.translatable("gui.viridian_engineering.wireless.unreachable");
        if (!CorporeaNetworkManager.isPowered(level, pos))
            return Component.translatable("gui.viridian_engineering.wireless.unpowered");
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !player.isShiftKeyDown()
                || !level.getBlockState(context.getClickedPos()).is(VEBlocks.CORPOREA_CRAFTING_TERMINAL.get()))
            return InteractionResult.PASS;
        if (level instanceof ServerLevel server
                && level.getBlockEntity(context.getClickedPos()) instanceof CorporeaTerminalBlockEntity terminal) {
            bind(context.getItemInHand(), server, terminal);
            player.displayClientMessage(Component.translatable("gui.viridian_engineering.wireless.bound")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        MachineEndpointKey key = currentBinding(serverLevel, stack);
        Component reason = unavailableReason(serverLevel, key);
        if ((key == null && boundId(stack) != null) || (reason == null && !matchesTerminal(serverLevel, key, stack)))
            reason = Component.translatable("gui.viridian_engineering.wireless.unreachable");
        if (reason != null) {
            player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        BlockPos pos = SableCompat.resolve(serverLevel, key);
        if (boundId(stack) == null && serverLevel.getBlockEntity(pos) instanceof CorporeaTerminalBlockEntity terminal)
            bind(stack, serverLevel, terminal);
        server.openMenu(new SimpleMenuProvider((id, inv, p) -> new CorporeaTerminalMenu(id, inv, pos, true),
                        Component.translatable("item.viridian_engineering.corporea_wireless_terminal")),
                buf -> CorporeaTerminalMenu.writeOpenData(buf, pos, true));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MachineEndpointKey key = boundTerminal(stack);
        if (key == null) {
            tooltip.add(Component.translatable("gui.viridian_engineering.wireless.tooltip_unbound").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos p = key.localPos();
            tooltip.add(Component.translatable("gui.viridian_engineering.wireless.tooltip_bound",
                    p.getX(), p.getY(), p.getZ(), key.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
            if (key.isOnShip())
                tooltip.add(Component.translatable("gui.viridian_engineering.wireless.tooltip_ship").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boundTerminal(stack) != null;
    }
}

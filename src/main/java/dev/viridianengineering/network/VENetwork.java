package dev.viridianengineering.network;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.corporea.CorporeaTerminalMenu;
import dev.viridianengineering.content.corporea.TerminalView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Custom packets. Handlers run on the main thread (NeoForge default). */
public final class VENetwork {

    private VENetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(TerminalPage.TYPE, TerminalPage.STREAM_CODEC, VENetwork::onTerminalPage);
        r.playToServer(TerminalAction.TYPE, TerminalAction.STREAM_CODEC, VENetwork::onTerminalAction);
        r.playToClient(TerminalStatus.TYPE, TerminalStatus.STREAM_CODEC, VENetwork::onTerminalStatus);
        r.playToClient(TerminalJobs.TYPE, TerminalJobs.STREAM_CODEC, (jobs, context) -> {
            if (context.player().containerMenu instanceof CorporeaTerminalMenu menu && menu.containerId == jobs.containerId())
                menu.acceptJobs(jobs);
        });
        r.playToClient(BatteryFailureStart.TYPE, BatteryFailureStart.STREAM_CODEC, VENetwork::onBatteryFailureStart);
        r.playToClient(BatteryDetonated.TYPE, BatteryDetonated.STREAM_CODEC, VENetwork::onBatteryDetonated);
        r.playToServer(DeckDash.TYPE, DeckDash.STREAM_CODEC, VENetwork::onDeckDash);
        r.playToClient(LanceCharge.TYPE, LanceCharge.STREAM_CODEC,
                (charge, context) -> dev.viridianengineering.client.GearClient.acceptCharge(charge));
        r.playToClient(LanceFx.TYPE, LanceFx.STREAM_CODEC, VENetwork::onLanceFx);
    }

    public record LanceCharge(int ticks, boolean stalled) implements CustomPacketPayload {
        public static final Type<LanceCharge> TYPE = new Type<>(ViridianEngineering.asResource("lance_charge"));
        public static final StreamCodec<ByteBuf, LanceCharge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LanceCharge::ticks, ByteBufCodecs.BOOL, LanceCharge::stalled, LanceCharge::new);
        @Override public Type<LanceCharge> type() { return TYPE; }
    }

    /** Server-authored charge meter for the wielder. */
    public static void lanceCharge(ServerPlayer player, int ticks, boolean stalled) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new LanceCharge(ticks, stalled));
    }

    /**
     * Gaia Kinetic Lance effects for everyone watching: {@code kind} 0 = the wind-lunge's gale (from
     * (x, y, z) along (dx, dy, dz) for {@code length} blocks), 1 = an empowered strike landing at (x, y, z)
     * with {@code length} = how much of the kinetic bonus it had (0..1).
     */
    public record LanceFx(int kind, double x, double y, double z, float dx, float dy, float dz, float length)
            implements CustomPacketPayload {
        public static final Type<LanceFx> TYPE = new Type<>(ViridianEngineering.asResource("lance_fx"));
        public static final StreamCodec<ByteBuf, LanceFx> STREAM_CODEC = new StreamCodec<>() {
            @Override public LanceFx decode(ByteBuf b) {
                return new LanceFx(b.readByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(),
                        b.readFloat(), b.readFloat());
            }
            @Override public void encode(ByteBuf b, LanceFx p) {
                b.writeByte(p.kind); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
                b.writeFloat(p.dx); b.writeFloat(p.dy); b.writeFloat(p.dz); b.writeFloat(p.length);
            }
        };

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void onLanceFx(LanceFx packet, IPayloadContext ctx) {
        dev.viridianengineering.client.LanceEffects.start(packet); // client only
    }

    /** Server: shows a lance effect to everyone tracking {@code player}, and the player. */
    public static void lanceFx(ServerPlayer player, LanceFx fx) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, fx);
    }

    /**
     * Pneumatic Boot Thrusters: the client saw a midair jump or a double-tapped sprint and asks for a
     * burst in a horizontal direction. Only an intent: the server checks the boots, cooldown and air.
     */
    public record DeckDash(float dx, float dz, boolean jump) implements CustomPacketPayload {
        public static final Type<DeckDash> TYPE = new Type<>(ViridianEngineering.asResource("deck_dash"));
        public static final StreamCodec<ByteBuf, DeckDash> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, DeckDash::dx, ByteBufCodecs.FLOAT, DeckDash::dz, ByteBufCodecs.BOOL, DeckDash::jump, DeckDash::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void onDeckDash(DeckDash packet, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer p)
            dev.viridianengineering.content.gear.SylvanEvents.deckDash(p, packet.dx(), packet.dz(), packet.jump());
    }

    /**
     * Spec 3.4 {@code BatteryFailureStartS2CPacket}: a battery turned UNSTABLE; clients play the
     * 200-tick fracture, levitation and core-collapse cinematic until {@code detonationTick}.
     */
    public record BatteryFailureStart(java.util.UUID eventId, net.minecraft.core.BlockPos controller, int validLayers,
                                      float fillFraction, long detonationTick) implements CustomPacketPayload {
        public static final Type<BatteryFailureStart> TYPE = new Type<>(ViridianEngineering.asResource("battery_failure_start"));
        public static final StreamCodec<ByteBuf, BatteryFailureStart> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, BatteryFailureStart::eventId,
                net.minecraft.core.BlockPos.STREAM_CODEC, BatteryFailureStart::controller,
                ByteBufCodecs.VAR_INT, BatteryFailureStart::validLayers,
                ByteBufCodecs.FLOAT, BatteryFailureStart::fillFraction,
                ByteBufCodecs.VAR_LONG, BatteryFailureStart::detonationTick,
                BatteryFailureStart::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A battery detonated: clients draw the expanding mana sphere. */
    public record BatteryDetonated(double x, double y, double z, float radius, float fill) implements CustomPacketPayload {
        public static final Type<BatteryDetonated> TYPE = new Type<>(ViridianEngineering.asResource("battery_detonated"));
        public static final StreamCodec<ByteBuf, BatteryDetonated> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, BatteryDetonated::x, ByteBufCodecs.DOUBLE, BatteryDetonated::y, ByteBufCodecs.DOUBLE, BatteryDetonated::z,
                ByteBufCodecs.FLOAT, BatteryDetonated::radius, ByteBufCodecs.FLOAT, BatteryDetonated::fill, BatteryDetonated::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void onBatteryDetonated(BatteryDetonated packet, IPayloadContext ctx) {
        dev.viridianengineering.client.ManaExplosions.start(packet); // client only
    }

    private static void onBatteryFailureStart(BatteryFailureStart packet, IPayloadContext ctx) {
        dev.viridianengineering.client.BatteryCinematics.start(packet); // client only: this handler never runs on a server
    }

    /**
     * Spec 3.6 {@code TerminalSyncPageS2CPacket}: one 64-entry slice of the server-side,
     * already searched and sorted view.
     */
    public record TerminalPage(int containerId, int page, int pages, int totalTypes, int flags,
                               List<TerminalView.Entry> entries) implements CustomPacketPayload {
        public static final int POWERED = 1, WIRELESS = 2, PATTERNS = 4;

        public boolean patterns() { return (flags & PATTERNS) != 0; }

        public boolean powered() { return (flags & POWERED) != 0; }

        public boolean wireless() { return (flags & WIRELESS) != 0; }

        public static final Type<TerminalPage> TYPE = new Type<>(ViridianEngineering.asResource("terminal_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TerminalPage> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalPage::containerId,
                ByteBufCodecs.VAR_INT, TerminalPage::page,
                ByteBufCodecs.VAR_INT, TerminalPage::pages,
                ByteBufCodecs.VAR_INT, TerminalPage::totalTypes,
                ByteBufCodecs.VAR_INT, TerminalPage::flags,
                TerminalView.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(TerminalView.PAGE_SIZE)), TerminalPage::entries,
                TerminalPage::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * RECORD: save the crafting grid's recipe as a pattern. RECORD_RECIPE: save recipe {@code text}
     * (JEI / EMI transfer). MODE: toggle items / patterns view. CRAFT: request {@code text} of pattern
     * {@code index}. REMOVE_PATTERN: delete pattern {@code index}. CANCEL_JOB: cancel the running job.
     */
    public enum Action { PAGE, SEARCH, SORT, WITHDRAW, DEPOSIT, RECORD, RECORD_RECIPE, MODE, CRAFT, REMOVE_PATTERN, CANCEL_JOB, JOBS, JOB_SCROLL }

    public record JobRow(java.util.UUID id, String target, String progress, String machine, boolean cancellable) {
        static final StreamCodec<ByteBuf, JobRow> CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, JobRow::id,
                ByteBufCodecs.stringUtf8(160), JobRow::target,
                ByteBufCodecs.stringUtf8(100), JobRow::progress,
                ByteBufCodecs.stringUtf8(160), JobRow::machine,
                ByteBufCodecs.BOOL, JobRow::cancellable, JobRow::new);
    }

    public record TerminalJobs(int containerId, int first, int total, List<JobRow> rows) implements CustomPacketPayload {
        public static final int VISIBLE_ROWS = 6;
        public static final Type<TerminalJobs> TYPE = new Type<>(ViridianEngineering.asResource("terminal_jobs"));
        public static final StreamCodec<ByteBuf, TerminalJobs> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalJobs::containerId,
                ByteBufCodecs.VAR_INT, TerminalJobs::first,
                ByteBufCodecs.VAR_INT, TerminalJobs::total,
                JobRow.CODEC.apply(ByteBufCodecs.list(VISIBLE_ROWS)), TerminalJobs::rows, TerminalJobs::new);

        @Override public Type<TerminalJobs> type() { return TYPE; }
    }

    /** One status line for the terminal (the current autocrafting job), sent when it changes. */
    public record TerminalStatus(int containerId, String text) implements CustomPacketPayload {
        public static final Type<TerminalStatus> TYPE = new Type<>(ViridianEngineering.asResource("terminal_status"));
        public static final StreamCodec<ByteBuf, TerminalStatus> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalStatus::containerId,
                ByteBufCodecs.stringUtf8(256), TerminalStatus::text,
                TerminalStatus::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Client to server terminal input. The server re-validates everything: the menu must be
     * open and valid, and indexes refer to the page the server last sent.
     */
    public record TerminalAction(int containerId, int action, int index, int button, boolean shift, String text)
            implements CustomPacketPayload {
        public static final Type<TerminalAction> TYPE = new Type<>(ViridianEngineering.asResource("terminal_action"));
        public static final StreamCodec<ByteBuf, TerminalAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalAction::containerId,
                ByteBufCodecs.VAR_INT, TerminalAction::action,
                ByteBufCodecs.VAR_INT, TerminalAction::index,
                ByteBufCodecs.VAR_INT, TerminalAction::button,
                ByteBufCodecs.BOOL, TerminalAction::shift,
                ByteBufCodecs.stringUtf8(256), TerminalAction::text,
                TerminalAction::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void onTerminalPage(TerminalPage page, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof CorporeaTerminalMenu menu && menu.containerId == page.containerId())
            menu.acceptPage(page);
    }

    private static void onTerminalStatus(TerminalStatus status, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof CorporeaTerminalMenu menu && menu.containerId == status.containerId())
            menu.acceptStatus(status.text());
    }

    private static void onTerminalAction(TerminalAction action, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player
                && player.containerMenu instanceof CorporeaTerminalMenu menu
                && menu.containerId == action.containerId()) {
            menu.handleAction(player, action);
        }
    }
}

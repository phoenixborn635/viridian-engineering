package dev.viridianengineering.api.mana;

import net.minecraft.core.Direction;

/**
 * A node on the Terrasteel mana-only pipe network (spec 3.2).
 * <p>
 * Deliberately NOT a fluid handler: this is exposed only through
 * {@link dev.viridianengineering.registry.ModCapabilities#MANA_PIPE_NODE}, so Create
 * fluid pipes, hoses and tanks can never touch mana.
 * <p>
 * Every transfer must conserve mana: {@code extracted == inserted}.
 */
public interface IManaPipeNode {

    long getStoredMana();

    long getManaCapacity();

    /**
     * @return how much mana was (or, if {@code simulate}, would be) accepted.
     */
    long insertMana(long amount, boolean simulate);

    /**
     * @return how much mana was (or, if {@code simulate}, would be) removed.
     */
    long extractMana(long amount, boolean simulate);

    /** Whether a pipe may connect to this node from the given side. */
    default boolean canConnectMana(Direction side) {
        return true;
    }

    /** How pipes treat this node when no pump drives them. */
    enum Role {
        /** Holds mana both ways (pools, batteries, pipes): pipes equalize with it by fill fraction. */
        STORE,
        /** Only gives mana (a generator's output, a creative pool): pipes drain it whenever they have room. */
        SOURCE,
        /** Only takes mana (a machine): pipes feed it whenever they hold some. */
        SINK
    }

    default Role role() {
        return Role.STORE;
    }

    default float getFillFraction() {
        long cap = getManaCapacity();
        return cap <= 0 ? 0f : (float) ((double) getStoredMana() / (double) cap);
    }
}

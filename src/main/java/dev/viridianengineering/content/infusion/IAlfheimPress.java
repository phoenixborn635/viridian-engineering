package dev.viridianengineering.content.infusion;

/**
 * Shared Alfheim attachment and mana state on Create presses and mixers.
 * Both values are persisted with their block entity.
 */
public interface IAlfheimPress {

    boolean ve$hasAlfheimModule();

    void ve$setAlfheimModule(boolean attached);

    int ve$getMana();

    void ve$setMana(int mana);
}

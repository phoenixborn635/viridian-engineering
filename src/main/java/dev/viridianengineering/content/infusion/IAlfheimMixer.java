package dev.viridianengineering.content.infusion;

/** The mixer's module state includes the installer's verified Terrasteel progression. */
public interface IAlfheimMixer extends IAlfheimPress {
    boolean ve$isTerrasteelUnlocked();
    void ve$unlockTerrasteel();
}

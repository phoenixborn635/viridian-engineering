# Viridian Engineering

Viridian Engineering is a Minecraft 1.21.1 NeoForge addon that connects Botania Revived mana, Create machinery and logistics, and Create: Aeronautics airships.

## Installation

Use Minecraft **1.21.1** and Java **21**. Install the mod and its dependencies on the client and dedicated server. These are the versions used for release-candidate checks.

| Dependency | Version |
|---|---|
| NeoForge | 21.1.257 |
| Create | 6.0.10 |
| Botania Revived | 2.0.4 |
| Patchouli | 1.21.1-93-NEOFORGE |
| Curios | 9.5.1+1.21.1 |
| Create: Aeronautics | 1.3.3 |
| Sable | 2.0.6 |

JEI and EMI are optional and display Alfheim infusion recipes. Better Combat is optional and provides spear attacks for the Gaia Kinetic Lance.

## Features

- **Mana industry** includes Terrasteel mana pipes, faster Manasteel fluid pipes, wrench-toggle sight glasses, and Alfheim upgrades for the Mechanical Press and Mechanical Mixer. Liquid Mana for Create recipes moves through ordinary Create fluid pipes.
- **Mana structures** include a scalable Terrasteel Mana Reactor, a layered Mana Battery, and the redstone-triggered Gaia Arcane Cannon.
- **Corporea logistics** includes a crafting terminal, Archive Pylons with Terra Drive storage, Relay Sparks, and input, output, machine, and bridge ports. The terminal records crafting patterns and shows active jobs. Connected pipes carry light toward the nearest wired terminal.
- **Elementium logistics** uses a Request Interface to tell nearby Smart Funnels and Smart Tunnels what items to move. Use a held stack on the interface to add a request and an empty hand to open its status screen.
- **Airships** can use Mana Levitation Pylons, Gaia Thrusters, the Bifrost Road Projector, and the Flügel Navigation Core.
- **Gaia gear** includes the Gaia Kinetic Lance and Sylvan armor. The lance’s lower-right meter shows wind-lunge readiness, charge, and cooldown.

### Mana Levitation Pylon

| Redstone strength | Behavior on a powered ship |
|---|---|
| 0 | Hover |
| 1–7 | Ascend |
| 8–15 | Descend |

The pylon reads the strongest signal on either half. Stronger signals move the ship faster within each range. At signal 0, a navigating Flügel Navigation Core supplies the flight setpoint.

## Development

Run these commands from the mod directory with JDK 21 installed.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runGameTestServer
.\gradlew.bat runData
```

Generated blockstates, loot tables, tags, and translations are in `src/generated/resources`. Art generators are in `tools`.

## Release validation

The packaged `0.7.0-phase5a` JAR passed all 92 required GameTests. Bulk crafting accounted for all 800 gold across restart and cancellation checks. Client world entry and a live dedicated-server join are still pending because the validation environment denied localhost connections.

See [CHANGELOG.md](CHANGELOG.md) for the release-candidate changes.

**License** · All Rights Reserved

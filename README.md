# HighwayTools for Meteor Client

Port of the Lambda plugin [HighwayTools](https://github.com/lambda-plugins/HighwayTools) (by Constructor / Avanatiker)
to Meteor Client 26.2 (Minecraft 26.2, Fabric).

Fully automated highway building: places building material and destroys wrong blocks with well-timed packets,
handles liquids, restocks from shulker boxes and ender chests, and paves in all 8 directions.

## Install

1. Build with `./gradlew build` (JDK 25). The jar lands in `build/libs/`.
   Note: Fabric Loom cannot rewrite its intermediate jars on a network share (`ReadOnlyFileSystemException`),
   so copy the project to a local drive before building if it lives on one.
2. Drop `highwaytools-<version>.jar` into your `mods` folder next to `meteor-client`.
3. Optional but strongly recommended: install Baritone (`meteordevelopment:baritone` build for 26.2).
   Without Baritone the module falls back to walking straight towards its goal and cannot path around obstacles.

## Usage

- Module: `World > HighwayTools` (aliases `ht`, `hwt`). Look in the direction you want to build and enable it.
- HUD: add the `HighwayTools` element in the HUD editor for live session/lifetime/performance stats.
- Commands (prefix is your Meteor prefix, default `.`):
  - `.ht` / `.ht settings` prints materials and ignored blocks
  - `.ht add <block>` / `.ht remove <block>` edits the ignore list
  - `.ht material <block>` / `.ht filler <block>` picks the main and filler material
  - `.ht food <item>` / `.ht tool <item>` picks the food and pickaxe that get restocked
  - `.ht distance <blocks>` stops after the given distance (0 removes the limit)
  - `.ht reset` resets the statistics

## Differences to the Lambda plugin

- Setting pages are Meteor setting groups (Blueprint, Behavior, Mining, Placing, Storage Management, Render).
- Lambda's `InventoryManager` eject list is replaced by the `Eject Items` setting in Storage Management.
- Lambda's `LagNotifier` pause is replaced by the `Lag Pause` setting (seconds since the last server tick).
- Warnings reference Meteor modules (`AutoEat`, `AutoLog`, `AntiHunger`, `Velocity`, `AntiAFK`).
- Restock tool is configurable (`Tool` setting, default diamond pickaxe).

## License

The original HighwayTools is copyright Constructor / Avanatiker and is provided for educational,
research and not-for-profit purposes. This port keeps the same terms.

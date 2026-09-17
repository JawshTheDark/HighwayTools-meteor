package com.jawsh.highwaytools.modules;

import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.Renderer;
import com.jawsh.highwaytools.trombone.Trombone;
import com.jawsh.highwaytools.trombone.handler.ContainerHandler;
import com.jawsh.highwaytools.trombone.handler.PacketHandler;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.task.TaskState;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of the Lambda HighwayTools plugin (by Avanatiker / Constructor) to Meteor Client.
 */
public class HighwayTools extends Module {
    public static HighwayTools INSTANCE;

    private final SettingGroup sgBlueprint = settings.createGroup("Blueprint");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgMining = settings.createGroup("Mining");
    private final SettingGroup sgPlacing = settings.createGroup("Placing");
    private final SettingGroup sgStorage = settings.createGroup("Storage Management");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Blueprint
    public final Setting<Trombone.Structure> mode = sgBlueprint.add(new EnumSetting.Builder<Trombone.Structure>()
        .name("mode").description("Choose the structure.").defaultValue(Trombone.Structure.HIGHWAY).build());
    public final Setting<Integer> width = sgBlueprint.add(new IntSetting.Builder()
        .name("width").description("Sets the width of the blueprint in blocks.").defaultValue(6).range(1, 11).sliderRange(1, 11).build());
    public final Setting<Boolean> clearSpace = sgBlueprint.add(new BoolSetting.Builder()
        .name("clear-space").description("Clears out the tunnel if necessary.").defaultValue(true).build());
    public final Setting<Integer> height = sgBlueprint.add(new IntSetting.Builder()
        .name("height").description("Sets the height of the blueprint in blocks.").defaultValue(4).range(2, 6).sliderRange(2, 6)
        .visible(clearSpace::get).build());
    public final Setting<Boolean> backfill = sgBlueprint.add(new BoolSetting.Builder()
        .name("backfill").description("Fills the tunnel behind you.").defaultValue(false)
        .visible(() -> mode.get() == Trombone.Structure.TUNNEL).build());
    public final Setting<Boolean> cleanFloor = sgBlueprint.add(new BoolSetting.Builder()
        .name("clean-floor").description("Cleans up the tunnel's floor.").defaultValue(false)
        .visible(() -> mode.get() == Trombone.Structure.TUNNEL && !backfill.get()).build());
    public final Setting<Boolean> cleanRightWall = sgBlueprint.add(new BoolSetting.Builder()
        .name("clean-right-wall").description("Cleans up the right wall.").defaultValue(false)
        .visible(() -> mode.get() == Trombone.Structure.TUNNEL && !backfill.get()).build());
    public final Setting<Boolean> cleanLeftWall = sgBlueprint.add(new BoolSetting.Builder()
        .name("clean-left-wall").description("Cleans up the left wall.").defaultValue(false)
        .visible(() -> mode.get() == Trombone.Structure.TUNNEL && !backfill.get()).build());
    public final Setting<Boolean> cleanRoof = sgBlueprint.add(new BoolSetting.Builder()
        .name("clean-roof").description("Cleans up the tunnel's roof.").defaultValue(false)
        .visible(() -> mode.get() == Trombone.Structure.TUNNEL && !backfill.get()).build());
    public final Setting<Boolean> cornerBlock = sgBlueprint.add(new BoolSetting.Builder()
        .name("corner-block").description("If activated will break the corner in tunnel or place a corner while paving.").defaultValue(false)
        .visible(() -> mode.get() == Trombone.Structure.HIGHWAY || (mode.get() == Trombone.Structure.TUNNEL && !backfill.get() && width.get() > 2)).build());
    public final Setting<Boolean> cleanCorner = sgBlueprint.add(new BoolSetting.Builder()
        .name("clean-corner").description("Cleans up the tunnel's corner.").defaultValue(false)
        .visible(() -> mode.get() == Trombone.Structure.TUNNEL && !cornerBlock.get() && !backfill.get() && width.get() > 2).build());
    public final Setting<Boolean> railing = sgBlueprint.add(new BoolSetting.Builder()
        .name("railing").description("Adds a railing/rim/border to the highway.").defaultValue(true)
        .visible(() -> mode.get() == Trombone.Structure.HIGHWAY).build());
    public final Setting<Integer> railingHeight = sgBlueprint.add(new IntSetting.Builder()
        .name("railing-height").description("Sets the height of the railing in blocks.").defaultValue(1).range(1, 4).sliderRange(1, 4)
        .visible(() -> railing.get() && mode.get() == Trombone.Structure.HIGHWAY).build());
    public final Setting<Block> material = sgBlueprint.add(new BlockSetting.Builder()
        .name("material").description("Main building material.").defaultValue(Blocks.OBSIDIAN).build());
    public final Setting<Block> fillerMat = sgBlueprint.add(new BlockSetting.Builder()
        .name("filler-material").description("Filler material used to patch liquids and supports.").defaultValue(Blocks.NETHERRACK).build());
    public final Setting<Item> food = sgBlueprint.add(new ItemSetting.Builder()
        .name("food").description("Food item that gets restocked.").defaultValue(Items.GOLDEN_APPLE).filter(HighwayTools::isFoodSafe).build());
    public final Setting<Item> tool = sgBlueprint.add(new ItemSetting.Builder()
        .name("tool").description("Pickaxe that gets restocked.").defaultValue(Items.DIAMOND_PICKAXE)
        .filter(HighwayTools::isPickaxeSafe).build());
    public final Setting<List<Block>> ignoreBlocks = sgBlueprint.add(new BlockListSetting.Builder()
        .name("ignore-blocks").description("Blocks that will not be broken.").defaultValue(defaultIgnoreBlocks()).build());

    // Behavior
    public final Setting<Double> maxReach = sgBehavior.add(new DoubleSetting.Builder()
        .name("max-reach").description("Sets the range of the blueprint. Decrease when tasks fail!").defaultValue(4.9).range(1.0, 7.0).sliderRange(1.0, 7.0).build());
    public final Setting<Integer> rubberbandTimeout = sgBehavior.add(new IntSetting.Builder()
        .name("rubberband-timeout").description("Ticks to pause after a rubberband / lag back.").defaultValue(50).range(5, 100).sliderRange(5, 100).build());
    public final Setting<Integer> taskTimeout = sgBehavior.add(new IntSetting.Builder()
        .name("task-timeout").description("Ticks to wait for the server before trying again.").defaultValue(8).range(0, 20).sliderRange(0, 20).build());
    public final Setting<Double> moveSpeed = sgBehavior.add(new DoubleSetting.Builder()
        .name("packet-move-speed").description("Maximum player velocity per tick while bridging.").defaultValue(0.2).range(0.0, 1.0).sliderRange(0.0, 1.0).build());
    public final Setting<Double> lagPause = sgBehavior.add(new DoubleSetting.Builder()
        .name("lag-pause").description("Pause when no server time update arrived for this many seconds (0 to disable). Values below 2 pause constantly on slow servers.").defaultValue(0.0).range(0.0, 10.0).sliderRange(0.0, 10.0).build());
    public final Setting<Integer> minHunger = sgBehavior.add(new IntSetting.Builder()
        .name("min-hunger").description("Pause while the hunger bar is below this value so AutoEat can eat. Disables only when no food is left.").defaultValue(7).range(0, 20).sliderRange(0, 20).build());
    public final Setting<Boolean> noSprint = sgBehavior.add(new BoolSetting.Builder()
        .name("no-sprint").description("Prevents sprinting (also for Baritone) to save hunger.").defaultValue(true).build());
    public final Setting<Boolean> multiBuilding = sgBehavior.add(new BoolSetting.Builder()
        .name("shuffle-tasks").description("Only activate when working with several players.").defaultValue(false).build());
    public final Setting<Integer> commandInterval = sgBehavior.add(new IntSetting.Builder()
        .name("command-interval").description("Runs the interval command every this many blocks travelled (0 to disable).").defaultValue(0).range(0, 100000).sliderRange(0, 2000).build());
    public final Setting<String> intervalCommand = sgBehavior.add(new StringSetting.Builder()
        .name("interval-command").description("Chat command sent every interval. Placeholders: {x} {y} {z} {distance}.").defaultValue("/sethome highway")
        .visible(() -> commandInterval.get() > 0).build());

    // Mining
    public final Setting<Integer> breakDelay = sgMining.add(new IntSetting.Builder()
        .name("break-delay").description("Sets the delay ticks between break tasks.").defaultValue(1).range(1, 20).sliderRange(1, 20).build());
    public final Setting<Double> miningSpeedFactor = sgMining.add(new DoubleSetting.Builder()
        .name("mining-speed-factor").description("Factor to manipulate the calculated mining speed.").defaultValue(1.0).range(0.0, 2.0).sliderRange(0.0, 2.0).build());
    public final Setting<Integer> interactionLimit = sgMining.add(new IntSetting.Builder()
        .name("interaction-limit").description("Interaction limit per second.").defaultValue(20).range(1, 100).sliderRange(1, 100).build());
    public final Setting<Boolean> multiBreak = sgMining.add(new BoolSetting.Builder()
        .name("multi-break").description("Breaks multiple instant breaking blocks intersecting with the view vector.").defaultValue(true).build());
    public final Setting<Boolean> packetFlood = sgMining.add(new BoolSetting.Builder()
        .name("packet-flood").description("Exploit for faster packet breaks. Sends START and STOP packet on the same tick.").defaultValue(false).build());
    public final Setting<Boolean> instantMine = sgMining.add(new BoolSetting.Builder()
        .name("ender-chest-instant-mine").description("Instant mine NCP exploit.").defaultValue(false).visible(packetFlood::get).build());

    // Placing
    public final Setting<Integer> placeDelay = sgPlacing.add(new IntSetting.Builder()
        .name("place-delay").description("Sets the delay ticks between placement tasks.").defaultValue(3).range(1, 20).sliderRange(1, 20).build());
    public final Setting<Boolean> dynamicDelay = sgPlacing.add(new BoolSetting.Builder()
        .name("dynamic-place-delay").description("Slows down on failed placement attempts.").defaultValue(true).build());
    public final Setting<Boolean> illegalPlacements = sgPlacing.add(new BoolSetting.Builder()
        .name("illegal-placements").description("Do not use on 2b2t. Tries to interact with invisible surfaces.").defaultValue(false).build());
    public final Setting<Boolean> scaffold = sgPlacing.add(new BoolSetting.Builder()
        .name("scaffold").description("Tries to bridge / scaffold when stuck placing.").defaultValue(true).build());
    public final Setting<Integer> placementSearch = sgPlacing.add(new IntSetting.Builder()
        .name("place-deep-search").description("EXPERIMENTAL: Attempts to find a support block for placing against.").defaultValue(2).range(1, 4).sliderRange(1, 4).build());

    // Storage management
    public final Setting<Boolean> storageManagement = sgStorage.add(new BoolSetting.Builder()
        .name("manage-storage").description("Choose to interact with containers using only packets.").defaultValue(true).build());
    public final Setting<Boolean> searchEChest = sgStorage.add(new BoolSetting.Builder()
        .name("search-ender-chest").description("Allow access to your ender chest.").defaultValue(false).visible(storageManagement::get).build());
    public final Setting<Boolean> leaveEmptyShulkers = sgStorage.add(new BoolSetting.Builder()
        .name("leave-empty-shulkers").description("Does not break empty shulkers.").defaultValue(true).visible(storageManagement::get).build());
    public final Setting<Boolean> grindObsidian = sgStorage.add(new BoolSetting.Builder()
        .name("grind-obsidian").description("Destroy ender chests to obtain obsidian.").defaultValue(true).visible(storageManagement::get).build());
    public final Setting<Boolean> fastFill = sgStorage.add(new BoolSetting.Builder()
        .name("fast-fill").description("Moves as many item stacks to the inventory as possible.").defaultValue(true).visible(storageManagement::get).build());
    public final Setting<Integer> keepFreeSlots = sgStorage.add(new IntSetting.Builder()
        .name("free-slots").description("How many inventory slots are untouched on refill.").defaultValue(1).range(0, 30).sliderRange(0, 30).visible(storageManagement::get).build());
    public final Setting<Boolean> preferEnderChests = sgStorage.add(new BoolSetting.Builder()
        .name("prefer-ender-chests").description("Prevent using raw material shulkers.").defaultValue(false).visible(storageManagement::get).build());
    public final Setting<Boolean> manageFood = sgStorage.add(new BoolSetting.Builder()
        .name("manage-food").description("Choose to manage food.").defaultValue(true).visible(storageManagement::get).build());
    public final Setting<Integer> saveMaterial = sgStorage.add(new IntSetting.Builder()
        .name("save-material").description("How many material blocks are saved.").defaultValue(12).range(0, 64).sliderRange(0, 64).visible(storageManagement::get).build());
    public final Setting<Integer> saveTools = sgStorage.add(new IntSetting.Builder()
        .name("save-tools").description("How many tools are saved.").defaultValue(1).range(0, 36).sliderRange(0, 36).visible(storageManagement::get).build());
    public final Setting<Integer> saveEnder = sgStorage.add(new IntSetting.Builder()
        .name("save-ender-chests").description("How many ender chests are saved.").defaultValue(1).range(0, 64).sliderRange(0, 64).visible(storageManagement::get).build());
    public final Setting<Integer> saveFood = sgStorage.add(new IntSetting.Builder()
        .name("save-food").description("How many food items are saved.").defaultValue(1).range(0, 64).sliderRange(0, 64)
        .visible(() -> storageManagement.get() && manageFood.get()).build());
    public final Setting<Double> minDistance = sgStorage.add(new DoubleSetting.Builder()
        .name("min-container-distance").description("Avoid player movement collision with container placement.").defaultValue(1.5).range(0.0, 3.0).sliderRange(0.0, 3.0).visible(storageManagement::get).build());
    public final Setting<List<Item>> ejectItems = sgStorage.add(new ItemListSetting.Builder()
        .name("eject-items").description("Junk items that may be thrown out or overwritten to make room.").build());
    public final Setting<IO.DisableMode> disableMode = sgStorage.add(new EnumSetting.Builder<IO.DisableMode>()
        .name("disable-mode").description("Choose the action when the bot is out of materials or tools.").defaultValue(IO.DisableMode.NONE).build());
    public final Setting<Boolean> usingProxy = sgStorage.add(new BoolSetting.Builder()
        .name("proxy").description("Enable this if you are using a proxy to call the given command.").defaultValue(false)
        .visible(() -> disableMode.get() == IO.DisableMode.LOGOUT).build());
    public final Setting<String> proxyCommand = sgStorage.add(new StringSetting.Builder()
        .name("proxy-command").description("Command to be sent to log out.").defaultValue("/dc")
        .visible(() -> usingProxy.get() && disableMode.get() == IO.DisableMode.LOGOUT).build());

    // Render
    public final Setting<Boolean> anonymizeStats = sgRender.add(new BoolSetting.Builder()
        .name("anonymize").description("Censors all coordinates in HUD and chat.").defaultValue(false).build());
    public final Setting<Boolean> fakeSounds = sgRender.add(new BoolSetting.Builder()
        .name("fake-sounds").description("Adds artificial sounds to the actions.").defaultValue(true).build());
    public final Setting<Boolean> info = sgRender.add(new BoolSetting.Builder()
        .name("show-info").description("Prints session stats in chat.").defaultValue(true).build());
    public final Setting<IO.DebugLevel> debugLevel = sgRender.add(new EnumSetting.Builder<IO.DebugLevel>()
        .name("debug-level").description("Sets the debug log depth level.").defaultValue(IO.DebugLevel.IMPORTANT).build());
    public final Setting<Boolean> goalRender = sgRender.add(new BoolSetting.Builder()
        .name("baritone-goal").description("Renders the Baritone goal.").defaultValue(false).build());
    public final Setting<Boolean> showCurrentPos = sgRender.add(new BoolSetting.Builder()
        .name("current-pos").description("Renders the current position.").defaultValue(false).build());
    public final Setting<Boolean> filled = sgRender.add(new BoolSetting.Builder()
        .name("filled").description("Renders colored task surfaces.").defaultValue(true).build());
    public final Setting<Boolean> outline = sgRender.add(new BoolSetting.Builder()
        .name("outline").description("Renders colored task outlines.").defaultValue(true).build());
    public final Setting<Boolean> popUp = sgRender.add(new BoolSetting.Builder()
        .name("pop-up").description("Funny render effect.").defaultValue(true).build());
    public final Setting<Integer> popUpSpeed = sgRender.add(new IntSetting.Builder()
        .name("pop-up-speed").description("Sets the speed of the pop up effect in ms.").defaultValue(150).range(0, 500).sliderRange(0, 500).visible(popUp::get).build());
    public final Setting<Boolean> showDebugRender = sgRender.add(new BoolSetting.Builder()
        .name("debug-render").description("Render debug info on tasks.").defaultValue(false).build());
    public final Setting<Double> textScale = sgRender.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the debug text.").defaultValue(1.0).range(0.25, 4.0).sliderRange(0.25, 4.0).visible(showDebugRender::get).build());
    public final Setting<Boolean> disableWarnings = sgRender.add(new BoolSetting.Builder()
        .name("disable-warnings").description("DANGEROUS: Disable warnings on enable.").defaultValue(false).build());
    public final Setting<Integer> aFilled = sgRender.add(new IntSetting.Builder()
        .name("filled-alpha").description("Sets the opacity of the surfaces.").defaultValue(26).range(0, 255).sliderRange(0, 255).visible(filled::get).build());
    public final Setting<Integer> aOutline = sgRender.add(new IntSetting.Builder()
        .name("outline-alpha").description("Sets the opacity of the outlines.").defaultValue(91).range(0, 255).sliderRange(0, 255).visible(outline::get).build());

    public HighwayTools() {
        super(Categories.World, "highway-tools", "Be the grief a step ahead. Automated highway building.", "ht", "hwt");
        INSTANCE = this;
    }

    // Setting filters run while Meteor restores saved settings, before item components are bound. Never throw there.
    private static boolean isFoodSafe(Item item) {
        try {
            return Utils.isFood(item);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isPickaxeSafe(Item item) {
        try {
            return item.getDefaultInstance().is(ItemTags.PICKAXES);
        } catch (Exception e) {
            return true;
        }
    }

    private static Block[] defaultIgnoreBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof SignBlock
                || block instanceof AbstractBannerBlock
                || block instanceof ShulkerBoxBlock) {
                blocks.add(block);
            }
        }
        blocks.add(Blocks.BEDROCK);
        blocks.add(Blocks.END_PORTAL);
        blocks.add(Blocks.END_PORTAL_FRAME);
        blocks.add(Blocks.NETHER_PORTAL);
        blocks.add(Blocks.MOVING_PISTON);
        blocks.add(Blocks.PISTON_HEAD);
        blocks.add(Blocks.BARRIER);
        return blocks.toArray(new Block[0]);
    }

    public boolean isIgnored(Block block) {
        return ignoreBlocks.get().contains(block);
    }

    private boolean running = false;

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) {
            error("You need to be in a world to use HighwayTools.");
            toggle();
            return;
        }
        running = true;
        Trombone.onEnable();
    }

    @Override
    public void onDeactivate() {
        if (!running) return;
        running = false;
        Trombone.onDisable();
    }

    @Override
    public String getInfoString() {
        if (IO.pauseReason != null) return "Paused: " + IO.pauseReason;
        BlockTask task = ContainerHandler.containerTask.taskState != TaskState.DONE
            ? ContainerHandler.containerTask
            : TaskManager.lastTask;
        if (task != null && task.taskState != TaskState.DONE) return task.taskState.name();
        return Pathfinder.moveState.name();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        Trombone.tick();
        if (!IO.pauseCheck()) Pathfinder.updatePathing();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;
        PacketHandler.handlePacket(event.packet);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;
        Renderer.renderWorld(event);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        Renderer.renderOverlay(event);
    }
}

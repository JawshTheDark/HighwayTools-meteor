package com.jawsh.highwaytools.commands;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.Statistics;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HighwayToolsCommand extends Command {
    private static final int SUCCESS = com.mojang.brigadier.Command.SINGLE_SUCCESS;
    private static final DynamicCommandExceptionType UNKNOWN_BLOCK = new DynamicCommandExceptionType(name -> Component.literal("Unknown block: " + name));
    private static final DynamicCommandExceptionType UNKNOWN_ITEM = new DynamicCommandExceptionType(name -> Component.literal("Unknown item: " + name));

    public HighwayToolsCommand() {
        super("highwaytools", "Customize settings of HighwayTools.", "ht", "hwt", "high");
    }

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        literals(builder, sub -> sub.then(blockArg().executes(ctx -> {
            Block block = getBlock(ctx);
            List<Block> list = new ArrayList<>(m().ignoreBlocks.get());
            if (!list.contains(block)) {
                list.add(block);
                m().ignoreBlocks.set(list);
                IO.printSettings();
                info("Added §7" + Names.get(block) + "§r to ignore list.");
            } else {
                info("§7" + Names.get(block) + "§r is already ignored.");
            }
            return SUCCESS;
        })), "add", "new", "+");

        literals(builder, sub -> sub.then(blockArg().executes(ctx -> {
            Block block = getBlock(ctx);
            List<Block> list = new ArrayList<>(m().ignoreBlocks.get());
            if (list.remove(block)) {
                m().ignoreBlocks.set(list);
                IO.printSettings();
                info("Removed §7" + Names.get(block) + "§r from ignore list.");
            } else {
                info("§7" + Names.get(block) + "§r is not yet ignored.");
            }
            return SUCCESS;
        })), "remove", "rem", "-", "del");

        literals(builder, sub -> sub.then(RequiredArgumentBuilder.<ClientSuggestionProvider, Integer>argument("distance", IntegerArgumentType.integer(0)).executes(ctx -> {
            int distance = IntegerArgumentType.getInteger(ctx, "distance");
            Pathfinder.distancePending = distance;
            info("HighwayTools will stop after (" + distance + ") blocks distance. To remove the limit use distance 0");
            return SUCCESS;
        })), "distance", "dist");

        literals(builder, sub -> sub.then(blockArg().executes(ctx -> {
            Block block = getBlock(ctx);
            m().material.set(block);
            info("Set your building material to §7" + Names.get(block) + "§r.");
            return SUCCESS;
        })), "material", "mat");

        literals(builder, sub -> sub.then(blockArg().executes(ctx -> {
            Block block = getBlock(ctx);
            m().fillerMat.set(block);
            info("Set your filling material to §7" + Names.get(block) + "§r.");
            return SUCCESS;
        })), "filler", "fil");

        literals(builder, sub -> sub.then(itemArg().executes(ctx -> {
            Item item = getItem(ctx);
            m().food.set(item);
            info("Set your food item to §7" + Names.get(item) + "§r.");
            return SUCCESS;
        })), "food", "fd");

        literals(builder, sub -> sub.then(itemArg().executes(ctx -> {
            Item item = getItem(ctx);
            m().tool.set(item);
            info("Set your tool item to §7" + Names.get(item) + "§r.");
            return SUCCESS;
        })), "tool");

        literals(builder, sub -> sub.executes(ctx -> {
            Statistics.resetStats();
            info("Stats reset.");
            return SUCCESS;
        }), "reset", "resetstats");

        literals(builder, sub -> sub.executes(ctx -> {
            IO.printSettings();
            return SUCCESS;
        }), "settings");

        builder.executes(ctx -> {
            IO.printSettings();
            return SUCCESS;
        });
    }

    /** Registers the same sub command under several literal names. */
    private static void literals(LiteralArgumentBuilder<ClientSuggestionProvider> parent, Consumer<LiteralArgumentBuilder<ClientSuggestionProvider>> configure, String... names) {
        for (String name : names) {
            LiteralArgumentBuilder<ClientSuggestionProvider> literal = LiteralArgumentBuilder.literal(name);
            configure.accept(literal);
            parent.then(literal);
        }
    }

    private static RequiredArgumentBuilder<ClientSuggestionProvider, String> blockArg() {
        return RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("block", StringArgumentType.greedyString())
            .suggests((ctx, suggestions) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), suggestions));
    }

    private static RequiredArgumentBuilder<ClientSuggestionProvider, String> itemArg() {
        return RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("item", StringArgumentType.greedyString())
            .suggests((ctx, suggestions) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), suggestions));
    }

    private static <T> T lookup(Registry<T> registry, String raw) {
        String name = raw.trim();
        Identifier id = name.contains(":") ? Identifier.tryParse(name) : Identifier.tryParse("minecraft:" + name);
        if (id == null) return null;
        return registry.getOptional(id).orElse(null);
    }

    private static Block getBlock(CommandContext<ClientSuggestionProvider> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "block");
        Block block = lookup(BuiltInRegistries.BLOCK, raw);
        if (block == null) throw UNKNOWN_BLOCK.create(raw);
        return block;
    }

    private static Item getItem(CommandContext<ClientSuggestionProvider> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "item");
        Item item = lookup(BuiltInRegistries.ITEM, raw);
        if (item == null) throw UNKNOWN_ITEM.create(raw);
        return item;
    }
}

package dev.attuned.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.attuned.attunement.AttunedAttachments;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin/debug commands for Attuned. Currently just {@code /attuned capacity},
 * which reads or sets the player's attunement capacity (the real player-facing
 * progression mechanic for capacity is future work).
 */
public final class AttunedCommands {
	private AttunedCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("attuned")
				// Operator-only (permission level 2). In 26.1 the old
				// CommandSourceStack#hasPermission(int) is gone; gating now uses
				// Commands.hasPermission(PermissionCheck) with a LEVEL_* constant.
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("capacity")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						int capacity = AttunedAttachments.getCapacity(player);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Attunement capacity: " + capacity), false);
						return capacity;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(0))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							int amount = IntegerArgumentType.getInteger(ctx, "amount");
							AttunedAttachments.setCapacity(player, amount);
							ctx.getSource().sendSuccess(
								() -> Component.literal("Attunement capacity set to " + amount), false);
							return amount;
						})))));
	}
}

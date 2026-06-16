package dev.attuned.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class PlayerMessages {
	private PlayerMessages() {
	}

	public static void overlay(Player player, Component message) {
		player.displayClientMessage(message, true);
	}

	public static void system(Player player, Component message) {
		player.displayClientMessage(message, false);
	}
}

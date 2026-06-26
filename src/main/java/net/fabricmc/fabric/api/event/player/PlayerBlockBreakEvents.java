package net.fabricmc.fabric.api.event.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class PlayerBlockBreakEvents {
	public static final After AFTER = new After();

	private PlayerBlockBreakEvents() {}

	public static final class After {
		private final List<Callback> callbacks = new ArrayList<>();

		private After() {
			NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
				if (!(event.getLevel() instanceof Level level)) {
					return;
				}
				BlockEntity blockEntity = level.getBlockEntity(event.getPos());
				for (Callback callback : List.copyOf(callbacks)) {
					callback.afterBlockBreak(level, event.getPlayer(), event.getPos(),
						event.getState(), blockEntity);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void afterBlockBreak(Level level, Player player, BlockPos pos,
			BlockState state, BlockEntity blockEntity);
	}
}

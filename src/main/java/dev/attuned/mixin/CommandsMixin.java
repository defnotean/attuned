package dev.attuned.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void attuned$registerCommands(Commands.CommandSelection environment, CallbackInfo ci) {
		CommandDispatcher<CommandSourceStack> dispatcher =
			((Commands) (Object) this).getDispatcher();
		CommandRegistrationCallback.EVENT.fire(
			dispatcher, environment == Commands.CommandSelection.DEDICATED);
	}
}

package net.fabricmc.fabric.api.resource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public interface SimpleSynchronousResourceReloadListener extends IdentifiableResourceReloadListener {
	void onResourceManagerReload(ResourceManager resourceManager);

	@Override
	default CompletableFuture<Void> reload(PreparableReloadListener.SharedState sharedState,
			Executor backgroundExecutor, PreparableReloadListener.PreparationBarrier barrier, Executor gameExecutor) {
		return barrier.wait(null)
			.thenRunAsync(() -> onResourceManagerReload(sharedState.resourceManager()), gameExecutor);
	}
}

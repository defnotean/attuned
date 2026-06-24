package dev.attuned.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal compatibility adapter for source that is shared with newer Minecraft
 * branches where GuiGraphics is provided by vanilla.
 */
public final class GuiGraphics {
	private final Minecraft minecraft;
	private final PoseStack pose;

	public GuiGraphics(Minecraft minecraft, PoseStack pose) {
		this.minecraft = minecraft;
		this.pose = pose;
	}

	public PoseStack pose() {
		return this.pose;
	}

	public int guiWidth() {
		return this.minecraft.getWindow().getGuiScaledWidth();
	}

	public int guiHeight() {
		return this.minecraft.getWindow().getGuiScaledHeight();
	}

	public void fill(int minX, int minY, int maxX, int maxY, int color) {
		GuiComponent.fill(this.pose, minX, minY, maxX, maxY, color);
	}

	public void blit(ResourceLocation texture, int x, int y, float u, float v,
			int width, int height, int textureWidth, int textureHeight) {
		RenderSystem.setShaderTexture(0, texture);
		GuiComponent.blit(this.pose, x, y, u, v, width, height, textureWidth, textureHeight);
	}

	public void blitSprite(ResourceLocation sprite, int x, int y, int width, int height) {
		ResourceLocation texture = new ResourceLocation(sprite.getNamespace(),
			"textures/gui/sprites/" + sprite.getPath() + ".png");
		RenderSystem.setShaderTexture(0, texture);
		GuiComponent.blit(this.pose, x, y, 0.0F, 0.0F, width, height, width, height);
	}

	public void drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
		if (dropShadow) {
			font.drawShadow(this.pose, text, x, y, color);
		} else {
			font.draw(this.pose, text, x, y, color);
		}
	}

	public void drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
		if (dropShadow) {
			font.drawShadow(this.pose, text, x, y, color);
		} else {
			font.draw(this.pose, text, x, y, color);
		}
	}

	public void drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
		if (dropShadow) {
			font.drawShadow(this.pose, text, x, y, color);
		} else {
			font.draw(this.pose, text, x, y, color);
		}
	}

	public void renderItem(ItemStack stack, int x, int y) {
		this.minecraft.getItemRenderer().renderAndDecorateItem(this.pose, stack, x, y);
	}
}

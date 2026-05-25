package dev.attuned.client.screen;

import dev.attuned.Attuned;
import dev.attuned.api.focus.Affinity;
import dev.attuned.network.OpenJournalPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Attuned's custom guide UI. This is deliberately not a vanilla book clone: it
 * uses a compact codex layout with chapter navigation, a stance/accent header,
 * and a single scroll-free page surface sized for the existing guide copy.
 */
public final class AttunementJournalScreen extends Screen {
	private static final Identifier BACKGROUND_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/attunement_journal.png");

	private static final int PANEL_WIDTH = 336;
	private static final int PANEL_HEIGHT = 214;
	private static final int NAV_WIDTH = 88;
	private static final int PADDING = 12;
	private static final int CONTENT_GAP = 12;
	private static final int CHAPTER_BUTTON_HEIGHT = 20;
	private static final int PAGE_BUTTON_WIDTH = 66;
	private static final int PAGE_BUTTON_HEIGHT = 20;

	private static final int BACKDROP = 0xE6131218;
	private static final int PANEL_SHADOW = 0xB0000000;
	private static final int CONTENT_INSET = 0xFF17141C;
	private static final int TEXT_TITLE = 0xFFF2E7FF;
	private static final int TEXT_BODY = 0xFFE8DEF4;
	private static final int TEXT_MUTED = 0xFFB7ACCA;
	private static final int LINE = 0xFF5B4B73;

	private static final List<Page> PAGES = List.of(
		new Page("Core", "journal.attuned.page1", 0xFFB995FF, null),
		new Page("Core", "journal.attuned.page2", 0xFFB995FF, null),
		new Page("Core", "journal.attuned.page3", 0xFFB995FF, null),
		new Page("Core", "journal.attuned.page4", 0xFFB995FF, null),
		new Page("Core", "journal.attuned.page5", 0xFFFF6AA8, null),
		new Page("Pacts", "journal.attuned.page6", 0xFFFFD37A, null),
		new Page("Apex", "journal.attuned.page7", 0xFFFFD37A, null),
		new Page("Altar", "journal.attuned.page8", 0xFFAEEAFF, null),
		new Page("Unseen", "journal.attuned.page9", 0xFFB995FF, null),
		new Page("Finding", "journal.attuned.page10", 0xFF95E6B3, null),
		new Page("Builds", "journal.attuned.page11", 0xFF95E6B3, null),
		new Page("Builds", "journal.attuned.page12", 0xFF95E6B3, null),
		new Page("Builds", "journal.attuned.page13", 0xFF95E6B3, null),
		new Page("Altar", "journal.attuned.page14", 0xFFAEEAFF, null),
		new Page("Pacts", "journal.attuned.page15", 0xFFFF8A5A, Affinity.FURY),
		new Page("Pacts", "journal.attuned.page16", 0xFFFFD37A, Affinity.BASTION),
		new Page("Pacts", "journal.attuned.page17", 0xFF70D7FF, Affinity.ZEPHYR),
		new Page("Pacts", "journal.attuned.page18", 0xFFFF6AA8, null),
		new Page("Pacts", "journal.attuned.page19", 0xFFFF6AA8, null)
	);
	private static final List<Chapter> CHAPTERS = List.of(
		new Chapter("Core", 0),
		new Chapter("Pacts", 5),
		new Chapter("Apex", 6),
		new Chapter("Altar", 7),
		new Chapter("Unseen", 8),
		new Chapter("Finding", 9),
		new Chapter("Builds", 10)
	);

	private final List<Button> chapterButtons = new ArrayList<>();
	private int pageIndex;
	private Button previousButton;
	private Button nextButton;

	public AttunementJournalScreen() {
		super(Component.translatable("journal.attuned.screen.title"));
	}

	public static void initNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(OpenJournalPayload.TYPE, (payload, context) ->
			context.client().execute(() ->
				context.client().setScreen(new AttunementJournalScreen())));
	}

	@Override
	protected void init() {
		this.chapterButtons.clear();
		int left = left();
		int top = top();
		int navX = left + PADDING;
		int y = top + 40;
		for (int i = 0; i < CHAPTERS.size(); i++) {
			Chapter chapter = CHAPTERS.get(i);
			Button button = Button.builder(Component.literal(chapter.name()), btn -> setPage(chapter.firstPage()))
				.bounds(navX, y + i * (CHAPTER_BUTTON_HEIGHT + 4), NAV_WIDTH - 10, CHAPTER_BUTTON_HEIGHT)
				.build();
			this.chapterButtons.add(button);
			this.addRenderableWidget(button);
		}

		int buttonY = top + PANEL_HEIGHT - PADDING - PAGE_BUTTON_HEIGHT;
		int contentLeft = contentLeft();
		int contentWidth = contentWidth();
		this.previousButton = Button.builder(Component.literal("Previous"), btn -> setPage(this.pageIndex - 1))
			.bounds(contentLeft, buttonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT)
			.build();
		this.nextButton = Button.builder(Component.literal("Next"), btn -> setPage(this.pageIndex + 1))
			.bounds(contentLeft + contentWidth - PAGE_BUTTON_WIDTH, buttonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT)
			.build();
		this.addRenderableWidget(this.previousButton);
		this.addRenderableWidget(this.nextButton);
		updateButtonState();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawFrame(graphics);
		drawNavigation(graphics);
		drawPage(graphics);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void setPage(int page) {
		this.pageIndex = Math.max(0, Math.min(PAGES.size() - 1, page));
		updateButtonState();
	}

	private void updateButtonState() {
		if (this.previousButton != null) {
			this.previousButton.active = this.pageIndex > 0;
		}
		if (this.nextButton != null) {
			this.nextButton.active = this.pageIndex < PAGES.size() - 1;
		}
	}

	private void drawFrame(GuiGraphicsExtractor graphics) {
		int left = left();
		int top = top();
		graphics.fill(0, 0, this.width, this.height, BACKDROP);
		graphics.fill(left + 4, top + 4, left + PANEL_WIDTH + 4, top + PANEL_HEIGHT + 4, PANEL_SHADOW);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, left, top,
			0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
	}

	private void drawNavigation(GuiGraphicsExtractor graphics) {
		int left = left();
		int top = top();
		Page page = PAGES.get(this.pageIndex);
		graphics.text(this.font, Component.translatable("journal.attuned.screen.title"),
			left + PADDING, top + 10, TEXT_TITLE, false);
		graphics.text(this.font, Component.translatable("journal.attuned.screen.subtitle"),
			left + PADDING, top + 22, TEXT_MUTED, false);

		for (int i = 0; i < CHAPTERS.size(); i++) {
			Chapter chapter = CHAPTERS.get(i);
			if (chapter.contains(page)) {
				Button button = this.chapterButtons.get(i);
				graphics.fill(button.getX() - 2, button.getY() - 2,
					button.getX() + button.getWidth() + 2, button.getY() + button.getHeight() + 2,
					page.accent());
			}
		}
	}

	private void drawPage(GuiGraphicsExtractor graphics) {
		Page page = PAGES.get(this.pageIndex);
		PageText text = pageText(page.translationKey());
		int x = contentLeft() + 10;
		int y = top() + 45;
		int w = contentWidth() - 20;
		int accent = page.accent();

		if (page.affinity() != null) {
			drawMiniGem(graphics, x, y - 2, 14, page.affinity(), accent);
			graphics.text(this.font, Component.literal(text.title()).withStyle(ChatFormatting.BOLD),
				x + 20, y, TEXT_TITLE, false);
		} else {
			graphics.fill(x, y + 4, x + 12, y + 6, accent);
			graphics.text(this.font, Component.literal(text.title()).withStyle(ChatFormatting.BOLD),
				x + 18, y, TEXT_TITLE, false);
		}
		graphics.fill(x, y + 18, x + w, y + 19, LINE);
		graphics.fill(x, y + 18, x + Math.min(w + x, x + 76), y + 19, accent);

		int bodyY = y + 30;
		for (String paragraph : text.body().split("\\n")) {
			if (paragraph.isBlank()) {
				bodyY += 6;
				continue;
			}
			bodyY = drawWrapped(graphics, Component.literal(paragraph), x, bodyY, w, TEXT_BODY, top() + 172);
			bodyY += 5;
		}

		String progress = (this.pageIndex + 1) + " / " + PAGES.size();
		graphics.text(this.font, Component.literal(progress), x + (w - this.font.width(progress)) / 2,
			top() + PANEL_HEIGHT - 26, TEXT_MUTED, false);
		int progressWidth = w;
		int progressFill = Math.max(4, Math.round(progressWidth * ((this.pageIndex + 1) / (float) PAGES.size())));
		int progressY = top() + PANEL_HEIGHT - 31;
		graphics.fill(x, progressY, x + progressWidth, progressY + 2, CONTENT_INSET);
		graphics.fill(x, progressY, x + progressFill, progressY + 2, accent);
	}

	private int drawWrapped(GuiGraphicsExtractor graphics, Component component, int x, int y,
			int width, int color, int bottom) {
		for (FormattedCharSequence line : this.font.split(component, width)) {
			if (y + 9 > bottom) {
				graphics.text(this.font, Component.literal("..."), x, y, TEXT_MUTED, false);
				return bottom;
			}
			graphics.text(this.font, line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	private static void drawMiniGem(GuiGraphicsExtractor graphics, int x, int y, int size, Affinity affinity, int accent) {
		int face = switch (affinity) {
			case FURY -> 0xFFE95E4D;
			case BASTION -> 0xFFFFC857;
			case ZEPHYR -> 0xFF54C7F0;
		};
		graphics.fill(x, y + 3, x + 3, y + size - 3, 0xFF0C0B10);
		graphics.fill(x + 3, y, x + size - 3, y + size, 0xFF0C0B10);
		graphics.fill(x + size - 3, y + 3, x + size, y + size - 3, 0xFF0C0B10);
		graphics.fill(x + 2, y + 4, x + 4, y + size - 4, accent);
		graphics.fill(x + 4, y + 2, x + size - 4, y + size - 2, face);
		graphics.fill(x + size - 4, y + 4, x + size - 2, y + size - 4, accent);
		graphics.fill(x + 5, y + 3, x + size - 5, y + 4, 0xCCFFFFFF);
		graphics.fill(x + size / 2, y + size / 2, x + size / 2 + 1, y + size / 2 + 1, 0xFFFFFFFF);
	}

	private static PageText pageText(String key) {
		String raw = I18n.get(key);
		String[] parts = raw.split("\\n", 2);
		String title = parts.length > 0 ? parts[0].strip() : key;
		String body = parts.length > 1 ? parts[1].strip() : "";
		return new PageText(title, body);
	}

	private int left() {
		return (this.width - PANEL_WIDTH) / 2;
	}

	private int top() {
		return (this.height - PANEL_HEIGHT) / 2;
	}

	private int contentLeft() {
		return left() + PADDING + NAV_WIDTH + CONTENT_GAP;
	}

	private int contentWidth() {
		return PANEL_WIDTH - PADDING * 2 - NAV_WIDTH - CONTENT_GAP;
	}

	private record Page(String chapter, String translationKey, int accent, Affinity affinity) {}
	private record Chapter(String name, int firstPage) {
		private boolean contains(Page page) {
			return page.chapter().equals(this.name);
		}
	}
	private record PageText(String title, String body) {}
}

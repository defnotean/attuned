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
	private static boolean initialized;

	private static final int PANEL_WIDTH = 336;
	private static final int PANEL_HEIGHT = 214;
	private static final int NAV_WIDTH = 88;
	private static final int PADDING = 12;
	private static final int CONTENT_GAP = 12;
	private static final int CHAPTER_BUTTON_HEIGHT = 14;
	private static final int CHAPTER_BUTTON_GAP = 1;
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
		new Page("Apex", "journal.attuned.page30", 0xFFFF6AA8, null),
		new Page("Apex", "journal.attuned.page31", 0xFFD8D4E8, null),
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
		new Page("Pacts", "journal.attuned.page29", 0xFFFFF1A8, Affinity.HOLY),
		new Page("Pacts", "journal.attuned.page18", 0xFFFF6AA8, null),
		new Page("Pacts", "journal.attuned.page19", 0xFFFF6AA8, null),
		new Page("Lore", "journal.attuned.page20", 0xFFAEEAFF, null),
		new Page("Lore", "journal.attuned.page21", 0xFFAEEAFF, null),
		new Page("Lore", "journal.attuned.page22", 0xFFAEEAFF, null),
		new Page("Radiant", "journal.attuned.page23", 0xFFFFD37A, null),
		new Page("Unseen", "journal.attuned.page24", 0xFFB995FF, null),
		new Page("Lore", "journal.attuned.page25", 0xFFFFD37A, null),
		new Page("Lore", "journal.attuned.page33", 0xFFB9E8FF, null),
		new Page("Seafarers", "journal.attuned.page26", 0xFF70D7FF, null),
		new Page("Seafarers", "journal.attuned.page27", 0xFF70D7FF, null),
		new Page("Offshore", "journal.attuned.page32", 0xFF56D8CF, null),
		new Page("HUD", "journal.attuned.page28", 0xFF95E6B3, null)
	);
	private static final List<Chapter> CHAPTERS = List.of(
		new Chapter("Core", 0),
		new Chapter("Pacts", 5),
		new Chapter("Apex", 6),
		new Chapter("Altar", 9),
		new Chapter("Unseen", 10),
		new Chapter("Finding", 11),
		new Chapter("Builds", 12),
		new Chapter("Lore", 22),
		new Chapter("Radiant", 25),
		new Chapter("Seafarers", 29),
		new Chapter("Offshore", 31),
		new Chapter("HUD", 32)
	);

	private final List<Button> chapterButtons = new ArrayList<>();
	private int pageIndex;
	private Button previousButton;
	private Button nextButton;

	public AttunementJournalScreen() {
		super(Component.translatable("journal.attuned.screen.title"));
	}

	public static void initNetworking() {
		if (initialized) {
			return;
		}
		initialized = true;

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
		int y = top + 36;
		for (int i = 0; i < CHAPTERS.size(); i++) {
			Chapter chapter = CHAPTERS.get(i);
			Button button = addJournalButton(Component.literal(chapter.name()),
				navX, y + i * (CHAPTER_BUTTON_HEIGHT + CHAPTER_BUTTON_GAP), NAV_WIDTH - 10, CHAPTER_BUTTON_HEIGHT,
				true, btn -> setPage(chapter.firstPage()));
			this.chapterButtons.add(button);
		}

		int buttonY = top + PANEL_HEIGHT - PADDING - PAGE_BUTTON_HEIGHT;
		int contentLeft = contentLeft();
		int contentWidth = contentWidth();
		this.previousButton = addJournalButton(Component.translatable("screen.attuned.journal.previous"),
			contentLeft, buttonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT, false, btn -> setPage(this.pageIndex - 1));
		this.nextButton = addJournalButton(Component.translatable("screen.attuned.journal.next"),
			contentLeft + contentWidth - PAGE_BUTTON_WIDTH, buttonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT,
			false, btn -> setPage(this.pageIndex + 1));
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

	private Button addJournalButton(Component label, int x, int y, int width, int height,
			boolean chapterButton, Button.OnPress onPress) {
		Button button = new JournalButton(x, y, width, height, label, chapterButton, onPress);
		return this.addRenderableWidget(button);
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
			bodyY = drawWrapped(graphics, Component.literal(paragraph), x, bodyY, w, TEXT_BODY, top() + 154);
			bodyY += 5;
		}

		String progress = (this.pageIndex + 1) + " / " + PAGES.size();
		int progressX = x + PAGE_BUTTON_WIDTH + 10;
		int progressWidth = w - (PAGE_BUTTON_WIDTH + 10) * 2;
		graphics.text(this.font, Component.literal(progress),
			progressX + (progressWidth - this.font.width(progress)) / 2,
			top() + PANEL_HEIGHT - 45, TEXT_MUTED, false);
		int progressFill = Math.max(4, Math.round(progressWidth * ((this.pageIndex + 1) / (float) PAGES.size())));
		int progressY = top() + PANEL_HEIGHT - 34;
		graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 2, CONTENT_INSET);
		graphics.fill(progressX, progressY, progressX + progressFill, progressY + 2, accent);
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
			case HOLY -> 0xFFFFF1A8;
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

	private static final class JournalButton extends Button {
		private final boolean chapterButton;

		private JournalButton(int x, int y, int width, int height, Component message,
				boolean chapterButton, OnPress onPress) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
			this.chapterButton = chapterButton;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x0 = getX();
			int y0 = getY();
			int x1 = x0 + getWidth();
			int y1 = y0 + getHeight();
			int face = this.active
				? (isHoveredOrFocused() ? 0xFF4B415F : (this.chapterButton ? 0xFF302A3A : 0xFF2D2935))
				: 0xFF24222A;
			int trim = this.active ? (this.chapterButton ? 0xFFB995FF : 0xFFAEEAFF) : 0xFF5F596A;
			graphics.fill(x0, y0, x1, y1, 0xFF15131B);
			graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, trim);
			graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, face);
			graphics.fill(x0 + 3, y0 + 3, x1 - 3, y0 + 4, this.active ? 0xFFE0C6FF : 0xFF77707E);
			graphics.fill(x0 + 3, y1 - 4, x1 - 3, y1 - 3, 0xFF17151D);
			extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		}
	}

	private record Page(String chapter, String translationKey, int accent, Affinity affinity) {}
	private record Chapter(String name, int firstPage) {
		private boolean contains(Page page) {
			return page.chapter().equals(this.name);
		}
	}
	private record PageText(String title, String body) {}
}

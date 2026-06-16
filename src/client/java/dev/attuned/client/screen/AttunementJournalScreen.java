package dev.attuned.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.synergy.SynergyDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.network.OpenJournalPayload;
import dev.attuned.pacts.Pact;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Attuned's custom guide UI: a compact codex with a chapter rail and a single
 * reading pane. Each chapter owns its pages and renders as one continuous,
 * mouse-wheel-scrollable document; the rail (and the Previous/Next buttons) move
 * between chapters. No content is ever truncated.
 */
public final class AttunementJournalScreen extends Screen {
	private static final ResourceLocation BACKGROUND_TEXTURE =
		new ResourceLocation(Attuned.MOD_ID, "textures/gui/attunement_journal.png");
	private static final String CONFLUENCE_PAGE_KEY = "journal.attuned.confluence.intro";
	private static final String PACT_TRIALS_PAGE_KEY = "journal.attuned.pact_trials.intro";
	private static boolean initialized;

	private static final int PANEL_WIDTH = 336;
	private static final int PANEL_HEIGHT = 214;
	private static final int NAV_WIDTH = 88;
	private static final int NAV_X_OFFSET = 34;
	private static final int PADDING = 12;
	private static final int CONTENT_GAP = 12;
	private static final int PAGE_CONTENT_WIDTH = 216;
	private static final int CHAPTER_BUTTON_Y = 34;
	private static final int CHAPTER_BUTTON_WIDTH = 62;
	private static final int CHAPTER_BUTTON_HEIGHT = 12;
	private static final int CHAPTER_BUTTON_GAP = 1;
	private static final int PAGE_BUTTON_WIDTH = 66;
	private static final int PAGE_BUTTON_HEIGHT = 20;
	private static final int LINE_HEIGHT = 10;
	private static final int HEADER_HEIGHT = 24;
	private static final int SECTION_GAP = 10;
	private static final int SCROLL_STEP = 12;
	private static final int CONTENT_TOP = 18;
	private static final int CONTENT_BOTTOM = PANEL_HEIGHT - 48;
	private static final int SCROLLBAR_HIT_WIDTH = 8;

	private static final int BACKDROP = 0xE6131218;
	private static final int PANEL_SHADOW = 0xB0000000;
	private static final int TEXT_TITLE = 0xFFF2E7FF;
	private static final int TEXT_MUTED = 0xFFB7ACCA;
	private static final int LABEL_LIGHT = 0xFFE8DEF4;
	private static final int PAGE_TITLE = 0xFF2C2118;
	private static final int PAGE_BODY = 0xFF3D3024;
	private static final int PAGE_MUTED = 0xFF6E5E45;
	private static final int PAGE_LINE = 0xFF9A8A66;
	private static final int PAGE_TRACK = 0xFFAB9C7E;
	private static final int ROW_HOVER = 0x22FFFFFF;

	private static final List<Chapter> CHAPTERS = List.of(
		chapter("Core",
			page("journal.attuned.page1", 0xFFB995FF),
			page("journal.attuned.page2", 0xFFB995FF),
			page("journal.attuned.page3", 0xFFB995FF),
			page("journal.attuned.page4", 0xFFB995FF),
			page("journal.attuned.page34", 0xFF56D8CF),
			page("journal.attuned.page35", 0xFF56D8CF),
			page("journal.attuned.page36", 0xFF56D8CF),
			page("journal.attuned.page5", 0xFFFF6AA8)),
		chapter("Pacts",
			page("journal.attuned.page6", 0xFFFFD37A),
			gemPage("journal.attuned.page15", 0xFFFF8A5A, Affinity.FURY),
			gemPage("journal.attuned.page16", 0xFFFFD37A, Affinity.BASTION),
			gemPage("journal.attuned.page17", 0xFF70D7FF, Affinity.ZEPHYR),
			gemPage("journal.attuned.page29", 0xFFFFF1A8, Affinity.HOLY),
			gemPage("journal.attuned.page37", 0xFF2F7FD0, Affinity.TIDE),
			gemPage("journal.attuned.page38", 0xFFC85A2B, Affinity.FORGE),
			gemPage("journal.attuned.page39", 0xFF5FC23E, Affinity.VERDANT),
			gemPage("journal.attuned.page40", 0xFF7A4FB5, Affinity.UMBRAL),
			page("journal.attuned.page18", 0xFFFF6AA8),
			dynamicPage(PACT_TRIALS_PAGE_KEY, 0xFFFF6AA8),
			page("journal.attuned.page19", 0xFFFF6AA8)),
		chapter("Apex",
			page("journal.attuned.page7", 0xFFFFD37A),
			page("journal.attuned.page41", 0xFF56D8CF),
			page("journal.attuned.page30", 0xFFFF6AA8),
			page("journal.attuned.page31", 0xFFD8D4E8)),
		chapter("Altar",
			page("journal.attuned.page8", 0xFFAEEAFF),
			page("journal.attuned.page14", 0xFFAEEAFF)),
		chapter("Unseen",
			page("journal.attuned.page9", 0xFFB995FF),
			page("journal.attuned.page24", 0xFFB995FF)),
		chapter("Finding",
			page("journal.attuned.page10", 0xFF95E6B3)),
		chapter("Builds",
			page("journal.attuned.page11", 0xFF95E6B3),
			page("journal.attuned.page12", 0xFF95E6B3),
			page("journal.attuned.page13", 0xFF95E6B3),
			page("journal.attuned.page_tempering", 0xFFFFD37A)),
		chapter("Lore",
			page("journal.attuned.page20", 0xFFAEEAFF),
			page("journal.attuned.page21", 0xFFAEEAFF),
			page("journal.attuned.page22", 0xFFAEEAFF),
			page("journal.attuned.page25", 0xFFFFD37A),
			page("journal.attuned.page33", 0xFFB9E8FF)),
		chapter("Radiant",
			page("journal.attuned.page23", 0xFFFFD37A)),
		chapter("Seafarers",
			page("journal.attuned.page26", 0xFF70D7FF),
			page("journal.attuned.page27", 0xFF70D7FF)),
		chapter("Offshore",
			page("journal.attuned.page32", 0xFF56D8CF)),
		chapter("HUD",
			page("journal.attuned.page28", 0xFF95E6B3)),
		chapter("Confluences",
			dynamicPage(CONFLUENCE_PAGE_KEY, 0xFF95E6B3))
	);

	private final List<Button> chapterButtons = new ArrayList<>();
	private int chapterIndex;
	private int scrollOffset;
	private int maxScroll;
	private boolean scrollbarDragging;
	private int scrollbarGrabOffset;
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

		ClientPlayNetworking.registerGlobalReceiver(OpenJournalPayload.TYPE, (payload, player, sender) ->
			Minecraft.getInstance().execute(() ->
				Minecraft.getInstance().setScreen(new AttunementJournalScreen())));
	}

	@Override
	protected void init() {
		this.chapterButtons.clear();
		int left = left();
		int top = top();
		int navX = left + NAV_X_OFFSET;
		int y = top + CHAPTER_BUTTON_Y;
		for (int i = 0; i < CHAPTERS.size(); i++) {
			final int target = i;
			Button button = addJournalButton(Component.literal(CHAPTERS.get(i).name()),
				navX, y + i * (CHAPTER_BUTTON_HEIGHT + CHAPTER_BUTTON_GAP), CHAPTER_BUTTON_WIDTH, CHAPTER_BUTTON_HEIGHT,
				true, btn -> setChapter(target));
			this.chapterButtons.add(button);
		}

		int buttonY = top + PANEL_HEIGHT - PADDING - PAGE_BUTTON_HEIGHT;
		int contentLeft = contentLeft();
		int contentWidth = contentWidth();
		this.previousButton = addJournalButton(Component.translatable("screen.attuned.journal.previous"),
			contentLeft, buttonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT, false, btn -> setChapter(this.chapterIndex - 1));
		this.nextButton = addJournalButton(Component.translatable("screen.attuned.journal.next"),
			contentLeft + contentWidth - PAGE_BUTTON_WIDTH, buttonY, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT,
			false, btn -> setChapter(this.chapterIndex + 1));
		updateButtonState();
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
		drawFrame(graphics);
		drawNavigation(graphics);
		drawChapter(graphics);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.maxScroll > 0) {
			int next = this.scrollOffset - (int) Math.round(scrollY) * SCROLL_STEP;
			this.scrollOffset = Math.max(0, Math.min(this.maxScroll, next));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
			this.scrollbarDragging = true;
			int thumbY = scrollbarThumbY();
			int thumbHeight = scrollbarThumbHeight();
			if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
				this.scrollbarGrabOffset = (int) Math.round(mouseY - thumbY);
			} else {
				this.scrollbarGrabOffset = thumbHeight / 2;
			}
			updateScrollFromMouse(mouseY);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (this.scrollbarDragging && button == 0 && this.maxScroll > 0) {
			updateScrollFromMouse(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && this.scrollbarDragging) {
			this.scrollbarDragging = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void setChapter(int index) {
		this.chapterIndex = Math.max(0, Math.min(CHAPTERS.size() - 1, index));
		this.scrollOffset = 0;
		this.scrollbarDragging = false;
		this.scrollbarGrabOffset = 0;
		updateButtonState();
	}

	private void updateButtonState() {
		if (this.previousButton != null) {
			this.previousButton.active = this.chapterIndex > 0;
		}
		if (this.nextButton != null) {
			this.nextButton.active = this.chapterIndex < CHAPTERS.size() - 1;
		}
	}

	private Button addJournalButton(Component label, int x, int y, int width, int height,
			boolean chapterButton, Button.OnPress onPress) {
		Button button = new JournalButton(x, y, width, height, label, chapterButton, onPress);
		return this.addRenderableWidget(button);
	}

	private void drawFrame(GuiGraphics graphics) {
		int left = left();
		int top = top();
		graphics.fill(0, 0, this.width, this.height, BACKDROP);
		graphics.fill(left + 4, top + 4, left + PANEL_WIDTH + 4, top + PANEL_HEIGHT + 4, PANEL_SHADOW);
		graphics.blit(BACKGROUND_TEXTURE, left, top,
			0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
	}

	private void drawNavigation(GuiGraphics graphics) {
		int left = left();
		int top = top();
		graphics.drawString(this.font, Component.translatable("journal.attuned.screen.title"),
			left + PADDING, top + 10, TEXT_TITLE, false);
		graphics.drawString(this.font, Component.translatable("journal.attuned.screen.subtitle"),
			left + PADDING, top + 22, TEXT_MUTED, false);

		for (int i = 0; i < CHAPTERS.size(); i++) {
			Button button = this.chapterButtons.get(i);
			int bx = button.getX();
			int by = button.getY();
			int bw = button.getWidth();
			int bh = button.getHeight();
			int dot = chapterColor(i);
			if (i == this.chapterIndex) {
				graphics.fill(bx - 2, by - 1, bx + bw + 2, by + bh + 1, 0x33000000 | (dot & 0x00FFFFFF));
				graphics.fill(bx - 2, by - 1, bx, by + bh + 1, 0xFF000000 | (dot & 0x00FFFFFF));
			}
			drawDot(graphics, bx - 6, by + bh / 2, dot);
		}
	}

	private void drawChapter(GuiGraphics graphics) {
		Chapter chapter = CHAPTERS.get(this.chapterIndex);
		int x = contentLeft() + 10;
		int w = contentWidth() - 20;
		int textWidth = w - 6;
		int contentTop = top() + CONTENT_TOP;
		int contentBottom = top() + CONTENT_BOTTOM;
		int windowHeight = contentBottom - contentTop;

		List<Section> sections = new ArrayList<>();
		int total = 0;
		for (Page page : chapter.pages()) {
			PageText text = page.dynamic() ? dynamicText(page) : pageText(page.translationKey());
			List<FormattedCharSequence> lines = layoutBody(text.body(), textWidth);
			sections.add(new Section(page, text.title(), lines));
			total += HEADER_HEIGHT + lines.size() * LINE_HEIGHT + SECTION_GAP;
		}
		total = Math.max(0, total - SECTION_GAP);
		this.maxScroll = Math.max(0, total - windowHeight);
		this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));

		int y = contentTop - this.scrollOffset;
		for (Section section : sections) {
			drawSectionHeader(graphics, section, x, y, w, contentTop, contentBottom);
			y += HEADER_HEIGHT;
			for (FormattedCharSequence line : section.lines()) {
				if (y >= contentTop && y + 9 <= contentBottom) {
					graphics.drawString(this.font, line, x, y, PAGE_BODY, false);
				}
				y += LINE_HEIGHT;
			}
			y += SECTION_GAP;
		}

		if (this.maxScroll > 0) {
			int trackX = scrollbarX();
			graphics.fill(trackX, contentTop, trackX + 2, contentBottom, PAGE_TRACK);
			int thumbHeight = scrollbarThumbHeight();
			int thumbY = scrollbarThumbY();
			graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, chapterColor(this.chapterIndex));
		}

		drawFooter(graphics, x, w);
	}

	private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
		int trackX = scrollbarX();
		int contentTop = top() + CONTENT_TOP;
		int contentBottom = top() + CONTENT_BOTTOM;
		return this.maxScroll > 0
			&& mouseX >= trackX - SCROLLBAR_HIT_WIDTH / 2.0
			&& mouseX <= trackX + 2 + SCROLLBAR_HIT_WIDTH / 2.0
			&& mouseY >= contentTop
			&& mouseY <= contentBottom;
	}

	private void updateScrollFromMouse(double mouseY) {
		if (this.maxScroll <= 0) {
			this.scrollOffset = 0;
			return;
		}
		int contentTop = top() + CONTENT_TOP;
		int travel = Math.max(1, scrollWindowHeight() - scrollbarThumbHeight());
		int thumbTop = (int) Math.round(mouseY) - this.scrollbarGrabOffset;
		int clampedThumbTop = Math.max(contentTop, Math.min(contentTop + travel, thumbTop));
		float position = (clampedThumbTop - contentTop) / (float) travel;
		this.scrollOffset = Math.max(0, Math.min(this.maxScroll, Math.round(this.maxScroll * position)));
	}

	private int scrollbarX() {
		return contentLeft() + contentWidth() - 11;
	}

	private int scrollWindowHeight() {
		return CONTENT_BOTTOM - CONTENT_TOP;
	}

	private int scrollbarThumbHeight() {
		if (this.maxScroll <= 0) {
			return scrollWindowHeight();
		}
		int windowHeight = scrollWindowHeight();
		int total = this.maxScroll + windowHeight;
		return Math.max(10, Math.round(windowHeight * (windowHeight / (float) total)));
	}

	private int scrollbarThumbY() {
		int contentTop = top() + CONTENT_TOP;
		if (this.maxScroll <= 0) {
			return contentTop;
		}
		int travel = Math.max(0, scrollWindowHeight() - scrollbarThumbHeight());
		return contentTop + Math.round(travel * (this.scrollOffset / (float) this.maxScroll));
	}

	private void drawSectionHeader(GuiGraphics graphics, Section section, int x, int y, int w,
			int contentTop, int contentBottom) {
		Page page = section.page();
		int accent = page.accent();
		if (y >= contentTop && y + 9 <= contentBottom) {
			if (page.affinity() != null) {
				drawMiniGem(graphics, x, y - 2, 14, page.affinity(), accent);
				graphics.drawString(this.font, Component.literal(section.title()).withStyle(ChatFormatting.BOLD),
					x + 20, y, PAGE_TITLE, false);
			} else {
				graphics.fill(x, y + 4, x + 12, y + 6, accent);
				graphics.drawString(this.font, Component.literal(section.title()).withStyle(ChatFormatting.BOLD),
					x + 18, y, PAGE_TITLE, false);
			}
		}
		int ruleY = y + 16;
		if (ruleY >= contentTop && ruleY <= contentBottom) {
			graphics.fill(x, ruleY, x + w, ruleY + 1, PAGE_LINE);
			graphics.fill(x, ruleY, x + Math.min(w, 76), ruleY + 1, accent);
		}
	}

	private void drawFooter(GuiGraphics graphics, int x, int w) {
		String progress = (this.chapterIndex + 1) + " / " + CHAPTERS.size();
		int progressX = x + PAGE_BUTTON_WIDTH + 10;
		int progressWidth = w - (PAGE_BUTTON_WIDTH + 10) * 2;
		graphics.drawString(this.font, Component.literal(progress),
			progressX + (progressWidth - this.font.width(progress)) / 2,
			top() + PANEL_HEIGHT - 45, PAGE_MUTED, false);
		int progressY = top() + PANEL_HEIGHT - 34;
		graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 2, PAGE_TRACK);
		int fill = this.maxScroll > 0
			? Math.round(progressWidth * (this.scrollOffset / (float) this.maxScroll))
			: progressWidth;
		graphics.fill(progressX, progressY, progressX + fill, progressY + 2, chapterColor(this.chapterIndex));
	}

	private List<FormattedCharSequence> layoutBody(String body, int width) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (String paragraph : body.split("\\n")) {
			if (paragraph.isBlank()) {
				out.add(FormattedCharSequence.EMPTY);
				continue;
			}
			out.addAll(this.font.split(Component.literal(paragraph), width));
		}
		return out;
	}

	private static void drawDot(GuiGraphics graphics, int cx, int cy, int color) {
		int c = 0xFF000000 | (color & 0x00FFFFFF);
		graphics.fill(cx - 1, cy, cx + 2, cy + 1, c);
		graphics.fill(cx, cy - 1, cx + 1, cy + 2, c);
	}

	private static int chapterColor(int chapterIndex) {
		return CHAPTERS.get(chapterIndex).pages().get(0).accent();
	}

	private static void drawMiniGem(GuiGraphics graphics, int x, int y, int size, Affinity affinity, int accent) {
		int face = switch (affinity) {
			case FURY -> 0xFFE95E4D;
			case BASTION -> 0xFFFFC857;
			case ZEPHYR -> 0xFF54C7F0;
			case HOLY -> 0xFFFFF1A8;
			case TIDE -> 0xFF2F7FD0;
			case FORGE -> 0xFFC85A2B;
			case VERDANT -> 0xFF5FC23E;
			case UMBRAL -> 0xFF7A4FB5;
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

	private static PageText dynamicText(Page page) {
		if (CONFLUENCE_PAGE_KEY.equals(page.translationKey())) {
			return confluencePageText();
		}
		if (PACT_TRIALS_PAGE_KEY.equals(page.translationKey())) {
			return pactTrialPageText();
		}
		return pageText(page.translationKey());
	}

	private static PageText pageText(String key) {
		String raw = I18n.get(key);
		String[] parts = raw.split("\\n", 2);
		String title = parts.length > 0 ? parts[0].strip() : key;
		String body = parts.length > 1 ? parts[1].strip() : "";
		return new PageText(title, body);
	}

	/**
	 * The Confluences page is dynamic: one line per Confluence, the real name for
	 * discovered ones and a redacted "??? (N Foci)" row for the rest, read from the
	 * {@code targetOnly}-synced discovery attachment plus the client copy of the
	 * SYNERGY_DEFINITIONS registry.
	 */
	private static PageText confluencePageText() {
		String title = I18n.get("journal.attuned.confluence.title");
		List<String> rows = new ArrayList<>();
		rows.add(I18n.get(CONFLUENCE_PAGE_KEY));
		rows.add("");
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			Set<String> discovered = new HashSet<>(AttunedAttachments.getDiscoveredConfluences(player));
			Registry<SynergyDefinition> registry =
				player.getLevel().registryAccess().registryOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS);
			registry.stream().forEach(def -> {
				String id = registry.getKey(def).toString();
				if (discovered.contains(id)) {
					rows.add(I18n.get("confluence.attuned." + pathOf(id) + ".name"));
				} else {
					rows.add(I18n.get("journal.attuned.confluence.redacted")
						+ " (" + I18n.get("journal.attuned.confluence.members", def.members().size()) + ")");
				}
			});
		}
		return new PageText(title, String.join("\n", rows));
	}

	private static String pathOf(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	/**
	 * The Pacts trial page is dynamic: one row per {@link Pact}, read from the
	 * synced trial-progress attachment with a defensive empty fallback.
	 */
	private static PageText pactTrialPageText() {
		String title = I18n.get("journal.attuned.pact_trials.title");
		List<String> rows = new ArrayList<>();
		rows.add(I18n.get(PACT_TRIALS_PAGE_KEY));
		rows.add("");
		LocalPlayer player = Minecraft.getInstance().player;
		Map<Pact, AttunedAttachments.PactTrialState> progress = player == null
			? Map.of()
			: AttunedAttachments.getPactTrialProgress(player);
		for (Pact pact : Pact.values()) {
			rows.add(pact.displayName().getString());
			AttunedAttachments.PactTrialState state = progress.get(pact);
			if (state != null && state.tier4Complete()) {
				rows.add(I18n.get("journal.attuned.trial.complete"));
			} else {
				int current = state == null ? 0 : state.progress();
				int goal = state == null ? 0 : state.goal();
				rows.add(I18n.get("journal.attuned.trial.progress", current, goal));
			}
		}
		return new PageText(title, String.join("\n", rows));
	}

	private static Chapter chapter(String name, Page... pages) {
		return new Chapter(name, List.of(pages));
	}

	private static Page page(String translationKey, int accent) {
		return new Page(translationKey, accent, null, false);
	}

	private static Page gemPage(String translationKey, int accent, Affinity affinity) {
		return new Page(translationKey, accent, affinity, false);
	}

	private static Page dynamicPage(String translationKey, int accent) {
		return new Page(translationKey, accent, null, true);
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
		return PAGE_CONTENT_WIDTH;
	}

	private static final class JournalButton extends Button {
		private final boolean chapterButton;

		private JournalButton(int x, int y, int width, int height, Component message,
				boolean chapterButton, OnPress onPress) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
			this.chapterButton = chapterButton;
		}

		@Override
		public void renderWidget(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
			GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
			int x0 = getX();
			int y0 = getY();
			int x1 = x0 + getWidth();
			int y1 = y0 + getHeight();
			if (this.chapterButton) {
				if (isHoveredOrFocused()) {
					graphics.fill(x0, y0, x1, y1, ROW_HOVER);
				}
				renderString(poseStack, Minecraft.getInstance().font, LABEL_LIGHT);
				return;
			}
			int face = this.active
				? (isHoveredOrFocused() ? 0xFF3A3550 : 0xFF2A2735)
				: 0xFF24222A;
			int trim = this.active ? 0xFFAEEAFF : 0xFF5F596A;
			graphics.fill(x0, y0, x1, y1, 0xFF15131B);
			graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, trim);
			graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, face);
			graphics.fill(x0 + 3, y0 + 3, x1 - 3, y0 + 4, this.active ? 0xFFE0C6FF : 0xFF77707E);
			graphics.fill(x0 + 3, y1 - 4, x1 - 3, y1 - 3, 0xFF17151D);
			renderString(poseStack, Minecraft.getInstance().font, LABEL_LIGHT);
		}
	}

	private record Page(String translationKey, int accent, Affinity affinity, boolean dynamic) {}
	private record Chapter(String name, List<Page> pages) {
		private Chapter {
			pages = List.copyOf(pages);
		}
	}
	private record Section(Page page, String title, List<FormattedCharSequence> lines) {}
	private record PageText(String title, String body) {}
}

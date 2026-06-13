"""Offline preview of the Attunement Journal screen.

Composites the real generated `gui/attunement_journal.png` with the screen's exact
layout math so journal layout can be inspected without launching the game. Each
chapter renders as one continuous, scrollable document (mirrors drawChapter). Font
metrics approximate the vanilla 6px font (per-char advance) closely enough to catch
overflow/overlap. Keep the constants here in sync with AttunementJournalScreen.java.

Run: python tools/preview_journal.py  ->  tmp/journal_preview.png
"""
import json
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURE = os.path.join(ROOT, "src/main/resources/assets/attuned/textures/gui/attunement_journal.png")
LANG = os.path.join(ROOT, "src/main/resources/assets/attuned/lang/en_us.json")
OUT = os.path.join(ROOT, "tmp/journal_preview.png")
SCALE = 4

# --- layout constants (mirror AttunementJournalScreen.java) ---
PANEL_W, PANEL_H = 336, 214
NAV_X_OFFSET = 34
PADDING = 12
NAV_WIDTH = 88
CONTENT_GAP = 12
PAGE_CONTENT_WIDTH = 216
CHAPTER_BUTTON_Y = 34
CHAPTER_BUTTON_WIDTH = 62
CHAPTER_BUTTON_HEIGHT = 12
CHAPTER_BUTTON_GAP = 1
PAGE_BUTTON_WIDTH = 66
PAGE_BUTTON_HEIGHT = 20
LINE_HEIGHT = 10
HEADER_HEIGHT = 24
SECTION_GAP = 10
CONTENT_TOP = 18
CONTENT_BOTTOM = PANEL_H - 48

CONTENT_LEFT = PADDING + NAV_WIDTH + CONTENT_GAP  # 112
PAGE_TITLE = (0x2C, 0x21, 0x18)
PAGE_BODY = (0x3D, 0x30, 0x24)
PAGE_MUTED = (0x6E, 0x5E, 0x45)
PAGE_LINE = (0x9A, 0x8A, 0x66)
PAGE_TRACK = (0xAB, 0x9C, 0x7E)
NAV_TITLE = (0xF2, 0xE7, 0xFF)
NAV_MUTED = (0xB7, 0xAC, 0xCA)
LABEL = (0xE8, 0xDE, 0xF4)

CHAPTERS = [
    ("Core", ["journal.attuned.page1", "journal.attuned.page2", "journal.attuned.page3",
              "journal.attuned.page4", "journal.attuned.page5"], 0xB995FF),
    ("Pacts", ["journal.attuned.page6", "journal.attuned.page15", "journal.attuned.page16",
               "journal.attuned.page17", "journal.attuned.page29", "journal.attuned.page18",
               "journal.attuned.page19"], 0xFFD37A),
    ("Apex", ["journal.attuned.page7", "journal.attuned.page30", "journal.attuned.page31"], 0xFFD37A),
    ("Altar", ["journal.attuned.page8", "journal.attuned.page14"], 0xAEEAFF),
    ("Unseen", ["journal.attuned.page9", "journal.attuned.page24"], 0xB995FF),
    ("Finding", ["journal.attuned.page10"], 0x95E6B3),
    ("Builds", ["journal.attuned.page11", "journal.attuned.page12", "journal.attuned.page13"], 0x95E6B3),
    ("Lore", ["journal.attuned.page20", "journal.attuned.page21", "journal.attuned.page22",
              "journal.attuned.page25", "journal.attuned.page33"], 0xAEEAFF),
    ("Radiant", ["journal.attuned.page23"], 0xFFD37A),
    ("Seafarers", ["journal.attuned.page26", "journal.attuned.page27"], 0x70D7FF),
    ("Offshore", ["journal.attuned.page32"], 0x56D8CF),
    ("HUD", ["journal.attuned.page28"], 0x95E6B3),
    ("Confluences", ["__confluence__"], 0x95E6B3),
]

NARROW = {'i': 2, 'l': 3, '.': 2, ',': 2, '!': 2, ':': 2, ';': 2, '|': 2, "'": 2,
          '`': 3, ' ': 4, 't': 4, 'f': 5, 'I': 4, '(': 5, ')': 5, '[': 4, ']': 4}


def text_width(s):
    return sum(NARROW.get(c, 6) for c in s)


def wrap(text, maxw):
    lines = []
    for word in text.split(' '):
        if not lines:
            lines.append(word)
            continue
        trial = lines[-1] + ' ' + word
        if text_width(trial) <= maxw:
            lines[-1] = trial
        else:
            lines.append(word)
    return lines


def draw_mc_text(d, x, y, s, fill, font):
    cx = x
    for ch in s:
        d.text((cx, y), ch, fill=fill, font=font)
        cx += NARROW.get(ch, 6)


def argb(hexval):
    return ((hexval >> 16) & 0xFF, (hexval >> 8) & 0xFF, hexval & 0xFF)


def page_text(lang, key):
    if key == "__confluence__":
        title = lang.get("journal.attuned.confluence.title", "Confluences")
        rows = [lang.get("journal.attuned.confluence.intro", ""), ""]
        rows.append(lang.get("confluence.attuned.immovable.name", "Immovable"))
        red = lang.get("journal.attuned.confluence.redacted", "???")
        members = lang.get("journal.attuned.confluence.members", "%s Foci").replace("%s", "2")
        for _ in range(3):
            rows.append(red + " (" + members + ")")
        return title, "\n".join(rows)
    raw = lang.get(key, key)
    parts = raw.split("\n", 1)
    title = parts[0].strip()
    body = parts[1].strip() if len(parts) > 1 else ""
    return title, body


def render_chapter(lang, chapter_idx, scroll):
    img = Image.open(TEXTURE).convert("RGBA").copy()
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 7)
    except Exception:
        font = ImageFont.load_default()

    draw_mc_text(d, PADDING, 8, "Attunement Codex", NAV_TITLE, font)
    draw_mc_text(d, PADDING, 20, "Foci, pacts, altar craft", NAV_MUTED, font)

    for i, (name, keys, accent) in enumerate(CHAPTERS):
        bx = NAV_X_OFFSET
        by = CHAPTER_BUTTON_Y + i * (CHAPTER_BUTTON_HEIGHT + CHAPTER_BUTTON_GAP)
        bw, bh = CHAPTER_BUTTON_WIDTH, CHAPTER_BUTTON_HEIGHT
        col = argb(accent)
        if i == chapter_idx:
            d.rectangle((bx - 2, by - 1, bx + bw + 2, by + bh + 1), fill=col + (0x33,))
            d.rectangle((bx - 2, by - 1, bx, by + bh + 1), fill=col)
        cx, cy = bx - 6, by + bh // 2
        d.rectangle((cx - 1, cy, cx + 1, cy), fill=col)
        d.rectangle((cx, cy - 1, cx, cy + 1), fill=col)
        draw_mc_text(d, bx + (bw - text_width(name)) // 2, by + 2, name, LABEL, font)

    name, keys, accent = CHAPTERS[chapter_idx]
    col = argb(accent)
    x = CONTENT_LEFT + 10
    w = PAGE_CONTENT_WIDTH - 20
    text_w = w - 6
    content_top, content_bottom = CONTENT_TOP, CONTENT_BOTTOM
    window_h = content_bottom - content_top

    sections = []
    total = 0
    for key in keys:
        title, body = page_text(lang, key)
        lines = []
        for para in body.split("\n"):
            if not para.strip():
                lines.append("")
            else:
                lines.extend(wrap(para, text_w))
        sections.append((title, lines))
        total += HEADER_HEIGHT + len(lines) * LINE_HEIGHT + SECTION_GAP
    total = max(0, total - SECTION_GAP)
    max_scroll = max(0, total - window_h)
    scroll = max(0, min(scroll, max_scroll))

    y = content_top - scroll
    for title, lines in sections:
        if content_top <= y <= content_bottom - 9:
            draw_mc_text(d, x + 18, y, title, PAGE_TITLE, font)
            d.rectangle((x, y + 4, x + 12, y + 5), fill=col)
        rule_y = y + 16
        if content_top <= rule_y <= content_bottom:
            d.rectangle((x, rule_y, x + w, rule_y), fill=PAGE_LINE)
            d.rectangle((x, rule_y, x + min(w, 76), rule_y), fill=col)
        y += HEADER_HEIGHT
        for line in lines:
            if y >= content_top and y + 9 <= content_bottom and line:
                draw_mc_text(d, x, y, line, PAGE_BODY, font)
                if text_width(line) > text_w:
                    d.line((x, y + 9, x + text_width(line), y + 9), fill=(255, 0, 0))
            y += LINE_HEIGHT
        y += SECTION_GAP

    if max_scroll > 0:
        tx = x + w - 1
        d.rectangle((tx, content_top, tx + 2, content_bottom), fill=PAGE_TRACK)
        thumb_h = max(10, round(window_h * (window_h / total)))
        thumb_y = content_top + round((window_h - thumb_h) * (scroll / max_scroll))
        d.rectangle((tx, thumb_y, tx + 2, thumb_y + thumb_h), fill=col)

    progress = str(chapter_idx + 1) + " / " + str(len(CHAPTERS))
    progress_x = x + PAGE_BUTTON_WIDTH + 10
    progress_w = w - (PAGE_BUTTON_WIDTH + 10) * 2
    draw_mc_text(d, progress_x + (progress_w - text_width(progress)) // 2, PANEL_H - 47,
                 progress, PAGE_MUTED, font)

    by = PANEL_H - PADDING - PAGE_BUTTON_HEIGHT
    for box in ((CONTENT_LEFT, by, CONTENT_LEFT + PAGE_BUTTON_WIDTH, by + PAGE_BUTTON_HEIGHT),
                (CONTENT_LEFT + PAGE_CONTENT_WIDTH - PAGE_BUTTON_WIDTH, by,
                 CONTENT_LEFT + PAGE_CONTENT_WIDTH, by + PAGE_BUTTON_HEIGHT)):
        d.rectangle(box, outline=(120, 110, 150))

    # guides
    d.rectangle((112, 12, 328, 181), outline=(0, 160, 255))
    d.rectangle((x, content_top, x + text_w, content_bottom), outline=(0, 200, 120))
    label = name + ("  scroll=" + str(scroll) + "/" + str(max_scroll) if max_scroll else "  (fits)")
    draw_mc_text(d, 200, 4, label, (200, 200, 0), font)
    return img


def save_one(lang, chapter_idx, scroll, suffix):
    p = render_chapter(lang, chapter_idx, scroll)
    p = p.resize((p.width * SCALE, p.height * SCALE), Image.NEAREST)
    path = os.path.join(ROOT, "tmp", "journal_" + suffix + ".png")
    p.save(path)
    print("wrote", path, p.size)


def main():
    os.makedirs(os.path.join(ROOT, "tmp"), exist_ok=True)
    with open(LANG, encoding="utf-8") as f:
        lang = json.load(f)
    save_one(lang, 1, 0, "pacts_top")
    save_one(lang, 1, 9999, "pacts_bottom")
    save_one(lang, 12, 0, "confluences")
    save_one(lang, 0, 0, "core")


if __name__ == "__main__":
    main()

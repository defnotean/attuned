# Simple Altar Textures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the noisy altar textures with simple, readable Minecraft-style block textures that still feel polished.

**Architecture:** Keep the existing block models and the local Three.js viewer. Rewrite `tools/generate_block_textures.py` as a deterministic low-noise texture generator, then inspect the generated PNGs on the actual JSON block models.

**Tech Stack:** Python, Pillow, Minecraft resource PNGs, existing local model viewer.

---

### Task 1: Simple Texture Generator

**Files:**
- Modify: `tools/generate_block_textures.py`
- Output: `src/main/resources/assets/attuned/textures/block/*.png`
- Test: `tests/test_generate_block_textures_contract.py`

- [ ] Replace AI-source crops with simple generated stone, trim, crystal, and rune shapes.
- [ ] Keep animated 64x512 strips for altar gem, pillar, and top textures.
- [ ] Regenerate all altar and reweaving texture PNGs.
- [ ] Inspect the actual 3D viewer screenshots, then tune once if the result is still too noisy.
- [ ] Run `python -m unittest discover -s tests`, `python tools\verify_repository.py`, and `.\gradlew.bat build --no-daemon`.

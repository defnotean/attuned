# Attuned Gameplay Polish QA

Use this checklist before cutting a release candidate for the gameplay-feel work on the 1.5.x line. It focuses on player-facing loops that cannot be proven by contract tests alone.

## Moment-to-moment combat feel

- [ ] Equip a direct combat build and confirm resonance gain/drain feedback is readable without flooding the screen.
- [ ] Trigger Apex capstones for at least Fury, Holy, Tide, Forge, Verdant, and Umbral; confirm particles/sounds make the proc noticeable.
- [ ] Build a short kill streak and confirm cooldown shaving feels like momentum rather than an invisible number change.
- [ ] Verify action-bar text stays useful during combat and does not bury critical cooldown or surge feedback.

## Pact loop

- [ ] Wake each Pact with three matching active affinity Foci and confirm the journal/readout names the correct Pact.
- [ ] Fire a Pact Tactical with no active ability Focus equipped; confirm the Focus Ability key reports clear feedback and starts the tactical cooldown.
- [ ] Crouch-fire a Pact Tactical at Apex resonance and confirm overcharge spends resonance, strengthens the effect, and explains the spend.
- [ ] Advance at least one Pact Trial from 0% through a progress milestone and confirm the goal text matches the current code goal.
- [ ] Complete one Pact Trial, re-awaken the Pact, and confirm the Tier 4 bonus is permanent but only active while the Pact is awake.

## Confluence and combo discovery

- [ ] Equip a known Confluence pair and confirm the HUD Confluence pips appear without hiding active/dormant Focus marks.
- [ ] Swap one Focus out of the pair and confirm the Confluence readout disappears cleanly.
- [ ] Test at least one Resonant Combo or pair payoff in combat and confirm the feedback makes the pairing discoverable.
- [ ] Open the Attunement Journal after discovering a Confluence and confirm the chapter explains what woke up and why.

## Resonant Surge loop

- [ ] During a thunderstorm, wait for or force a Resonant Surge and confirm the start broadcast gives enough location context to find the site.
- [ ] Stand inside the storm site and confirm Resonance rises faster than ordinary combat buildup.
- [ ] Fight at the surge and confirm monster pressure is present but not overwhelming.
- [ ] Earn a kill reward and confirm shard fragments are granted or dropped without inventory loss.

## Party-readiness smoke

- [ ] Create a Circle, invite a second player, accept the invite, and confirm both clients show the same public party HUD.
- [ ] Leave and reform a Circle, then confirm invite/create cooldown messages are readable and cannot be spammed.
- [ ] Disconnect one Circle member and confirm remaining members receive an updated party HUD without offline ghost rows.
- [ ] Verify shared-credit checks require same-dimension, nearby, online, recent contribution before any trial or party credit is granted.
- [ ] No AFK Circle member should gain Pact, Field, Circle, surge, or party progress by standing nearby without contribution.
- [ ] In a friendly-fire-off PvP check, confirm Circle members do not trigger hostile-only Focus, Pact, Apex, or party-assist effects on each other.

## Updraft flight feel

- [ ] Equip Updraft Focus and a functional elytra, then hold jump while gliding; confirm boost follows look direction smoothly.
- [ ] Hold sprint/control while fall-flying and confirm braking is strong but not a jarring stop.
- [ ] Release controls and confirm velocity settles naturally without rubber-banding.
- [ ] Maintain PvP pressure for more than five seconds and confirm PvP exhaustion brakes flight and applies short Weakness/Slowness.
- [ ] Confirm boost, brake, and exhaustion particles/sounds are readable from first-person and not noisy in third-person.

## Onboarding and journal clarity

- [ ] First-time hints appear for resonance armed, Focus Ability use, Confluence discovery, and Pact Trial completion.
- [ ] The Attunement Journal explains eight affinities as the current model and does not mention a fourth-affinity or every-affinity build requirement.
- [ ] Untethered and Maelstrom wording says four or more different affinity lanes with no lane stacked three deep.
- [ ] The HUD page explains the ability well, resonance bar, Confluence pips, and active/dormant Focus marks.
- [ ] Pact Tacticals, Pact Trials/Tier 4, Resonant Surges, and Updraft Focus each have a player-readable journal page.
- [ ] The Circles chapter explains public attunement state, no item sharing, and why solo play remains complete.

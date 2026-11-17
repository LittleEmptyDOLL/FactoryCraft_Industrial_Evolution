# 0.28.0 real client checks

Actual Minecraft 1.21.1 / NeoForge 21.1.252 under Xvfb/Mesa, not a UI mockup. Separate new world: `FactoryCraft0280-Optional-QA`. RU and EN clients ran in separate JVMs with FTB Teams, EMI, JEI and Jade together. Inputs supplied for graphical QA are explicit creative/finite diagnostic preparation, not evidence of a full survival playthrough.

| Check | Observed evidence |
|---|---|
| Controller and part open the same turret menu | [RU controller](controller-menu-fixed-ru.png), [RU part](part-menu-fixed-ru.png), [EN menu with distinct inventory slots](gun-menu-en.png) |
| Jade resolves the same owner/name/items | [Controller RU](jade-controller-ru.png), [part RU](jade-part-ru.png), [EN](jade-controller-en.png); no technical Casing name or duplicate inventory rows |
| Pistol, SMG, both shotguns, launcher and flamethrower fire | Actual hostile targets and component debits; [pistol](pistol-fire-ru.png), [SMG](smg-fire-ru.png), [shotgun](shotgun-fire-ru.png), [combat shotgun](combat-shotgun-fire-ru.png), [rocket](rocket-fire-ru.png), [flame after muzzle correction](flame-muzzle-fixed-en.png) |
| First/third person meshes, holding and switch/recoil | [Corrected third-person flamethrower](flamethrower-third-person-fixed-ru.png); other weapon contexts have distinct original meshes; no invisible generated-item geometry |
| One-item turret placement and active moving heads | [Gun preview](gun-turret-preview-ru.png), [placed gun](gun-turret-placed-ru.png), [flame preview](flame-preview-ru.png), [active flame](flame-turret-active-ru.png), [active laser](laser-active-ru.png) |
| Correct type-specific information | [Laser FE menu](laser-menu-en.png), [separate flame buffers](flame-menu-en.png) |
| Crafting and machine recipe visibility | [Counted military handcraft](emi-military-recipe-open-en.png), [machine recipe with 200 ticks, 10 FE/t and research lock](emi-military-processing-en.png). EMI imports JEI and the native FactoryCraft categories; both mods are loaded. A JEI-only graphical session was not performed. |
| New research remains searchable and gated | [Typed military search](research-military-search-en.png), including genuinely locked Military 4; no overlapping military nodes |
| Walls join; gate opens for its owner | [Closed gate/walls](walls-gate-closed-en.png), [automatic open](gate-auto-open-en.png) |
| Mine has visible physical placement | [Placed mine](land-mine-en.png); native tests separately verify arming, one blast, friendly exclusion and stun |
| Actual save/reopen and unloading partial magazine | Six-round pistol survived the separate client JVM. A subsequent ordinary shot reduced it to five; sneak-use then removed the container and returned precisely that five-round magazine to inventory. Read-only saved NBT confirms the exact object, without refill. |

The normally saved client world independently records **1000 mB = 916 mB remaining + 42 paid flame attacks × 2 mB**, and one laser shot with zero FE remaining, 125-J shot residue and one fifth of idle billing. Source finite FE/fuel were explicitly prepared for rendering; the separate native steam-to-grid-to-laser scenario verifies actual network supply. Read-only [saved ledger](client-saved-ledger.json) also records remaining rounds of all held weapons. The world was saved and closed normally.

Earlier setup and failed-view captures remain in this folder as development evidence, not successful final screenshots. The first parent/pose/muzzle and GUI-grid attempts are superseded by the named final images above. The initial too-fast synthetic Shift+Use event was processed as an ordinary shot; the later deliberate held-sneak interaction preserved and unloaded the five-round remainder.

Known environment/integration limits: the audio backend is absent, so sound output is unverified. Auth/public-key requests are refused; local play/save works. Combined EMI+JEI reports existing duplicate foreign tag recipes and untranslated tag warnings; this is not claimed warning-free compatibility. No FactoryCraft military recipe was duplicated in the inspected category. No real multiplayer survival session or large combat load was tested here.

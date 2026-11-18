# Military production and combat — 0.30.1

Reference: [Wube 2.1.24](https://github.com/wube/factorio-data/tree/2.1.24), **Space Age enabled**, normal quality. Numerical recipes/finite research/effects are recorded in [tools/combat_reference.json](tools/combat_reference.json). Models, meshes, animation code and textures are FactoryCraft originals; gun/laser/flame shots use original procedurally synthesized FactoryCraft audio; existing explosion/fire events use Minecraft sounds. TaCZ and Superb Warfare are architectural/visual references only, with no dependency or imported code/assets (see the parity audit).

## 0.30.1 corrections and reproduction

* **Laser starvation:** paying 3–4 idle FE before a 2133/2134-FE shot from a 2136-FE source-sized buffer made some fractional phases permanently unable to fire. `LaserStandby` pays previous bounded idle debt first, pays a valid shot before the current idle bill, and retains its exact 1/5-FE residue and at most four unpaid FE. Idle time with no available power does not accumulate new debt. Buffer size, shot bill, source cadence and grid delivery remain unchanged. The entire shot/idle/cooldown ledger commits before external damage listeners can pack the owner. Menu status distinguishes power, cooldown, aiming, obstruction and unloaded paths.
* **Near chunk borders:** hitscan clips its maximum ray at the first unavailable sample, then resolves actual cover/nearest eligible hit. An unloaded distant tail cannot suppress a loaded nearby hit. There are no forced chunks or traces through solids.
* **Flame target switching:** losing a target no longer clears genuine priming. Wube's `FluidTurretPrototype.activation_buffer_ratio` describes restarting a turret **that ran out of fluid ammunition**, not re-priming on every victim. A primed turret continues below 250 mB until fewer than 2 mB remain; an actual empty restart still needs 250. Source 25 mB/t feeding, 2 mB/attack, facing, minimum range and bounded flights remain intact. Three sequential targets are tested from a finite 251-mB stock; no replenishment hides a stall.
* **Knockback:** typed `DamageSource` retains the real owner for team/PvP/credit but uses the physical muzzle/impact position. Ground/sticker fire uses the scoped `minecraft:no_knockback` tag for FactoryCraft fire damage. It does not alter arbitrary other-mod knockback.
* **Grenades:** the old 15-block path exhaustion fabricated a MISS collision. Grenades now detonate on actual block/entity contact or a saved **100-Minecraft-tick (5-second) fuse**, a documented 3D safety adaptation. Rockets expire at their own range without fake impacts. Unavailable paths pause movement/damage and the 400-tick safety bound prevents leaks. Saved mode is also synchronized for client gravity; inventory debit remains exactly one grenade.
* **Weapons/effects:** all six first-person separate-transform models now follow their actual forward −Z barrel axis in both hands; recoil points upward. Other display contexts stay independent. Paid server results send bounded impact/normal/muzzle projections to nearby clients. Short bullet tracers, coherent red laser beams, travelling flame particles and impact sparks are client visuals, never damage authority or per-bullet entities. Projection packets contain at most 18 impacts, at most 512 transient client records, and only loaded clipped endpoints. Original firing sounds have a 40-block range and per-source 2/4-tick rate limits. Empty/unpowered attempts emit no shot effect. `tools/generate_combat_fix_assets.py` reproduces bit-exact original Vorbis assets; no reference-mod audio/art/code is copied.

Technical references actually reviewed: TaCZ **1.21.1** `GunItemTransforms` / `GunItemRendererWrapper`; Superb Warfare `ProjectileEntity.kt` (branch `superbwarfare`); NeoForge **21.1.252** native damage-position/packet/attribute APIs. Source parameters are from pinned [Wube 2.1.24 fire prototypes](https://github.com/wube/factorio-data/blob/2.1.24/base/prototypes/entity/fire.lua) and [activation-buffer semantics](https://lua-api.factorio.com/latest/prototypes/FluidTurretPrototype.html#activation_buffer_ratio). These references introduce no dependencies.

Integrated armor is the existing PR #11 implementation, not a second resistance system. Sixteen native synced attributes remain public; six nonzero item modifiers appear in vanilla tooltips and Apothic Attributes. Pure registration is not gameplay acceptance: actual evidence and limitations are tracked in [validation/v0301](validation/v0301).

## Survival progression

Military requires 10 red units at 15 seconds/unit and unlocks the SMG, shotgun and shells. Military 2 requires Military, Steel Processing and Logistic Science, costs 20 red+green units at 15 seconds/unit, and unlocks piercing magazines/grenades. Stone Wall and Gun Turret are separate 10-red, 10-second technologies. Military Science requires Military 2 and Stone Wall, and costs **30 red+green units at 15 seconds/unit**. There is no duplicate grey science item or lab slot.

**1 piercing magazine + 1 grenade + 2 stone walls → 2 military packs in 10 base seconds.** Actual assembler speed, FE and existing productivity rules apply. Native original costs include 4 iron plates per ordinary 10-round magazine; 2 full ordinary magazines + 1 steel + 2 copper → 2 piercing magazines; 5 iron + 10 coal → 1 grenade; 5 stone bricks → 1 wall. There is no raw-material compression to fit a crafting grid. Heavy Armor's 100 copper can span several grid stacks; ordinary counted handcraft rules remain unchanged. Handcrafts use the same server research locks as machine recipes. The flamethrower-turret bill accepts only empty, component-default pipes: portable liquid cannot be silently consumed as construction material.

Choose new military recipes explicitly in assemblers. Automatic recipe detection skips the new `military` category so adding magazines/light armor cannot silently change an existing iron-to-gears factory. Assemblers 1, 2 and 3 execute the same recipe IDs. Chemistry adds real explosives and flamethrower ammunition with exact native quantities (1 fluid unit = 10 mB); no late science/material substitute is introduced.

Battery Research now supplies the genuine prerequisite for Laser Research. Remaining utility/space-dependent combat technology definitions remain visible and disabled where the actual science industry is missing. In particular Combat Shotgun remains behind Military 4 and genuine utility science. Source-defined finite damage/speed upgrades affect their own ammo/turret categories only; infinite/offworld combat research is deferred rather than given fake costs.

## Player controls and ammunition

Hold **Use/right mouse** to fire. Sneak + Use unloads the current magazine, preserving its exact rounds/components; a full inventory leaves the remainder in the weapon. The next compatible inventory magazine loads immediately when needed, with no tactical reload timer. Switch weapons through the normal hotbar. Tooltips show loaded/remaining rounds. Weapons and source armor have no durability. A player receives one starting pistol once; there is no invented pistol recipe. The ordinary Minecraft start otherwise remains intact: ammunition must be manufactured.

Ordinary/piercing magazines and shotgun magazines contain 10 rounds; handheld flamethrower ammunition contains 100 charges. The vehicle-only 1.125 consumption modifier is deliberately not applied to the handheld weapon. Rockets contain one shot. A full inventory stack is split into one magazine before firing. The gun's native container component owns that magazine; the turret owns its separate supply/loaded slots. Partial magazines/shells cannot be upgraded into fresh piercing ammunition. Piercing shotgun shells unlock at Military 3: two full shells +two copper +one steel produce two magazines in eight seconds; each shot uses eighteen 12-damage pellets instead of twelve 8-damage pellets. `ammo_rounds` persists exact debits; `ammo_capacity` / `ammo_damage` provide bounded component-driven extension points for datapack results. These are not Quality gameplay.

Every shot commits ammo/FE/oil before damage/projectile callbacks. A persisted per-player clock prevents switching weapons or repeating use requests from bypassing cooldown. Grenade cooldown has a persisted server deadline as well. Component changes do not cause a false weapon-switch animation on every round. No client packet supplies hit results or damage.

| Device | Source cooldown (60 Hz) | Range | Source damage |
|---|---:|---:|---|
| Pistol | 15 | 15 | ammo: 5 / 8 |
| SMG | 6 | 18 | ammo: 5 / 8 |
| Shotgun | 40 | 15 | 12 × 8 pellets |
| Combat shotgun | 30 | 15 | 12 × 8 × 1.5 |
| Rocket launcher | 60 | 36 | 200 normal; 50 direct + 100 area explosive |
| Handheld flamethrower | 1 | 3–15 | 2 area on impact, fire sticker/ground flame |
| Gun turret | 6 | 18 | ammo: 5 / 8, separate turret research multiplier |
| Laser turret | 40 | 24 | 20 laser |
| Flamethrower turret | 4 | 6–30, 120° forward arc | 3 area, fire sticker/ground flame |

Source clock units are converted at 3:1 with retained fractional periods, including shooting-speed upgrades. Inactive time never accumulates a catch-up burst. Bullets/pellets/laser use server raycasts against real hitboxes and solid cover; they create no bullet entities. Grenades/rockets use saved server projectiles with bounded lifetime/range and loaded-path checks. Flame streams travel at 0.75 blocks/Minecraft tick using saved non-entity packets, then produce 2.5-block area hits, thirty-second stickers with 20% movement reduction, and bounded-duration local fire volumes. Sticker timing retains the 10-source-tick damage interval; source 13 HP/s ground fire, additional-fuel multiplier (up to 6), decay and lifetime are represented separately. Ground fields sharing one Minecraft block collapse into one local field. No particle entities or world block ignition are used.

## Turrets, walls, gates and mines

Gun/Laser turrets occupy 2×2×2; flame turrets 2×2×3 (X×height×depth). Heights adapt to Minecraft. One item forms the whole guarded structure; one controller owns ammo, FE, oil, research scope, paid residues and aim. Parts only forward current canonical capabilities. Break/pack any part to obtain at most one portable turret containing its resources. Full loaded assembly checks protect inventories, physical oil ports and energy capabilities.

Gun turrets accept real magazines through the GUI, inserters and sided item handlers. The supply slot is bounded to ten magazines as a Minecraft storage adaptation. Both controller and physical parts open the same guarded menu and Jade snapshot. Laser turrets join the existing explicit copper-wire power graph and footprint coverage; external FE capabilities remain valid. The original **801 kJ** buffer is **2136 FE**; **800 kJ/shot = 2133⅓ FE**, **24 kW idle = 3.2 FE/t**, with both residues saved exactly. No generator or global production rebalance was added.

Flame turrets accept crude/heavy/light oil only. A 1000-mB pass-through fluid box feeds a separate, solely owned 1000-mB private fuel buffer at 25 mB/Minecraft tick. Physical BOTH ports are on the rear-left/right faces and rotate with the complete machine. Heavy/light give source 1.05/1.1 damage multipliers. Priming requires 250 mB in the private buffer; each four-source-tick attack consumes **2 mB** (0.2 source units per attack), averaging 1.5 mB/Minecraft tick while continuously firing. Native external boundary pressure quotas still apply. The two buffers preserve exact fluid components and cannot internally mix incompatible oil. Water/gas/acid cannot fuel the turret. Its ports do not merge the two neighboring passive pipeline segments.

Acquisition uses a local loaded entity query every ten ticks; tracked targets recheck eligibility/range/LOS. Rotation is 16.2°/tick for gun/flame and 10.8°/tick for laser; elevation follows 3D targets. Static bases have cached mesh-derived collision shapes so a turret cannot raycast into its own full cuboid. Decorative pivoting heads do not add an invisible moving collision wall. No whole-world turret/entity scan, idle sync loop or forced chunk loading occurs.

Stone walls join through vanilla wall geometry. Metal gates open automatically for the placing owner/force, support native redstone/manual operation and close afterwards. These are ordinary one-cell Minecraft blocks with real collision, hardness and break/drop behavior. Mines arm for 40 Minecraft ticks, trigger on nearby eligible hostiles (2.5), remove their sole block before a radius-3 / 250-source-damage blast, and stun surviving mobs for three seconds. The original AI flag is restored and saved stun state survives chunk/JVM boundaries.

## Explicit Minecraft adaptations and limits

* **1 Factorio HP = 0.2 Minecraft health points** (0.1 heart). Minecraft entity max HP remains unchanged. A basic 5-damage bullet deals 1 MC health before protection; normal rocket 40. Native mob armor/protection still applies. Light/heavy armor add original flat/percentage resistance in source units before conversion; laser is not accidentally treated as physical. Zero damage remains zero. Flat-resistance math follows [Damage](https://wiki.factorio.com/Damage), including the low-damage branch.
* Blasts and fire damage entities under server PvP/team/LOS rules and leave terrain/blocks intact. Factory structures use Minecraft block hardness/explosion resistance rather than a new Factorio block-HP engine. There is no tree-fire spread or pollution emission. Source structure HP (gun 400, laser 1000, flame 1400) is **reference metadata, not implemented HP**; flame source fire immunity maps to nonflammable construction. No custom raids/enemies are added.
* Automatic weapons target vanilla `Enemy` mobs, not passive animals, villagers, players or pets. Player-fired attacks respect server PvP, allied teams, bound research force and tameable protection. Offline owners cannot inflict player damage through a saved projectile/fire field. This is deliberately stricter than indiscriminate area friendly fire.
* Flame work queues/fields have bounded safety limits; shooting pauses before a full flight queue can debit fuel. At extreme concurrent fire load, the arrival work budget can add latency. This milestone does not claim a massive-combat TPS target. Chunk-boundary projectiles pause or expire safely rather than loading terrain; fire does no damage through unloaded paths.
* Original low-detail 3D models, gloves, player holding/recoil/switch motion, turret pivot/elevation/recoil and particles are functional initial art. Higher-detail art remains future work; 0.30.1 adds original paid firing audio and synced effects. No TaCZ/Superb assets or animation engine is bundled.
* No vehicle, uranium, artillery, robot, rail, Quality or Space Age production content is registered by this stage. Three belt lifts and four circuit colors are unchanged. Old intermediate formats need no migration; diagnostic worlds are preserved.

Actual automated/client evidence and remaining checks are recorded in [VALIDATION.md](VALIDATION.md), not inferred from these implementation descriptions.

## Attribute-based chest armor (post-0.30 focused fix)

Light and Heavy Armor remain single, indestructible chest items. Their original normal-quality
Factorio 2.1.24 resistance values now come from eight pairs of standard, synced
`factorycraft:*_flat_resistance` / `factorycraft:*_percent_resistance` attributes:
physical, explosion, fire, acid, impact, poison, laser and electric. Values are item
`ItemAttributeModifiers` with the CHEST equipment slot, so vanilla item tooltips and
optional Apothic Attributes screens can display them; there is no special tooltip or
required dependency. A chest item contributes the four source-defined types it actually
resists, while other types remain zero.

The existing Factorio flat-then-percent formula still runs once via
`LivingIncomingDamageEvent` before normal vanilla or optional Apothic armor. Values
are defined in Factorio HP and converted using the existing 0.2 Minecraft-health-per-source-HP
scale. Normal Minecraft armor from other slots is not disabled, and no duplicate
Minecraft armor value is added for the FactoryCraft chestplate.

Vanilla explosion/fire, ordinary melee/projectiles, lightning and fly-into-wall are
mapped to typed resistance. Explicit `factorycraft` damage types and datapack
`factorycraft:damage_types/<kind>` tags provide mappings for laser/acid/poison/impact
and optional other-mod damage. Untagged falling, hunger, void, magic and unknown damage
remain unchanged. Mods and later equipment can grant the same attributes to any living
entity, not just wearers of these two items.

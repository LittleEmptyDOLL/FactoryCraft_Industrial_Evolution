# Furnaces — 0.25.1

Factorio 2.1 normal-quality definitions: [Stone](https://wiki.factorio.com/Stone_furnace), [Steel](https://wiki.factorio.com/Steel_furnace), [Electric](https://wiki.factorio.com/Electric_furnace).

| ID | X×Y×Z | Base speed | Active supply | Modules |
|---|---|---|---|---|
| stone_furnace | 2×2×2 | 1 | item fuel, 90 kW reference | 0 |
| steel_furnace | 2×3×2 | 2 | item fuel, 90 kW reference | 0 |
| electric_furnace | 3×3×3 | 2 | 24 FE/t, 180 kW reference | 2 |

Minecraft heights and energy units are adaptations. Existing steam generation maps 900 kW to 120 FE/t, hence electric furnace 180 kW maps to **24 FE per active Minecraft tick**, not 48. The optional 6 kW idle drain is not modeled. Both burners spend **one native Minecraft fuel-duration tick per active Minecraft tick** (coal 1600 = 80 seconds); this is an explicit fuel-unit adaptation, not a claim that vanilla coal has Factorio's MJ value. Neither burner joins the electric grid or accepts FE, modules or beacon effects.

One controller owns items, fuel duration/container remainder, FE, recipe, progress, modules and fixed-point remainders. Parts own identity only. The guarded existing one-item assembly/packing transaction covers all three profiles. Automation inserts fuel into slot 8 and can extract spent nonfuel containers; GUI uses the same inventory validation. A stacked fuel with a nonempty container remainder is conservatively rejected rather than overwriting remaining items. Fuel does not ignite/burn without matching inputs and reserved complete output space.

All three use one `smelting` recipe group and real RecipeManager `SMELTING` recipes, including other mods' smelting. Blasting/smoking are not implicitly imported. Inputs and component-exact outputs are reserved before payment; same-ID recipe-definition changes cannot reuse old work. Iron/copper base 64 ticks and steel 320 yield 64/320 Stone or 32/160 Steel/Electric. A native 200-tick recipe takes 200/100/100. Speed modules and paid beacons multiply base speed; productivity accrues from actual work, never from merely swapping modules at completion. Fractional work and FE residues persist. Very short recipes can complete several times within one paid tick through a bounded work-budget loop.

Stone furnace is hand-crafted from five cobblestone (Minecraft substitution for five Factorio stone). Steel uses six steel plates and ten stone bricks, gated by Advanced Material Processing (75 red/green units). Electric uses ten steel, five advanced circuits and ten stone bricks, gated by Advanced Material Processing 2 (250 red/green/chemical units). Stone-brick smelting uses two cobblestone → one vanilla stone-bricks item, base 64 ticks; recipe time follows Factorio while Minecraft materials/output artwork are substitutions. Refinery crafting uses stone bricks instead of consuming an electric furnace, avoiding a circular chemical-science gate. Existing fluid quantities/chemistry recipes are unchanged.

The invented `industrial_furnace` registry ID is removed. Pre-0.25.1 FactoryCraft worlds/items/config formats are unsupported; no migration or automatic world deletion is performed. Current-format portable and separate-JVM checkpoints remain strict. JEI/EMI share the smelting group with all three workstations; vanilla recipes remain in their native viewer categories.

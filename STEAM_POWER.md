## 0.29 nuclear heat and temperature-distinct steam

25-J quanta preserve the existing 375 J/FE scale. Reactor: 5×5×4, 40 MW base, +100% for each active loaded cardinal reactor exactly five horizontal blocks away on the same foundation level. One cell burns for 4000 Minecraft ticks (200 s, 8 GJ base); an ongoing burn continues and records wasted heat at the 1000°C cap. New fuel starts only below the cap, with space for one spent cell. Neighbor snapshots precede burning, so iteration order does not manufacture bonuses.

Heat Pipe: 1×1×1; Exchanger: 3×2×2; Turbine: 3×5×3 (X×Z×Y). Reactor heat capacity is 10 MJ/°C; pipe/exchanger capacity 1 MJ/°C, measured above 15°C. Physical contacts form a cached loaded-only graph. Actual burning, neighbor bonuses and heat transfer additionally require normally running block ticks at the controller; a retained FULL chunk is not permission to simulate. Exact equalization transfers conserve every quantum, with per-owner per-tick limits of 10/1/2 GW respectively; local topology work is limited to 32 owners and 2048 edge visits per tick. This conservative discrete transfer algorithm and six-direction pipe contacts are Minecraft adaptations, not a claim to identical Factorio heat gradients.

Exchanger draws at most 10 MW above 500°C, converting **one native mB water into one native mB 500°C steam**. Each hot mB owns 9700 J (388 quanta); sub-mB boiling credit is saved. Thus nominal throughput is 51.546… mB/t, at the existing 1:10 native fluid-unit scaling. No conversion from cold steam exists.

| Steam | Enthalpy per native mB | Consumer ceiling |
|---|---:|---:|
| Existing 165°C `steam` | 3000 J /8 FE | Engine: 15 mB/t →120 FE/t |
| 500°C `hot_steam` | 9700 J /388 quanta | Turbine: 30 mB/t →776 FE/t (5.82 MW) |
| 165°C in turbine | actual 3000 J | 30 mB/t →240 FE/t (1.8 MW) |

The expressly retained **legacy boiler 3 water →30 cold steam** adaptation remains unchanged. It is not applied to the new exchanger. Distinct steam fluid types cannot mix or enter the wrong engine; components propagate from water into hot steam. Turbines retain finite owned generator charge, with a shared grid/external output quota and same-tick production lease. There is no general temperature-fluid system or condensation recipe in this milestone.

# Steam power — 0.26.1

Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. Reference: Factorio 2.1.24 normal-quality prototypes ([power production](https://wiki.factorio.com/Power_production), [boiler](https://wiki.factorio.com/Boiler), [offshore pump](https://wiki.factorio.com/Offshore_pump)). One Factorio fluid unit is **10 native mB**, one second is 20 Minecraft ticks. External FluidStacks are never multiplied again.

| Device | Full-load input per MC tick | Full-load output per MC tick | Reference |
|---|---|---|---|
| Offshore Pump | Two valid loaded shoreline water contacts; no FE/fuel | Up to 600 mB water | 1200 Factorio water/s |
| Boiler | 3 mB water + 20 native smelting-fuel ticks | 30 mB steam | 6 water/s, 60 steam/s, 1800 kW |
| Steam Engine | Up to 15 mB steam | Up to 120 FE | 30 steam/s, 900 kW |
| Electric Pump | Already held fluid + 4 FE on an active tick | Directed transfer, nominal ceiling 600 mB/t before pressure | 30 kW; **never a world-water source** |

Thus **one boiler supplies two engines** at full load; one Offshore Pump's water rate supplies **200 boilers**. Water/steam volumes are deliberately unequal. A thermal ledger uses water-equivalent mass (`10 × remaining water + remaining steam + consumed steam`) or energy (`80 × remaining water + 8 × remaining steam + generated FE`), not the sum of fluid volumes.

## Heat and FE adaptation

The common scale is **7.5 kW per FE/t**. A native Minecraft smelting-fuel tick represents **4.5 kJ** of burner heat. Over one Minecraft tick, a 90-kW furnace spends one fuel tick, a 150-kW Burner Mining Drill spends 5/3 fuel ticks, and a 1800-kW boiler spends 20. The drill persists thirds; electrical work persists integer division remainders. Coal's native 1600 ticks therefore represent 7.2 MJ here, rather than pretending native Minecraft coal has Factorio's 4-MJ fuel value. Solid Fuel burns for 4800 native ticks, exactly three coals. Fuel-container remainders are retained and can be unloaded normally.

One coal operates a continuously supplied boiler for 80 MC ticks: **240 mB water → 2400 mB steam → 19200 gross FE**. Fuel cannot directly generate electricity. Coal Generator, its recipe, registration and config have been removed.

The canonical boiling datapack recipe is one complete 1-tick conversion. Fuel, input/output reservation and progress are committed before observers. Blocked steam space, missing water, unloaded parts and disabled logical conditions do not burn heat. Heat already consumed belongs to completed work; remaining heat and all buffers travel in the single portable machine item. Arbitrary slower datapack boiling recipes still retain their own partial progress and serialized recipe identity.

The Steam Engine owns a **16000-FE Minecraft buffer**. It may charge that finite buffer without a network load; when full it consumes no steam. Subsequent generation follows exported demand, using only whole 8-FE / 1-mB quanta. Small deficits retain buffer headroom until a full quantum fits; no fractional FE or steam is discarded. Distribution uses the existing explicit pole graph; external FE extraction accesses the same owned energy. This finite initial charge is an explicit Minecraft capability adaptation, not Factorio's exact zero-storage generator behavior. Temperature is a 165°C fluid-type label, not a dynamic thermodynamic field.

## Physical placement

A Boiler is 3×2, with two water BOTH ports and one forward steam output. The Engine is 3×5, with steam BOTH ports at both ends. Parts delegate only the actual physical port/side/channel to the single controller; water and steam never form one passive segment. Vertical heights and the original models are Minecraft adaptations.

Offshore Pump is a separate **1×2×1**, shore-normal structure. FACING points inland. Local front cell `(0,0,0)` is the land-supported controller and only pipe outlet; the rear identity-only cell hangs above a full vanilla water source, leaving that water unchanged. Unlike 0.26.0, there are no two side-by-side land supports. No support under the water intake, minimum lake size or biome rule is required. Land and source occupy the same Y layer below the machine; other coast elevations require a matching ledge. Floating/diagonal/remote sources, flowing/waterlogged blocks and obstructed over-water volume are invalid. Placement and preview share this rule. Runtime accepts only the structure's identity-bound intake at that position; lost support/source/unloaded parts pause production and retain stored water. Its source counter and last source tick persist, including same-tick reconstruction; capacity 4000 mB, max 600 mB/t, no FE/fuel.

`/factorycraft steamworks` builds a real Offshore → Boiler → **two Engines** demonstration, fueled with declared coal and no injected FE/working fluids. Other normal-world industrial demonstrations use the same real steam plant. Native legacy transport/control tests use an explicitly test-only engine with finite 40000-mB precharge where energy bootstrap is not their invariant. `ReleaseV26GameTests.coldBootstrap` is independent of that shortcut: it tests real shoreline water, heat, two engines, explicit poles, finite ore, automatic ore→plates→gears→chest, all three assemblers and oil-point→refining.

Current validation evidence is in [VALIDATION.md](VALIDATION.md). Pre-0.26 snapshots are archived and are not migrated or overwritten.

# Steam power — 0.26.0

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

Offshore Pump is a separate **2×1×1** structure. Facing points toward its dry output. Both base cells require solid upward support immediately below; immediately behind each support cell must be a full vanilla water source with clear air above. This is the documented two-contact 3D shoreline rule, without an invented minimum lake area. Placement preview and the server share the check. No floating, diagonal or remote source satisfies it. The water blocks remain in the world. If either contact/support becomes invalid or unloaded, production stops; already owned water is retained. The owner saves its cumulative generated-water counter and last source tick, preventing repeated production within the same world tick after reconstruction/portable placement. Maximum generation is also bounded by its 4000-mB buffer space.

`/factorycraft steamworks` builds a real Offshore → Boiler → **two Engines** demonstration, fueled with declared coal and no injected FE/working fluids. Other normal-world industrial demonstrations use the same real steam plant. Native legacy transport/control tests use an explicitly test-only engine with finite 40000-mB precharge where energy bootstrap is not their invariant. `ReleaseV26GameTests.coldBootstrap` is independent of that shortcut: it tests real shoreline water, heat, two engines, explicit poles, finite ore, automatic ore→plates→gears→chest, all three assemblers and oil-point→refining.

Current validation evidence is in [VALIDATION.md](VALIDATION.md). Pre-0.26 snapshots are archived and are not migrated or overwritten.

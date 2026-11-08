# Production progression — 0.10.0

Minecraft 1.21.1 / NeoForge 21.1.252. This extends the first research slice, not the complete Factorio tree. Team binding, old-save policy and definitions are described in [RESEARCH.md](RESEARCH.md).

The new assembling machine 2 forms a centered **3×3×3** structure from one item. It uses the same sole controller, 16000-FE buffer, four input/four output item slots, guarded physical fluid ports and atomic mixed production as the chemical plant. It accepts ordinary assembler recipes under their original IDs/research locks as well as its own fluid recipes. Ordinary recipes keep their existing times/FE; tier speed bonuses/modules are not yet implemented. The first assembler and its saved fields are unchanged. No empty Petroleum/ResearchScope data is added to legacy assemblers.

| Recipe | Machine | Inputs | Result | Work / FE per tick |
|---|---|---|---|---|
| Steel | Electric or industrial furnace | 5 iron plates | 1 steel plate | 320 t / 12 |
| Engine | Assembler 1 or 2 | 1 steel, 1 gear, 2 pipes | 1 engine | 200 t / 24 |
| Assembler 2 | Assembler 1 or 2 | 5 steel, 3 advanced circuits, 5 gears | 1 machine item | 200 t / 24 |
| Chemical science | Assembler 1 or 2 | 2 advanced circuits, 3 engines, 1 sulfur | 2 blue packs | 480 t / 24 |
| Electric engine | Assembler 2 | 1 engine, 2 circuits, 15 mB lubricant | 1 electric engine | 200 t / 30 |
| Processor | Assembler 2 | 20 circuits, 2 advanced circuits, 5 mB sulfuric acid | 1 processor | 200 t / 40 |

Industrial furnace advances steel twice as quickly at the same total FE, following the existing rule. One second is 20 Minecraft ticks. Steel/engine/electronics ratios follow the intended industrial chain; energy and heights are explicit Minecraft balance choices. Electric engines/processors are real outputs but their later robot/module/rocket consumers remain future work.

Six technologies are added to the six existing definitions:

| Technology | Prerequisites | Packs per unit | Units × ticks | Unlock |
|---|---|---|---|---|
| Steel processing | Automation | red + green | 10 × 60 | steel |
| Engines | Steel processing, Logistics | red + green | 20 × 60 | engine |
| Automation 2 | Electronics | red + green | 20 × 60 | assembler 2 production |
| Chemical science | Engines, Batteries | red + green | 30 × 80 | chemical science |
| Advanced electronics 2 | Chemical science, Automation 2 | red + green + blue | 50 × 120 | processors |
| Electric engines | Chemical science, Automation 2 | red + green + blue | 40 × 100 | electric engines |

Labs already have four science slots and now accept the third pack from native recipe definitions. Concurrent labs spend only one set of all three packs for the last shared unit. The existing six technology definitions and canonical partial-work signatures are retained; existing research is not restarted. New gates apply to explicitly owned machines; legacy/admin machines without an owner remain ungated, as in 0.9.0.

`/factorycraft chemicalworks` builds a component-fed, powered **steel/gears/pipes → engines → chemical science → chest** example in an empty loaded 30×8×13 area beside the player. It starts with 6 steel, 6 gears, 12 pipes, 4 advanced circuits, 2 sulfur and 8 coal, produces 6 engines and then 4 blue packs through inserters/belts, and grants no research. Its two production machines bind to the invoking player's effective research team. Complete Engines and Chemical science first; otherwise the line correctly waits on research. This example is not a raw-ore-to-blue factory: the steel/refining/electronics supply chains are separate real recipes and can be connected normally.

EMI/JEI list the native recipes, fluid quantities and requirements. Assembler 2 is also a catalyst for the original dry assembler category, avoiding duplicate synthetic recipe IDs. The built-in machine menu lists both dry and fluid definitions. Use explicit recipe selection when feeding components suitable for several outputs.

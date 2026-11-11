# Real client checks, 0.25.0

Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21 / Jade 15.10.6. Actual runClient, Xvfb display and software OpenGL; screenshots are unaltered game content cropped to its 854×480 window. No rendering/frame-rate or audio-quality benchmark is claimed. EN and RU resources are generated from the same source.

## Recipe selection and production

The isolated FactoryCraft0250-QA world is a named copy of the completed new 0.25 fluid-server world. Original benchmark checkpoint was not changed. Furnace and refinery are each placed from one actual machine item. Separate client JVMs reload this same client save.

* [RU refinery card](screenshots/recipe-ru.png): search by stable ID, localized recipe name, bucket icon, **1000 crude + 500 water → 550 gas + 450 light + 250 heavy**, time/FE and research lock; missing research disables selection. Compact Auto/Select labels fit the minimum tested GUI size.
* [EN refinery card](screenshots/recipe-en.png): same native mB values and lock through the same server-backed protocol. Corrected single background pass keeps the title sharp.
* [RU actual furnace result](screenshots/furnace-ru-result.png): native `minecraft:stone` selected/acknowledged by server; **two actual cobblestone + 6000 FE → two stone + 1200 FE**. This output was never inserted into the destination. Selection/content persist after closing and restarting the client. Parent menu opens through a physical part; Jade displays the same two stone/1200 FE.
* Native server tests additionally cover rejected wrong menu/unknown ID/range/research, Auto reset and same-ID/datapack-revalidation preserving paid work. Optional JEI/EMI and two-real-client interaction remain CI scope, not this smoke claim.

## Oil and terrain

[Oil point with Jade](screenshots/oil-jade-en.png) shows initial/current percentages and normal-speed mB/s, without a transferable geological fluid tank. The pumpjack at (1500,80,1199) is formed by an ordinary item click directly over the point (1500,79,1200). A declared **600-FE** bootstrap, no crude prefill, creates **199 mB** in two cycles at initially 100% (the 1-mB difference is retained as exact geological fractional residue). Its resulting energy is zero. Native tests cover the exact residue and paid-work remainder. The early incorrect camera angle hit the stone behind the point; final placement aims at the real point and native server-game-mode interaction is a required regression.

[Prepared slope](screenshots/slope.png) and [prepared cliff](screenshots/cliff.png) run the actual ResourceVeinFeature against explicit flat/slope/cliff QA terrain. They show finite layers, not a render-only resource patch. The artificial cliff's water column and gold obstacle are deliberate test setup; the generator itself never adds terrain/water or fills caves. The visible waterfall flows after the diagnostic setup.

[Final pump menu](screenshots/pump-en.png) shows actual 199 mB, zero FE, current/initial percentages and normal-speed rate on separate readable lines. Full natural-world chunk throughput, ore retained heap, multiplayer rendering, JEI/EMI/FTB client layouts and full/MIXED factory profiles are not claimed locally.

The additional ordinary world uses seed 3357260786188881509 and the preserved shared client stock/radius config (spacing override 6; shipped fresh default is 16). Native plain/slope/cliff diagnostics and pure profiles use their explicitly declared settings. First original-surface save contains 218 arrays; a separate client JVM preserved all 218 hashes exactly, with 556 snapshots after further exploration. Vanilla empty structure-reference entries initially suppressed patches; the corrected guard keeps actual starts/nonempty references protected and does not retrogenerate old chunks. The next saved scene contains 5129 genuine resource BEs and three surface oil points.

A premature shutdown during another region's generation got stuck in Minecraft ChunkMap.processUnloads/scheduleUnload, observed in a captured JVM thread dump. The owned diagnostic client was stopped with SIGTERM (143); original FactoryCraft0250-Natural-QA is retained unmodified. An explicit copy FactoryCraft0250-Natural-Recovery-QA is used for recovery. The old client options had invalid simulationDistance=3 (Minecraft reset it to 12); recovery uses valid render/simulation distance 5/5. This is diagnostic configuration, not a production energy/worldgen change. This failed shutdown is not called a successful save.

Recovery **PASS**: normal save-and-quit logs all dimensions saved. [Exact read-only comparison](natural-recovery-verify.json) preserves all 5132 original owner NBT records and 556 surface hashes. Newly generated areas raise totals to 78626 owners / 845 snapshots without modifying the retained failed source. [Recovered natural coast](screenshots/natural-coast.png) is a normal generated landscape, not manually reshaped terrain; no ore identity is inferred from distant pixels. The saved owner records are the natural-generation evidence. The final pump GUI and the original factory QA save also close normally.

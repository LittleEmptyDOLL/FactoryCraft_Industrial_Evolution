# Development rules

* Target Java 21 / Minecraft 1.21.1 / NeoForge 21.1.265 only. Confirm APIs against this dependency's sources and https://docs.neoforged.net/docs/1.21.1/.
* Inspect existing behavior before edits. Keep a buildable playable slice; no empty registered feature placeholders.
* Server owns inventories, recipes, mining and energy. Client code belongs under `factorycraft.client` and must never load on a dedicated server.
* Preserve item/energy conservation. Transfer simulation must not mutate source state; extracted inserter contents must have a persistent owner. Never force-load neighbor chunks.
* Persist all authoritative changes. Keep schema versions compatible; document migrations before changing them.
* Graph topology caches invalidate on load/unload/place/break only. No global whole-world scan per tick or async access to Level.
* Add meaningful JUnit or GameTests for changes affecting inventories, movement, recipes and persistence. Run `./gradlew build` and integration tests; record exact results and unresolved limits in VALIDATION.md.
* Update FEATURE_MATRIX.md honestly: implemented / partial / planned; registration alone never counts as implemented.
* Original/properly licensed assets only. Keep scripts reproducible. No extracted Factorio assets.
* Keep the next concrete task in IMPLEMENTATION_PLAN.md. Do not push or publish without user authorization.

# Schema-3 diagnostic archive — restored in 0.21.0

The list below preserves the provenance of all thirty scenarios archived after 0.19.0. Their Java methods now carry required `@GameTest` again: no `@LegacyAssemblyFixture` remains on these cases. Controller coordinates, physical schema-4 fluid faces, native pipe routes, explicit Medium-pole power and paid inserter timing have been adapted. They retain finite stocks and original resource/energy/product ledgers. Unsided maintenance access was replaced with guarded real physical ports, not restored as a bypass.

All thirty passed in the 10 November 2026 0.21.0 native run (`v0210-native7.log`; that entire run still failed one new capacity-test expectation, corrected separately). Latest complete-suite/restart status is recorded in VALIDATION.md. Old schema-3 worlds and failed diagnostic evidence are retained; no failed checkpoint was refilled. This file is an archive of the old list, not a mechanism excluding its tests.

Route repairs include finite ore/components, engine/chemical science, self-powered water/boiler/engine, oil/plastic, separated heavy/light/water risers and sulfur/acid/batteries, labs/research, fluid gates and modules/beacons. Shared diagnostics build actual source-to-output factories; no terminal product or intermediate chemical is injected.

- `AdvancedPetroleumGameTests.finiteOilBecomesBatteriesThroughPoweredPipesAndInserters`
- `AdvancedPetroleumGameTests.refineryNozzlesRemainDistinctInAllFourFacings`
- `AdvancedPetroleumGameTests.thirdNozzleMovesIntoARealPipeAndCachedPortStopsOnUnload`
- `ChemicalScienceGameTests.fluidAssemblyPausesForEnergyFullOutputAndPartUnload`
- `ChemicalScienceGameTests.poweredComponentLineBuildsEnginesThenChemicalScienceWithoutManualTransfers`
- `CircuitGameTests.pumpGateStopsBillingAndResumesFiniteFluidWithoutLoss`
- `FluidCircuitGameTests.adjacentTankChunkLossImmediatelyQuarantinesPhysicalSensor`
- `FluidCircuitGameTests.finiteCoalPoweredTankFeedbackStopsPumpAndConservesEveryMillibucket`
- `FluidCircuitGameTests.fluidReaderMatchesComponentsWithoutMutatingSampleOrTank`
- `FluidCircuitGameTests.malformedFluidSampleIsUnknownAndLegacyDefaultsOmitReaderNbt`
- `FluidCircuitGameTests.missingFluidHandlerClosesEqualityButRealEmptyTankEnablesIt`
- `FluidCircuitGameTests.nativeFluidSampleAndCrossChunkTankRetainExactDiskState`
- `FluidGameTests.aGeneratorPowersTheWaterPipeTankChain`
- `FluidGameTests.bucketsUseTheActualBlockInteractionWithoutLosingContainers`
- `FluidGameTests.fluidEntitiesReconstructAndResumeWithComponents`
- `FluidGameTests.fullOutputStopsPumpWithoutSpendingEnergyUntilDrained`
- `FluidGameTests.pipesMoveFiniteFluidBetweenTanksWithoutLoss`
- `FluidGameTests.pumpRequiresEnergyAndThenPumpsARealWaterSource`
- `FluidGameTests.verticalConnectionsWorkWithoutGravityOrChunkForcing`
- `ModuleGameTests.beaconEffectsRespectDistanceAndTwoBeaconAttenuation`
- `ModuleGameTests.finitePoweredModuleFactoryDeliversExactProductiveAndBeaconResults`
- `PetroleumGameTests.finiteDepositBecomesPlasticThroughPoweredPipesAndInserters`
- `PetroleumGameTests.petroleumStateAndFourChunkIdentityPersistAcrossServerProcesses`
- `PowerAndMultiblockGameTests.insertersAutomateTheMultiblockThroughACasingPort`
- `ResearchGameTests.automatedScienceLineActuallyUnlocksLogisticPacks`
- `ThermalGameTests.cachedThermalPortsFollowRotationAndUnload`
- `ThermalGameTests.finiteBoilerPipeEngineLineConservesEveryUnit`
- `ThermalGameTests.naturalWaterSteamPowersThePumpAndProductionWithoutCoalGenerator`
- `ThermalGameTests.plainBucketsCannotEraseFluidComponents`
- `ThermalGameTests.steamBucketsUseRealNeoForgeContainerCapabilities`

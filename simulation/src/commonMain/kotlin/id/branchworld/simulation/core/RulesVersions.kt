package id.branchworld.simulation.core

// Every rule set and world generator a save can name. Old versions keep replaying exactly, so a value here is never
// reused or changed; a new rule set takes the next number.

/** Rules v1: one rabbit walking the first island square. */
internal const val RulesVersion = 1

/** Rules v2: the same walk over generated terrain. */
internal const val TerrainRulesVersion = 2

/** Rules v3: forage, needs and breeding for rabbits and deer. */
internal const val EcologyRulesVersion = 3

/** Rules v4: weather, seasons and soil moisture. */
internal const val EnvironmentalRulesVersion = 4

/** Rules v5: the first settlements and their economy. */
const val CivilizationRulesVersion = 5

/** Rules v6: rivers, pools and banks from the drainage world generator. */
const val WatershedRulesVersion = 6

/** Rules v7: foxes, hunting and wildlife history. */
internal const val WildlifeRulesVersion = 7

/** Rules v8: local rain, planting and shaping of regions. */
const val ShapingRulesVersion = 8

/** Rules v9: named households, construction, petitions to the Keeper and a truthful village chronicle. */
const val VillageRulesVersion = 9

/**
 * Rules v10: seasonal deer ranges, the crossing between them, settlements that disturb the way and refuges the Keeper
 * can declare. Earlier rules never carry a habitat section, so their canonical bytes and hashes are unchanged.
 */
const val RangeRulesVersion = 10

/**
 * Rules v11: wildfire and regrowth. Fire moves over 2×2-cell tiles in fixed steps, with integer arithmetic and its own
 * random stream, so the wildlife sequence is never shifted by a fire draw. Earlier rules never carry a fire section.
 */
const val FireRulesVersion = 11

/**
 * Rules v12, the living sandbox. The player creates an island, sets down animals and plants, and watches what the
 * shared rules make of them. No story, petition or village: every consequence comes from the creatures themselves.
 */
const val LifeRulesVersion = 12

/** World generator v2: drainage, rivers and landmarks. Version 1 is the original island generator. */
const val WatershedWorldgenVersion = 2

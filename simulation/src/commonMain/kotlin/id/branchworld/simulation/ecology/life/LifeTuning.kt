package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.ecology.environment.DayLength
import id.branchworld.simulation.terrain.Biome

// Tuning for the living sandbox (rules v12). Changing any value changes every v12 history's replay.

internal const val MeadowSeed = 60
internal const val SettleTicks = 240L
internal const val HunterSettleTicks = 400L
internal const val WolfSettleTicks = 1800L
internal const val RabbitSight = 9
internal const val DeerSight = 11
internal const val FoxSight = 9
internal const val WolfSight = 11
internal const val Hearing = 4
internal const val ThumpReach = 6
internal const val AlarmTicks = 30L
internal const val HeardTicks = 20L
internal const val RefugeReach = 10
internal const val ChaseRange = 5
internal const val GiveUpStamina = 150
internal const val SprintFloor = 120
internal const val PackHuntTicks = 90L
internal const val SoloHuntTicks = 70L
internal const val SoloRecovery = 200L
internal const val EscapeTicks = 10L
internal const val ScanEvery = 3L
internal const val PackReach = 10
internal const val PackLimit = 6
internal const val LoneSearch = 22
internal const val Territory = 14
internal const val HungryTerritory = 30
internal const val HowlTicks = 40L
internal const val DigTicks = 40L
internal const val CourtTicks = 12L
internal const val MaleRest = 120L
internal const val WarrenSpacing = 4
internal const val WarrenRange = 10
internal const val FreshGrass = 25
internal const val RabbitMeatWolf = 380
internal const val RabbitMeatFox = 420
internal const val DeerMeat = 3000
internal const val PackRabbitMeat = 900
internal const val BoomFloor = 12
internal const val CrashFloor = 10
internal const val RidgeTwice = 4
internal const val FireAlarm = 3
internal const val FieldBudget = 10
internal const val CachedFields = 128
internal const val RollHeading = 1
internal const val RollRoam = 2
internal const val RollCatch = 3
internal const val RollJink = 4
internal const val RollSide = 5
internal const val RollLitter = 6
internal const val RollSeed = 16
internal val PlantBiomes = setOf(Biome.Meadow, Biome.PineForest, Biome.Wetland)
internal val Headings = listOf(CreatureHeading.PositiveX, CreatureHeading.NegativeX, CreatureHeading.PositiveZ, CreatureHeading.NegativeZ)

        fun packHunger(tick: Long): Int {
            val phase = tick % DayLength
            return if (phase >= 600 || phase < 60) 560 else 360
        }

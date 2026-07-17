package com.uprfvx.romio.constants;

/*----------------------------------------------------------------------------*/
/*--  GlobalConstants.java - constants that are relevant for multiple games --*/
/*--                         in the Pokemon series                          --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobalConstants {

    // bannedForDamagingMove is the ROM-wide HARD ban on moves that must never enter a RANDOM damaging pool,
    // consulted by the species learnset / TM / tutor randomizers (via Move.isGoodDamaging call sites). NOTE:
    // TrainerMovesetRandomizer runs a SEPARATE, trainer-specific ban philosophy (AI_UNUSABLE_MOVES /
    // AI_FLAWED_MOVES) keyed on what the in-game battle AI can actually use. The two lists overlap on several
    // IDs (suckerPunch, focusPunch, futureSight, doomDesire, selfDestruct) but are intentionally NOT merged:
    // this one governs random pool eligibility, those govern AI usability. Change each for its own reason.
    public static final boolean[] bannedRandomMoves = new boolean[827], bannedForDamagingMove = new boolean[827];
    static {
        bannedRandomMoves[MoveIDs.struggle] = true; //  self explanatory

        bannedForDamagingMove[MoveIDs.selfDestruct] = true;
        bannedForDamagingMove[MoveIDs.dreamEater] = true;
        bannedForDamagingMove[MoveIDs.explosion] = true;
        bannedForDamagingMove[MoveIDs.snore] = true;
        bannedForDamagingMove[MoveIDs.falseSwipe] = true;
        bannedForDamagingMove[MoveIDs.futureSight] = true;
        bannedForDamagingMove[MoveIDs.fakeOut] = true;
        bannedForDamagingMove[MoveIDs.focusPunch] = true;
        bannedForDamagingMove[MoveIDs.doomDesire] = true;
        bannedForDamagingMove[MoveIDs.feint] = true;
        bannedForDamagingMove[MoveIDs.lastResort] = true;
        bannedForDamagingMove[MoveIDs.suckerPunch] = true;
        bannedForDamagingMove[MoveIDs.constrict] = true; // overly weak
        bannedForDamagingMove[MoveIDs.rage] = true; // lock-in in gen1
        bannedForDamagingMove[MoveIDs.rollout] = true; // lock-in
        bannedForDamagingMove[MoveIDs.iceBall] = true; // Rollout clone
        bannedForDamagingMove[MoveIDs.synchronoise] = true; // hard to use
        bannedForDamagingMove[MoveIDs.shellTrap] = true; // hard to use
        bannedForDamagingMove[MoveIDs.foulPlay] = true; // doesn't depend on your own attacking stat
        bannedForDamagingMove[MoveIDs.spitUp] = true; // hard to use

        // make sure these cant roll
        bannedForDamagingMove[MoveIDs.sonicBoom] = true;
        bannedForDamagingMove[MoveIDs.dragonRage] = true;
        bannedForDamagingMove[MoveIDs.hornDrill] = true;
        bannedForDamagingMove[MoveIDs.guillotine] = true;
        bannedForDamagingMove[MoveIDs.fissure] = true;
        bannedForDamagingMove[MoveIDs.sheerCold] = true;

    }

    /* @formatter:off */
    public static final List<Integer> normalMultihitMoves = Arrays.asList(
            MoveIDs.armThrust, MoveIDs.barrage, MoveIDs.boneRush, MoveIDs.bulletSeed, MoveIDs.cometPunch, MoveIDs.doubleSlap,
            MoveIDs.furyAttack, MoveIDs.furySwipes, MoveIDs.icicleSpear, MoveIDs.pinMissile, MoveIDs.rockBlast, MoveIDs.spikeCannon,
            MoveIDs.tailSlap, MoveIDs.waterShuriken);

    public static final List<Integer> doubleHitMoves = Arrays.asList(
            MoveIDs.bonemerang, MoveIDs.doubleHit, MoveIDs.doubleIronBash, MoveIDs.doubleKick, MoveIDs.dragonDarts,
            MoveIDs.dualChop, MoveIDs.gearGrind, MoveIDs.twineedle);

    public static final List<Integer> varyingPowerZMoves = Arrays.asList(
            MoveIDs.breakneckBlitzPhysical, MoveIDs.breakneckBlitzSpecial,
            MoveIDs.allOutPummelingPhysical, MoveIDs.allOutPummelingSpecial,
            MoveIDs.supersonicSkystrikePhysical, MoveIDs.supersonicSkystrikeSpecial,
            MoveIDs.acidDownpourPhysical, MoveIDs.acidDownpourSpecial,
            MoveIDs.tectonicRagePhysical, MoveIDs.tectonicRageSpecial,
            MoveIDs.continentalCrushPhysical, MoveIDs.continentalCrushSpecial,
            MoveIDs.savageSpinOutPhysical, MoveIDs.savageSpinOutSpecial,
            MoveIDs.neverEndingNightmarePhysical, MoveIDs.neverEndingNightmareSpecial,
            MoveIDs.corkscrewCrashPhysical, MoveIDs.corkscrewCrashSpecial,
            MoveIDs.infernoOverdrivePhysical, MoveIDs.infernoOverdriveSpecial,
            MoveIDs.hydroVortexPhysical, MoveIDs.hydroVortexSpecial,
            MoveIDs.bloomDoomPhysical, MoveIDs.bloomDoomSpecial,
            MoveIDs.gigavoltHavocPhysical, MoveIDs.gigavoltHavocSpecial,
            MoveIDs.shatteredPsychePhysical, MoveIDs.shatteredPsycheSpecial,
            MoveIDs.subzeroSlammerPhysical, MoveIDs.subzeroSlammerSpecial,
            MoveIDs.devastatingDrakePhysical, MoveIDs.devastatingDrakeSpecial,
            MoveIDs.blackHoleEclipsePhysical, MoveIDs.blackHoleEclipseSpecial,
            MoveIDs.twinkleTacklePhysical, MoveIDs.twinkleTackleSpecial);

    public static final List<Integer> fixedPowerZMoves = Arrays.asList(
            MoveIDs.catastropika, MoveIDs.sinisterArrowRaid, MoveIDs.maliciousMoonsault, MoveIDs.oceanicOperetta,
            MoveIDs.guardianOfAlola, MoveIDs.soulStealing7StarStrike, MoveIDs.stokedSparksurfer, MoveIDs.pulverizingPancake,
            MoveIDs.extremeEvoboost, MoveIDs.genesisSupernova, MoveIDs.tenMillionVoltThunderbolt, MoveIDs.lightThatBurnsTheSky,
            MoveIDs.searingSunrazeSmash, MoveIDs.menacingMoonrazeMaelstrom, MoveIDs.letsSnuggleForever,
            MoveIDs.splinteredStormshards, MoveIDs.clangorousSoulblaze);

    public static final List<Integer> zMoves = Stream.concat(fixedPowerZMoves.stream(),
            varyingPowerZMoves.stream()).collect(Collectors.toList());

    /* @formatter:on */

    public static final List<Integer> xItems = Arrays.asList(ItemIDs.guardSpec, ItemIDs.direHit, ItemIDs.xAttack,
            ItemIDs.xDefense, ItemIDs.xSpeed, ItemIDs.xAccuracy, ItemIDs.xSpAtk, ItemIDs.xSpDef);

    public static final List<Integer> regularShopItems = Arrays.asList(
            ItemIDs.pokeBall, ItemIDs.greatBall, ItemIDs.ultraBall,
            ItemIDs.potion, ItemIDs.superPotion,ItemIDs.hyperPotion,  ItemIDs.maxPotion,
            ItemIDs.antidote, ItemIDs.burnHeal, ItemIDs.iceHeal, ItemIDs.awakening, ItemIDs.paralyzeHeal,
            ItemIDs.fullHeal, ItemIDs.fullRestore, ItemIDs.revive,
            ItemIDs.repel, ItemIDs.superRepel, ItemIDs.maxRepel, ItemIDs.escapeRope
    );

    public static final List<Integer> battleTrappingAbilities = Arrays.asList(AbilityIDs.shadowTag, AbilityIDs.magnetPull,
            AbilityIDs.arenaTrap);

    public static final List<Integer> negativeAbilities = Arrays.asList(
            AbilityIDs.defeatist, AbilityIDs.slowStart, AbilityIDs.truant, AbilityIDs.klutz, AbilityIDs.stall
    );

    public static final List<Integer> badAbilities = Arrays.asList(
            AbilityIDs.minus, AbilityIDs.plus, AbilityIDs.anticipation, AbilityIDs.forewarn, AbilityIDs.frisk,
            AbilityIDs.honeyGather, AbilityIDs.auraBreak, AbilityIDs.receiver, AbilityIDs.powerOfAlchemy
    );

    public static final List<Integer> doubleBattleAbilities = Arrays.asList(
            AbilityIDs.friendGuard, AbilityIDs.healer, AbilityIDs.telepathy, AbilityIDs.symbiosis,
            AbilityIDs.battery
    );

    public static final List<Integer> duplicateAbilities = Arrays.asList(
            AbilityIDs.vitalSpirit, AbilityIDs.whiteSmoke, AbilityIDs.purePower, AbilityIDs.shellArmor, AbilityIDs.airLock,
            AbilityIDs.solidRock, AbilityIDs.ironBarbs, AbilityIDs.turboblaze, AbilityIDs.teravolt, AbilityIDs.emergencyExit,
            AbilityIDs.dazzling, AbilityIDs.tanglingHair, AbilityIDs.powerOfAlchemy, AbilityIDs.fullMetalBody,
            AbilityIDs.shadowShield, AbilityIDs.prismArmor, AbilityIDs.libero, AbilityIDs.stalwart
    );

    public static final List<Integer> noPowerNonStatusMoves = Arrays.asList(
            MoveIDs.guillotine, MoveIDs.hornDrill, MoveIDs.sonicBoom, MoveIDs.lowKick, MoveIDs.counter, MoveIDs.seismicToss,
            MoveIDs.dragonRage, MoveIDs.fissure, MoveIDs.nightShade, MoveIDs.bide, MoveIDs.psywave, MoveIDs.superFang,
            MoveIDs.flail, MoveIDs.revenge, MoveIDs.returnTheMoveNotTheKeyword, MoveIDs.present, MoveIDs.frustration,
            MoveIDs.magnitude, MoveIDs.mirrorCoat, MoveIDs.beatUp, MoveIDs.spitUp, MoveIDs.sheerCold
    );

    public static final List<Integer> cannotBeObsoletedMoves = Arrays.asList(
            MoveIDs.returnTheMoveNotTheKeyword, MoveIDs.frustration, MoveIDs.endeavor, MoveIDs.flail, MoveIDs.reversal,
            MoveIDs.hiddenPower, MoveIDs.storedPower, MoveIDs.smellingSalts, MoveIDs.fling, MoveIDs.powerTrip, MoveIDs.counter,
            MoveIDs.mirrorCoat, MoveIDs.superFang
    );

    public static final List<Integer> cannotObsoleteMoves = Arrays.asList(
            MoveIDs.gearUp, MoveIDs.magneticFlux, MoveIDs.focusPunch, MoveIDs.explosion, MoveIDs.selfDestruct, MoveIDs.geomancy,
            MoveIDs.venomDrench
    );

    public static final List<Integer> doubleBattleMoves = Arrays.asList(
            MoveIDs.followMe, MoveIDs.helpingHand, MoveIDs.ragePowder, MoveIDs.afterYou, MoveIDs.allySwitch, MoveIDs.healPulse,
            MoveIDs.quash, MoveIDs.ionDeluge, MoveIDs.matBlock, MoveIDs.aromaticMist, MoveIDs.electrify, MoveIDs.instruct,
            MoveIDs.spotlight, MoveIDs.decorate, MoveIDs.lifeDew, MoveIDs.coaching
    );

    public static final List<Integer> uselessMoves = Arrays.asList(
            MoveIDs.splash, MoveIDs.celebrate, MoveIDs.holdHands, MoveIDs.teleport, MoveIDs.happyHour
    );

    public static final List<Integer> requiresOtherMove = Arrays.asList(
            MoveIDs.spitUp, MoveIDs.swallow, MoveIDs.dreamEater, MoveIDs.nightmare
    );

    // --- Pokemon Showdown teambuilder move-viability lists -------------------------------------------------
    // Source: smogon/pokemon-showdown-client, play.pokemonshowdown.com/src/battle-dex-search.ts
    //         (BattleMoveSearch.moveIsNotUseless + its GOOD_/BAD_ constants). MIT-licensed, GPL-compatible.
    // These start from how Showdown splits a Pokemon's movepool into "Moves" vs "Usually useless moves", then are
    // curated for this fork's trainer randomizer (weighting moves by whether the ROM battle AI can exploit them),
    // so they diverge from the Showdown originals in places. Gen 8/9 (and Legends/Let's-Go-only) moves from the
    // originals are omitted, as this randomizer's ROM handlers only reach Gen 7, so those IDs never appear.

    // Status moves considered worth running (everything else in the Status category is "usually useless").
    public static final List<Integer> goodStatusMoves = Arrays.asList(
            MoveIDs.acidArmor, MoveIDs.agility, MoveIDs.amnesia, MoveIDs.aquaRing, MoveIDs.aromatherapy,
            MoveIDs.auroraVeil, MoveIDs.autotomize, MoveIDs.banefulBunker, MoveIDs.batonPass, MoveIDs.bulkUp,
            MoveIDs.calmMind, MoveIDs.charm, MoveIDs.clangorousSoul, MoveIDs.coil, MoveIDs.confuseRay,
            MoveIDs.cosmicPower, MoveIDs.cottonGuard, MoveIDs.cottonSpore, MoveIDs.courtChange, MoveIDs.curse,
            MoveIDs.darkVoid, MoveIDs.defendOrder, MoveIDs.defog, MoveIDs.detect, MoveIDs.disable,
            MoveIDs.dragonDance, MoveIDs.encore, MoveIDs.extremeEvoboost, MoveIDs.featherDance, MoveIDs.geomancy,
            MoveIDs.glare, MoveIDs.grassWhistle, MoveIDs.growth, MoveIDs.hail, MoveIDs.haze,
            MoveIDs.healBell, MoveIDs.healingWish, MoveIDs.healOrder, MoveIDs.heartSwap, MoveIDs.honeClaws,
            MoveIDs.hypnosis, MoveIDs.ironDefense, MoveIDs.kingsShield, MoveIDs.leechSeed, MoveIDs.lightScreen,
            MoveIDs.lovelyKiss, MoveIDs.lunarDance, MoveIDs.magicCoat, MoveIDs.maxGuard, MoveIDs.memento,
            MoveIDs.milkDrink, MoveIDs.moonlight, MoveIDs.morningSun, MoveIDs.nastyPlot, MoveIDs.nobleRoar,
            MoveIDs.noRetreat, MoveIDs.obstruct, MoveIDs.painSplit, MoveIDs.partingShot, MoveIDs.poisonPowder,
            MoveIDs.protect, MoveIDs.quiverDance, MoveIDs.rainDance, MoveIDs.recover, MoveIDs.reflect,
            MoveIDs.reflectType, MoveIDs.rest, MoveIDs.roar, MoveIDs.rockPolish, MoveIDs.roost,
            MoveIDs.safeguard, MoveIDs.sandstorm, MoveIDs.scaryFace, MoveIDs.shellSmash, MoveIDs.shiftGear,
            MoveIDs.shoreUp, MoveIDs.sing, MoveIDs.slackOff, MoveIDs.sleepPowder, MoveIDs.sleepTalk,
            MoveIDs.softBoiled, MoveIDs.spikes, MoveIDs.spikyShield, MoveIDs.spore, MoveIDs.stealthRock,
            MoveIDs.stickyWeb, MoveIDs.strengthSap, MoveIDs.stunSpore, MoveIDs.substitute, MoveIDs.sunnyDay,
            MoveIDs.swordsDance, MoveIDs.synthesis, MoveIDs.tailGlow, MoveIDs.tailwind, MoveIDs.taunt,
            MoveIDs.thunderWave, MoveIDs.toxic, MoveIDs.toxicSpikes, MoveIDs.toxicThread, MoveIDs.transform,
            MoveIDs.whirlwind, MoveIDs.willOWisp, MoveIDs.wish, MoveIDs.workUp, MoveIDs.yawn
    );

    // Sub-75 base power attacks that are still worth running (utility/priority/pivot/etc.).
    public static final List<Integer> goodWeakMoves = Arrays.asList(
            MoveIDs.accelerock, MoveIDs.acidSpray, MoveIDs.acrobatics, MoveIDs.aerialAce, MoveIDs.airSlash,
            MoveIDs.ancientPower, MoveIDs.aquaJet, MoveIDs.assurance, MoveIDs.avalanche, MoveIDs.bonemerang,
            MoveIDs.bouncyBubble, MoveIDs.brine, MoveIDs.bugBite, MoveIDs.bulldoze, MoveIDs.bulletPunch,
            MoveIDs.buzzyBuzz, MoveIDs.chargeBeam, MoveIDs.circleThrow, MoveIDs.clearSmog, MoveIDs.covet,
            MoveIDs.doubleIronBash, MoveIDs.dragonBreath, MoveIDs.dragonDarts, MoveIDs.dragonTail, MoveIDs.drainingKiss,
            MoveIDs.drainPunch, MoveIDs.electroweb, MoveIDs.endeavor, MoveIDs.extremeSpeed, MoveIDs.facade,
            MoveIDs.fakeOut, MoveIDs.fellStinger, MoveIDs.fireFang, MoveIDs.flameCharge, MoveIDs.flipTurn,
            MoveIDs.freezeDry, MoveIDs.frustration, MoveIDs.gearGrind, MoveIDs.gigaDrain, MoveIDs.glaciate,
            MoveIDs.grassKnot, MoveIDs.gyroBall, MoveIDs.hex, MoveIDs.hornLeech, MoveIDs.iceFang,
            MoveIDs.iceShard, MoveIDs.icicleSpear, MoveIDs.icyWind, MoveIDs.knockOff, MoveIDs.lowKick,
            MoveIDs.lowSweep, MoveIDs.machPunch, MoveIDs.megaDrain, MoveIDs.mudShot, MoveIDs.naturesMadness,
            MoveIDs.nightShade, MoveIDs.nuzzle, MoveIDs.parabolicCharge, MoveIDs.pikaPapow, MoveIDs.poisonFang,
            MoveIDs.powerTrip, MoveIDs.powerUpPunch, MoveIDs.psychoCut, MoveIDs.pursuit, MoveIDs.quickAttack,
            MoveIDs.rapidSpin, MoveIDs.returnTheMoveNotTheKeyword, MoveIDs.revenge, MoveIDs.rockBlast, MoveIDs.rockSlide,
            MoveIDs.rockTomb, MoveIDs.scorchingSands, MoveIDs.seismicToss, MoveIDs.shadowClaw, MoveIDs.shadowSneak,
            MoveIDs.sizzlySlide, MoveIDs.sludge, MoveIDs.smackDown, MoveIDs.smartStrike, MoveIDs.snarl,
            MoveIDs.storedPower, MoveIDs.stormThrow, MoveIDs.struggleBug, MoveIDs.suckerPunch, MoveIDs.superFang,
            MoveIDs.surgingStrikes, MoveIDs.tailSlap, MoveIDs.thief, MoveIDs.thunderFang, MoveIDs.tripleAxel,
            MoveIDs.tropKick, MoveIDs.uTurn, MoveIDs.vacuumWave, MoveIDs.veeveeVolley, MoveIDs.venoshock,
            MoveIDs.vitalThrow, MoveIDs.voltSwitch, MoveIDs.waterShuriken, MoveIDs.weatherBall
    );

    // Strong attacks (mostly 75+ BP) carrying a real drawback: recoil, self-KO, self-stat-drop, bad accuracy,
    // recharge, multi-turn lock, or a condition the AI can't set up. The trainer randomizer soft-weights these
    // down (badStrongMoveWeight) so a clean move wins all else equal, without ever banning them.
    public static final List<Integer> badStrongMoves = Arrays.asList(
            MoveIDs.beakBlast, MoveIDs.belch, MoveIDs.blastBurn, MoveIDs.blizzard, MoveIDs.blueFlare,
            MoveIDs.boltStrike, MoveIDs.braveBird, MoveIDs.burnUp, MoveIDs.clangingScales, MoveIDs.closeCombat,
            MoveIDs.crossChop, MoveIDs.doubleEdge, MoveIDs.dracoMeteor, MoveIDs.dragonAscent, MoveIDs.dragonRush,
            MoveIDs.dreamEater, MoveIDs.dynamicPunch, MoveIDs.eggBomb, MoveIDs.eruption, MoveIDs.explosion,
            MoveIDs.finalGambit, MoveIDs.fireBlast, MoveIDs.firePledge, MoveIDs.flareBlitz, MoveIDs.fleurCannon,
            MoveIDs.focusBlast, MoveIDs.focusPunch, MoveIDs.frenzyPlant, MoveIDs.gigaImpact, MoveIDs.grassPledge,
            MoveIDs.gunkShot, MoveIDs.hammerArm, MoveIDs.headCharge, MoveIDs.headSmash, MoveIDs.highJumpKick,
            MoveIDs.hurricane, MoveIDs.hydroCannon, MoveIDs.hydroPump, MoveIDs.hyperBeam, MoveIDs.iceHammer,
            MoveIDs.inferno, MoveIDs.ironTail, MoveIDs.jawLock, MoveIDs.jumpKick, MoveIDs.leafStorm,
            MoveIDs.lightOfRuin, MoveIDs.magmaStorm, MoveIDs.megahorn, MoveIDs.megaKick, MoveIDs.megaPunch,
            MoveIDs.mindBlown, MoveIDs.mistyExplosion, MoveIDs.outrage, MoveIDs.overheat, MoveIDs.petalDance,
            MoveIDs.powerWhip, MoveIDs.precipiceBlades, MoveIDs.prismaticLaser, MoveIDs.psychoBoost, MoveIDs.roarOfTime,
            MoveIDs.rockWrecker, MoveIDs.selfDestruct, MoveIDs.shellTrap, MoveIDs.slam, MoveIDs.stoneEdge,
            MoveIDs.submission, MoveIDs.superpower, MoveIDs.synchronoise, MoveIDs.takeDown, MoveIDs.thrash,
            MoveIDs.thunder, MoveIDs.uproar, MoveIDs.vCreate, MoveIDs.voltTackle, MoveIDs.waterPledge,
            MoveIDs.waterSpout, MoveIDs.wildCharge, MoveIDs.woodHammer, MoveIDs.zapCannon
    );

    // Fixed-CONSTANT damage moves deal the same flat amount at every level (Dragon Rage always 40, SonicBoom
    // always 20). Their stored base power is 1, so any power-based ranking mistakes them for trivially weak
    // filler and can hand them to very low-level Pokemon, where the flat damage is a one-shot. Because their
    // output does NOT scale with level (unlike a real 40-BP move, which is weak early and grows), they are only
    // fair once the user is high enough that the flat damage is a reasonable, non-OHKO hit. Maps move number ->
    // flat damage dealt.
    public static final Map<Integer, Integer> fixedConstantDamageMoves = Map.of(
            MoveIDs.dragonRage, 40, MoveIDs.sonicBoom, 20);

    // A fixed-constant damage move is withheld from a Pokemon below level (flatDamage / this factor); above it,
    // the move is treated normally. Higher = the moves unlock earlier. At 1.5: Dragon Rage from ~Lv27, SonicBoom
    // from ~Lv14. Tuning knob shared by the trainer (Better Movesets) and species learnset randomizers.
    public static final double FIXED_CONSTANT_DAMAGE_GATE_FACTOR = 1.5;

    // True if moveNumber deals a fixed flat amount of damage that is disproportionately strong for a Pokemon at
    // this level, so it should be withheld from that Pokemon's move pool. Non-fixed moves always return false.
    public static boolean isFixedConstantDamageTooStrongForLevel(int moveNumber, int level) {
        Integer damage = fixedConstantDamageMoves.get(moveNumber);
        return damage != null && level < damage / FIXED_CONSTANT_DAMAGE_GATE_FACTOR;
    }

    // Because some base formes are non-obvious, they deserve forme suffixes to explain themselves
    public static final Map<Integer, String> baseFormesWithFormeSuffixes = Map.of(
            SpeciesIDs.wormadam, "-Plant",
            SpeciesIDs.basculin, "-Red",
            SpeciesIDs.meowstic, "-M",
            SpeciesIDs.pumpkaboo, "-S",
            SpeciesIDs.gourgeist, "-S",
            SpeciesIDs.oricorio, "-Baile",
            SpeciesIDs.lycanroc, "-Midday"
    );

    public static final int vanillaHappinessToEvolve = 220, easierHappinessToEvolve = 160;

    public static final int MIN_DAMAGING_MOVE_POWER = 50;

    // Accuracy (0-100) at or above which a move is treated as "reliable" - no accuracy-based penalty.
    // Single source of truth shared by Move.isGoodDamaging (a damaging move counts as good if reliable)
    // and the trainer moveset randomizer's accuracyWeight difficulty lever. NOT the same as the accuracy
    // bucketing in MoveDataRandomizer, which regenerates accuracy values and is deliberately left separate.
    public static final int RELIABLE_ACCURACY_THRESHOLD = 90;

    public static final int HIGHEST_POKEMON_GEN = 9;

    // Eevee has 8 potential evolutions
    public static final int LARGEST_NUMBER_OF_SPLIT_EVOS = 8;
}

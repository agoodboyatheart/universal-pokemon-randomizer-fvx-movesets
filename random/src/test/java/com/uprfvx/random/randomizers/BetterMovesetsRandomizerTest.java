package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.Effectiveness;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.TypeTable;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ROM-driven validation for the custom four-slot Better Movesets redesign.
 * <p>
 * Named to match the {@code *Randomizer*Test} filter so it runs under the {@code testROMs} Gradle task (which
 * sets {@code romsPath} and a 4 GB heap). It does NOT extend {@link RandomizerTest}, so it does not trigger the
 * load-every-ROM {@code @BeforeAll}; instead it loads each ROM in {@code roms/} itself, by content.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*BetterMovesets*" }</pre>
 * Runs Better Movesets on each ROM, prints sample movesets, and asserts the redesign's invariants. Skips itself
 * if no loadable ROM is present.
 */
public class BetterMovesetsRandomizerTest {

    // 3DS retail dumps are multi-GB. Decrypted ones ARE loadable: the NCCH handler reads via RandomAccessFile
    // and only pulls the GARC archives it needs into memory, so the ROM's file size never lands in the heap.
    // Cap generously (above the largest retail cart) so decrypted Gen 6/7 ROMs run; genuinely unloadable files
    // still get skipped gracefully by tryLoad returning null.
    private static final long MAX_ROM_BYTES = 6L * 1024 * 1024 * 1024;
    private static final double UBIQUITOUS_RATE = 0.20;
    private static final int SAMPLE_MOVESETS_PER_ROM = 6;

    private static final Set<Integer> RAIN_ABILITIES = Set.of(
            AbilityIDs.swiftSwim, AbilityIDs.rainDish, AbilityIDs.drySkin, AbilityIDs.hydration,
            AbilityIDs.drizzle, AbilityIDs.primordialSea);
    private static final Set<Integer> SUN_ABILITIES = Set.of(
            AbilityIDs.chlorophyll, AbilityIDs.solarPower, AbilityIDs.leafGuard, AbilityIDs.flowerGift,
            AbilityIDs.harvest, AbilityIDs.drought, AbilityIDs.desolateLand);
    private static final Set<Integer> SAND_ABILITIES = Set.of(
            AbilityIDs.sandVeil, AbilityIDs.sandRush, AbilityIDs.sandForce, AbilityIDs.sandStream);
    private static final Set<Integer> HAIL_ABILITIES = Set.of(
            AbilityIDs.snowCloak, AbilityIDs.iceBody, AbilityIDs.slushRush, AbilityIDs.snowWarning);

    // Fixed / proportional-damage moves the redesign now lets compete for the attacking slots (they store no
    // usable base power). Tallied per ROM as evidence they actually surface, not just as wildcard picks.
    private static final Set<Integer> SYNTHETIC_DAMAGE_MOVES = Set.of(
            MoveIDs.seismicToss, MoveIDs.nightShade, MoveIDs.superFang, MoveIDs.naturesMadness, MoveIDs.endeavor);

    // Doubles-only support moves: mirrors TrainerMovesetRandomizer.DOUBLES_ONLY_MOVES. Combined with UPR's
    // doubleBattleMoves, these must never appear on a single-battle mon, but are kept for genuine doubles.
    private static final Set<Integer> DOUBLES_ONLY_MOVES = Set.of(
            MoveIDs.allySwitch, MoveIDs.coaching, MoveIDs.followMe, MoveIDs.healPulse,
            MoveIDs.helpingHand, MoveIDs.ragePowder, MoveIDs.wideGuard, MoveIDs.decorate);

    // Sleep-inducing moves: mirrors TrainerMovesetRandomizer.SLEEP_INDUCING_MOVES. Dream Eater and Nightmare
    // only work on a sleeping target, so one of these must be present for either to be allowed.
    private static final Set<Integer> SLEEP_INDUCING_MOVES = Set.of(
            MoveIDs.hypnosis, MoveIDs.sleepPowder, MoveIDs.spore, MoveIDs.sing,
            MoveIDs.grassWhistle, MoveIDs.lovelyKiss, MoveIDs.darkVoid, MoveIDs.yawn);

    // Pure charge moves: mirrors TrainerMovesetRandomizer.PURE_CHARGE_MOVES. Penalised by practicalValueWeight
    // (a wasted wind-up turn), so they should be rare picks. Recharge moves are identified via Move.isRechargeMove.
    private static final Set<Integer> PURE_CHARGE_MOVES = Set.of(
            MoveIDs.solarBeam, MoveIDs.solarBlade, MoveIDs.skyAttack, MoveIDs.razorWind,
            MoveIDs.skullBash, MoveIDs.freezeShock, MoveIDs.iceBurn, MoveIDs.meteorBeam);

    // Sun-SETTER abilities: mirrors TrainerMovesetRandomizer.SUN_SETTER_ABILITIES (the exemption lever, a strict
    // subset of the broader SUN_ABILITIES benefit set). SolarBeam / Solar Blade skip the charge penalty only when
    // the mon guarantees sun - one of these abilities, or a Sunny Day in the same moveset.
    private static final Set<Integer> SUN_SETTER_ABILITIES = Set.of(AbilityIDs.drought, AbilityIDs.desolateLand);

    // A pure-charge or recharge move must not become a common pick under the practical-value penalty. Generous
    // ceiling (observed pre-change worst case ~3.5%): trips only if a penalty is missing/broken. Soft, sampled.
    private static final double FLAWED_STRONG_MOVE_MAX_RATE = 0.08;

    @Test
    public void inspectBetterMovesets() {
        String romsDir = System.getProperty("romsPath");
        assumeTrue(romsDir != null, "romsPath not set");
        File dir = new File(romsDir);
        assumeTrue(dir.isDirectory(), "roms dir missing: " + romsDir);

        File[] files = dir.listFiles();
        assumeTrue(files != null && files.length > 0, "roms dir empty: " + romsDir);
        List<File> candidates = new ArrayList<>(Arrays.asList(files));
        candidates.sort(Comparator.comparingLong(File::length)); // small/fast ROMs first

        int loaded = 0;
        List<String> failures = new ArrayList<>();
        for (File f : candidates) {
            if (!f.isFile() || f.getName().equalsIgnoreCase("readme.txt")) {
                continue;
            }
            if (f.length() > MAX_ROM_BYTES) {
                System.out.printf("SKIP (%.1f GB, too large - likely encrypted 3DS dump): %s%n",
                        f.length() / 1024.0 / 1024 / 1024, f.getName());
                continue;
            }
            RomHandler rh = tryLoad(f.getAbsolutePath());
            if (rh == null) {
                System.out.println("SKIP (not loadable): " + f.getName());
                continue;
            }
            loaded++;
            failures.addAll(checkOneRom(rh, f.getName()));
        }

        assumeTrue(loaded > 0, "No loadable ROM found in " + romsDir);
        assertTrue(failures.isEmpty(),
                "Better Movesets invariant failures (" + failures.size() + "):\n  " + String.join("\n  ", failures));
        System.out.println("\n=== All invariants held across " + loaded + " ROM(s). ===");
    }

    private List<String> checkOneRom(RomHandler romHandler, String romName) {
        romHandler.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());
        int gen = romHandler.generationOfPokemon();
        System.out.println("\n================ " + romName + " (gen " + gen + ") ================");

        Settings s = new Settings();
        s.setBetterBossTrainerMovesets(true);
        s.setBetterImportantTrainerMovesets(true);
        s.setBetterRegularTrainerMovesets(true);
        new TrainerMovesetRandomizer(romHandler, s, new Random(20260712L)).randomizeTrainerMovesets();

        List<Move> allMoves = romHandler.getMoves();
        Map<Integer, List<MoveLearnt>> movesLearnt = romHandler.getMovesLearnt();
        boolean altFormesCanDiffer = romHandler.altFormesCanHaveDifferentEvolutions();
        List<String> violations = new ArrayList<>();
        Map<Integer, Integer> moveCounts = new HashMap<>();
        int tpCount = 0;
        int printed = 0;
        int resetCount = 0;   // empty-pool mons handed back to the game for their natural level-up moveset
        int naturalCount = 0; // moveless mons in trainers with no custom moves (e.g. first-rival) -> game fills them
        int syntheticDamageUses = 0; // times a fixed/proportional-damage move (Seismic Toss etc.) was picked
        int fixedConstantDamageUses = 0; // times a fixed-CONSTANT-damage move (Dragon Rage, SonicBoom) was picked
        int pureChargeUses = 0;      // times a pure charge move (SolarBeam, Sky Attack, ...) was picked - now penalised
        int rechargeUses = 0;        // times a recharge move (Hyper Beam, Giga Impact) was picked - now penalised
        int solarOnNonSun = 0;       // SolarBeam / Solar Blade picks on a mon that cannot guarantee sun (info only)
        int doublesFormatMons = 0;   // mons in a genuine double/multi battle (ALWAYS multi-battle status)
        int doublesMoveUsesInDoubles = 0; // doubles-support moves kept on those double/multi-battle mons
        int committedDamagingMoves = 0;   // damaging moves on physical/special-committed attackers (raw base stats)
        int committedMatchingMoves = 0;   // of those, how many match the attacker's preferred category
        int realDamagingPicks = 0;        // real (power>1) non-natural damaging picks, for the soft-tier rate checks
        int overTierPicks = 0;            // ... whose power*hitCount exceeds the mon's level power tier
        int lowLevelDamagingPicks = 0;    // sub-L20 (mid-unlock) mons' real non-natural damaging picks
        int lowLevelHighPowerPicks = 0;   // ... of those, high-tier (81+ BP) - should be very rare under the soft gate

        for (Trainer tr : romHandler.getTrainers()) {
            boolean printThis = printed < SAMPLE_MOVESETS_PER_ROM;
            // No battle-style setting is applied here, so a trainer is a double/multi battle exactly when the
            // base game always makes it one. Doubles-support moves may appear only on these mons.
            boolean trainerDoubles = tr.getMultiBattleStatus() == Trainer.MultiBattleStatus.ALWAYS;
            for (TrainerPokemon tp : tr.getPokemon()) {
                tpCount++;
                if (trainerDoubles) {
                    doublesFormatMons++;
                }
                Species pk = tp.getSpecies();
                int ability = safeAbility(romHandler, tp);
                int lean = attackerLean(pk, gen); // 1 physical, -1 special, 0 mixed (raw base stats)
                // Moves the mon can learn naturally by level-up (itself or a pre-evo) at its level. These are
                // level-appropriate by definition and may legitimately exceed the mon's soft power tier.
                Set<Integer> naturalMoves = naturalLevelUpMoves(movesLearnt, altFormesCanDiffer, pk, tp.getLevel());
                Set<Integer> movesThisMon = new HashSet<>();
                int nonZero = 0;
                StringBuilder line = new StringBuilder("  [" + tierOf(tr) + "] L" + tp.getLevel() + " "
                        + pk.getName() + " (" + typeStr(pk) + "): ");

                for (int moveID : tp.getMoves()) {
                    if (moveID == 0) {
                        continue;
                    }
                    nonZero++;
                    moveCounts.merge(moveID, 1, Integer::sum);
                    if (SYNTHETIC_DAMAGE_MOVES.contains(moveID)) {
                        syntheticDamageUses++;
                    }
                    if (PURE_CHARGE_MOVES.contains(moveID)) {
                        pureChargeUses++;
                    }
                    if (allMoves.get(moveID).isRechargeMove) {
                        rechargeUses++;
                    }
                    // For a committed attacker, how often its damaging moves match its preferred category.
                    MoveCategory cat = allMoves.get(moveID).category;
                    if (lean != 0 && (cat == MoveCategory.PHYSICAL || cat == MoveCategory.SPECIAL)) {
                        committedDamagingMoves++;
                        if ((lean == 1 && cat == MoveCategory.PHYSICAL)
                                || (lean == -1 && cat == MoveCategory.SPECIAL)) {
                            committedMatchingMoves++;
                        }
                    }
                    // Ally-support moves must never appear in single battles, but are kept for double/multi battles.
                    if (DOUBLES_ONLY_MOVES.contains(moveID) || GlobalConstants.doubleBattleMoves.contains(moveID)) {
                        if (trainerDoubles) {
                            doublesMoveUsesInDoubles++;
                        } else {
                            violations.add(romName + ": doubles-only move " + allMoves.get(moveID).name
                                    + " on single-battle " + pk.getName());
                        }
                    }
                    String moveName = allMoves.get(moveID).name;
                    line.append(moveName).append(' ');
                    if (!movesThisMon.add(moveID)) {
                        violations.add(romName + ": duplicate move " + moveName + " on " + pk.getName());
                    }
                    String weather = weatherRedundancy(moveID, pk, ability);
                    if (weather != null) {
                        violations.add(romName + ": " + weather);
                    }
                    if (moveID == MoveIDs.trickRoom && pk.getSpeed() > 60) {
                        violations.add(romName + ": Trick Room on fast " + pk.getName() + " (spe " + pk.getSpeed() + ")");
                    }
                    // Water Sport / Mud Sport only halve incoming Fire / Electric damage, so they belong solely on
                    // mons that actually fear that type (2x+ weakness).
                    if (moveID == MoveIDs.waterSport && !isWeakTo(romHandler, pk, Type.FIRE)) {
                        violations.add(romName + ": Water Sport on Fire-resistant " + pk.getName()
                                + " (" + typeStr(pk) + ")");
                    }
                    if (moveID == MoveIDs.mudSport && !isWeakTo(romHandler, pk, Type.ELECTRIC)) {
                        violations.add(romName + ": Mud Sport on Electric-resistant " + pk.getName()
                                + " (" + typeStr(pk) + ")");
                    }
                    // Fixed-constant damage moves (Dragon Rage 40, SonicBoom 20) one-shot low-level mons, so they
                    // must never appear below their level gate (flat damage / gate factor).
                    if (GlobalConstants.fixedConstantDamageMoves.containsKey(moveID)) {
                        fixedConstantDamageUses++;
                        if (GlobalConstants.isFixedConstantDamageTooStrongForLevel(moveID, tp.getLevel())) {
                            violations.add(romName + ": " + moveName + " on under-gated L" + tp.getLevel()
                                    + " " + pk.getName());
                        }
                    }
                    // Real damaging moves follow the shared soft level->power-tier bias (see
                    // Randomizer.levelTierWeight): low (<=60 BP) always, mid (61-80) unlocks ~L20, high (81+) ~L35.
                    // Over-tier picks are allowed but should be rare, so we tally rather than hard-fail here and
                    // assert the aggregate rates below. Moves learnable naturally by level-up are tier-exempt, as
                    // are power<=1 moves (status, gimmicks, fixed-constant / synthetic damage).
                    Move mv = allMoves.get(moveID);
                    if (mv.power > 1 && !naturalMoves.contains(moveID)) {
                        realDamagingPicks++;
                        double effPow = mv.power * mv.hitCount;
                        int level = tp.getLevel();
                        int tierCeiling = level < 20 ? 60 : level < 35 ? 80 : Integer.MAX_VALUE;
                        if (effPow > tierCeiling) {
                            overTierPicks++;
                        }
                        if (level < 20) {
                            lowLevelDamagingPicks++;
                            if (effPow > 80) {
                                lowLevelHighPowerPicks++;
                                if (printThis) {
                                    System.out.println("     [over-tier] " + mv.name + " (" + (int) effPow
                                            + " pow) on L" + level + " " + pk.getName());
                                }
                            }
                        }
                    }
                }
                // Sleep Talk and Snore are useless without Rest, so they may only appear alongside it.
                if (!movesThisMon.contains(MoveIDs.rest)) {
                    if (movesThisMon.contains(MoveIDs.sleepTalk)) {
                        violations.add(romName + ": Sleep Talk without Rest on " + pk.getName());
                    }
                    if (movesThisMon.contains(MoveIDs.snore)) {
                        violations.add(romName + ": Snore without Rest on " + pk.getName());
                    }
                }
                // Spit Up and Swallow consume Stockpile counters, so they may only appear alongside Stockpile.
                if (!movesThisMon.contains(MoveIDs.stockpile)) {
                    if (movesThisMon.contains(MoveIDs.spitUp)) {
                        violations.add(romName + ": Spit Up without Stockpile on " + pk.getName());
                    }
                    if (movesThisMon.contains(MoveIDs.swallow)) {
                        violations.add(romName + ": Swallow without Stockpile on " + pk.getName());
                    }
                }
                // Dream Eater and Nightmare only work on a sleeping target, so they need a sleep-inducing move.
                if (Collections.disjoint(movesThisMon, SLEEP_INDUCING_MOVES)) {
                    if (movesThisMon.contains(MoveIDs.dreamEater)) {
                        violations.add(romName + ": Dream Eater without a sleep move on " + pk.getName());
                    }
                    if (movesThisMon.contains(MoveIDs.nightmare)) {
                        violations.add(romName + ": Nightmare without a sleep move on " + pk.getName());
                    }
                }
                // SolarBeam / Solar Blade skip the charge penalty only on a sun-guaranteed mon (sun-setter ability
                // or a Sunny Day it also carries). Count picks that lack both - not a violation (the penalty just
                // makes them rare wildcards, it does not ban them), but tracked as evidence the exemption is scoped.
                if (movesThisMon.contains(MoveIDs.solarBeam) || movesThisMon.contains(MoveIDs.solarBlade)) {
                    boolean sun = SUN_SETTER_ABILITIES.contains(ability) || movesThisMon.contains(MoveIDs.sunnyDay);
                    if (!sun) {
                        solarOnNonSun++;
                    }
                }
                if (nonZero == 0) {
                    // A mon with empty move slots is only genuinely moveless in-game when its trainer writes
                    // custom moves yet this mon is neither reset nor given any. When the trainer has no custom
                    // moves at all - e.g. the intentionally-excluded first-rival battle - the game supplies the
                    // natural level-up moveset, so that is expected, not a regression.
                    if (tp.isResetMoves()) {
                        resetCount++;
                    } else if (tr.pokemonHaveCustomMoves()) {
                        violations.add(romName + ": moveless " + pk.getName() + " in a custom-move trainer (no moves, no reset)");
                    } else {
                        naturalCount++;
                    }
                }
                if (printThis) {
                    System.out.println(line);
                }
            }
            if (printThis && !tr.getPokemon().isEmpty()) {
                printed++;
            }
        }

        int finalTp = tpCount;
        for (Map.Entry<Integer, Integer> e : moveCounts.entrySet()) {
            double rate = (double) e.getValue() / (double) finalTp;
            if (rate >= UBIQUITOUS_RATE) {
                violations.add(String.format("%s: '%s' is ubiquitous (%.1f%% of mons)",
                        romName, allMoves.get(e.getKey()).name, rate * 100));
            }
        }
        System.out.printf("  -> %d trainer Pokemon, %d distinct moves, %d violation(s)%s%s%s%s%s%n",
                tpCount, moveCounts.size(), violations.size(),
                resetCount > 0 ? " (" + resetCount + " empty-pool mon(s) reset to natural moveset)" : "",
                naturalCount > 0 ? " (" + naturalCount + " excluded/no-custom-move mon(s) -> natural moveset)" : "",
                syntheticDamageUses > 0 ? " (" + syntheticDamageUses + " fixed/%-damage move pick(s))" : "",
                fixedConstantDamageUses > 0 ? " (" + fixedConstantDamageUses + " fixed-constant-damage pick(s), all gated)" : "",
                doublesFormatMons > 0
                        ? " (" + doublesMoveUsesInDoubles + " doubles-support pick(s) across "
                                + doublesFormatMons + " double/multi-battle mon(s))"
                        : "");
        if (committedDamagingMoves > 0) {
            System.out.printf("     committed attackers: %.0f%% of damaging moves match preferred category (%d/%d)%n",
                    100.0 * committedMatchingMoves / committedDamagingMoves,
                    committedMatchingMoves, committedDamagingMoves);
        }
        // Practical-value discount evidence: pure charge / recharge moves should be uncommon picks now.
        System.out.printf("     practical-value: %d pure-charge pick(s) (%d SolarBeam/Solar Blade on non-sun mons), "
                        + "%d recharge pick(s), across %d Pokemon%n",
                pureChargeUses, solarOnNonSun, rechargeUses, tpCount);
        // Soft guarantee: no single pure-charge or recharge move should be a common pick under the penalty. This
        // is looser than a hard per-appearance ban (the moves stay legal rare surprises) but catches a missing or
        // broken practical-value weight, which would let a flawed strong move flood the slots again.
        if (tpCount > 100) {
            for (Map.Entry<Integer, Integer> e : moveCounts.entrySet()) {
                int id = e.getKey();
                boolean flawed = PURE_CHARGE_MOVES.contains(id) || allMoves.get(id).isRechargeMove;
                if (!flawed) {
                    continue;
                }
                double rate = (double) e.getValue() / tpCount;
                if (rate > FLAWED_STRONG_MOVE_MAX_RATE) {
                    violations.add(String.format("%s: flawed strong move '%s' too common (%.1f%% of mons > %.0f%% cap)"
                                    + " - practical-value penalty not biasing",
                            romName, allMoves.get(id).name, rate * 100, FLAWED_STRONG_MOVE_MAX_RATE * 100));
                }
            }
        }
        // Soft level->power-tier guarantees (see Randomizer.levelTierWeight). Over-tier picks are allowed but
        // must stay a minority - a broken gate would favour the strongest moves and push most picks over-tier.
        // A sub-L20 mon carrying a high-power (81+) move sits ~15 levels below that tier's unlock (weight ~3%),
        // so it should be genuinely rare. Thresholds are generous to avoid flaking on normal soft spillover.
        if (realDamagingPicks > 20) {
            double overRate = (double) overTierPicks / realDamagingPicks;
            System.out.printf("     soft tier: %.0f%% of real damaging picks over-tier (%d/%d); "
                            + "%d/%d sub-L20 picks high-power%n",
                    overRate * 100, overTierPicks, realDamagingPicks,
                    lowLevelHighPowerPicks, lowLevelDamagingPicks);
            if (overRate > 0.40) {
                violations.add(String.format(
                        "%s: %.0f%% of real damaging picks exceed their level power tier (%d/%d) - tier gate not biasing",
                        romName, overRate * 100, overTierPicks, realDamagingPicks));
            }
        }
        if (lowLevelDamagingPicks > 20) {
            double lowHighRate = (double) lowLevelHighPowerPicks / lowLevelDamagingPicks;
            if (lowHighRate > 0.15) {
                violations.add(String.format(
                        "%s: sub-L20 mons carry high-power (81+) moves too often (%d/%d = %.0f%%)",
                        romName, lowLevelHighPowerPicks, lowLevelDamagingPicks, lowHighRate * 100));
            }
        }
        return violations;
    }

    // Returns a violation description if this weather move is redundant on the Pokemon, else null.
    private String weatherRedundancy(int moveID, Species pk, int ability) {
        boolean ok = switch (moveID) {
            case MoveIDs.rainDance -> hasType(pk, Type.WATER) || RAIN_ABILITIES.contains(ability);
            case MoveIDs.sunnyDay -> hasType(pk, Type.FIRE) || SUN_ABILITIES.contains(ability);
            case MoveIDs.sandstorm -> hasType(pk, Type.ROCK) || hasType(pk, Type.GROUND)
                    || hasType(pk, Type.STEEL) || SAND_ABILITIES.contains(ability);
            case MoveIDs.hail -> hasType(pk, Type.ICE) || HAIL_ABILITIES.contains(ability);
            default -> true;
        };
        if (ok) {
            return null;
        }
        String move = switch (moveID) {
            case MoveIDs.rainDance -> "Rain Dance";
            case MoveIDs.sunnyDay -> "Sunny Day";
            case MoveIDs.sandstorm -> "Sandstorm";
            default -> "Hail";
        };
        return "redundant " + move + " on " + pk.getName() + " (" + typeStr(pk) + "), ability " + ability;
    }

    private static int safeAbility(RomHandler romHandler, TrainerPokemon tp) {
        try {
            return romHandler.abilitiesPerSpecies() != 0 ? romHandler.getAbilityForTrainerPokemon(tp) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean hasType(Species pk, Type type) {
        return pk.getPrimaryType(false) == type || pk.getSecondaryType(false) == type;
    }

    // Whether the Pokemon takes at least 2x (double/quadruple) damage from the given attacking type.
    private static boolean isWeakTo(RomHandler romHandler, Species pk, Type attackType) {
        TypeTable tt = romHandler.getTypeTable();
        if (!tt.getTypes().contains(attackType)) {
            return false;
        }
        Effectiveness eff = tt.against(pk.getPrimaryType(false), pk.getSecondaryType(false)).get(attackType);
        return eff == Effectiveness.DOUBLE || eff == Effectiveness.QUADRUPLE;
    }

    // The moves a Pokemon can learn by level-up at the given level - from its own learnset and every
    // pre-evolution's - mirroring the level-up and pre-evo branches of
    // TrainerMovesetRandomizer.getMoveSelectionPoolAtLevel. These are level-appropriate by definition
    // (the game only teaches them at that level), so they are exempt from the soft power tier.
    private static Set<Integer> naturalLevelUpMoves(Map<Integer, List<MoveLearnt>> movesLearnt,
                                                    boolean altFormesCanDiffer, Species pk, int level) {
        Set<Integer> natural = new HashSet<>();
        List<MoveLearnt> own = movesLearnt.get(pk.getNumber());
        if (own != null) {
            for (MoveLearnt ml : own) {
                if ((ml.level <= level && ml.level != 0) || (ml.level == 0 && level >= 30)) {
                    natural.add(ml.move);
                }
            }
        }
        Species preEvo = altFormesCanDiffer ? pk : pk.getBaseForme();
        while (!preEvo.getEvolutionsTo().isEmpty()) {
            preEvo = preEvo.getEvolutionsTo().get(0).getFrom();
            List<MoveLearnt> pre = movesLearnt.get(preEvo.getNumber());
            if (pre != null) {
                for (MoveLearnt ml : pre) {
                    if (ml.level <= level) {
                        natural.add(ml.move);
                    }
                }
            }
        }
        return natural;
    }

    // Physical (1) / special (-1) / mixed (0) lean from raw base stats, mirroring the randomizer's 1.15 ratio
    // threshold. Approximate: ignores ability adjustments (Huge Power, etc.), so a few mons may differ.
    private static int attackerLean(Species pk, int gen) {
        int spatk = gen == 1 ? pk.getSpecial() : pk.getSpatk();
        if (spatk <= 0) {
            return 1;
        }
        double ratio = (double) pk.getAttack() / spatk;
        if (ratio >= 1.15) {
            return 1;
        }
        if (ratio <= 1.0 / 1.15) {
            return -1;
        }
        return 0;
    }

    private static String typeStr(Species pk) {
        Type t2 = pk.getSecondaryType(false);
        return pk.getPrimaryType(false) + (t2 == null ? "" : "/" + t2);
    }

    private static String tierOf(Trainer tr) {
        if (tr.isBoss()) {
            return "BOSS";
        }
        if (tr.isImportant()) {
            return "IMP ";
        }
        return "REG ";
    }

    private RomHandler tryLoad(String path) {
        for (Generation gen : new HashSet<>(Generation.GAME_TO_GENERATION.values())) {
            try {
                RomHandler.Factory factory = gen.createFactory();
                if (factory.isLoadable(path)) {
                    RomHandler rh = factory.create();
                    rh.loadRom(path);
                    return rh;
                }
            } catch (Throwable e) {
                System.out.println("  [gen " + gen.getNumber() + " load error for " + new File(path).getName()
                        + "]: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return null;
    }
}

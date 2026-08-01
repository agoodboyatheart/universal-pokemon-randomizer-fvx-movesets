package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.constants.SpeciesIDs;
import com.uprfvx.romio.gamedata.Effectiveness;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.StatChangeMoveType;
import com.uprfvx.romio.gamedata.StatChangeType;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.TypeTable;
import com.uprfvx.romio.gamedata.basestats.Gen1BaseStats;
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
import java.util.function.BiFunction;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

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
    // Gen 1 has a tiny shared movepool (few TMs, each learnable by much of the dex) and no physical/special split,
    // so under Batch 5's 100%-pool availability a handful of universal moves (Bubblebeam, Swift, Rest, Substitute,
    // Toxic) legitimately land on ~1 in 4-5 mons - observed max ~24.5%. This mirrors the existing gen-1 carve-outs
    // for duplicate-attack-type and team-repeat; gens 2+ still hold under the base 20% ceiling.
    private static final double UBIQUITOUS_RATE_GEN1 = 0.28;
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

    // Delayed typeless hits: mirrors TrainerMovesetRandomizer.DELAYED_TYPELESS_STAB_MOVES. Barred from the STAB
    // slot along with the synthetic-damage moves above (none earns STAB or type effectiveness there).
    private static final Set<Integer> DELAYED_TYPELESS_STAB_MOVES = Set.of(MoveIDs.futureSight, MoveIDs.doomDesire);

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

    // Sun-dependent nukes: mirrors TrainerMovesetRandomizer.SUN_DEPENDENT_MOVES. SolarBeam / Solar Blade skip
    // their charge turn only under sun, so they are gated to a sun source (a Sunny Day move in the set or a
    // sun-setting ability). Conversely, a Sunny Day carried by a non-Fire / non-sun-ability mon is only allowed
    // BECAUSE it enables one of these - so it must co-occur with one, never appear alone off such a mon.
    private static final Set<Integer> SUN_DEPENDENT_MOVES = Set.of(MoveIDs.solarBeam, MoveIDs.solarBlade);

    // A pure-charge or recharge move must not become a common pick under the practical-value penalty. Generous
    // ceiling (observed pre-change worst case ~3.5%): trips only if a penalty is missing/broken. Soft, sampled.
    private static final double FLAWED_STRONG_MOVE_MAX_RATE = 0.08;

    // Batch 7 - AI move-usability filtering. Mirrors TrainerMovesetRandomizer.AI_UNUSABLE_MOVES: moves the ROM
    // battle AI structurally cannot use (prediction / multi-turn plans it never runs), stripped from the trainer
    // pool up front. NB feint = 364 (Protect-breaker), NOT feintAttack. These must NEVER appear on a buffed mon.
    private static final Set<Integer> AI_UNUSABLE_MOVES = Set.of(
            MoveIDs.feint, MoveIDs.suckerPunch, MoveIDs.counter, MoveIDs.mirrorCoat, MoveIDs.metalBurst,
            MoveIDs.bide, MoveIDs.fling, MoveIDs.naturalGift, MoveIDs.lastResort);

    // Mirrors TrainerMovesetRandomizer.AI_FLAWED_MOVES: the AI can fire these but usually to little effect, so they
    // are heavily weight-penalised (never banned). Should stay rare, checked against a generous soft ceiling.
    private static final Set<Integer> AI_FLAWED_MOVES = Set.of(
            MoveIDs.explosion, MoveIDs.selfDestruct, MoveIDs.trick, MoveIDs.switcheroo,
            MoveIDs.perishSong, MoveIDs.bellyDrum, MoveIDs.destinyBond, MoveIDs.endeavor,
            MoveIDs.doomDesire, MoveIDs.futureSight, MoveIDs.present, MoveIDs.rage,
            MoveIDs.beatUp, MoveIDs.punishment, MoveIDs.finalGambit);

    // An AI-flawed move is a heavy-penalty rare surprise, not a staple. Generous ceiling (trips only if the
    // aiUsabilityWeight penalty is missing/broken). Soft, sampled; gated on a decent sample size like the others.
    // Gen 1's tiny movepools concentrate near-ubiquitous flawed moves (Rage ~11%) even under the penalty, so it
    // gets a higher cap like the other gen-1 carve-outs below.
    private static final double AI_FLAWED_MOVE_MAX_RATE = 0.05;
    private static final double AI_FLAWED_MOVE_MAX_RATE_GEN1 = 0.13;

    // OHKO moves: mirrors TrainerMovesetRandomizer.OHKO_MOVES. Soft-penalised (ohkoWeight), not banned, so they
    // stay a rare surprise - a 30%-accuracy instant KO should never be a staple. Checked against a soft ceiling
    // like the AI-flawed block; trips only if ohkoWeight is missing/broken.
    private static final Set<Integer> OHKO_MOVES = Set.of(
            MoveIDs.fissure, MoveIDs.hornDrill, MoveIDs.guillotine, MoveIDs.sheerCold);
    private static final double OHKO_MOVE_MAX_RATE = 0.05;

    // No-duplicate-attacking-type guard: a mon should almost never carry two attacking moves of the same type.
    // The guard is best-effort (it relaxes when avoiding a duplicate would leave a slot unfillable, and the
    // "<=4 distinct candidates" shortcut takes moves unguarded), so genuinely mono-type / tiny movepools can
    // still produce one. Ceilings are gen-aware: modern gens sit near-zero (observed <=1.9%), but gen 1 has no
    // physical/special split and much smaller movepools, so the fallback legitimately produces more overlap
    // (observed ~10.8% on Red/Blue). Generous margins: they trip only if the guard is missing/broken.
    private static final double DUPLICATE_ATTACK_TYPE_MAX_RATE = 0.05;
    private static final double DUPLICATE_ATTACK_TYPE_MAX_RATE_GEN1 = 0.18;

    // Team-level authoring (Issue D): the per-trainer soft penalty should keep a single trainer from stacking the
    // same NON-STAB move (coverage / status / wildcard territory) across several teammates. STAB is deliberately
    // exempt (a mono-type themed team legitimately shares its STAB), so we measure only non-STAB repetition: for
    // each trainer, a move carried as non-STAB by k of its mons contributes k-1 "repeat" slots. Tallied as an
    // aggregate rate over all non-STAB slots and soft-asserted under a generous cap - it trips only if the team
    // penalty is missing/broken (which would let the coverage/status pickers hand every similar mon the same move).
    // Caps are gen-aware, like the dup-attack-type guard: gen 1's tiny movepools (and no phys/special split) force
    // more unavoidable repetition, so the penalty has less headroom there. Measured (penalty on vs off) over the
    // local ROM set: gen 1 ~12.9% on / ~17.1% off; gen 2 ~9.2% / ~13.0%; gen 3+ <=5.9% / up to ~11.2%. The caps sit
    // above the penalty-on figures with margin (deterministic, fixed seed) but below the penalty-off ones.
    private static final double TEAM_NONSTAB_REPEAT_MAX_RATE = 0.12;
    private static final double TEAM_NONSTAB_REPEAT_MAX_RATE_GEN1 = 0.17;

    // Accuracy-as-difficulty lever (Issue E): mirrors TrainerMovesetRandomizer.RELIABLE_ACCURACY. A damaging move
    // is "low accuracy" when its hitratio is a real value below this (never-miss moves store hitratio ==
    // getPerfectAccuracy() and are exempt). The boss/important CURATED attacking slots (STAB, coverage) softly demote
    // such moves. NOTE: this is measured and PRINTED as evidence, not hard-asserted. A cross-tier comparison is not
    // a valid test of the lever: boss/important mons carry MORE low-accuracy attacks than regulars regardless (their
    // higher levels and fuller movepools give them the strong-but-inaccurate nukes - Blizzard, Focus Blast, Hydro
    // Pump, Stone Edge - that regulars' weaker, more accurate pools never reach). The lever's real effect is WITHIN
    // the boss tier: penalty-on vs penalty-off (measured over this ROM set, seed 20260712) lowers the boss low-acc
    // rate in every game - e.g. gen 2 Gold 24.2%->21.5%, gen 3 Emerald 14.6%->11.6%, gen 4 HG/SS 11.3%->9.3%, gen 7
    // Ultra Sun 8.5%->6.3%. The reduction (~0.3-3pp, a soft Moderate demotion on ~2 of 4 boss slots) is far smaller
    // than the cross-gen spread (4%-24%), so no absolute or cross-tier cap can robustly tell working from broken -
    // hence a print, not an assert. The print still surfaces a gross regression during review.
    private static final double RELIABLE_ACCURACY = 90.0;

    // Boss offensive breadth (Batch 11): how far a boss/important tier's average attacking-move count may trail the
    // regular tier's before it signals the boss-wildcard damaging lean is missing/broken. The reserved status slot
    // gives regulars a small structural edge (~0.1); this tolerance covers it with margin, but a wildcard reverting
    // to a flat ~39%-damaging draw (a second non-damaging move stacking on the status one) trails by ~0.3 and trips.
    private static final double BOSS_BREADTH_TOLERANCE = 0.20;

    // STAB-slot exclusion of type-independent-damage moves: fixed/proportional moves (Seismic Toss, Night Shade,
    // ...) and delayed typeless hits (Future Sight, Doom Desire) earn no STAB or type effectiveness, so they are
    // barred from the guaranteed STAB slot (slot 0). They stay legitimate elsewhere - Future Sight is a real
    // 120-power Psychic coverage move in gen 5+ - so we inspect ONLY slot 0 and only when it type-matches (a
    // would-be STAB). Post-fix it reaches slot 0 only via the rare pickBestDamaging fallback or a tiny (<=4) pool,
    // so the rate is near-zero unless the STAB filter breaks (which would flood every mon's STAB slot with these).
    private static final double STAB_SLOT_SYNTHETIC_MAX_RATE = 0.01;

    // Exclusive-category stat-boost gate: a single-stat status booster (Swords Dance, Nasty Plot, Curse-for-non-
    // Ghost, ...) is wasted when the mon also carries an opposite-category attack, so the status/wildcard gate blocks
    // it against the already-picked attacks. A soft ceiling, not a hard zero: a later wildcard can still draw a
    // contradicting attack after the booster was slotted, so a few slip through - but the gate keeps them rare, and
    // the rate would jump to the unfiltered baseline if it were removed.
    private static final double MISMATCHED_BOOSTER_MAX_RATE = 0.05;

    // Zero-STAB-anywhere guard (Batch 12 TODO 1 fix): a pure single-type mon has no secondary type to fall back
    // on, so if enforceEnablerDependencies has to backfill its vacated STAB slot (e.g. Dream Eater with no sleep
    // move present) a type-blind backfill could leave it with no same-type attack in ANY of its 4 slots. Ditto
    // (Transform only) and Wobbuffet (Counter/Mirror Coat, both AI_UNUSABLE_MOVES and stripped up front) are the
    // two authentic vanilla cases where the game itself gives a mon zero attacking moves of its own type.
    private static final Set<Integer> PURE_SINGLE_TYPE_NO_STAB_EXCEPTIONS = Set.of(
            SpeciesIDs.ditto, SpeciesIDs.wobbuffet);

    // Soft ceiling, not a hard zero: the type-aware backfill still falls back to any-type when a mon has genuinely
    // no other own-type damaging move anywhere in its pool (natural/TM/HM/tutor/egg) - a keep-best-style guard
    // mirroring the STAB floor's never-empty pattern. Gen-aware like the other soft-ceiling guards above: Gen 1-2
    // have far shallower TM/tutor support (Gen 1 has no move tutors at all; both have few TMs and only 1-2 HMs
    // outside Water/Flying), so more species genuinely have no same-type attack at all - observed up to 17.8%
    // (Gen 1) / 10.0% (Gen 2) even after wiring HM moves into the pool (see getMoveSelectionPoolAtLevel). Gen 3+
    // have much deeper TM/tutor/egg-move coverage, observed <=6.6%. Both trip only on a further regression.
    private static final double ZERO_STAB_ANYWHERE_MAX_RATE = 0.10;
    private static final double ZERO_STAB_ANYWHERE_MAX_RATE_GEN1_2 = 0.20;

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

    /**
     * Smeargle special-case: because its signature move Sketch can copy ANY move in the game, a trainer Smeargle
     * should draw from the full usable move universe (then pass through the same slot logic as every other mon) -
     * but ONLY when species movesets are UNCHANGED, and never under a randomised moveset mode.
     * <p>
     * Trainer Smeargles are rare in vanilla ROMs, so rather than hope one exists we inject Smeargle into a boss
     * trainer mon (at a high level, to unlock every power tier) and check both paths deterministically on a single
     * representative gen 2+ ROM (the branch is generation-independent). See {@code buildSmeargleSketchPool}.
     */
    @Test
    public void smeargleGetsFullSketchPoolOnlyWhenMovesetsUnchanged() {
        String romsDir = System.getProperty("romsPath");
        assumeTrue(romsDir != null, "romsPath not set");
        File dir = new File(romsDir);
        assumeTrue(dir.isDirectory(), "roms dir missing: " + romsDir);
        File[] files = dir.listFiles();
        assumeTrue(files != null && files.length > 0, "roms dir empty: " + romsDir);
        List<File> candidates = new ArrayList<>(Arrays.asList(files));
        candidates.sort(Comparator.comparingLong(File::length)); // smallest/fastest first

        // Load the smallest ROM that actually has Smeargle (gen 2 onward).
        RomHandler romHandler = null;
        Species smeargle = null;
        for (File f : candidates) {
            if (!f.isFile() || f.getName().equalsIgnoreCase("readme.txt") || f.length() > MAX_ROM_BYTES) {
                continue;
            }
            RomHandler rh = tryLoad(f.getAbsolutePath());
            if (rh == null) {
                continue;
            }
            for (Species sp : rh.getSpecies()) {
                if (sp != null && sp.getNumber() == SpeciesIDs.smeargle) {
                    smeargle = sp;
                    break;
                }
            }
            if (smeargle != null) {
                romHandler = rh;
                System.out.println("\n=== Smeargle sketch-pool test using: " + f.getName() + " ===");
                break;
            }
        }
        assumeTrue(romHandler != null, "No Smeargle-capable (gen 2+) ROM found in " + romsDir);

        final RomHandler rom = romHandler; // effectively-final alias for use in lambdas below
        rom.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());

        // Turn a boss/important trainer mon into a level-50 Smeargle. Boss tier gets the full STAB + coverage +
        // status + wildcard treatment, and L50 unlocks every power tier, so the whole sketch pool is exercised.
        TrainerPokemon victim = null;
        for (Trainer tr : rom.getTrainers()) {
            if ((tr.isBoss() || tr.isImportant()) && !tr.shouldNotGetBuffs() && !tr.getPokemon().isEmpty()) {
                victim = tr.getPokemon().get(0);
                break;
            }
        }
        assumeTrue(victim != null, "No eligible boss/important trainer mon to inject Smeargle into");
        victim.getSpeciesHolder().setSpecies(smeargle);
        victim.setLevel(50);

        List<String> failures = new ArrayList<>();

        // ON: movesets UNCHANGED -> Smeargle draws from the full usable move universe.
        Settings on = new Settings();
        on.setBetterBossTrainerMovesets(true);
        on.setBetterImportantTrainerMovesets(true);
        on.setMovesetsMod(Settings.MovesetsMod.UNCHANGED); // default, set explicitly for clarity
        new TrainerMovesetRandomizer(rom, on, new Random(20260714L)).randomizeTrainerMovesets();
        List<Integer> onMoves = nonZeroMoves(victim.getMoves());
        boolean onReset = victim.isResetMoves();
        System.out.println("  UNCHANGED -> " + moveNames(rom, onMoves) + (onReset ? " [reset]" : ""));

        long onDamaging = onMoves.stream().filter(id -> rom.getMoves().get(id).power > 1).count();
        if (onReset || onMoves.isEmpty()) {
            failures.add("Smeargle got no custom moveset under UNCHANGED (reset=" + onReset + ")");
        }
        if (onMoves.size() > 4) {
            failures.add("Smeargle has more than 4 moves under UNCHANGED: " + onMoves.size());
        }
        if (new HashSet<>(onMoves).size() != onMoves.size()) {
            failures.add("Smeargle has duplicate moves under UNCHANGED: " + moveNames(rom, onMoves));
        }
        // A boss draws a damaging STAB + coverage move, and vanilla Smeargle learns NO damaging move by level-up
        // (only Sketch). So >=2 damaging moves here proves the expanded pool was actually used.
        if (onDamaging < 2) {
            failures.add("Smeargle drew only " + onDamaging + " damaging move(s) under UNCHANGED - "
                    + "expanded pool not applied: " + moveNames(rom, onMoves));
        }

        // OFF: a randomised moveset mode -> the special branch must be gated off, so Smeargle falls back to its
        // ordinary (vanilla, here) learnset-derived pool. With the same seed and mon, the result must differ from
        // the full-universe draw above (the tiny vanilla pool cannot reproduce it).
        Settings off = new Settings();
        off.setBetterBossTrainerMovesets(true);
        off.setBetterImportantTrainerMovesets(true);
        off.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        new TrainerMovesetRandomizer(rom, off, new Random(20260714L)).randomizeTrainerMovesets();
        List<Integer> offMoves = nonZeroMoves(victim.getMoves());
        System.out.println("  COMPLETELY_RANDOM -> " + moveNames(rom, offMoves)
                + (victim.isResetMoves() ? " [reset]" : ""));
        if (new HashSet<>(onMoves).equals(new HashSet<>(offMoves))) {
            failures.add("Smeargle got the same moveset with movesets randomised as UNCHANGED - trigger gate not "
                    + "respected: " + moveNames(rom, onMoves));
        }

        assertTrue(failures.isEmpty(), "Smeargle sketch-pool failures:\n  " + String.join("\n  ", failures));
        System.out.println("  === Smeargle sketch-pool checks held. ===");
    }

    private static List<Integer> nonZeroMoves(int[] moves) {
        return Arrays.stream(moves).filter(id -> id != 0).boxed().collect(Collectors.toList());
    }

    private static String moveNames(RomHandler romHandler, List<Integer> moveIDs) {
        List<Move> allMoves = romHandler.getMoves();
        return moveIDs.stream().map(id -> allMoves.get(id).name).collect(Collectors.joining(" "));
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
        int perfectAccuracy = romHandler.getPerfectAccuracy();
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
        int aiUnusableUses = 0;      // times an AI-unusable move (Feint, Counter, ...) was picked - must stay 0
        int counterSpecialistUses = 0; // times Counter/Mirror Coat was picked on Wobbuffet/Wynaut - sanctioned exception
        int aiFlawedUses = 0;        // times an AI-flawed move (Explosion, Trick, ...) was picked - now penalised
        int ohkoUses = 0;            // times an OHKO move (Fissure, Sheer Cold, ...) was picked - now penalised
        int solarOnNonSun = 0;       // SolarBeam / Solar Blade picks on a mon that cannot guarantee sun (must be 0)
        int doublesFormatMons = 0;   // mons in a genuine double/multi battle (ALWAYS multi-battle status)
        int doublesMoveUsesInDoubles = 0; // doubles-support moves kept on those double/multi-battle mons
        int committedDamagingMoves = 0;   // damaging moves on physical/special-committed attackers (raw base stats)
        int committedMatchingMoves = 0;   // of those, how many match the attacker's preferred category
        int realDamagingPicks = 0;        // real (power>1) non-natural damaging picks, for the soft-tier rate checks
        int overTierPicks = 0;            // ... whose power*hitCount exceeds the mon's level power tier
        int lowLevelDamagingPicks = 0;    // sub-L20 (mid-unlock) mons' real non-natural damaging picks
        int lowLevelHighPowerPicks = 0;   // ... of those, high-tier (81+ BP) - should be very rare under the soft gate
        int dupAttackTypeMons = 0;        // mons carrying 2+ attacking moves of one type (no-dup-type guard fallback)
        int teamNonStabSlots = 0;         // total non-STAB move slots across all teams (team-repeat denominator)
        int teamNonStabRepeats = 0;       // of those, ones repeating a non-STAB move an earlier teammate already had
        int bossAttackPicks = 0;          // boss/important attacking-move picks (accuracy lever denominator)
        int bossLowAccAttackPicks = 0;    // ... of those, low-accuracy (below RELIABLE_ACCURACY, not never-miss)
        int regAttackPicks = 0;           // regular-tier attacking-move picks (loose - no accuracy lever)
        int regLowAccAttackPicks = 0;     // ... of those, low-accuracy
        // Boss offensive breadth (Batch 11): bosses reserve a guaranteed status slot, which - left unchecked -
        // let the wildcard stack a SECOND non-damaging move and dropped their average attack count BELOW regulars'.
        // The boss-wildcard damaging lean should restore a boss edge, so we tally attacking moves per mon by tier
        // and assert boss/important average >= regular average below.
        int bossMonCount = 0;             // boss/important mons that received a moveset (breadth denominator)
        int bossAttackMoveTotal = 0;      // total attacking moves across those mons
        int regMonCount = 0;              // regular-tier mons that received a moveset
        int regAttackMoveTotal = 0;       // total attacking moves across those mons
        int stabSlotSyntheticUses = 0; // mons whose STAB slot (slot 0) is a type-matching synthetic/delayed move (now barred)
        int mismatchedBoosterMons = 0;        // mons with a single-stat status booster + an opposite-category attack
        int pureSingleTypeMons = 0;           // randomized pure single-type mons eligible for the zero-STAB-anywhere guard
        int zeroStabAnywhereMons = 0;         // ... of those, ones with no same-type attack in any of the 4 slots

        for (Trainer tr : romHandler.getTrainers()) {
            boolean printThis = printed < SAMPLE_MOVESETS_PER_ROM;
            // Team-level authoring tally: how many mons on THIS trainer carry each non-STAB move. A move counted as
            // STAB for its carrier (its type matches one of the mon's types) is skipped, so themed mono-type teams
            // don't register their shared STAB as "repetition". A move on k of the team's mons adds k-1 repeats.
            Map<Integer, Integer> teamNonStabMoveCounts = new HashMap<>();
            // No battle-style setting is applied here, so a trainer is a double/multi battle exactly when the
            // base game always makes it one. Doubles-support moves may appear only on these mons.
            boolean trainerDoubles = tr.getMultiBattleStatus() == Trainer.MultiBattleStatus.ALWAYS;
            // Boss/Important trainers get the accuracy-biased curated slots; regulars stay loose (accuracy lever).
            boolean bossTier = tr.isBoss() || tr.isImportant();
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
                // Complete move set gathered up front, so co-occurrence checks (e.g. Sunny Day + a sun nuke) are
                // order-independent - movesThisMon is only complete after the per-move loop below.
                Set<Integer> allMovesThisMon = new HashSet<>();
                for (int mid : tp.getMoves()) {
                    if (mid != 0) {
                        allMovesThisMon.add(mid);
                    }
                }
                int nonZero = 0;
                int monAttackMoves = 0; // attacking moves on THIS mon (boss offensive-breadth tally)
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
                    if (AI_UNUSABLE_MOVES.contains(moveID)) {
                        // Sanctioned exception: Wobbuffet/Wynaut's real (unchanged) learnsets are otherwise almost
                        // nothing but Splash/Charm/status moves, so Counter/Mirror Coat - their purpose-built kit -
                        // are deliberately let through for just these two species (TrainerMovesetRandomizer). Every
                        // other AI_UNUSABLE_MOVES entry, and Counter/Mirror Coat on every other species, stays a
                        // hard violation.
                        boolean isCounterSpecialistException =
                                (pk.getNumber() == SpeciesIDs.wobbuffet || pk.getNumber() == SpeciesIDs.wynaut)
                                        && (moveID == MoveIDs.counter || moveID == MoveIDs.mirrorCoat);
                        if (isCounterSpecialistException) {
                            counterSpecialistUses++;
                        } else {
                            aiUnusableUses++;
                            violations.add(romName + ": AI-unusable move " + allMoves.get(moveID).name
                                    + " on " + pk.getName() + " - pool strip missing/broken");
                        }
                    }
                    if (AI_FLAWED_MOVES.contains(moveID)) {
                        aiFlawedUses++;
                    }
                    if (OHKO_MOVES.contains(moveID)) {
                        ohkoUses++;
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
                    String weather = weatherRedundancy(moveID, pk, ability, allMovesThisMon);
                    if (weather != null) {
                        violations.add(romName + ": " + weather);
                    }
                    if (moveID == MoveIDs.trickRoom && pk.getBaseStats().getSpeed() > 60) {
                        violations.add(romName + ": Trick Room on fast " + pk.getName()
                                + " (spe " + pk.getBaseStats().getSpeed() + ")");
                    }
                    // Electro Ball wants a fast user, Gyro Ball a slow one (base-Speed gate mirrors the source).
                    if (moveID == MoveIDs.electroBall && pk.getBaseStats().getSpeed() < 90) {
                        violations.add(romName + ": Electro Ball on slow " + pk.getName()
                                + " (spe " + pk.getBaseStats().getSpeed() + ")");
                    }
                    if (moveID == MoveIDs.gyroBall && pk.getBaseStats().getSpeed() > 60) {
                        violations.add(romName + ": Gyro Ball on fast " + pk.getName()
                                + " (spe " + pk.getBaseStats().getSpeed() + ")");
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
                    // Real damaging moves are constrained by the trainer hard power-band filter
                    // (TrainerMovesetRandomizer.applyPowerBandFilter). The tier ceiling below (low <=60 always,
                    // mid 61-80 from ~L20, high 81+ from ~L35) is a deliberately lenient statistical guardrail -
                    // looser than the real L15/L30 windows - so genuine over-tier spillover stays conservative.
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
                    // Accuracy-as-difficulty lever (Issue E): tally attacking picks by tier and how many are
                    // low-accuracy. A move is attacking when it deals real or synthetic damage (mirrors
                    // effectivePower > 0); "low accuracy" = a real hitratio below RELIABLE_ACCURACY (never-miss
                    // moves store hitratio == perfectAccuracy and are exempt, matching accuracyWeight).
                    boolean attackingMove = mv.power > 1 || SYNTHETIC_DAMAGE_MOVES.contains(moveID);
                    if (attackingMove) {
                        monAttackMoves++;
                        boolean lowAcc = mv.hitratio != perfectAccuracy && mv.hitratio < RELIABLE_ACCURACY;
                        if (bossTier) {
                            bossAttackPicks++;
                            if (lowAcc) {
                                bossLowAccAttackPicks++;
                            }
                        } else {
                            regAttackPicks++;
                            if (lowAcc) {
                                regLowAccAttackPicks++;
                            }
                        }
                    }
                }
                // Boss offensive breadth (Batch 11): accumulate this mon's attacking-move count by tier. Only mons
                // that actually received a moveset count (a moveless/reset mon skipped the slot logic, so it carries
                // no authored profile and would just dilute both averages).
                if (nonZero > 0) {
                    if (bossTier) {
                        bossMonCount++;
                        bossAttackMoveTotal += monAttackMoves;
                    } else {
                        regMonCount++;
                        regAttackMoveTotal += monAttackMoves;
                    }
                }
                // No-duplicate-attacking-type guard: no mon should carry two attacking moves of one type. A move
                // counts as attacking when it deals real or synthetic damage (mirrors the randomizer's
                // effectivePower > 0). Tallied, not hard-failed, because the guard relaxes for tiny / mono-type
                // pools; the aggregate rate is asserted below.
                Map<Type, Integer> attackTypeCounts = new HashMap<>();
                for (int moveID : movesThisMon) {
                    Move am = allMoves.get(moveID);
                    boolean attacking = am.power > 1 || SYNTHETIC_DAMAGE_MOVES.contains(moveID);
                    if (attacking && am.type != null) {
                        attackTypeCounts.merge(am.type, 1, Integer::sum);
                    }
                }
                if (attackTypeCounts.values().stream().anyMatch(c -> c >= 2)) {
                    dupAttackTypeMons++;
                    if (printThis) {
                        System.out.println("     [dup-attack-type] " + pk.getName() + " (" + typeStr(pk)
                                + ") carries 2+ same-type attacks");
                    }
                }
                // Zero-STAB-anywhere guard: only meaningful for the randomizer's own authored output, and only a
                // pure single-type mon has no secondary type to fall back on if its own-type attack gets bumped.
                boolean randomizedMon = !tr.shouldNotGetBuffs() && !tp.isResetMoves();
                if (randomizedMon && nonZero > 0 && pk.getSecondaryType(false) == null
                        && !PURE_SINGLE_TYPE_NO_STAB_EXCEPTIONS.contains(pk.getNumber())) {
                    pureSingleTypeMons++;
                    if (!attackTypeCounts.containsKey(pk.getPrimaryType(false))) {
                        zeroStabAnywhereMons++;
                        if (printThis) {
                            System.out.println("     [zero-STAB-anywhere] " + pk.getName() + " (" + typeStr(pk)
                                    + ") has no same-type attack in any slot");
                        }
                    }
                }
                // STAB-slot exclusion (Action 2): the STAB slot is the first move picked (slot 0; enabler-dependency
                // cleanup preserves order and never removes a damaging STAB). It must not be a type-independent-damage
                // move (fixed / proportional / delayed typeless) that type-matches - that is the would-be STAB the slot
                // now bars. The same move as OFF-type coverage/wildcard is legitimate and not counted.
                int stabSlotMoveId = tp.getMoves()[0];
                if (stabSlotMoveId != 0) {
                    boolean typeIndependent = SYNTHETIC_DAMAGE_MOVES.contains(stabSlotMoveId)
                            || DELAYED_TYPELESS_STAB_MOVES.contains(stabSlotMoveId);
                    Type stabSlotType = allMoves.get(stabSlotMoveId).type;
                    if (typeIndependent && stabSlotType != null && hasType(pk, stabSlotType)) {
                        stabSlotSyntheticUses++;
                        if (printThis) {
                            System.out.println("     [synthetic STAB slot] " + allMoves.get(stabSlotMoveId).name
                                    + " on " + pk.getName() + " (" + typeStr(pk) + ")");
                        }
                    }
                }
                // Exclusive-category stat-boost gate (Action 1): only STATUS-category self-boosters are gated (a
                // damaging rider like Charge Beam is deliberately not), so classify those and flag a mon that also
                // carries an opposite-category attack - the waste the gate blocks.
                boolean hasPhysAttack = false;
                boolean hasSpecAttack = false;
                boolean atkOnlyBooster = false;
                boolean spatkOnlyBooster = false;
                for (int moveID : movesThisMon) {
                    Move m = allMoves.get(moveID);
                    boolean attacking = m.power > 1 || SYNTHETIC_DAMAGE_MOVES.contains(moveID);
                    if (attacking && m.category == MoveCategory.PHYSICAL) {
                        hasPhysAttack = true;
                    }
                    if (attacking && m.category == MoveCategory.SPECIAL) {
                        hasSpecAttack = true;
                    }
                    if (m.category == MoveCategory.STATUS
                            && m.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_USER) {
                        boolean bAtk = Arrays.stream(m.statChanges).anyMatch(sc -> sc.stages > 0
                                && (sc.type == StatChangeType.ATTACK || sc.type == StatChangeType.ALL));
                        boolean bSpatk = Arrays.stream(m.statChanges).anyMatch(sc -> sc.stages > 0
                                && (sc.type == StatChangeType.SPECIAL_ATTACK || sc.type == StatChangeType.SPECIAL
                                        || sc.type == StatChangeType.ALL));
                        if (bAtk && !bSpatk) {
                            atkOnlyBooster = true;
                        }
                        if (bSpatk && !bAtk) {
                            spatkOnlyBooster = true;
                        }
                    }
                }
                if ((atkOnlyBooster && hasSpecAttack) || (spatkOnlyBooster && hasPhysAttack)) {
                    mismatchedBoosterMons++;
                    if (printThis) {
                        System.out.println("     [mismatched booster] " + pk.getName()
                                + " carries a single-stat booster with an opposite-category attack");
                    }
                }
                // Team-level authoring: record this mon's non-STAB moves against the trainer's running tally, so a
                // move a later teammate repeats registers as a repeat. STAB moves (type matches one of the mon's
                // types) are exempt, matching the randomizer's penalty scope.
                for (int moveID : movesThisMon) {
                    // A typeless move (type == null) is never STAB; guard so it isn't wrongly exempted on a
                    // mono-type mon, whose null secondary type would make hasType(pk, null) return true.
                    Type moveType = allMoves.get(moveID).type;
                    if (moveType != null && hasType(pk, moveType)) {
                        continue;
                    }
                    teamNonStabSlots++;
                    if (teamNonStabMoveCounts.merge(moveID, 1, Integer::sum) >= 2) {
                        teamNonStabRepeats++;
                    }
                }
                checkEnablerDependencies(movesThisMon, allMoves, pk, romName, violations);
                // SolarBeam / Solar Blade are sun-gated: allowed only on a sun-guaranteed mon (sun-setter ability
                // or a Sunny Day it also carries). Tally any pick lacking both - asserted to be 0 below (the enabler
                // enforcement drops an orphaned nuke; the practical-value penalty separately keeps sun picks rare).
                // Only the randomizer's own authored output is in scope. Trainers it skips (shouldNotGetBuffs, e.g.
                // scripted tutorial/rival battles) and empty-pool mons handed back for their natural level-up moveset
                // keep their VANILLA moves, which can include Game Freak's own sun-less SolarBeam (a real vanilla
                // troll-set the randomizer never touched) - not a gate failure.
                boolean randomized = !tr.shouldNotGetBuffs() && !tp.isResetMoves();
                if (randomized
                        && (movesThisMon.contains(MoveIDs.solarBeam) || movesThisMon.contains(MoveIDs.solarBlade))) {
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

        double ubiquitousCap = gen == 1 ? UBIQUITOUS_RATE_GEN1 : UBIQUITOUS_RATE;
        for (Map.Entry<Integer, Integer> e : moveCounts.entrySet()) {
            double rate = (double) e.getValue() / (double) tpCount;
            if (rate >= ubiquitousCap) {
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
        // HARD invariant (mirrors the AI-unusable strip below): after the sun-enabler gate, SolarBeam / Solar Blade
        // may NEVER appear on a mon that cannot guarantee sun - the enabler enforcement drops an orphaned nuke.
        if (solarOnNonSun > 0) {
            violations.add(String.format("%s: %d SolarBeam/Solar Blade pick(s) on a non-sun mon"
                    + " - sun-enabler gate missing/broken", romName, solarOnNonSun));
        }
        // Soft guarantee: no single pure-charge or recharge move should be a common pick under the penalty. This
        // is looser than a hard per-appearance ban (the moves stay legal rare surprises) but catches a missing or
        // broken practical-value weight, which would let a flawed strong move flood the slots again.
        if (tpCount > 100) {
            checkRateCap(moveCounts, tpCount, allMoves,
                    id -> PURE_CHARGE_MOVES.contains(id) || allMoves.get(id).isRechargeMove,
                    FLAWED_STRONG_MOVE_MAX_RATE,
                    (mv, rate) -> String.format("%s: flawed strong move '%s' too common (%.1f%% of mons > %.0f%% cap)"
                                    + " - practical-value penalty not biasing",
                            romName, mv.name, rate * 100, FLAWED_STRONG_MOVE_MAX_RATE * 100),
                    violations);
        }
        // Batch 7 - AI move usability. AI-unusable moves are stripped from the pool up front, so they must NEVER
        // appear on a buffed mon (a HARD invariant, checked at any sample size) - except Counter/Mirror Coat on
        // Wobbuffet/Wynaut, a sanctioned species-specific carve-out tallied separately above. AI-flawed moves are
        // only heavily penalised, so they stay rare surprises - checked against a soft ceiling like the
        // practical-value block. Violations for genuine (non-exempt) AI-unusable picks are added inline above,
        // where the carrying species is known.
        System.out.printf("     ai-usability: %d unusable pick(s) (must be 0), %d Counter/Mirror Coat specialist "
                        + "pick(s) (Wobbuffet/Wynaut exception), %d flawed pick(s), across %d Pokemon%n",
                aiUnusableUses, counterSpecialistUses, aiFlawedUses, tpCount);
        if (tpCount > 100) {
            double flawedCap = gen == 1 ? AI_FLAWED_MOVE_MAX_RATE_GEN1 : AI_FLAWED_MOVE_MAX_RATE;
            checkRateCap(moveCounts, tpCount, allMoves,
                    id -> AI_FLAWED_MOVES.contains(id),
                    flawedCap,
                    (mv, rate) -> String.format("%s: AI-flawed move '%s' too common (%.1f%% of mons > %.0f%% cap)"
                                    + " - aiUsabilityWeight penalty not biasing",
                            romName, mv.name, rate * 100, flawedCap * 100),
                    violations);
        }
        // OHKO moves are soft-penalised, not banned, so they stay rare - checked against a soft ceiling.
        System.out.printf("     ohko: %d OHKO pick(s) across %d Pokemon%n", ohkoUses, tpCount);
        if (tpCount > 100) {
            checkRateCap(moveCounts, tpCount, allMoves,
                    id -> OHKO_MOVES.contains(id),
                    OHKO_MOVE_MAX_RATE,
                    (mv, rate) -> String.format("%s: OHKO move '%s' too common (%.1f%% of mons > %.0f%% cap)"
                                    + " - ohkoWeight penalty not biasing",
                            romName, mv.name, rate * 100, OHKO_MOVE_MAX_RATE * 100),
                    violations);
        }
        // STAB-slot exclusion (Action 2): a type-matching synthetic/delayed move in the STAB slot should be near-zero
        // (only the rare pickBestDamaging fallback or a tiny <=4 pool). A spike means the STAB filter is missing/broken.
        System.out.printf("     stab-slot exclusion: %d synthetic/delayed STAB-slot pick(s) across %d Pokemon%n",
                stabSlotSyntheticUses, tpCount);
        if (tpCount > 100) {
            double rate = (double) stabSlotSyntheticUses / tpCount;
            if (rate > STAB_SLOT_SYNTHETIC_MAX_RATE) {
                violations.add(String.format(
                        "%s: %.1f%% of mons have a type-matching synthetic/delayed move in the STAB slot (%d/%d > %.0f%% "
                                + "cap) - STAB-slot exclusion missing/broken",
                        romName, rate * 100, stabSlotSyntheticUses, tpCount, STAB_SLOT_SYNTHETIC_MAX_RATE * 100));
            }
        }
        // Exclusive-category stat-boost gate (Action 1): a single-stat booster paired with an opposite-category
        // attack should be rare (only when a later wildcard adds the contradicting attack). A spike means the gate
        // is missing/broken (boosters landing on mixed sets at the unfiltered rate).
        System.out.printf("     stat-boost gate: %d mon(s) with a mismatched single-stat booster across %d Pokemon%n",
                mismatchedBoosterMons, tpCount);
        if (tpCount > 100) {
            double rate = (double) mismatchedBoosterMons / tpCount;
            if (rate > MISMATCHED_BOOSTER_MAX_RATE) {
                violations.add(String.format(
                        "%s: %.1f%% of mons carry a single-stat booster with an opposite-category attack (%d/%d > %.0f%% "
                                + "cap) - exclusive-category stat-boost gate missing/broken",
                        romName, rate * 100, mismatchedBoosterMons, tpCount, MISMATCHED_BOOSTER_MAX_RATE * 100));
            }
        }
        // Zero-STAB-anywhere guard (Batch 12 TODO 1 fix): a pure single-type mon should virtually always keep at
        // least one same-type attack. A spike means the type-aware enabler-dependency backfill is missing/broken -
        // its type-blind predecessor could reassign a pure single-type mon's STAB slot to an off-type move with
        // nothing left of its own type anywhere in the set.
        if (pureSingleTypeMons > 20) {
            double zeroStabRate = (double) zeroStabAnywhereMons / pureSingleTypeMons;
            double zeroStabCap = gen <= 2 ? ZERO_STAB_ANYWHERE_MAX_RATE_GEN1_2 : ZERO_STAB_ANYWHERE_MAX_RATE;
            System.out.printf("     zero-STAB-anywhere: %d/%d pure single-type mons (%.1f%%) have no same-type attack%n",
                    zeroStabAnywhereMons, pureSingleTypeMons, zeroStabRate * 100);
            if (zeroStabRate > zeroStabCap) {
                violations.add(String.format(
                        "%s: %.1f%% of pure single-type mons have zero same-type attack in any slot (%d/%d > %.0f%% "
                                + "cap) - type-aware enabler backfill or HM pool wiring missing/broken",
                        romName, zeroStabRate * 100, zeroStabAnywhereMons, pureSingleTypeMons, zeroStabCap * 100));
            }
        }
        // No-duplicate-attacking-type guard: with the guard in place, carrying two attacks of one type should be
        // rare (only tiny / mono-type pools that force the fallback). A high rate means the guard is not biasing.
        if (tpCount > 100) {
            double dupRate = (double) dupAttackTypeMons / tpCount;
            double dupCap = gen == 1 ? DUPLICATE_ATTACK_TYPE_MAX_RATE_GEN1 : DUPLICATE_ATTACK_TYPE_MAX_RATE;
            System.out.printf("     no-dup-attack-type: %d/%d mons (%.1f%%) carry 2+ same-type attacks (guard fallback)%n",
                    dupAttackTypeMons, tpCount, dupRate * 100);
            if (dupRate > dupCap) {
                violations.add(String.format(
                        "%s: %.1f%% of mons carry two attacks of one type (%d/%d > %.0f%% cap) - dup-type guard not biasing",
                        romName, dupRate * 100, dupAttackTypeMons, tpCount, dupCap * 100));
            }
        }
        // Team-level authoring (Issue D): the per-trainer penalty should keep trainers from stacking the same
        // non-STAB move across teammates. A high non-STAB repeat rate means the penalty is not biasing.
        if (teamNonStabSlots > 100) {
            double repeatRate = (double) teamNonStabRepeats / teamNonStabSlots;
            double repeatCap = gen == 1 ? TEAM_NONSTAB_REPEAT_MAX_RATE_GEN1 : TEAM_NONSTAB_REPEAT_MAX_RATE;
            System.out.printf("     team authoring: %.1f%% of non-STAB move slots repeat a teammate's move (%d/%d)%n",
                    repeatRate * 100, teamNonStabRepeats, teamNonStabSlots);
            if (repeatRate > repeatCap) {
                violations.add(String.format(
                        "%s: %.1f%% of non-STAB slots repeat a teammate's move (%d/%d > %.0f%% cap) - team penalty not biasing",
                        romName, repeatRate * 100, teamNonStabRepeats, teamNonStabSlots, repeatCap * 100));
            }
        }
        // Accuracy-as-difficulty lever (Issue E): PRINTED as evidence only (see RELIABLE_ACCURACY comment for why a
        // cross-tier or absolute assert is not well-founded here). Shows the boss/imp vs regular low-accuracy attack
        // rates so a gross regression is visible during review.
        if (bossAttackPicks > 50 && regAttackPicks > 50) {
            double bossRate = (double) bossLowAccAttackPicks / bossAttackPicks;
            double regRate = (double) regLowAccAttackPicks / regAttackPicks;
            System.out.printf("     accuracy lever: boss/imp low-acc attacks %.1f%% (%d/%d) vs regular %.1f%% (%d/%d)%n",
                    bossRate * 100, bossLowAccAttackPicks, bossAttackPicks,
                    regRate * 100, regLowAccAttackPicks, regAttackPicks);
        }
        // Boss offensive breadth (Batch 11). Bosses reserve a guaranteed status slot that regulars do not, so in RAW
        // attack count a boss structurally tops out at 3 attacks (STAB + coverage + wildcard) while a regular can
        // reach 4 (STAB + 2nd attack + two wildcards) - a small built-in edge (~0.1 attacks) in regulars' favour.
        // That makes a strict boss>=regular assert wrong-headed (the same cross-tier confound the accuracy lever
        // above documents). What the boss-wildcard damaging lean must prevent is the wildcard stacking a SECOND
        // non-damaging move on top of the reserved status one, which widens the gap sharply (the wildcard would fall
        // from ~66% to ~39% damaging, ~0.3 fewer boss attacks). So PRINT both averages and assert only that the gap
        // stays within a tolerance the reserved status slot explains but a missing/broken wildcard lean would not.
        // Measured gaps with the lean are <=0.06 (SoulSilver boss even leads); without it they run ~0.3.
        if (bossMonCount > 50 && regMonCount > 50) {
            double bossAvg = (double) bossAttackMoveTotal / bossMonCount;
            double regAvg = (double) regAttackMoveTotal / regMonCount;
            System.out.printf("     offensive breadth: boss/imp avg %.2f attacks (n=%d) vs regular %.2f (n=%d)%n",
                    bossAvg, bossMonCount, regAvg, regMonCount);
            if (bossAvg < regAvg - BOSS_BREADTH_TOLERANCE) {
                violations.add(String.format(
                        "%s: boss/imp avg attacks (%.2f) trails regular (%.2f) by > %.2f - boss wildcard damaging lean "
                                + "missing/broken (a second non-damaging move is stacking on the reserved status slot)",
                        romName, bossAvg, regAvg, BOSS_BREADTH_TOLERANCE));
            }
        }
        // Power-band guarantees (see TrainerMovesetRandomizer.applyPowerBandFilter). Over-tier picks are allowed but
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

    // Soft ceiling over the whole-ROM pick tally: flags any move matching `applies` that lands on more than `cap`
    // of the ROM's trainer Pokemon. The caller guards on a minimum sample size; `violation` builds the message from
    // the offending move and its measured rate.
    private void checkRateCap(Map<Integer, Integer> moveCounts, int tpCount, List<Move> allMoves,
                              IntPredicate applies, double cap, BiFunction<Move, Double, String> violation,
                              List<String> violations) {
        for (Map.Entry<Integer, Integer> e : moveCounts.entrySet()) {
            if (!applies.test(e.getKey())) {
                continue;
            }
            double rate = (double) e.getValue() / tpCount;
            if (rate > cap) {
                violations.add(violation.apply(allMoves.get(e.getKey()), rate));
            }
        }
    }

    // Enabler-dependent moves are useless without a partner move, so flag any that appears without it.
    private void checkEnablerDependencies(Set<Integer> movesThisMon, List<Move> allMoves, Species pk, String romName,
                                          List<String> violations) {
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
        // Baton Pass is pointless with nothing to pass, so it needs a self-boosting setup move.
        boolean hasBoostToPass = movesThisMon.stream().anyMatch(id -> {
            Move m = allMoves.get(id);
            return m != null && m.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_USER
                    && Arrays.stream(m.statChanges).anyMatch(sc -> sc.type != StatChangeType.NONE && sc.stages > 0);
        });
        if (!hasBoostToPass && movesThisMon.contains(MoveIDs.batonPass)) {
            violations.add(romName + ": Baton Pass without a stat-boosting move on " + pk.getName());
        }
    }

    // Returns a violation description if this weather move is redundant on the Pokemon, else null. Sunny Day has
    // a third legitimate case beyond Fire type / sun ability: it enables a sun-dependent nuke (SolarBeam / Solar
    // Blade) carried in the same set. That co-occurrence exception still catches a lone Sunny Day off such a mon.
    private String weatherRedundancy(int moveID, Species pk, int ability, Set<Integer> movesOnMon) {
        boolean ok = switch (moveID) {
            case MoveIDs.rainDance -> hasType(pk, Type.WATER) || RAIN_ABILITIES.contains(ability);
            case MoveIDs.sunnyDay -> hasType(pk, Type.FIRE) || SUN_ABILITIES.contains(ability)
                    || !Collections.disjoint(movesOnMon, SUN_DEPENDENT_MOVES);
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
        int spatk = gen == 1 ? ((Gen1BaseStats) pk.getBaseStats()).getSpecial() : pk.getBaseStats().getSpatk();
        if (spatk <= 0) {
            return 1;
        }
        double ratio = (double) pk.getBaseStats().getAttack() / spatk;
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

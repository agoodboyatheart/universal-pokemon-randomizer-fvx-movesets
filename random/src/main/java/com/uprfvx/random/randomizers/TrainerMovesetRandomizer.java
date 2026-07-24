package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.constants.SpeciesIDs;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;
import java.util.stream.Collectors;

public class TrainerMovesetRandomizer extends Randomizer {

    private Map<Integer, List<MoveLearnt>> allLevelUpMoves;
    private Map<Integer, List<Integer>> allEggMoves;
    private Map<Species, boolean[]> allTMCompat, allTutorCompat;
    // allTMCompat's boolean[] is 1-indexed against allTMMoves (indices 1..allTMMoves.size()) THEN allHMMoves
    // (allTMMoves.size()+1..+allHMMoves.size()) - one combined TM/HM compatibility array per species.
    private List<Integer> allTMMoves, allHMMoves, allTutorMoves;
    // Move number -> number of species that can learn it (any source).
    private Map<Integer, Integer> moveAvailability;
    // Cached once per run: romHandler.getTypeTable() rebuilds from ROM on every call.
    private TypeTable typeTable;
    // Move numbers that raise one of the user's own stats - the enabler set for the Baton Pass dependency.
    private Set<Integer> statBoostMoveNumbers;

    private final boolean hasAbilities;

    public TrainerMovesetRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
        this.hasAbilities = romHandler.abilitiesPerSpecies() != 0;
    }

    public void randomizeTrainerMovesets() {
        boolean isCyclicEvolutions = settings.getEvolutionsMod() == Settings.EvolutionsMod.RANDOM_EVERY_LEVEL;
        boolean betterBossMovesets = settings.isBetterBossTrainerMovesets();
        boolean betterImportantMovesets = settings.isBetterImportantTrainerMovesets();
        boolean betterRegularMovesets = settings.isBetterRegularTrainerMovesets();

        List<Trainer> trainers = romHandler.getTrainers().stream()
                .filter(t -> (t.isBoss() && betterBossMovesets) ||
                        (t.isImportant() && betterImportantMovesets) ||
                        (t.isRegular() && betterRegularMovesets))
                .filter(t -> !t.shouldNotGetBuffs())
                .collect(Collectors.toList());

        for (Trainer t : trainers) {

            boolean doubles = isDoublesFormatBattle(t);

            // Per-trainer memory of earlier teammates' moves/types, so choosy slots can avoid repeats.
            teamUsage = new TeamMoveUsage();

            // Strongest mon first, so the ace sees the unpenalised pool before teamUsage demotes repeats
            // onto weaker teammates; a stable sort on a copy leaves the trainer's actual team order untouched.
            List<TrainerPokemon> assignmentOrder = new ArrayList<>(t.getPokemon());
            assignmentOrder.sort(Comparator.comparingInt(TrainerPokemon::getLevel).reversed());

            for (TrainerPokemon tp : assignmentOrder) {
                tp.setResetMoves(false);

                List<Move> movesAtLevel = getMoveSelectionPoolAtLevel(tp, isCyclicEvolutions);

                Species pk = tp.getSpecies();
                int ability = hasAbilities ? romHandler.getAbilityForTrainerPokemon(tp) : 0;

                // True when Sunny Day has no standalone payoff but this mon can learn a sun nuke, so it's kept
                // only to enable that nuke - see isSituationalStatusRedundant and isDependencyUnmet.
                currentSunnyDayNeedsSolar =
                        movesAtLevel.stream().anyMatch(mv -> SUN_DEPENDENT_MOVES.contains(mv.number))
                        && !SUN_SETTER_ABILITIES.contains(ability)
                        && !(hasType(pk, Type.FIRE) || SUN_BENEFIT_ABILITIES.contains(ability));

                // Strip situational status moves (weather / Trick Room) up front so small pools / fallback paths
                // that skip the per-slot gate can't let them slip in.
                movesAtLevel.removeIf(mv -> isSituationalStatusRedundant(mv, pk, ability));

                // Same up-front strip for base-Speed-scaled attacks (Electro Ball / Gyro Ball) on the wrong side
                // of the speed curve.
                movesAtLevel.removeIf(mv -> isSpeedMismatchedVariableMove(mv, pk));

                // AI-unusable moves (Feint, Counter, Focus Punch, ...): the greedy single-turn ROM battle AI can't
                // predict the player or plan multi-turn, so these are dead weight in its hands. Strip before any
                // slot logic to close all three doors (attack, status, wildcard) at once.
                movesAtLevel.removeIf(mv -> AI_UNUSABLE_MOVES.contains(mv.number));

                // Moves dead weight without an "enabler" (Sleep Talk/Snore need Rest, Spit Up/Swallow need
                // Stockpile): strip if the enabler isn't even in the pool. The post-pass below covers the case
                // where the enabler is present but unpicked.
                stripUnsupportedDependentMoves(movesAtLevel, ability);

                // Singles: drop ally/multi-target-only support moves (Follow Me, Wide Guard, ...) up front so they
                // can't slip through the small-pool or trim paths. Genuine doubles keep them.
                if (!doubles) {
                    movesAtLevel.removeIf(TrainerMovesetRandomizer::isDoublesSupportMove);
                }

                // Fixed-constant damage moves (Dragon Rage 40, SonicBoom 20) one-shot low-level mons since their
                // damage doesn't scale; strip until the mon is high enough for the flat damage to be fair.
                movesAtLevel.removeIf(mv -> GlobalConstants.isFixedConstantDamageTooStrongForLevel(mv.number, tp.getLevel()));

                if (movesAtLevel.isEmpty()) {
                    // No custom moves to offer (e.g. a low-level rival starter): let the game assign its natural
                    // level-up moveset at ROM-write time instead of leaving it moveless.
                    tp.setResetMoves(true);
                    continue;
                }

                movesAtLevel = trimMoveList(tp, movesAtLevel, doubles, ability);

                if (movesAtLevel.isEmpty()) {
                    // trimMoveList already wrote a small (<=4) moveset directly into tp.
                    continue;
                }

                int level = tp.getLevel();
                AttackerProfile profile = classifyAttacker(pk, ability);
                // Boss/Important get the full curated structure (STAB + coverage + status + wildcard); Regular
                // trainers are deliberately dumbed down (STAB + plain second attack + two wildcards, no coverage
                // or status slot) so the tier gap reads like the mainline games.
                boolean isBossTier = t.isBoss() || t.isImportant();

                List<Move> distinctPool = movesAtLevel.stream().distinct().collect(Collectors.toList());
                List<Move> picked = new ArrayList<>();

                if (distinctPool.size() <= 4) {
                    // Too few candidates to be choosy - take what is available.
                    picked.addAll(distinctPool);
                } else {
                    // Slot 1: a STAB attacking move, base power scaled to the Pokemon's level.
                    Move stab = pickStabMove(pk, ability, distinctPool, level, profile, picked, isBossTier);
                    if (stab == null) {
                        stab = pickBestDamaging(distinctPool, picked, level, ability, isBossTier);
                    }
                    if (stab != null) {
                        picked.add(stab);
                    }

                    // Slot 2: a second attacking move. Bosses/Important get optimized coverage for the STAB's
                    // hole; Regular trainers get a plain damaging move so their teams look less curated.
                    Move secondAttack;
                    if (isBossTier) {
                        secondAttack = pickCoverageMove(pk, ability, distinctPool, level,
                                stab == null ? null : stab.type, profile, picked);
                    } else {
                        secondAttack = pickRegularSecondAttack(distinctPool, picked, level, ability, profile);
                    }
                    if (secondAttack == null) {
                        secondAttack = pickBestDamaging(distinctPool, picked, level, ability, isBossTier);
                    }
                    if (secondAttack != null) {
                        picked.add(secondAttack);
                    }

                    // Slot 3 (bosses/important only): a non-redundant status move.
                    if (isBossTier) {
                        Move status = pickStatusMove(pk, ability, distinctPool, picked, level);
                        if (status == null) {
                            status = pickBestDamaging(distinctPool, picked, level, ability, isBossTier);
                        }
                        if (status != null) {
                            picked.add(status);
                        }
                    }

                    // Remaining slots: wildcard picks reusing the existing synergy-weighted logic.
                    fillWildcardMoves(tp, pk, ability, distinctPool, picked, doubles, level, isBossTier);
                }

                // Drop any dependent whose enabler didn't make the final set, then backfill with the next-best
                // damaging move.
                enforceEnablerDependencies(picked, distinctPool, level, ability, isBossTier,
                        pk.getPrimaryType(false), pk.getSecondaryType(false));

                writeMoves(tp, picked);

                // Record this mon's final moves so later teammates can softly avoid repeating them. (Tiny-pool and
                // reset-moves mons continue out above and never reach here, which is fine - they had no real choice.)
                for (Move mv : picked) {
                    teamUsage.record(mv, level);
                }
            }
        }
        changesMade = true;
    }

    // ===== Role-based moveset construction ===========================================================
    // Boss/Important: STAB + Coverage + Status + Wildcard. Regular: STAB + Coverage + Wildcard + Wildcard.
    // Any unfillable slot falls back to the next-best damaging move, so every mon gets 4 moves.

    // ===== Team-level authoring ======================================================================
    // Soft per-trainer memory of what earlier teammates were given: attack/status/wildcard slots multiply pick
    // weight by teamRepeatWeight so a team leans toward role variety instead of stacking a move/type - a demotion
    // only, never a ban, so mono-type teams and small movepools still fill all four slots.
    private TeamMoveUsage teamUsage;

    // Weight multiplier for a candidate earlier teammates already used: geometric decay by count, unpenalised on
    // first appearance, each prior use multiplying in another penalty. Exact-move bites harder than shared type.
    private static final double TEAM_MOVE_REPEAT_PENALTY = 0.35;
    private static final double TEAM_TYPE_REPEAT_PENALTY = 0.5;

    // STAB is type-locked with a boss power/category lean, so on a mono-type team the penalty above is too weak to
    // break a repeated STAB. This stronger exact-move penalty applies only to STAB picks. Tuning knob.
    private static final double STAB_TEAM_MOVE_REPEAT_PENALTY = 0.20;

    private double teamRepeatWeight(Move mv, int level) {
        return teamRepeatWeight(mv, level, TEAM_MOVE_REPEAT_PENALTY);
    }

    private double teamRepeatWeight(Move mv, int level, double moveRepeatPenalty) {
        if (teamUsage == null) {
            return 1.0;
        }
        double weight = Math.pow(moveRepeatPenalty, teamUsage.moveUses(mv.number));
        if (effectivePower(mv, level) > 0) {
            weight *= Math.pow(TEAM_TYPE_REPEAT_PENALTY, teamUsage.typeUses(mv.type));
        }
        return weight;
    }

    // Set once per mon (see randomizeTrainerMovesets); true when Sunny Day is kept only to enable a learnable
    // sun nuke, so the enabler enforcement force-drops it unless that nuke is also picked.
    private boolean currentSunnyDayNeedsSolar = false;

    // Per-trainer tally of moves/attacking-types assigned to earlier teammates. "Attacking" means real or
    // synthetic damage (effectivePower > 0); status moves only tally as exact-move, not type.
    private static final class TeamMoveUsage {
        private final Map<Integer, Integer> moveCounts = new HashMap<>();
        private final Map<Type, Integer> typeCounts = new HashMap<>();

        int moveUses(int moveNumber) {
            return moveCounts.getOrDefault(moveNumber, 0);
        }

        int typeUses(Type type) {
            return type == null ? 0 : typeCounts.getOrDefault(type, 0);
        }

        void record(Move mv, int level) {
            moveCounts.merge(mv.number, 1, Integer::sum);
            if (mv.type != null && effectivePower(mv, level) > 0) {
                typeCounts.merge(mv.type, 1, Integer::sum);
            }
        }
    }

    private static final double COVERAGE_BLIND_SPOT_BONUS = 2.0;
    // Weight multiplier for a coverage move that hits a STAB hole super-effectively - a strong preference, not a
    // hard gate, so bosses occasionally get a flavourful neutral move instead. Tuning knob.
    private static final double COVERAGE_SUPER_EFFECTIVE_BONUS = 3.5;

    // Weight multiplier biasing a Boss/Important wildcard toward a damaging move: the reserved status slot already
    // guarantees one non-damaging move, so an unbiased wildcard stacks a second ~61% of the time, dropping bosses
    // below regulars in attack count. Soft (a status wildcard can still win); regulars unaffected. Calibrated to
    // 5.0 from a SoulSilver log (3.0 left high-level bosses modal at 2 attacks). Non-final so
    // MovesetProfileRandomizerTest can sweep it; treat as a constant in production. Tuning knob.
    static double bossWildcardDamagingBonus = 5.0;
    private static final int TRICK_ROOM_MAX_SPEED = 60;
    // Electro Ball rewards outspeeding the target, Gyro Ball rewards being slower; base Speed is the proxy since
    // the opponent is unknown at assignment time.
    private static final int ELECTRO_BALL_MIN_SPEED = 90;
    private static final int GYRO_BALL_MAX_SPEED = 60;

    // Attacking-stat profile from the (ability-adjusted) Attack:Sp.Atk ratio. Committed attackers prefer their
    // stronger category in STAB/coverage; mixed attackers have no preference.
    private enum AttackerProfile { PHYSICAL, SPECIAL, MIXED }

    // How lopsided Attack vs Sp.Atk must be to commit to a category (>=1.25x); below this the mon is mixed. Tuning knob.
    private static final double ATTACKER_COMMIT_RATIO = 1.25;
    // Weight multiplier for a move matching a committed attacker's category; ~9x gives a ~90% lean. Tuning knob.
    private static final double CATEGORY_PREFERENCE_BONUS = 9.0;

    // Availability normalization. A move learnable dex-wide (universal TMs like Toxic/Return) lands in nearly
    // every pool while a rare signature move lands in almost none, so under a near-uniform pick a move's frequency
    // ends up proportional to pool count rather than merit. This down-weights a move by its dex-wide learnability
    // so each gets a fairer shot - always a soft multiplier (availability >= 1 keeps weight finite and > 0), never
    // a ban. Frequency scales as availability^(1-k): k=0 disables it, k=1 fully flattens (risking obscure-move
    // flooding). k~0.4 is a partial correction - the one exposed knob, tune vs logs.
    private static final double AVAILABILITY_NORMALIZATION_EXPONENT = 0.4;

    // Down-weights a move by how many species can learn it (see AVAILABILITY_NORMALIZATION_EXPONENT above).
    private double availabilityWeight(Move mv) {
        int a = moveAvailability.getOrDefault(mv.number, 1);
        return Math.pow(Math.max(a, 1), -AVAILABILITY_NORMALIZATION_EXPONENT);
    }

    // Softens the Boss attack-slot power term so weaker same-band moves still surface (1.0 = power-proportional;
    // 0.5 gives ~55/45 for a 60-vs-40 BP pair). Boss STAB/coverage/fallback only - Regular picks stay flat.
    // Across-band level-appropriateness is the hard filter (applyPowerBandFilter), not this weight. Tuning knob.
    private static final double POWER_SELECTION_EXPONENT = 0.5;

    // A move's power-based selection weight, softened by POWER_SELECTION_EXPONENT.
    private static double powerSelectionWeight(double effectivePower) {
        return Math.pow(effectivePower, POWER_SELECTION_EXPONENT);
    }

    // Continuous level-scaled power banding via centerPower(level) - the effective power expected at that level -
    // replacing old fixed Lv15/Lv30 breakpoints to avoid a fencepost cliff. A hard sliding CEILING
    // (applyPowerBandFilter) removes over-level moves at the pool stage; a soft sliding FLOOR
    // (levelAppropriatenessWeight) demotes (never removes) below-level moves at pick time, so a thin STAB pool
    // always has an alternative. Curve: BASE at Lv1 rising linearly to MAX by the saturation level. Tuning knobs
    // (calibrate with -Dbm.sweep).
    private static final double LEVEL_POWER_BASE = 45.0;
    private static final double LEVEL_POWER_MAX = 95.0;
    private static final double LEVEL_POWER_SATURATION_LEVEL = 50.0;

    static double centerPower(int level) {
        double t = Math.min(1.0, level / LEVEL_POWER_SATURATION_LEVEL);
        return LEVEL_POWER_BASE + (LEVEL_POWER_MAX - LEVEL_POWER_BASE) * t;
    }

    // A move is culled above centerPower * a level-scaled ceiling multiplier: tight at low level (reproducing the
    // old ~60 cap), widening at high level so premier nukes (Draco Meteor/Overheat 130) survive and only
    // 150+ self-KO/gimmick moves fall outside. Floored at the old Low-band cap so a low-level pool is never
    // tighter than before. Tuning knobs.
    private static final double POWER_CEILING_MULTIPLIER_LOW = 0.95;
    private static final double POWER_CEILING_MULTIPLIER_HIGH = 1.63;

    private static double powerCeiling(int level) {
        double t = Math.min(1.0, level / LEVEL_POWER_SATURATION_LEVEL);
        double mult = POWER_CEILING_MULTIPLIER_LOW + (POWER_CEILING_MULTIPLIER_HIGH - POWER_CEILING_MULTIPLIER_LOW) * t;
        return Math.max(TIER_LOW_MAX_BP, centerPower(level) * mult);
    }

    // Below centerPower * this fraction a move starts losing pick weight; the falloff exponent is tier-scaled so
    // Boss/Important lean firmly level-appropriate while Regular trainers keep weaker, more surprising moves in
    // play. Also the boss-STAB hard floor (pickStabMove) - non-final so the harness can sweep it (-Dbm.stabfloor).
    static double POWER_FLOOR_FRACTION = 0.75;
    private static final double POWER_FLOOR_EXPONENT_BOSS = 2.0;
    private static final double POWER_FLOOR_EXPONENT_REGULAR = 0.8;

    // Hard sliding ceiling (pool stage): removes attacking moves too strong for the mon's level. Status/gimmick
    // moves and the mon's own level-up moves are exempt; below-level weakness is handled softly elsewhere.
    private void applyPowerBandFilter(List<Move> pool, int level, Set<Integer> ownLevelUpMoveNumbers) {
        double ceiling = powerCeiling(level);
        pool.removeIf(mv -> {
            double ep = effectivePower(mv, level);
            if (ep <= 0 || ownLevelUpMoveNumbers.contains(mv.number)) {
                return false;
            }
            return ep > ceiling;
        });
    }

    // Soft sliding floor (pick stage): demotes, never removes, moves weaker than the mon's level warrants, falling
    // off below the floor with a tier-scaled exponent. Priority/utility weak moves (goodWeakMoves) and status
    // moves are exempt, so Aqua Jet / Sucker Punch / Rapid Spin keep full weight at any level.
    private static double levelAppropriatenessWeight(Move mv, int level, boolean bossTier) {
        double ep = effectivePower(mv, level);
        if (ep <= 0 || GlobalConstants.goodWeakMoves.contains(mv.number)) {
            return 1.0;
        }
        double floor = centerPower(level) * POWER_FLOOR_FRACTION;
        if (ep >= floor) {
            return 1.0;
        }
        return Math.pow(ep / floor, bossTier ? POWER_FLOOR_EXPONENT_BOSS : POWER_FLOOR_EXPONENT_REGULAR);
    }

    // Nominal base power is a poor proxy for practical value: charge moves waste a turn winding up and recharge
    // moves waste the turn after, so the power-weighted pickers over-rated flawed moves (SolarBeam, Hyper Beam,
    // ...). practicalValueWeight demotes them to rare surprises - it does NOT ban them.
    //
    // Only PURE charge moves are penalised: the semi-invulnerable two-turn moves (Dig, Dive, Fly, Bounce) share
    // the isChargeMove flag but are mainline staples we keep, so an explicit set is curated instead. Geomancy is a
    // STATUS move so it never reaches an attack slot and is omitted.
    private static final Set<Integer> PURE_CHARGE_MOVES = Set.of(
            MoveIDs.solarBeam, MoveIDs.solarBlade, MoveIDs.skyAttack, MoveIDs.razorWind,
            MoveIDs.skullBash, MoveIDs.freezeShock, MoveIDs.iceBurn, MoveIDs.meteorBeam);
    // Charge moves are penalised harder than recharge moves - a wasted turn up front is worse than one after the
    // hit lands. Tuning knobs.
    private static final double CHARGE_MOVE_WEIGHT_PENALTY = 0.15;
    private static final double RECHARGE_MOVE_WEIGHT_PENALTY = 0.25;

    // Penalty for pure charge/recharge moves, 1.0 otherwise. SolarBeam/Solar Blade are exempt when the mon can
    // guarantee sun (a sun-setting ability, or Sunny Day already picked).
    private double practicalValueWeight(Move mv, int ability, List<Move> picked) {
        if (PURE_CHARGE_MOVES.contains(mv.number)) {
            boolean solar = mv.number == MoveIDs.solarBeam || mv.number == MoveIDs.solarBlade;
            boolean sun = SUN_SETTER_ABILITIES.contains(ability)
                    || picked.stream().anyMatch(m -> m.number == MoveIDs.sunnyDay);
            return (solar && sun) ? 1.0 : CHARGE_MOVE_WEIGHT_PENALTY;
        }
        if (mv.isRechargeMove) {
            return RECHARGE_MOVE_WEIGHT_PENALTY;
        }
        return 1.0;
    }

    // Moves the ROM battle AI structurally cannot use: it's a greedy single-turn scorer that can't predict the
    // player or run a multi-turn plan, so these are dead weight (or self-defeating) in its hands - see
    // project_memory/enemy-ai-move-limitations.md. Trainer-side only: goodWeakMoves/goodStatusMoves still rate
    // these for a human player. A second ban philosophy beside GlobalConstants.bannedForDamagingMove (random-pool
    // eligibility) - the two overlap on some IDs but govern different things; keep aligned by intent, don't merge.

    // Tier 1 - HARD exclude, stripped up front (randomizeTrainerMovesets) so they reach no slot. NB feint = 364
    // (Protect-breaker), NOT feintAttack = 185 (a fine 60-BP Dark move). suckerPunch is in goodWeakMoves; this
    // strip overrides that whitelist trainer-side. Why each is unusable to the AI:
    //  feint        - Protect-breaker; AI can't know the player will Protect
    //  suckerPunch  - only works if the target attacks that turn - unpredictable
    //  counter      - needs to predict a physical hit
    //  mirrorCoat   - needs to predict a special hit
    //  metalBurst   - needs to predict either
    //  bide         - stores damage over 2-3 turns with no prediction
    //  fling        - fails unless the user holds a usable item, which the AI can't guarantee
    //  naturalGift  - fails unless the user holds a Berry, which the AI can't guarantee
    //  lastResort   - unusable until all of the mon's other moves have been used
    private static final Set<Integer> AI_UNUSABLE_MOVES = Set.of(
            MoveIDs.feint, MoveIDs.suckerPunch, MoveIDs.counter, MoveIDs.mirrorCoat,
            MoveIDs.metalBurst, MoveIDs.bide, MoveIDs.fling, MoveIDs.naturalGift,
            MoveIDs.lastResort);

    // Tier 2 - WEIGHTED penalty: the AI can fire these but usually to little effect (self-KO it can't value,
    // delayed/relative-HP damage it can't time, a Perish/Belly-Drum plan it can't coordinate). They never outright
    // fail, so they're discouraged, not banned - kept as rare surprises via a heavy weight penalty.
    private static final Set<Integer> AI_FLAWED_MOVES = Set.of(
            MoveIDs.explosion, MoveIDs.selfDestruct, MoveIDs.trick, MoveIDs.switcheroo,
            MoveIDs.perishSong, MoveIDs.bellyDrum, MoveIDs.destinyBond, MoveIDs.endeavor,
            MoveIDs.doomDesire, MoveIDs.futureSight, MoveIDs.present, MoveIDs.rage,
            MoveIDs.beatUp, MoveIDs.punishment, MoveIDs.finalGambit);
    // Tuning knob (matches CHARGE penalty).
    private static final double AI_FLAWED_MOVE_WEIGHT_PENALTY = 0.15;

    // Demotes the AI-flawed moves above (1.0 otherwise), applied in every slot's weightedPick. Demotion, not a ban.
    private double aiUsabilityWeight(Move mv) {
        return AI_FLAWED_MOVES.contains(mv.number) ? AI_FLAWED_MOVE_WEIGHT_PENALTY : 1.0;
    }

    private static final double BAD_STRONG_MOVE_WEIGHT_PENALTY = 0.35;

    // Demotes GlobalConstants.badStrongMoves (strong attacks with a real drawback - self-KO, bad accuracy,
    // recharge) so a clean move wins all else equal, but a mon with only flawed options still gets one.
    private double badStrongMoveWeight(Move mv) {
        return GlobalConstants.badStrongMoves.contains(mv.number) ? BAD_STRONG_MOVE_WEIGHT_PENALTY : 1.0;
    }

    private static final double OHKO_MOVE_WEIGHT_PENALTY = 0.15;

    // OHKO moves store power 0 so they only surface in the wildcard slot. A 30%-accuracy instant KO on a boss is
    // unfair variance rather than authored challenge, so it's demoted to a rare surprise.
    private static final Set<Integer> OHKO_MOVES = Set.of(
            MoveIDs.fissure, MoveIDs.hornDrill, MoveIDs.guillotine, MoveIDs.sheerCold);

    private double ohkoWeight(Move mv) {
        return OHKO_MOVES.contains(mv.number) ? OHKO_MOVE_WEIGHT_PENALTY : 1.0;
    }

    // Reliability as a difficulty lever: a boss leaning on reliable moves is scarier than one packing a flashy
    // coin-flip nuke, so this softly demotes low-accuracy moves for the Boss/Important curated slots (STAB,
    // coverage, status) only - Regular second attacks and every tier's wildcards skip it and stay loose. A demotion,
    // never a ban.
    //
    // mv.hitratio is 0-100, with one trap: never-miss moves (Swift, Aerial Ace, ...) and no-check status moves
    // (Swords Dance, Rest) store hitratio == getPerfectAccuracy() (0 in most gens), NOT 100 - read as perfectly
    // reliable, not 0% accurate. At/above RELIABLE_ACCURACY the weight is 1.0; below it falls off as
    // (accuracy/RELIABLE_ACCURACY)^ACCURACY_PENALTY_EXPONENT (80% keeps ~0.79, 50% keeps ~0.31). Tuning knobs.
    private static final double RELIABLE_ACCURACY = GlobalConstants.RELIABLE_ACCURACY_THRESHOLD;
    // Non-final so MovesetProfileRandomizerTest can sweep it (-Dbm.accuracyexp); treat as a constant in
    // production. Tuning knob.
    static double ACCURACY_PENALTY_EXPONENT = 2.0;

    private double accuracyWeight(Move mv) {
        double acc = mv.hitratio;
        if (acc == romHandler.getPerfectAccuracy() || acc >= RELIABLE_ACCURACY) {
            return 1.0;
        }
        return Math.pow(acc / RELIABLE_ACCURACY, ACCURACY_PENALTY_EXPONENT);
    }

    // Weather moves are only worth running if the Pokemon benefits from that weather, and redundant if its own
    // ability already sets it.
    private static final Set<Integer> RAIN_BENEFIT_ABILITIES = Set.of(
            AbilityIDs.swiftSwim, AbilityIDs.rainDish, AbilityIDs.drySkin, AbilityIDs.hydration);
    private static final Set<Integer> RAIN_SETTER_ABILITIES = Set.of(
            AbilityIDs.drizzle, AbilityIDs.primordialSea);
    private static final Set<Integer> SUN_BENEFIT_ABILITIES = Set.of(
            AbilityIDs.chlorophyll, AbilityIDs.solarPower, AbilityIDs.leafGuard, AbilityIDs.flowerGift, AbilityIDs.harvest);
    private static final Set<Integer> SUN_SETTER_ABILITIES = Set.of(
            AbilityIDs.drought, AbilityIDs.desolateLand);
    private static final Set<Integer> SAND_BENEFIT_ABILITIES = Set.of(
            AbilityIDs.sandVeil, AbilityIDs.sandRush, AbilityIDs.sandForce);
    private static final Set<Integer> SAND_SETTER_ABILITIES = Set.of(AbilityIDs.sandStream);
    private static final Set<Integer> HAIL_BENEFIT_ABILITIES = Set.of(
            AbilityIDs.snowCloak, AbilityIDs.iceBody, AbilityIDs.slushRush);
    private static final Set<Integer> HAIL_SETTER_ABILITIES = Set.of(AbilityIDs.snowWarning);

    // Dream Eater and Nightmare only do anything to a sleeping target, so one of these must also be carried. Yawn
    // counts - it puts the target to sleep next turn.
    private static final Set<Integer> SLEEP_INDUCING_MOVES = Set.of(
            MoveIDs.hypnosis, MoveIDs.sleepPowder, MoveIDs.spore, MoveIDs.sing,
            MoveIDs.grassWhistle, MoveIDs.lovelyKiss, MoveIDs.darkVoid, MoveIDs.yawn);

    // SolarBeam/Solar Blade skip their charge turn only under sun, guaranteed by a Sunny Day in the set or a
    // sun-setting ability (special-cased in isDependencyUnmet).
    private static final Set<Integer> SUN_DEPENDENT_MOVES = Set.of(MoveIDs.solarBeam, MoveIDs.solarBlade);

    // Moves useless without an "enabler": Snore/Sleep Talk need Rest, Spit Up/Swallow need Stockpile, Dream
    // Eater/Nightmare need a sleep-inducer, SolarBeam/Solar Blade need Sunny Day (or a sun ability). Maps
    // dependent move -> enablers, ANY of which satisfies the dependency.
    private static final Map<Integer, Set<Integer>> DEPENDENT_MOVE_ENABLERS = Map.of(
            MoveIDs.snore, Set.of(MoveIDs.rest),
            MoveIDs.sleepTalk, Set.of(MoveIDs.rest),
            MoveIDs.spitUp, Set.of(MoveIDs.stockpile),
            MoveIDs.swallow, Set.of(MoveIDs.stockpile),
            MoveIDs.dreamEater, SLEEP_INDUCING_MOVES,
            MoveIDs.nightmare, SLEEP_INDUCING_MOVES,
            MoveIDs.solarBeam, Set.of(MoveIDs.sunnyDay),
            MoveIDs.solarBlade, Set.of(MoveIDs.sunnyDay));

    // Moves that only pay off with an ally or multiple targets, dead weight (or actively harmful, e.g. Heal
    // Pulse on the lone opponent) in a single battle, so they're stripped there. The broader
    // GlobalConstants.doubleBattleMoves list also covers spread moves fine in singles too; those go through
    // trimMoveList instead.
    private static final Set<Integer> DOUBLES_ONLY_MOVES = Set.of(
            MoveIDs.allySwitch, MoveIDs.coaching, MoveIDs.followMe, MoveIDs.healPulse,
            MoveIDs.helpingHand, MoveIDs.ragePowder, MoveIDs.wideGuard, MoveIDs.decorate);

    // A move worth slotting because the trainer fights with an ally on the field.
    private static boolean isDoublesSupportMove(Move mv) {
        return DOUBLES_ONLY_MOVES.contains(mv.number) || GlobalConstants.doubleBattleMoves.contains(mv.number);
    }

    // Odds a doubles-format Pokemon that can learn a support move actually gets one slotted - without this nudge
    // such moves almost never win a wildcard roll, so Twins/Couples never ran doubles tech. At most one per
    // Pokemon, so it stays flavour. Tuning knob.
    private static final double DOUBLES_SUPPORT_MOVE_CHANCE = 0.5;

    // Fixed/proportional-damage moves store no usable base power (0 or 1), locking them out of the power-band
    // math and thus the STAB/coverage slots and damaging fallback. Give them a synthetic "effective power" so
    // they can compete. Deliberately a curated subset of GlobalConstants.noPowerNonStatusMoves - only these two
    // shapes get synthetic power; the rest stays wildcard-only.
    // Level-based: deal damage equal to the user's level.
    private static final Set<Integer> LEVEL_DAMAGE_MOVES = Set.of(MoveIDs.seismicToss, MoveIDs.nightShade);
    // HP-proportional (Super Fang/Nature's Madness halve the target's HP, Endeavor drops it to the user's): real
    // output swings with current HP, so rank by a rough ~level proxy rather than excluding them.
    private static final Set<Integer> HP_PROPORTIONAL_DAMAGE_MOVES = Set.of(
            MoveIDs.superFang, MoveIDs.naturesMadness, MoveIDs.endeavor);

    // Delayed hits landing two turns later: typeless before gen 5, and a poor fit for the one guaranteed STAB
    // slot even after. Kept out of that slot only.
    private static final Set<Integer> DELAYED_TYPELESS_STAB_MOVES = Set.of(MoveIDs.futureSight, MoveIDs.doomDesire);

    // The damage a move actually deals, on the same scale as power*hitCount so it can be ranked. Returns 0 for
    // status moves and other power<=1 moves deliberately left wildcard-only (OHKO gimmicks, counter/mirror-coat).
    private static double effectivePower(Move mv, int level) {
        if (mv == null) {
            return 0;
        }
        if (mv.isDamaging()) {
            return mv.power * mv.hitCount;
        }
        if (isSyntheticDamageMove(mv)) {
            return level;
        }
        return 0;
    }

    private static boolean isSyntheticDamageMove(Move mv) {
        return LEVEL_DAMAGE_MOVES.contains(mv.number) || HP_PROPORTIONAL_DAMAGE_MOVES.contains(mv.number);
    }

    // A move that must never fill the type-locked STAB slot: its damage ignores type or is delayed and typeless,
    // so it earns neither STAB nor super-effectiveness there. Allowed in other slots.
    private static boolean isStabSlotIneligible(Move mv) {
        return isSyntheticDamageMove(mv) || DELAYED_TYPELESS_STAB_MOVES.contains(mv.number);
    }

    // A move that can fill an attacking slot (STAB or coverage): a real, level-appropriate damaging move. No
    // MIN_DAMAGING_MOVE_POWER floor here - it culled weak-but-level-appropriate STABs (Leech Life 20 BP, Mega
    // Drain 40), collapsing low-level variety to one move per type. Recoil/accuracy/charge/AI downsides are all
    // handled softly elsewhere by the pick-slot weights, never as a hard ban here.
    private boolean isAttackSlotEligible(Move mv, int level) {
        if (mv == null || mv.category == MoveCategory.STATUS) {
            return false;
        }
        return effectivePower(mv, level) > 0;
    }

    // Slot 1: a STAB attacking move. Over-level moves are removed by the hard ceiling; levelAppropriatenessWeight
    // then softly demotes below-level ones (firmer for bosses). Boss/Important also lean toward the stronger
    // in-band move and reliability; Regular trainers pick flat across power so a weak level-up STAB still
    // competes with a universal TM. Both tiers nudge a committed attacker toward its category. teamRepeatWeight's
    // exact-move term stops a mono-type team stacking the identical STAB move; its type term self-cancels among
    // same-type candidates, so the mon is steered to a different move of its type, never off-type.
    private Move pickStabMove(Species pk, int ability, List<Move> pool, int level, AttackerProfile profile,
                              List<Move> exclude, boolean bossTier) {
        Type t1 = pk.getPrimaryType(false);
        Type t2 = pk.getSecondaryType(false);
        List<Move> candidates = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> mv.type == t1 || (t2 != null && mv.type == t2))
                .filter(mv -> isAttackSlotEligible(mv, level))
                .filter(mv -> !isStabSlotIneligible(mv))
                // goodWeakMoves (priority chip/utility, e.g. Aqua Jet) are reserved for coverage/wildcard slots,
                // not a mon's main STAB - also stops the large low-BP set from swamping the pick. The fallback
                // below still lets one through on a genuinely starved pool, so the mon keeps its identity.
                .filter(mv -> !GlobalConstants.goodWeakMoves.contains(mv.number))
                // Fake Out only fires the turn the user switches in - a dead pick as a main STAB.
                .filter(mv -> mv.number != MoveIDs.fakeOut)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            // No good-damaging STAB at all: fall back to any damaging STAB (effectivePower > 0 guaranteed here).
            candidates = pool.stream()
                    .filter(mv -> !exclude.contains(mv))
                    .filter(mv -> effectivePower(mv, level) > 0)
                    .filter(mv -> !isStabSlotIneligible(mv))
                    .filter(mv -> mv.type == t1 || (t2 != null && mv.type == t2))
                    .collect(Collectors.toList());
        }
        // Soft ability anti-synergy applies here too, not just wildcards: a weather/aura mon shouldn't be steered
        // into a STAB its ability undercuts (Drizzle -> Fire, Drought -> Water).
        if (hasAbilities) {
            candidates = updateMovesConsideringAbilitySynergies(ability, candidates);
        }
        // Boss-only: cull STABs below the level-scaled floor, an ABSOLUTE bar (not relative to the pool's best) so
        // weak candidate mass can't capture the pick on a big BP rung (a mono-type Rock pool once collapsed to
        // Stone Edge/Head Smash only, deleting special Probopass's Power Gem). Keep-best guard: if every candidate
        // is below the floor, keep the full list so it never empties. Regulars keep the full loose pool.
        if (bossTier && candidates.size() > 1) {
            double floor = centerPower(level) * POWER_FLOOR_FRACTION;
            List<Move> strong = candidates.stream()
                    .filter(mv -> effectivePower(mv, level) >= floor)
                    .collect(Collectors.toList());
            if (!strong.isEmpty()) {
                candidates = strong;
            }
        }
        return weightedPick(candidates, mv -> {
            double ep = effectivePower(mv, level);
            return (bossTier ? powerSelectionWeight(ep) : 1.0) * categoryPreference(mv, profile)
                    * practicalValueWeight(mv, ability, exclude)
                    * availabilityWeight(mv) * aiUsabilityWeight(mv) * badStrongMoveWeight(mv)
                    * teamRepeatWeight(mv, level, STAB_TEAM_MOVE_REPEAT_PENALTY)
                    // The hard floor above already clears POWER_FLOOR_FRACTION for boss candidates, making the
                    // strict boss exponent redundant here, so always use the looser Regular exponent.
                    * levelAppropriatenessWeight(mv, level, false)
                    * (bossTier ? accuracyWeight(mv) : 1.0);
        });
    }

    // Slot 2: a coverage move hitting a type that resists the STAB move; SE-gated, blind-spot-weighted, and
    // (for a committed attacker) nudged toward the Pokemon's preferred damage category.
    private Move pickCoverageMove(Species pk, int ability, List<Move> pool, int level, Type stabType,
                                  AttackerProfile profile, List<Move> exclude) {
        if (stabType == null) {
            return null;
        }
        TypeTable tt = typeTable;
        if (!tt.getTypes().contains(stabType)) {
            return null;
        }
        Set<Type> holes = resistantOrImmuneTypes(tt, stabType);
        Set<Type> blindSpots = computeBlindSpots(pk, tt, holes);

        List<Move> eligible = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> mv.type != stabType)
                .filter(mv -> isAttackSlotEligible(mv, level))
                .filter(mv -> tt.getTypes().contains(mv.type))
                .filter(mv -> coversAny(tt, mv.type, holes, false))
                .collect(Collectors.toList());
        if (eligible.isEmpty()) {
            return null;
        }
        if (hasAbilities) {
            eligible = updateMovesConsideringAbilitySynergies(ability, eligible);
        }
        // Strongly prefer super-effective coverage as a weight, not a hard gate, so bosses read as authored
        // rather than perfectly optimized.
        return weightedPick(eligible, mv -> {
            double ep = effectivePower(mv, level);
            double weight = powerSelectionWeight(ep);
            if (coversAny(tt, mv.type, holes, true)) {
                weight *= COVERAGE_SUPER_EFFECTIVE_BONUS;
            }
            if (coversAny(tt, mv.type, blindSpots, false)) {
                weight *= COVERAGE_BLIND_SPOT_BONUS;
            }
            return weight * categoryPreference(mv, profile) * practicalValueWeight(mv, ability, exclude)
                    * availabilityWeight(mv) * aiUsabilityWeight(mv) * badStrongMoveWeight(mv)
                    * teamRepeatWeight(mv, level) * levelAppropriatenessWeight(mv, level, true) * accuracyWeight(mv);
        });
    }

    // Types that resist or are immune to the given attacking type.
    private static Set<Type> resistantOrImmuneTypes(TypeTable tt, Type attackType) {
        Set<Type> types = new HashSet<>(tt.notVeryEffectiveWhenAttacking(attackType));
        types.addAll(tt.immuneWhenAttacking(attackType));
        return types;
    }

    // The Pokemon's true offensive blind spots: types resisting BOTH of its types (== STAB holes if mono-type).
    private Set<Type> computeBlindSpots(Species pk, TypeTable tt, Set<Type> stabHoles) {
        Type t1 = pk.getPrimaryType(false);
        Type t2 = pk.getSecondaryType(false);
        if (t2 == null || t1 == t2) {
            return stabHoles;
        }
        Set<Type> h1 = resistantOrImmuneTypes(tt, t1);
        h1.retainAll(resistantOrImmuneTypes(tt, t2));
        return h1;
    }

    // Does a move of attackType hit at least one of the given defending types for the required strength?
    private boolean coversAny(TypeTable tt, Type attackType, Set<Type> defenders, boolean superEffectiveOnly) {
        for (Type d : defenders) {
            if (!tt.getTypes().contains(d)) {
                continue;
            }
            Effectiveness eff = tt.getEffectiveness(attackType, d);
            if (superEffectiveOnly) {
                if (eff == Effectiveness.DOUBLE) {
                    return true;
                }
            } else if (eff == Effectiveness.NEUTRAL || eff == Effectiveness.DOUBLE) {
                return true;
            }
        }
        return false;
    }

    // Slot 3: a non-redundant good status move, picked FLAT among eligible candidates. The stat-boost gate lives
    // in isRedundantStatusMove: a single-category booster is eligible only if every attack picked shares its
    // category, so a mixed set gets neither. Beyond that gate no synergy or accuracy lean applies, so boss
    // status reads as varied rather than optimised.
    private Move pickStatusMove(Species pk, int ability, List<Move> pool, List<Move> picked, int level) {
        List<Move> candidates = pool.stream()
                .filter(mv -> !picked.contains(mv))
                .filter(mv -> mv.category == MoveCategory.STATUS)
                .filter(mv -> GlobalConstants.goodStatusMoves.contains(mv.number))
                .filter(mv -> !GlobalConstants.uselessMoves.contains(mv.number))
                .filter(mv -> !isRedundantStatusMove(mv, pk, ability, picked))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }
        // Flat pick, tempered by teamRepeatWeight (status has effectivePower 0, so only the exact-move tally
        // applies) and availabilityWeight so universal status TMs (Toxic, Protect, ...) don't flood the slot.
        return weightedPick(candidates, mv -> teamRepeatWeight(mv, level) * availabilityWeight(mv)
                * aiUsabilityWeight(mv));
    }

    // Global fallback for any unfillable slot: a damaging move softly weighted toward stronger picks, with the hard
    // ceiling (pool) and the tier-scaled levelAppropriatenessWeight keeping it level-appropriate.
    private Move pickBestDamaging(List<Move> pool, List<Move> exclude, int level, int ability, boolean bossTier) {
        List<Move> damaging = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> effectivePower(mv, level) > 0)
                .collect(Collectors.toList());
        // No-duplicate-attacking-type guard, relaxed automatically if it would leave nothing to pick.
        damaging = withoutDuplicateAttackingType(damaging, exclude, level);
        return weightedPick(damaging, mv ->
                powerSelectionWeight(effectivePower(mv, level)) * practicalValueWeight(mv, ability, exclude)
                        * availabilityWeight(mv) * aiUsabilityWeight(mv) * badStrongMoveWeight(mv)
                        * levelAppropriatenessWeight(mv, level, bossTier));
    }

    // Slot 2 for Regular-tier trainers: a plain second attack. Unlike the boss coverage slot it doesn't
    // hole-target and isn't power-weighted - a deliberate flat draw, since power-weighting let one ubiquitous
    // high-BP TM dominate the slot across the whole cast. Over-level moves are removed by the hard ceiling;
    // below-level ones are only gently demoted (Regular exponent). Category lean, the generic-neutral penalty,
    // the practical-value discount, and the no-duplicate-attacking-type guard still apply.
    private Move pickRegularSecondAttack(List<Move> pool, List<Move> exclude, int level, int ability,
                                         AttackerProfile profile) {
        List<Move> damaging = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> effectivePower(mv, level) > 0)
                .filter(mv -> isAttackSlotEligible(mv, level))
                .collect(Collectors.toList());
        if (damaging.isEmpty()) {
            // No "good" damaging move: relax to any damaging move (mirrors the STAB slot's fallback).
            damaging = pool.stream()
                    .filter(mv -> !exclude.contains(mv))
                    .filter(mv -> effectivePower(mv, level) > 0)
                    .collect(Collectors.toList());
        }
        damaging = withoutDuplicateAttackingType(damaging, exclude, level);
        if (hasAbilities) {
            damaging = updateMovesConsideringAbilitySynergies(ability, damaging);
        }
        // Flat power (no powerSelectionWeight) so weaker and stronger moves compete evenly - variety over
        // optimisation. The generic-neutral penalty demotes always-neutral universal TMs (Secret Power, Facade,
        // Return) that would otherwise flood this slot as flavourless filler. Both are demotions, not bans.
        return weightedPick(damaging, mv -> genericNeutralPenalty(mv) * categoryPreference(mv, profile)
                * practicalValueWeight(mv, ability, exclude) * availabilityWeight(mv) * aiUsabilityWeight(mv)
                * badStrongMoveWeight(mv) * teamRepeatWeight(mv, level)
                * levelAppropriatenessWeight(mv, level, false));
    }

    // Demotes "always-neutral" attacking moves (type super-effective against nothing in this ROM's type chart,
    // e.g. Normal), computed from the live TypeTable so it stays correct for custom/randomised charts. Tuning knob.
    private static final double GENERIC_NEUTRAL_MOVE_PENALTY = 0.2;

    private double genericNeutralPenalty(Move mv) {
        TypeTable tt = typeTable;
        if (mv.type == null || !tt.getTypes().contains(mv.type)) {
            return 1.0;
        }
        return tt.superEffectiveWhenAttacking(mv.type).isEmpty() ? GENERIC_NEUTRAL_MOVE_PENALTY : 1.0;
    }

    // Situational status moves whose payoff can never apply to this Pokemon based on intrinsic traits only
    // (type / ability / speed / weakness). Safe to strip from the movepool before any slot logic runs, so they
    // cannot leak in via a small movepool, the trim path, or a damaging fallback.
    private boolean isSituationalStatusRedundant(Move mv, Species pk, int ability) {
        switch (mv.number) {
            case MoveIDs.rainDance:
                if (RAIN_SETTER_ABILITIES.contains(ability)) {
                    return true;
                }
                return !(hasType(pk, Type.WATER) || RAIN_BENEFIT_ABILITIES.contains(ability));
            case MoveIDs.sunnyDay:
                if (SUN_SETTER_ABILITIES.contains(ability)) {
                    return true;
                }
                if (hasType(pk, Type.FIRE) || SUN_BENEFIT_ABILITIES.contains(ability)) {
                    return false;
                }
                // No standalone payoff: keep it only to enable a sun nuke this mon can learn (enforced later).
                return !currentSunnyDayNeedsSolar;
            case MoveIDs.sandstorm:
                if (SAND_SETTER_ABILITIES.contains(ability)) {
                    return true;
                }
                return !(hasType(pk, Type.ROCK) || hasType(pk, Type.GROUND) || hasType(pk, Type.STEEL)
                        || SAND_BENEFIT_ABILITIES.contains(ability));
            case MoveIDs.hail:
                if (HAIL_SETTER_ABILITIES.contains(ability)) {
                    return true;
                }
                return !(hasType(pk, Type.ICE) || HAIL_BENEFIT_ABILITIES.contains(ability));
            case MoveIDs.trickRoom:
                return pk.getSpeed() > TRICK_ROOM_MAX_SPEED;
            case MoveIDs.waterSport:
                // Water Sport only halves incoming Fire damage, so it is dead weight unless the mon fears Fire.
                return !isWeakTo(pk, Type.FIRE);
            case MoveIDs.mudSport:
                // Mud Sport only halves incoming Electric damage, so it is dead weight unless the mon fears Electric.
                return !isWeakTo(pk, Type.ELECTRIC);
            default:
                return false;
        }
    }

    // Electro Ball's power grows with outspeeding the target, Gyro Ball's with being slower. Strip whichever sits
    // the wrong side of the mon's base-Speed curve - the damaging-move analogue of the Trick Room gate.
    private boolean isSpeedMismatchedVariableMove(Move mv, Species pk) {
        if (mv.number == MoveIDs.electroBall) {
            return pk.getSpeed() < ELECTRO_BALL_MIN_SPEED;
        }
        if (mv.number == MoveIDs.gyroBall) {
            return pk.getSpeed() > GYRO_BALL_MAX_SPEED;
        }
        return false;
    }

    // Whether the Pokemon takes at least 2x damage (double or quadruple weakness) from the given attacking type.
    private boolean isWeakTo(Species pk, Type attackType) {
        TypeTable tt = typeTable;
        if (!tt.getTypes().contains(attackType)) {
            return false;
        }
        Effectiveness eff = tt.against(pk.getPrimaryType(false), pk.getSecondaryType(false)).get(attackType);
        return eff == Effectiveness.DOUBLE || eff == Effectiveness.QUADRUPLE;
    }

    // A status move is redundant when its situational payoff cannot apply, or a stat boost it grants is wasted.
    private boolean isRedundantStatusMove(Move mv, Species pk, int ability, List<Move> picked) {
        if (isSituationalStatusRedundant(mv, pk, ability)) {
            return true;
        }

        // A single-stat booster is wasted unless every attack picked shares its category, so a mixed set gets
        // neither. Boosters raising both stats are never gated.
        boolean boostsAtk = raisesUserAttack(mv);
        boolean boostsSpAtk = raisesUserSpecialAttack(mv);
        if (boostsAtk && !boostsSpAtk) {
            return !hasCategory(picked, MoveCategory.PHYSICAL) || hasCategory(picked, MoveCategory.SPECIAL);
        }
        if (boostsSpAtk && !boostsAtk) {
            return !hasCategory(picked, MoveCategory.SPECIAL) || hasCategory(picked, MoveCategory.PHYSICAL);
        }
        return false;
    }

    // Remaining slots: reuse the existing synergy-weighted pick + anti-synergy removal, but never pick a
    // redundant status move (so e.g. Spinarak never rolls Sunny Day and powers up the Fire moves it fears).
    private void fillWildcardMoves(TrainerPokemon tp, Species pk, int ability,
                                   List<Move> pool, List<Move> picked, boolean doubles, int level,
                                   boolean isBossTier) {
        if (picked.size() >= 4) {
            return;
        }

        // Doubles trainers sometimes get a double-battle support move, without every such mon carrying it: give
        // a set chance to slot exactly one before the ordinary wildcard fill.
        if (doubles) {
            maybeAddDoublesSupportMove(pool, picked);
            if (picked.size() >= 4) {
                return;
            }
        }

        List<Move> working = new ArrayList<>();
        for (Move mv : pool) {
            if (picked.contains(mv)) {
                continue;
            }
            if (mv.category == MoveCategory.STATUS && isRedundantStatusMove(mv, pk, ability, picked)) {
                continue;
            }
            working.add(mv);
        }

        // Only anti-synergy REMOVAL shapes the wildcard pool; additive synergy bonuses would be dead weight since
        // eligibleWildcards calls .distinct() before the pick. A near-uniform draw is the slot's intended surprise.
        double softMoveAntiBias = 0.5;

        if (hasAbilities) {
            working = updateMovesConsideringAbilitySynergies(ability, working);
        }
        working = updateMovesConsideringStatSynergies(pk, working);

        // Drop moves that hard-clash with an already-chosen role move (kept - a genuine pool shaper).
        for (Move rolePick : new ArrayList<>(picked)) {
            working.removeAll(MoveSynergy.getHardMoveAntiSynergy(rolePick, working));
        }

        while (picked.size() < 4) {
            List<Move> distinct = eligibleWildcards(working, pk, ability, picked, level);
            int slotsLeft = 4 - picked.size();
            if (distinct.isEmpty()) {
                break;
            }
            if (distinct.size() <= slotsLeft) {
                picked.addAll(distinct);
                break;
            }

            // A light practical-value discount keeps charge/recharge rarer here too; normal moves keep equal odds,
            // preserving the wildcard's surprise. teamRepeatWeight steers away from earlier teammates' picks. A
            // boss damaging pick is always a distinct attacking type (no-dup guard already ran), so the boss lean
            // broadens coverage. ohkoWeight applies only here: the wildcard is the sole slot an OHKO move can reach.
            Move move = weightedPick(distinct,
                    mv -> practicalValueWeight(mv, ability, picked) * teamRepeatWeight(mv, level)
                            * availabilityWeight(mv) * aiUsabilityWeight(mv) * badStrongMoveWeight(mv)
                            * ohkoWeight(mv) * levelAppropriatenessWeight(mv, level, isBossTier)
                            * (isBossTier && isAttackSlotEligible(mv, level) ? bossWildcardDamagingBonus : 1.0));
            picked.add(move);
            if (picked.size() >= 4) {
                break;
            }

            working.removeAll(Collections.singletonList(move));
            working.removeAll(MoveSynergy.getHardMoveAntiSynergy(move, working));

            List<Move> softAnti = MoveSynergy.getSoftMoveAntiSynergy(move, working);
            Collections.shuffle(softAnti, random);
            int softAntiCount = (int) (softMoveAntiBias * softAnti.size());
            for (int j = 0; j < softAntiCount; j++) {
                int needed = 4 - picked.size();
                if (countEligibleWildcards(working, pk, ability, picked, level, needed) <= needed) {
                    break;
                }
                working.remove(softAnti.get(j));
            }
        }
    }

    // With DOUBLES_SUPPORT_MOVE_CHANCE odds, slot one doubles-only support move the Pokemon can learn (Follow Me,
    // Wide Guard, ...). Damaging moves merely "fine in doubles" are left to ordinary wildcard logic.
    private void maybeAddDoublesSupportMove(List<Move> pool, List<Move> picked) {
        if (picked.size() >= 4 || random.nextDouble() >= DOUBLES_SUPPORT_MOVE_CHANCE) {
            return;
        }
        List<Move> candidates = pool.stream()
                .filter(mv -> !picked.contains(mv))
                .filter(TrainerMovesetRandomizer::isDoublesSupportMove)
                .distinct()
                .collect(Collectors.toList());
        if (!candidates.isEmpty()) {
            picked.add(candidates.get(random.nextInt(candidates.size())));
        }
    }

    // The distinct, currently-pickable wildcard moves (excludes picked moves, redundant status, and - via the
    // no-duplicate-attacking-type guard - any attack of a type the mon already attacks with).
    private List<Move> eligibleWildcards(List<Move> working, Species pk, int ability, List<Move> picked, int level) {
        List<Move> distinct = working.stream()
                .filter(mv -> !picked.contains(mv))
                .filter(mv -> !(mv.category == MoveCategory.STATUS && isRedundantStatusMove(mv, pk, ability, picked)))
                .distinct()
                .collect(Collectors.toList());
        return withoutDuplicateAttackingType(distinct, picked, level);
    }

    // Size of eligibleWildcards(...) without materialising its intermediate lists, with an early-out past `cap`.
    // Runs in the hot path (recomputed after every soft-anti removal), so mirrors eligibleWildcards exactly,
    // including its no-duplicate-attacking-type fallback.
    private int countEligibleWildcards(List<Move> working, Species pk, int ability, List<Move> picked, int level,
                                       int cap) {
        Set<Type> used = usedAttackingTypes(picked, level);
        Set<Move> seen = new HashSet<>();
        int distinctCount = 0;
        int filteredCount = 0;
        for (Move mv : working) {
            if (picked.contains(mv)) {
                continue;
            }
            if (mv.category == MoveCategory.STATUS && isRedundantStatusMove(mv, pk, ability, picked)) {
                continue;
            }
            if (!seen.add(mv)) {
                continue;
            }
            distinctCount++;
            if (used.isEmpty() || effectivePower(mv, level) <= 0 || !used.contains(mv.type)) {
                filteredCount++;
                if (filteredCount > cap) {
                    return filteredCount;
                }
            }
        }
        if (used.isEmpty()) {
            return distinctCount;
        }
        return filteredCount == 0 ? distinctCount : filteredCount;
    }

    // The attacking types already covered by picked moves, for the no-duplicate-attacking-type guard. Only REAL
    // damaging moves count: fixed/proportional-damage moves (Seismic Toss, Super Fang, ...) deal type-independent
    // damage and provide no coverage, so holding one shouldn't block a genuine attack of that type.
    private Set<Type> usedAttackingTypes(List<Move> picked, int level) {
        Set<Type> types = new HashSet<>();
        for (Move mv : picked) {
            if (effectivePower(mv, level) > 0 && !isSyntheticDamageMove(mv)) {
                types.add(mv.type);
            }
        }
        return types;
    }

    // Drops candidates whose attacking type is already covered by a picked move, so no mon gets two attacks of
    // the same type; status moves are never filtered. Falls back to the unfiltered list when that would leave
    // nothing to pick, so small/mono-type movepools still fill all four slots.
    private List<Move> withoutDuplicateAttackingType(List<Move> candidates, List<Move> picked, int level) {
        Set<Type> used = usedAttackingTypes(picked, level);
        if (used.isEmpty()) {
            return candidates;
        }
        List<Move> filtered = candidates.stream()
                .filter(mv -> effectivePower(mv, level) <= 0 || !used.contains(mv.type))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? candidates : filtered;
    }

    private static boolean hasType(Species pk, Type type) {
        return pk.getPrimaryType(false) == type || pk.getSecondaryType(false) == type;
    }

    // Whether this trainer fights in a format with an ally on the field (Double/Triple/Multi, not Single or
    // Rotation), so doubles-support moves are worth keeping. currBattleStyle is authoritative here since battle
    // style is finalized before movesets build; POTENTIAL multi-battle trainers are treated as singles.
    private boolean isDoublesFormatBattle(Trainer t) {
        // The base game always runs this trainer as a multi/double battle, even under a Single-style setting.
        if (t.getMultiBattleStatus() == Trainer.MultiBattleStatus.ALWAYS) {
            return true;
        }
        BattleStyle style = t.getCurrBattleStyle();
        return style.isBattleStyleChanged() && styleHasBattleAlly(style.getStyle());
    }

    // Double and Triple battles put an ally on the field; Single and Rotation (one active Pokemon per side) do not.
    private static boolean styleHasBattleAlly(BattleStyle.Style style) {
        return style == BattleStyle.Style.DOUBLE_BATTLE || style == BattleStyle.Style.TRIPLE_BATTLE;
    }

    // Enablers for a dependent move. Most live in the static DEPENDENT_MOVE_ENABLERS map; Baton Pass is the
    // inverse case (worthless with nothing to pass), whose enabler set - every self-boost move - is computed per
    // run and resolved here instead. Null for a non-dependent move.
    private Set<Integer> enablersFor(int moveNumber) {
        if (moveNumber == MoveIDs.batonPass) {
            return statBoostMoveNumbers;
        }
        return DEPENDENT_MOVE_ENABLERS.get(moveNumber);
    }

    private boolean isEnablerDependent(int moveNumber) {
        return moveNumber == MoveIDs.batonPass || DEPENDENT_MOVE_ENABLERS.containsKey(moveNumber);
    }

    // Whether a move's enabler dependency is unmet given the present moves and this mon's ability. Two special
    // cases beyond the static map: a sun-setting ability satisfies a sun nuke on its own, and Sunny Day itself
    // becomes dependent on the nuke only via currentSunnyDayNeedsSolar (so they appear together or not at all).
    private boolean isDependencyUnmet(int moveNumber, Set<Integer> presentMoves, int ability) {
        if (moveNumber == MoveIDs.sunnyDay) {
            return currentSunnyDayNeedsSolar && Collections.disjoint(presentMoves, SUN_DEPENDENT_MOVES);
        }
        Set<Integer> enablers = enablersFor(moveNumber);
        if (enablers == null) {
            return false;
        }
        if (SUN_DEPENDENT_MOVES.contains(moveNumber) && SUN_SETTER_ABILITIES.contains(ability)) {
            return false;
        }
        return Collections.disjoint(presentMoves, enablers);
    }

    private void stripUnsupportedDependentMoves(List<Move> pool, int ability) {
        Set<Integer> present = new HashSet<>();
        for (Move mv : pool) {
            present.add(mv.number);
        }
        pool.removeIf(mv -> isDependencyUnmet(mv.number, present, ability));
    }

    // Final cross-slot guarantee: drop any dependent whose enabler didn't make the chosen set, then backfill with
    // the next-best damaging move. The backfill pool excludes every dependent, or pickBestDamaging could
    // re-select the move just removed (Snore/Spit Up are themselves valid damaging moves).
    //
    // Backfills are written IN PLACE at the vacated index, highest index first, never appended - list position is
    // the move slot, and appending used to silently promote a later pick into slot 0 whenever the STAB slot's own
    // pick was dropped (the confirmed "off-type move in the STAB slot" bug, TODO 3 / Jynx).
    //
    // Index 0 is always the STAB slot (see randomizeTrainerMovesets: pickStabMove's result is picked.add()-ed
    // first, for both tiers). A high-power STAB is sometimes enabler-dependent itself (Dream Eater needs a
    // sleep-inducer, Snore needs Rest); when that enabler doesn't survive into the other 3 slots, a type-blind
    // backfill here silently erases the mon's only same-type attack (Batch 12 TODO 1 - e.g. Espeon losing Dream
    // Eater to off-type Bite). So slot 0's backfill prefers a same-type damaging replacement when one exists,
    // falling back to the normal any-type pool only if the mon has no other own-type damaging move at all.
    private void enforceEnablerDependencies(List<Move> picked, List<Move> distinctPool, int level, int ability,
                                            boolean bossTier, Type t1, Type t2) {
        Set<Integer> pickedNumbers = new HashSet<>();
        for (Move mv : picked) {
            pickedNumbers.add(mv.number);
        }
        List<Integer> unmetIndices = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            if (isDependencyUnmet(picked.get(i).number, pickedNumbers, ability)) {
                unmetIndices.add(i);
            }
        }
        if (unmetIndices.isEmpty()) {
            return;
        }
        List<Move> backfillPool = distinctPool.stream()
                .filter(mv -> !isEnablerDependent(mv.number))
                .collect(Collectors.toList());
        List<Move> ownTypeBackfillPool = backfillPool.stream()
                .filter(mv -> mv.type == t1 || (t2 != null && mv.type == t2))
                .collect(Collectors.toList());
        for (int i = unmetIndices.size() - 1; i >= 0; i--) {
            int idx = unmetIndices.get(i);
            List<Move> pool = idx == 0 && !ownTypeBackfillPool.isEmpty() ? ownTypeBackfillPool : backfillPool;
            Move fill = pickBestDamaging(pool, picked, level, ability, bossTier);
            if (fill != null) {
                picked.set(idx, fill);
            } else {
                picked.remove(idx);
            }
        }
    }

    private static boolean hasCategory(List<Move> moves, MoveCategory category) {
        for (Move mv : moves) {
            if (mv.category == category) {
                return true;
            }
        }
        return false;
    }

    private static boolean raisesOwnStat(Move mv) {
        return mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_USER
                || mv.statChangeMoveType == StatChangeMoveType.DAMAGE_USER;
    }

    private static boolean raisesUserAttack(Move mv) {
        return raisesOwnStat(mv) && (mv.hasSpecificStatChange(StatChangeType.ATTACK, true)
                || mv.hasSpecificStatChange(StatChangeType.ALL, true));
    }

    private static boolean raisesUserSpecialAttack(Move mv) {
        return raisesOwnStat(mv) && (mv.hasSpecificStatChange(StatChangeType.SPECIAL_ATTACK, true)
                || mv.hasSpecificStatChange(StatChangeType.SPECIAL, true)
                || mv.hasSpecificStatChange(StatChangeType.ALL, true));
    }

    // A dedicated setup move that raises any of the user's own stats - i.e. something Baton Pass can carry.
    // NO_DAMAGE_USER only: damaging riders are unreliable and self-debuffs aren't worth passing.
    private static boolean raisesAnyUserStat(Move mv) {
        if (mv.statChangeMoveType != StatChangeMoveType.NO_DAMAGE_USER) {
            return false;
        }
        for (Move.StatChange sc : mv.statChanges) {
            if (sc.type != StatChangeType.NONE && sc.stages > 0) {
                return true;
            }
        }
        return false;
    }

    // Removal only: prunes moves that clash with the Pokemon's ability (soft ability anti-synergy).
    private List<Move> updateMovesConsideringAbilitySynergies(int ability, List<Move> movesAtLevel) {
        return removeAntiSynergyMoves(movesAtLevel, MoveSynergy.getSoftAbilityMoveAntiSynergy(ability, movesAtLevel));
    }

    // Removal only: prunes moves that clash with the Pokemon's stats (stat anti-synergy).
    private List<Move> updateMovesConsideringStatSynergies(Species pk, List<Move> movesAtLevel) {
        return removeAntiSynergyMoves(movesAtLevel, MoveSynergy.getStatMoveAntiSynergy(pk, movesAtLevel));
    }

    // Drops the anti-synergy moves from the pool, but never returns an empty pool (keeps the original if pruning
    // would clear it).
    private List<Move> removeAntiSynergyMoves(List<Move> movesAtLevel, List<Move> antiSynergy) {
        List<Move> pruned = new ArrayList<>(movesAtLevel);
        for (Move mv : antiSynergy) {
            pruned.remove(mv);
        }
        return pruned.isEmpty() ? movesAtLevel : pruned;
    }

    // Classify the Pokemon as a physical, special or mixed attacker from its (ability-adjusted) Attack:Sp.Atk
    // ratio. Only a clear lean (>= ATTACKER_COMMIT_RATIO either way) commits; anything near 1:1 stays mixed.
    private AttackerProfile classifyAttacker(Species pk, int ability) {
        double ratio = getAtkSpatkRatio(pk, ability);
        if (ratio >= ATTACKER_COMMIT_RATIO) {
            return AttackerProfile.PHYSICAL;
        }
        if (ratio <= 1.0 / ATTACKER_COMMIT_RATIO) {
            return AttackerProfile.SPECIAL;
        }
        return AttackerProfile.MIXED;
    }

    // Selection-weight multiplier favouring a move that matches a committed attacker's preferred damage category.
    private static double categoryPreference(Move mv, AttackerProfile profile) {
        if (profile == AttackerProfile.PHYSICAL && mv.category == MoveCategory.PHYSICAL) {
            return CATEGORY_PREFERENCE_BONUS;
        }
        if (profile == AttackerProfile.SPECIAL && mv.category == MoveCategory.SPECIAL) {
            return CATEGORY_PREFERENCE_BONUS;
        }
        return 1.0;
    }

    private double getAtkSpatkRatio(Species pk, int ability) {
        int spatk = romHandler.generationOfPokemon() == 1 ? pk.getSpecial() : pk.getSpatk();
        double atkSpatkRatio = (double) pk.getAttack() / (double) spatk;
        if (hasAbilities) {
            switch (ability) {
                case AbilityIDs.hugePower:
                case AbilityIDs.purePower:
                    atkSpatkRatio *= 2;
                    break;
                case AbilityIDs.hustle:
                case AbilityIDs.gorillaTactics:
                    atkSpatkRatio *= 1.5;
                    break;
                case AbilityIDs.moxie:
                    atkSpatkRatio *= 1.1;
                    break;
                case AbilityIDs.soulHeart:
                    atkSpatkRatio *= 0.9;
                    break;
            }
        }
        return atkSpatkRatio;
    }

    // Writes up to four moves into a trainer Pokemon's move slots, zero-filling any unused slot.
    private static void writeMoves(TrainerPokemon tp, List<Move> moves) {
        for (int i = 0; i < 4; i++) {
            tp.getMoves()[i] = i < moves.size() ? moves.get(i).number : 0;
        }
    }

    // If already narrowed to four or fewer moves, write it straight into the Pokemon and report done; otherwise
    // leaves tp untouched and returns false.
    private static boolean writeMovesetIfSmallEnough(TrainerPokemon tp, List<Move> moves) {
        if (moves.size() > 4) {
            return false;
        }
        writeMoves(tp, moves);
        return true;
    }

    private List<Move> trimMoveList(TrainerPokemon tp, List<Move> movesAtLevel, boolean isDoubleBattle, int ability) {
        if (writeMovesetIfSmallEnough(tp, movesAtLevel)) {
            return new ArrayList<>();
        }

        movesAtLevel = movesAtLevel
                .stream()
                .filter(mv -> !GlobalConstants.uselessMoves.contains(mv.number) &&
                        (isDoubleBattle || !GlobalConstants.doubleBattleMoves.contains(mv.number)))
                .collect(Collectors.toList());

        if (writeMovesetIfSmallEnough(tp, movesAtLevel)) {
            return new ArrayList<>();
        }

        // The upstream "obsolete weaker same-type/category moves" cull is deliberately NOT applied here: run
        // before the role slots and team-repeat penalty, it collapsed each (type, category) to one strongest
        // move, forcing the same STAB onto multiple teammates on a mono-type team for no benefit - level and
        // quality are already enforced by the power-band filter and the pick-slot weights.

        if (hasAbilities) {
            List<Move> withoutHardAntiSynergy = new ArrayList<>(movesAtLevel);
            withoutHardAntiSynergy.removeAll(MoveSynergy.getHardAbilityMoveAntiSynergy(
                    ability,
                    movesAtLevel));

            if (!withoutHardAntiSynergy.isEmpty()) {
                movesAtLevel = withoutHardAntiSynergy;
            }
        }

        if (writeMovesetIfSmallEnough(tp, movesAtLevel)) {
            return new ArrayList<>();
        }
        return movesAtLevel;
    }

    // Builds the dex-wide availability tally: move number -> number of DISTINCT species that can learn it via any
    // source (level-up, egg, TM/HM, tutor); a species learning it by two routes counts once. Level is ignored -
    // this is raw learnability, not level-appropriateness. Called once per run, cost is O(species x sources).
    private void buildMoveAvailability() {
        Map<Integer, Set<Integer>> perSpecies = new HashMap<>();

        // Level-up moves (keyed by species number).
        for (Map.Entry<Integer, List<MoveLearnt>> e : allLevelUpMoves.entrySet()) {
            Set<Integer> set = perSpecies.computeIfAbsent(e.getKey(), k -> new HashSet<>());
            for (MoveLearnt ml : e.getValue()) {
                set.add(ml.move);
            }
        }

        // Egg moves (keyed by species number; lists may be null).
        for (Map.Entry<Integer, List<Integer>> e : allEggMoves.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            perSpecies.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
        }

        // TM moves (keyed by Species; boolean[] is 1-indexed against allTMMoves).
        for (Map.Entry<Species, boolean[]> e : allTMCompat.entrySet()) {
            Set<Integer> set = perSpecies.computeIfAbsent(e.getKey().getNumber(), k -> new HashSet<>());
            boolean[] compat = e.getValue();
            for (int i = 0; i < allTMMoves.size(); i++) {
                if (compat[i + 1]) {
                    set.add(allTMMoves.get(i));
                }
            }
            // HM moves (same compat array, indices immediately after the TM range). Previously never
            // tallied - see the matching pool fix in getMoveSelectionPoolAtLevel for why that starved
            // some Water/Flying-type mons (e.g. Surf/Waterfall) of their only real STAB.
            for (int i = 0; i < allHMMoves.size(); i++) {
                if (compat[allTMMoves.size() + i + 1]) {
                    set.add(allHMMoves.get(i));
                }
            }
        }

        // Tutor moves (same shape as TMs), only if this game has them.
        if (romHandler.hasMoveTutors() && allTutorCompat != null) {
            for (Map.Entry<Species, boolean[]> e : allTutorCompat.entrySet()) {
                Set<Integer> set = perSpecies.computeIfAbsent(e.getKey().getNumber(), k -> new HashSet<>());
                boolean[] compat = e.getValue();
                for (int i = 0; i < allTutorMoves.size(); i++) {
                    if (compat[i + 1]) {
                        set.add(allTutorMoves.get(i));
                    }
                }
            }
        }

        // Collapse per-species sets into per-move counts (each species contributes at most 1 to each move).
        moveAvailability = new HashMap<>();
        for (Set<Integer> moves : perSpecies.values()) {
            for (int moveNumber : moves) {
                moveAvailability.merge(moveNumber, 1, Integer::sum);
            }
        }
    }

    // Lazily loads the move-source caches (once per run) and builds the availability tally from them. Called
    // before the Smeargle branch in getMoveSelectionPoolAtLevel, so a Smeargle-only run still gets a populated
    // moveAvailability for the pick-slot weights.
    private void ensureMoveSourceCaches() {
        if (allLevelUpMoves == null) {
            allLevelUpMoves = romHandler.getMovesLearnt();
        }
        if (allEggMoves == null) {
            allEggMoves = romHandler.getEggMoves();
        }
        if (allTMCompat == null) {
            allTMCompat = romHandler.getTMHMCompatibility();
        }
        if (allTMMoves == null) {
            allTMMoves = romHandler.getTMMoves();
        }
        if (allHMMoves == null) {
            allHMMoves = romHandler.getHMMoves();
        }
        if (allTutorCompat == null && romHandler.hasMoveTutors()) {
            allTutorCompat = romHandler.getMoveTutorCompatibility();
        }
        // Loaded unconditionally, unlike allTutorCompat above: on tutorless games the getter returns an empty list,
        // and every reader of allTutorMoves gates on hasMoveTutors() first, so that empty list is never indexed.
        if (allTutorMoves == null) {
            allTutorMoves = romHandler.getMoveTutorMoves();
        }
        // Type chart (cached like the move sources above): romHandler.getTypeTable() re-parses the ROM's type
        // effectiveness bytes and builds a fresh TypeTable on EVERY call, so the per-candidate weight lambdas
        // (genericNeutralPenalty, isWeakTo) would otherwise rebuild it thousands of times per run. It never
        // changes during a run, so read it once here.
        if (typeTable == null) {
            typeTable = romHandler.getTypeTable();
        }
        // Availability tally: built once from the SAME cached maps - never re-calls the (uncached,
        // per-call-rebuilding) RomHandler getters. See availabilityWeight.
        if (moveAvailability == null) {
            buildMoveAvailability();
        }
        // Enabler set for the Baton Pass dependency (see enablersFor): the moves worth passing to a teammate.
        if (statBoostMoveNumbers == null) {
            statBoostMoveNumbers = new HashSet<>();
            for (Move mv : romHandler.getMoves()) {
                if (mv != null && raisesAnyUserStat(mv)) {
                    statBoostMoveNumbers.add(mv.number);
                }
            }
        }
    }

    private List<Move> getMoveSelectionPoolAtLevel(TrainerPokemon tp, boolean cyclicEvolutions) {

        ensureMoveSourceCaches();

        // Smeargle's Sketch lets it copy any move, so with UNCHANGED learnsets its real pool is basically just
        // Sketch - hand it the full usable move universe instead. Skipped when species movesets are randomised,
        // since Smeargle already gets a randomised learnset then.
        if (tp.getSpecies().getNumber() == SpeciesIDs.smeargle
                && settings.getMovesetsMod() == Settings.MovesetsMod.UNCHANGED) {
            return buildSmeargleSketchPool(tp);
        }

        List<Move> moves = romHandler.getMoves();

        // The mon's own learnset moves, collected so the hard power-band filter below can exempt them (level-up
        // moves are level-appropriate by definition, at any base power).
        List<Move> ownLevelUpMoves = allLevelUpMoves.getOrDefault(tp.getSpecies().getNumber(), List.of())
                .stream()
                .filter(ml -> (ml.level <= tp.getLevel() && ml.level != 0) || (ml.level == 0 && tp.getLevel() >= 30))
                .map(ml -> moves.get(ml.move))
                .distinct()
                .collect(Collectors.toList());
        Set<Integer> ownLevelUpMoveNumbers = ownLevelUpMoves.stream()
                .map(mv -> mv.number).collect(Collectors.toSet());
        List<Move> moveSelectionPoolAtLevel = new ArrayList<>(ownLevelUpMoves);

        // Pre-evo moves (100% availability); unlike the mon's own level-up moves, NOT exempt from the power-band
        // filter below.
        if (!cyclicEvolutions) {
            Species preEvo;
            if (romHandler.altFormesCanHaveDifferentEvolutions()) {
                preEvo = tp.getSpecies();
            } else {
                preEvo = tp.getSpecies().getBaseForme();
            }
            while (!preEvo.getEvolutionsTo().isEmpty()) {
                preEvo = preEvo.getEvolutionsTo().get(0).getFrom();
                moveSelectionPoolAtLevel.addAll(allLevelUpMoves.getOrDefault(preEvo.getNumber(), List.of())
                        .stream()
                        .filter(ml -> ml.level <= tp.getLevel())
                        .map(ml -> moves.get(ml.move))
                        .distinct().collect(Collectors.toList()));
            }
        }

        // TM moves (100% availability); the power-band filter below removes level-inappropriate ones.
        boolean[] tmCompat = allTMCompat.get(tp.getSpecies());
        if (tmCompat != null) {
            for (int i = 0; i < allTMMoves.size(); i++) {
                int tmMove = allTMMoves.get(i);
                if (tmCompat[i + 1]) {
                    moveSelectionPoolAtLevel.add(moves.get(tmMove));
                }
            }
            // HM moves (same compat array, indices immediately after the TM range; 100% availability like TMs).
            // Previously never added here at all - a trainer mon could never draw Surf/Waterfall/Fly/etc., which
            // starved some Water/Flying-type mons (e.g. Seel, no natural Water move) of any same-type attack.
            for (int i = 0; i < allHMMoves.size(); i++) {
                int hmMove = allHMMoves.get(i);
                if (tmCompat[allTMMoves.size() + i + 1]) {
                    moveSelectionPoolAtLevel.add(moves.get(hmMove));
                }
            }
        }

        // Move Tutor Moves (100% availability, same as TMs).
        if (romHandler.hasMoveTutors()) {
            boolean[] tutorCompat = allTutorCompat.get(tp.getSpecies());
            if (tutorCompat != null) {
                for (int i = 0; i < allTutorMoves.size(); i++) {
                    int tutorMove = allTutorMoves.get(i);
                    if (tutorCompat[i + 1]) {
                        moveSelectionPoolAtLevel.add(moves.get(tutorMove));
                    }
                }
            }
        }

        // Egg Moves
        if (!cyclicEvolutions) {
            Species firstEvo;
            if (romHandler.altFormesCanHaveDifferentEvolutions()) {
                firstEvo = tp.getSpecies();
            } else {
                firstEvo = tp.getSpecies().getBaseForme();
            }
            while (!firstEvo.getEvolutionsTo().isEmpty()) {
                firstEvo = firstEvo.getEvolutionsTo().get(0).getFrom();
            }
            if (allEggMoves.get(firstEvo.getNumber()) != null) {
                // 100% availability - egg moves carry no level requirement, so a low-level mon could otherwise
                // inherit a far-too-strong move; the power-band filter below removes over-level ones.
                moveSelectionPoolAtLevel.addAll(allEggMoves.get(firstEvo.getNumber())
                        .stream()
                        .map(moves::get)
                        .collect(Collectors.toList()));
            }
        }

        // Hard sliding ceiling: removes over-level attacking moves (status/gimmick and own level-up moves exempt).
        applyPowerBandFilter(moveSelectionPoolAtLevel, tp.getLevel(), ownLevelUpMoveNumbers);

        // Mutable: the caller's up-front removeIf strips narrow this pool in place.
        return moveSelectionPoolAtLevel.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
    }

    // The candidate pool for a trainer Smeargle: the full usable move universe (Sketch can copy anything), minus
    // banned moves, run through the same power-band ceiling as every other mon's pool. None of these are "own
    // level-up" moves, so everything downstream (trimMoveList, role slots) treats Smeargle identically.
    private List<Move> buildSmeargleSketchPool(TrainerPokemon tp) {
        Set<Integer> banned = new HashSet<>();
        banned.addAll(romHandler.getGameBreakingMoves());
        banned.addAll(romHandler.getIllegalMoves());
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        // Struggle is not a selectable move; a trainer Smeargle re-Sketching Sketch is pointless.
        banned.add(MoveIDs.struggle);
        banned.add(MoveIDs.sketch);

        List<Move> pool = new ArrayList<>();
        for (Move mv : romHandler.getMoves()) {
            if (mv == null) {
                // The move list is indexed by number; index 0 is blank.
                continue;
            }
            if (banned.contains(mv.number)) {
                continue;
            }
            pool.add(mv);
        }
        applyPowerBandFilter(pool, tp.getLevel(), Collections.emptySet());
        // Mutable: shares the caller's in-place removeIf strips with the ordinary pool.
        return pool.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
    }
}

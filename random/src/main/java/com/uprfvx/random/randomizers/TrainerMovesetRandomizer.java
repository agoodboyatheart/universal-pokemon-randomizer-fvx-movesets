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
    private List<Integer> allTMMoves, allTutorMoves;
    private Map<Integer, Integer> moveAvailability;   // move number -> # of species that can learn it (any source)
    private TypeTable typeTable;   // cached once per run: romHandler.getTypeTable() rebuilds from ROM on every call

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

        // Reset the cross-trainer per-species move tally (Batch 8) once per run, before any trainer is built.
        speciesMoveUsage.clear();

        for (Trainer t : trainers) {

            boolean doubles = isDoublesFormatBattle(t);

            // Team-level authoring: track the moves/attacking-types already handed to EARLIER Pokemon on this
            // same trainer, so the choosy slots can softly avoid repeating them (mainline teams feel authored
            // through role variety, not by stacking the same coverage/status on every mon). Reset per trainer.
            teamUsage = new TeamMoveUsage();

            for (TrainerPokemon tp : t.getPokemon()) {
                tp.setResetMoves(false);

                List<Move> movesAtLevel = getMoveSelectionPoolAtLevel(tp, isCyclicEvolutions);

                Species pk = tp.getSpecies();
                // The current species, so speciesRepeatWeight can consult the run-wide tally without threading
                // the Species through every picker (Batch 8).
                currentSpeciesNumber = pk.getNumber();
                int ability = hasAbilities ? romHandler.getAbilityForTrainerPokemon(tp) : 0;

                // Strip intrinsically-redundant situational status moves (weather / Trick Room) up front, so they
                // cannot slip in via a small movepool or the trim / fallback paths that skip the per-slot gate.
                movesAtLevel.removeIf(mv -> isSituationalStatusRedundant(mv, pk, ability));

                // AI-unusable moves (Feint, Counter, Focus Punch, ...): the ROM battle AI is a greedy single-turn
                // scorer that can't predict the player or run a multi-turn plan, so these are dead weight in its
                // hands. Strip them before any slot logic - one removal here closes ALL three doors they enter by
                // (attack slots, the status slot, the wildcard pool), mirroring the situational-status strip above.
                movesAtLevel.removeIf(mv -> AI_UNUSABLE_MOVES.contains(mv.number));

                // Some moves are dead weight unless an "enabler" move is also known: Sleep Talk and Snore only act
                // while the user sleeps (Rest), and Spit Up and Swallow consume Stockpile counters. If a dependent's
                // enabler is not even in the pool, strip it before it can claim a slot; the post-pass below then
                // guarantees the dependency even when the enabler is available but goes unpicked.
                stripUnsupportedDependentMoves(movesAtLevel);

                // In single battles, drop moves that only pay off with an ally / multiple targets (Follow Me,
                // Rage Powder, Wide Guard, Helping Hand, ...). Done up front so they cannot slip through the
                // small-pool or trim paths, mirroring the situational-status strip above. Genuine doubles keep them.
                if (!doubles) {
                    movesAtLevel.removeIf(TrainerMovesetRandomizer::isDoublesSupportMove);
                }

                // Fixed-constant damage moves (Dragon Rage 40, SonicBoom 20) deal the same flat damage at every
                // level, so on a low-level mon they one-shot. Their stored power is 1, so nothing else here reads
                // them as strong - strip them up front until the mon is high enough that the flat damage is fair,
                // so they cannot slip through the wildcard / small-pool / trim paths that skip the per-slot gate.
                movesAtLevel.removeIf(mv -> GlobalConstants.isFixedConstantDamageTooStrongForLevel(mv.number, tp.getLevel()));

                if (movesAtLevel.isEmpty()) {
                    // No custom moves to offer (e.g. a low-level rival starter whose selection pool is empty).
                    // Rather than leave the Pokemon moveless (all-zero slots), let the game assign it its natural
                    // level-up moveset at ROM-write time.
                    tp.setResetMoves(true);
                    continue;
                }

                movesAtLevel = trimMoveList(tp, movesAtLevel, doubles);

                if (movesAtLevel.isEmpty()) {
                    // trimMoveList already wrote a small (<=4) moveset directly into tp; nothing more to build.
                    continue;
                }

                int level = tp.getLevel();
                AttackerProfile profile = classifyAttacker(tp, pk);
                // Boss & Important trainers get the full curated structure: STAB + optimized coverage + status +
                // wildcard. Regular trainers are deliberately "dumbed down" so the tier gap reads like the mainline
                // games - they get a guaranteed STAB, a plain (non-optimized) second attacking move, and two
                // wildcards, but neither the super-effective hole-targeted coverage slot nor the status slot.
                boolean isBossTier = t.isBoss() || t.isImportant();

                List<Move> distinctPool = movesAtLevel.stream().distinct().collect(Collectors.toList());
                List<Move> picked = new ArrayList<>();

                if (distinctPool.size() <= 4) {
                    // Too few candidates to be choosy - just take what is available.
                    picked.addAll(distinctPool);
                } else {
                    // Slot 1: a STAB attacking move, base power scaled to the Pokemon's level.
                    Move stab = pickStabMove(pk, ability, distinctPool, level, profile, picked, isBossTier);
                    if (stab == null) {
                        stab = pickBestDamaging(distinctPool, picked, level, ability);
                    }
                    if (stab != null) {
                        picked.add(stab);
                    }

                    // Slot 2: a second attacking move. Bosses/Important get an optimized coverage move that hits
                    // what the STAB move is walled by; Regular trainers instead get a plain level-appropriate
                    // damaging move (no super-effective hole-targeting), so their teams look less curated.
                    Move secondAttack;
                    if (isBossTier) {
                        secondAttack = pickCoverageMove(pk, ability, distinctPool, level,
                                stab == null ? null : stab.type, profile, picked);
                    } else {
                        secondAttack = pickRegularSecondAttack(distinctPool, picked, level, ability, profile);
                    }
                    if (secondAttack == null) {
                        secondAttack = pickBestDamaging(distinctPool, picked, level, ability);
                    }
                    if (secondAttack != null) {
                        picked.add(secondAttack);
                    }

                    // Slot 3 (bosses/important only): a non-redundant status move.
                    if (isBossTier) {
                        Move status = pickStatusMove(pk, ability, distinctPool, picked, level);
                        if (status == null) {
                            status = pickBestDamaging(distinctPool, picked, level, ability);
                        }
                        if (status != null) {
                            picked.add(status);
                        }
                    }

                    // Remaining slots: wildcard picks reusing the existing synergy-weighted logic.
                    fillWildcardMoves(tp, pk, ability, distinctPool, picked, doubles, level);
                }

                // Enabler-dependency guarantee across every slot: drop any dependent whose enabler did not make the
                // final set (Snore/Sleep Talk without Rest, Spit Up/Swallow without Stockpile), then backfill with
                // the next-best damaging move.
                enforceEnablerDependencies(picked, distinctPool, level, ability);

                writeMoves(tp, picked);

                // Record this mon's final moves so later teammates can softly avoid repeating them. (The tiny-pool
                // and reset-moves paths above continue out before this, so they never contribute - those mons had
                // no real choice of moves anyway, so leaving them out of the team tally is fine.)
                for (Move mv : picked) {
                    teamUsage.record(mv, level);
                    // Also tally against this species' run-wide usage so later trainers' copies of the same
                    // species are softly steered off the moves it has already been given (Batch 8).
                    recordSpeciesMove(mv);
                }
            }
        }
        changesMade = true;
    }

    // ===== Role-based moveset construction (custom Better Movesets redesign) ==========================
    // Boss/Important: STAB + Coverage + Status + Wildcard. Regular: STAB + Coverage + Wildcard + Wildcard.
    // Any slot that cannot be filled falls back to the next-best damaging move, so every mon gets 4 moves.

    // ===== Team-level authoring (Batch 4 Issue D) ====================================================
    // A soft, per-trainer memory of what earlier teammates were given. The choosy attack/status/wildcard
    // slots multiply their pick weights by teamRepeatWeight, so a trainer's team tends toward role variety
    // instead of stacking the same move or attacking type on several mons - closer to how mainline teams
    // read as authored. Always a demotion, never a ban (weightedPick falls back to a uniform pick if every
    // weight collapses), so mono-type teams and small movepools still fill all four slots.
    private TeamMoveUsage teamUsage;

    // Weight multiplier for a candidate the current trainer's earlier mons already used. Geometric decay by
    // count: the FIRST time a move/type appears on the team it is unpenalised (x1.0); each prior use multiplies
    // in another penalty factor. The exact-move penalty bites harder than the attacking-type penalty - the same
    // move on every mon reads worse than merely sharing an attacking type. Tuning knobs.
    private static final double TEAM_MOVE_REPEAT_PENALTY = 0.35;
    private static final double TEAM_TYPE_REPEAT_PENALTY = 0.5;

    private double teamRepeatWeight(Move mv, int level) {
        if (teamUsage == null) {
            return 1.0;
        }
        double weight = Math.pow(TEAM_MOVE_REPEAT_PENALTY, teamUsage.moveUses(mv.number));
        if (effectivePower(mv, level) > 0) {
            weight *= Math.pow(TEAM_TYPE_REPEAT_PENALTY, teamUsage.typeUses(mv.type));
        }
        return weight;
    }

    // ===== Species-level authoring (Batch 8) =========================================================
    // teamRepeatWeight above stops one TEAM stacking the same move; it has no memory across trainers, so a
    // species still received its single best-available move on nearly every trainer it appeared on - e.g.
    // Electrode -> Signal Beam on 8/8 appearances, Ninjask -> X-Scissor on 6/6. That makes each species feel
    // "solved". speciesRepeatWeight is the cross-run analogue: a per-SPECIES tally of the moves that species
    // has already been handed anywhere in this run, geometrically down-weighting a repeat so the move spreads
    // across the species' appearances instead of hitting 100%. The FIRST appearance keeps the signature move at
    // full weight (x1.0); each later one demotes it. Soft, never a ban (weightedPick falls back to a uniform
    // draw), so a species with only ONE viable move for a slot still keeps it - the penalty only diversifies
    // where real alternatives exist, which is exactly the "authored, not random" behaviour we want.
    //
    // Per-run state (cleared at the top of randomizeTrainerMovesets). currentSpeciesNumber is set once per mon
    // so the six pick lambdas can consult the tally without threading the Species through every picker.
    private final Map<Integer, Map<Integer, Integer>> speciesMoveUsage = new HashMap<>();
    private int currentSpeciesNumber = -1;

    // Geometric decay per prior use of this move on this species this run. The primary tuning knob for Batch 8 -
    // calibrate against a fresh log so a high-appearance species' top move lands around 55-65% (moderate: keep
    // the signature common, not guaranteed), not below ~50% (which reads as random and erases species identity).
    private static final double SPECIES_MOVE_REPEAT_PENALTY = 0.6;

    private double speciesRepeatWeight(Move mv) {
        if (currentSpeciesNumber < 0) {
            return 1.0;
        }
        Map<Integer, Integer> counts = speciesMoveUsage.get(currentSpeciesNumber);
        int uses = counts == null ? 0 : counts.getOrDefault(mv.number, 0);
        return Math.pow(SPECIES_MOVE_REPEAT_PENALTY, uses);
    }

    // Record one picked move against the current species' run-wide tally.
    private void recordSpeciesMove(Move mv) {
        if (currentSpeciesNumber < 0) {
            return;
        }
        speciesMoveUsage.computeIfAbsent(currentSpeciesNumber, k -> new HashMap<>())
                .merge(mv.number, 1, Integer::sum);
    }

    // Per-trainer tally of moves and attacking types already assigned to earlier teammates. An "attacking" move
    // is one that deals real or synthetic damage (effectivePower > 0), matching the no-duplicate-type guard; a
    // status move contributes only to the exact-move tally, not the type tally.
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
    // Weight multiplier for a coverage move that hits a STAB hole super-effectively. A strong preference rather
    // than a hard gate: bosses still usually get SE coverage, but occasionally a flavourful neutral move (more
    // "authored", less Smogon-optimal). Mirrors COVERAGE_BLIND_SPOT_BONUS. Tuning knob.
    private static final double COVERAGE_SUPER_EFFECTIVE_BONUS = 3.5;
    private static final int TRICK_ROOM_MAX_SPEED = 60;

    // Attacking-stat profile, from the (ability-adjusted) Attack:Sp.Atk ratio. Committed attackers prefer moves
    // of their stronger category in the STAB and coverage slots; mixed attackers have no preference.
    private enum AttackerProfile { PHYSICAL, SPECIAL, MIXED }

    // How lopsided Attack vs Sp.Atk must be to commit to a category (>=1.25x, i.e. a ~25% edge). Below this the
    // mon is "mixed" and its picks are category-flat. Tuning knob.
    private static final double ATTACKER_COMMIT_RATIO = 1.25;
    // Weight multiplier applied to a move matching a committed attacker's preferred category (STAB, coverage,
    // Regular second attack). ~9x gives a committed attacker a roughly 90% category lean. Tuning knob.
    private static final double CATEGORY_PREFERENCE_BONUS = 9.0;

    // Availability normalization (Batch 6). The 100%-available pool (Batch 5) enters every learnable move into
    // every eligible mon's pool, so a move learnable by half the dex (universal TMs like Double Team / Toxic /
    // Return, broad tutors like Signal Beam) lands in nearly every pool while a rare signature move lands in
    // almost none. Under a near-uniform pick, a move's population frequency ends up proportional to how many
    // movepools it qualifies for - so ubiquitous moves win by exposure, not merit. This weight down-weights a
    // move by its dex-wide learnability so each move gets a fairer shot within its qualifying set. It is a soft
    // multiplicative weight applied in EVERY pick slot, never a ban: an availability >= 1 always yields a finite
    // weight > 0, so these common moves still appear - just at a fair rate, not a runaway one.
    //
    // A move's cross-population frequency scales as availability^(1 - k). k = 0 disables it; k = 1 fully flattens
    // frequency (risking obscure-move flooding). k ~ 0.4 is a partial correction: a move in 400 pools still
    // appears clearly more than one in 4 pools, it just no longer swamps it. The one exposed knob - tune vs logs.
    private static final double AVAILABILITY_NORMALIZATION_EXPONENT = 0.4;

    // Down-weights a move by how many species can learn it (see AVAILABILITY_NORMALIZATION_EXPONENT). Mirrors
    // teamRepeatWeight's shape. availability is >= 1 (a move in a candidate pool is learnable by >= 1 species),
    // so the result is always finite and > 0 - this never removes a move, only rebalances the odds.
    private double availabilityWeight(Move mv) {
        int a = moveAvailability.getOrDefault(mv.number, 1);
        return Math.pow(Math.max(a, 1), -AVAILABILITY_NORMALIZATION_EXPONENT);
    }

    // Within a single power band the Boss attack-slot pickers weight moves by effective power, so the stronger
    // in-band move is softly favoured. This exponent softens that power term so weaker same-band moves still
    // surface. 1.0 = power-proportional; 0.5 (square root) gives roughly a 55/45 split for a 60-vs-40 BP pair.
    // Applied only to the Boss STAB / coverage / fallback picks - Regular STAB and the Regular second attack are
    // flat (no power term). Across-band level-appropriateness is now enforced by the hard power-band filter
    // (applyPowerBandFilter), not by a soft weight. Tuning knob.
    private static final double POWER_SELECTION_EXPONENT = 0.5;

    // A move's power-based selection weight, softened by POWER_SELECTION_EXPONENT.
    private static double powerSelectionWeight(double effectivePower) {
        return Math.pow(effectivePower, POWER_SELECTION_EXPONENT);
    }

    // Hard power-band filter (Batch 5). Where the shared levelTierWeight is a SOFT bias used by the species
    // power-curve randomizer, the trainer side removes level-inappropriate attacking moves outright, so a
    // level's movepool reads as authored rather than occasionally sprouting an over-level nuke. Bands reuse the
    // shared BP edges (TIER_LOW_MAX_BP 60 / TIER_MID_MAX_BP 80); only the level windows are trainer-specific:
    //   Lv < 15        -> Low only     (remove effective power > 60)
    //   15 <= Lv < 30  -> Low + Avg    (remove effective power > 80)
    //   Lv >= 30       -> Avg + High   (remove effective power <= 60, i.e. drop the now-weak Low band)
    // Exemptions: status/gimmick moves (effectivePower 0) are never banded. The mon's OWN level-up moves are
    // exempt from the low/mid-level caps ONLY where they EXCEED the cap - a signature move learned early stays
    // usable (that is the whole point of the exemption). They are deliberately NOT exempt from the Lv30+ Low-band
    // drop: a move learned early is not level-appropriate for a high-level mon, so a Lv45 mon does not keep its
    // Lv3 Water Gun / Leech Life as a STAB. From the Lv30+ Low-removal, priority/utility weak moves (goodWeakMoves)
    // are still kept, so a high-level mon can run Aqua Jet / Sucker Punch / Rapid Spin.
    private static final int BAND_MID_UNLOCK_LEVEL = 15;   // Average band (61-80) becomes available here
    private static final int BAND_HIGH_UNLOCK_LEVEL = 30;  // High band (81+) available AND Low band dropped here

    private void applyPowerBandFilter(List<Move> pool, int level, Set<Integer> ownLevelUpMoveNumbers) {
        pool.removeIf(mv -> {
            double ep = effectivePower(mv, level);
            if (ep <= 0) {
                return false; // status / gimmick / non-attacking: never banded
            }
            boolean ownLevelUp = ownLevelUpMoveNumbers.contains(mv.number);
            if (level < BAND_MID_UNLOCK_LEVEL) {
                // Lv<15: keep Low only. Exempt own level-up moves that EXCEED the cap (a signature move learned
                // early is level-appropriate for that mon).
                return ep > TIER_LOW_MAX_BP && !ownLevelUp;
            }
            if (level < BAND_HIGH_UNLOCK_LEVEL) {
                return ep > TIER_MID_MAX_BP && !ownLevelUp;
            }
            // Lv >= 30: drop the now-weak Low band. The own-level-up exemption does NOT apply to this drop - a
            // move learned early is not level-appropriate for a high-level mon. Only priority/utility weak moves
            // (goodWeakMoves) survive.
            return ep <= TIER_LOW_MAX_BP && !GlobalConstants.goodWeakMoves.contains(mv.number);
        });
    }

    // A move's nominal base power is a poor proxy for its practical value in a trainer battle: charge moves waste
    // a turn winding up, and recharge moves waste the turn after. The power-weighted pickers (and the wildcard)
    // score these like clean hits, so strong-but-flawed moves (SolarBeam, Sky Attack, Hyper Beam, Giga Impact)
    // showed up far too often, even on top bosses. practicalValueWeight demotes them so they stay rare surprises
    // rather than staples - it does NOT ban them.
    //
    // Only PURE charge moves are penalised: the semi-invulnerable two-turn moves (Dig, Dive, Fly, Bounce) share
    // the same isChargeMove flag but are mainline staples we deliberately keep, so we curate an explicit set
    // instead of reading the flag. Geomancy is a STATUS move so it never reaches an attack slot and is omitted.
    private static final Set<Integer> PURE_CHARGE_MOVES = Set.of(
            MoveIDs.solarBeam, MoveIDs.solarBlade, MoveIDs.skyAttack, MoveIDs.razorWind,
            MoveIDs.skullBash, MoveIDs.freezeShock, MoveIDs.iceBurn, MoveIDs.meteorBeam);
    // Selection-weight multipliers for the two flawed classes (tuning knobs). Charge moves are penalised harder
    // than recharge moves - a wasted turn up front is worse than one after the hit has landed.
    private static final double CHARGE_MOVE_WEIGHT_PENALTY = 0.15;
    private static final double RECHARGE_MOVE_WEIGHT_PENALTY = 0.25;

    // Selection-weight multiplier reflecting a move's practical (not nominal) value: a penalty for pure charge
    // and recharge moves, 1.0 for everything else. SolarBeam / Solar Blade are exempt (charge skipped) when the
    // mon can guarantee sun - either a sun-setting ability, or a Sunny Day already picked into this moveset.
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

    // Batch 7 - filtering moves the ROM battle AI structurally cannot use. The AI is a greedy single-turn scorer:
    // it can't predict what the player will do this turn and it never runs a multi-turn plan, so any move whose
    // value depends on either is dead weight (or self-defeating) in its hands - which quietly LOWERS difficulty,
    // the opposite of this fork's goal. See project_memory/enemy-ai-move-limitations.md for the per-generation
    // reasoning. These are TRAINER-side only: the shared goodWeakMoves / goodStatusMoves lists still rate them for
    // a human player (and the species learnset randomizer), so we do not touch those lists.

    // Tier 1 - HARD exclude: structurally unusable, stripped from the trainer move pool up front (see the strip in
    // randomizeTrainerMovesets) so they reach no slot at all. NB feint = 364 (the Protect-breaker), NOT feintAttack
    // = 185 (a fine 60-BP Dark move). suckerPunch / endeavor are in goodWeakMoves and destinyBond is in
    // goodStatusMoves; stripping here overrides those whitelists trainer-side without editing the shared lists.
    private static final Set<Integer> AI_UNUSABLE_MOVES = Set.of(
            MoveIDs.feint,        // Protect-breaker; AI can't know the player will Protect
            MoveIDs.suckerPunch,  // only works if the target attacks that turn - unpredictable
            MoveIDs.counter,      // needs to predict a physical hit
            MoveIDs.mirrorCoat,   // needs to predict a special hit
            MoveIDs.metalBurst,   // needs to predict either
            MoveIDs.bide,         // stores damage over 2-3 turns with no prediction
            MoveIDs.focusPunch,   // fails if hit first; AI can't predict incoming damage
            MoveIDs.futureSight,  // delayed damage with no lookahead to set it up
            MoveIDs.doomDesire,   // delayed damage with no lookahead to set it up
            MoveIDs.endeavor,     // value depends on relative-HP timing the AI can't model
            MoveIDs.destinyBond); // needs to bait the player's killing blow

    // Tier 2 - WEIGHTED penalty: the AI CAN fire these, but usually to little effect (a self-KO it has no
    // self-faint awareness of, an item swap it can't value, a Perish/Belly-Drum plan it can't coordinate). Not
    // banned - kept as rare surprises via a heavy weight penalty, per the "authored, surprising, not optimal"
    // goal. (Two-turn PURE_CHARGE_MOVES are already demoted by practicalValueWeight, so they are not repeated.)
    private static final Set<Integer> AI_FLAWED_MOVES = Set.of(
            MoveIDs.explosion, MoveIDs.selfDestruct,
            MoveIDs.trick, MoveIDs.switcheroo,
            MoveIDs.perishSong, MoveIDs.bellyDrum);
    private static final double AI_FLAWED_MOVE_WEIGHT_PENALTY = 0.15; // tuning knob (matches CHARGE penalty)

    // Selection-weight multiplier that demotes the AI-flawed moves above; 1.0 for everything else. Multiplied into
    // every slot's weightedPick (STAB / coverage / regular-2nd / best-damaging / status / wildcard) so the penalty
    // applies wherever a flawed move could be chosen. Always a demotion, never a ban.
    private double aiUsabilityWeight(Move mv) {
        return AI_FLAWED_MOVES.contains(mv.number) ? AI_FLAWED_MOVE_WEIGHT_PENALTY : 1.0;
    }

    // Reliability as a difficulty lever (Batch 4 Issue E). For a Hardcore Nuzlocke the player fears variance, so a
    // boss that leans on reliable moves is scarier than one packing a flashy but coin-flip 70%-accuracy nuke. This
    // softly demotes low-accuracy moves so the Boss/Important CURATED slots (STAB, coverage, status) trend toward
    // dependable options; Regular second attacks and every tier's wildcards deliberately skip it, so lower tiers
    // stay loose and surprising. Always a demotion, never a ban.
    //
    // Accuracy lives in mv.hitratio on a 0-100 scale, with one trap: never-miss moves (Swift, Aerial Ace, Aura
    // Sphere, ...) and no-accuracy-check status moves (Swords Dance, Rest) store hitratio == getPerfectAccuracy()
    // (0 in most gens), NOT 100 - so they must be read as perfectly reliable, not as 0% accurate. Moves at or above
    // RELIABLE_ACCURACY are unpenalised; below it the weight falls off as (accuracy / RELIABLE_ACCURACY) raised to
    // ACCURACY_PENALTY_EXPONENT, so an 80% move keeps ~0.79 of its weight and a 50% move ~0.31. Tuning knobs.
    private static final double RELIABLE_ACCURACY = 90.0;
    private static final double ACCURACY_PENALTY_EXPONENT = 2.0;

    private double accuracyWeight(Move mv) {
        double acc = mv.hitratio;
        if (acc == romHandler.getPerfectAccuracy() || acc >= RELIABLE_ACCURACY) {
            return 1.0;
        }
        return Math.pow(acc / RELIABLE_ACCURACY, ACCURACY_PENALTY_EXPONENT);
    }

    // Weather moves are only worth running if the Pokemon benefits from that weather, and are pointless
    // (redundant) if the Pokemon's own ability already sets it.
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

    // Sleep-inducing moves: Dream Eater and Nightmare only do anything to a sleeping target, so one of these must
    // be in the same moveset for either to be worth carrying. Yawn counts - it puts the target to sleep next turn.
    private static final Set<Integer> SLEEP_INDUCING_MOVES = Set.of(
            MoveIDs.hypnosis, MoveIDs.sleepPowder, MoveIDs.spore, MoveIDs.sing,
            MoveIDs.grassWhistle, MoveIDs.lovelyKiss, MoveIDs.darkVoid, MoveIDs.yawn);

    // Moves that accomplish nothing unless an "enabler" move is also known: Snore and Sleep Talk only act while the
    // user sleeps (Rest), Spit Up and Swallow consume Stockpile counters, and Dream Eater and Nightmare only work on
    // a sleeping target (any sleep-inducer). Each may be selected only when at least one of its enablers is present
    // in the same moveset. Maps dependent move -> the set of enablers, ANY of which satisfies the dependency.
    private static final Map<Integer, Set<Integer>> DEPENDENT_MOVE_ENABLERS = Map.of(
            MoveIDs.snore, Set.of(MoveIDs.rest),
            MoveIDs.sleepTalk, Set.of(MoveIDs.rest),
            MoveIDs.spitUp, Set.of(MoveIDs.stockpile),
            MoveIDs.swallow, Set.of(MoveIDs.stockpile),
            MoveIDs.dreamEater, SLEEP_INDUCING_MOVES,
            MoveIDs.nightmare, SLEEP_INDUCING_MOVES);

    // Doubles-support moves that only pay off with an ally or multiple targets, so they are dead weight (or
    // actively harmful, e.g. Heal Pulse on the lone opponent) in a single battle and get stripped in singles.
    // The broader GlobalConstants.doubleBattleMoves list additionally covers damaging spread moves that are
    // fine in singles too (Muddy Water, Snarl, ...); those are handled by trimMoveList, not stripped here.
    private static final Set<Integer> DOUBLES_ONLY_MOVES = Set.of(
            MoveIDs.allySwitch, MoveIDs.coaching, MoveIDs.followMe, MoveIDs.healPulse,
            MoveIDs.helpingHand, MoveIDs.ragePowder, MoveIDs.wideGuard, MoveIDs.decorate);

    // A move worth slotting specifically because the trainer fights with an ally on the field: our curated
    // doubles-only tech plus the ROM's own double-battle move list.
    private static boolean isDoublesSupportMove(Move mv) {
        return DOUBLES_ONLY_MOVES.contains(mv.number) || GlobalConstants.doubleBattleMoves.contains(mv.number);
    }

    // How often a doubles-format Pokemon that CAN learn a double-battle support move (Follow Me, Helping Hand,
    // Wide Guard, ...) actually gets one slotted. Without this nudge such moves almost never win a wildcard roll
    // against the whole movepool, so Twins/Couples never ran doubles tech. Tuning knob: raise for more, lower
    // for less. At most one such move is ever added per Pokemon, so it stays flavour, not every slot.
    private static final double DOUBLES_SUPPORT_MOVE_CHANCE = 0.5;

    // Fixed / proportional-damage moves store no usable base power (0 or 1), so the power-band math that
    // ranks the attacking slots can't see them and they end up locked out of the STAB / coverage slots and
    // the damaging fallback. We give them a synthetic "effective power" so they can compete: for a low-level
    // (or already-damaged) mon, damage equal to the user's level (or a chunk of the target's HP) can
    // out-damage its real STAB.
    // Level-based: deal damage equal to the user's level.
    private static final Set<Integer> LEVEL_DAMAGE_MOVES = Set.of(MoveIDs.seismicToss, MoveIDs.nightShade);
    // HP-proportional: Super Fang / Nature's Madness halve the target's HP; Endeavor drops it to the user's.
    // Their real output swings with current HP (Endeavor does nothing at full HP but a lot when the user is
    // hurt, which trainer mons often are mid-battle), so we rank them by a rough ~level proxy rather than
    // excluding them.
    private static final Set<Integer> HP_PROPORTIONAL_DAMAGE_MOVES = Set.of(
            MoveIDs.superFang, MoveIDs.naturesMadness, MoveIDs.endeavor);

    // The damage a move actually deals, expressed on the same scale as power*hitCount so it can be ranked.
    // Returns 0 for status moves and for the many other power<=1 moves we deliberately leave wildcard-only
    // (OHKO gimmicks, counter/mirror-coat, variable-power moves), keeping them out of the attacking slots.
    private static double effectivePower(Move mv, int level) {
        if (mv == null || mv.category == MoveCategory.STATUS) {
            return 0;
        }
        if (mv.power > 1) {
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

    // A move that can fill an attacking slot (STAB or coverage): a real, level-appropriate damaging move.
    private boolean isAttackSlotEligible(Move mv, int level) {
        if (mv == null || mv.category == MoveCategory.STATUS) {
            return false;
        }
        if (effectivePower(mv, level) <= 0) {
            return false;
        }
        // Any real damaging move is eligible. Level-appropriateness is enforced SOLELY by the hard power-band filter
        // (applyPowerBandFilter). Two upstream culls are deliberately NOT applied trainer-side:
        //  - the isGoodDamaging / MIN_DAMAGING_MOVE_POWER (50) floor - it culled weak-but-level-appropriate STABs the
        //    band filter already permits (gen-4 Leech Life 20 BP, Mega Drain 40, Fury Cutter), collapsing low-level
        //    variety to the single move per type that cleared 50 BP (Bug -> Bug Bite);
        //  - the badStrongMoves hard-ban - it barred legitimate staples (Mega Kick, Take Down, Thrash, Slam, Strength,
        //    Uproar, Hyper Fang, Crush Claw, Dragon Rush...) from EVER filling an attack slot.
        // Recoil / low-accuracy / charge / AI-flawed downsides are handled SOFTLY by the pick-slot weights
        // (powerSelectionWeight, accuracyWeight, practicalValueWeight, aiUsabilityWeight) and by the up-front
        // AI_UNUSABLE strip + enabler-dependency checks - not by a hard eligibility ban. (Do NOT edit the shared
        // badStrongMoves list itself - the species moveset randomizer still relies on it.)
        return effectivePower(mv, level) > 0;
    }

    // Slot 1: a STAB attacking move. Level-appropriateness is already enforced by the hard power-band filter on
    // the pool, so this slot no longer applies a level->power weight. Boss/Important trainers get a soft power
    // lean (and the accuracy difficulty lever); Regular trainers pick flat across the available bands, so a weak
    // level-up STAB competes evenly with a universal TM. Both tiers nudge a committed attacker toward its category.
    // teamRepeatWeight applies here too (Batch 8 follow-up): STAB was the one choosy slot omitting it, which let a
    // mono-type team (a Ghost/Dragon gym leader) stack the identical STAB move on every mon - e.g. Ominous Wind on
    // 4 of Morty's Ghosts. Its exact-move term breaks that; its type term self-cancels among same-type candidates,
    // so a mono-type mon is still steered to a DIFFERENT move of its type, never off-type.
    private Move pickStabMove(Species pk, int ability, List<Move> pool, int level, AttackerProfile profile,
                              List<Move> exclude, boolean bossTier) {
        Type t1 = pk.getPrimaryType(false);
        Type t2 = pk.getSecondaryType(false);
        List<Move> candidates = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> mv.type == t1 || (t2 != null && mv.type == t2))
                .filter(mv -> isAttackSlotEligible(mv, level))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            // No good-damaging STAB move at all: fall back to any damaging STAB. effectivePower > 0 guaranteed here.
            candidates = pool.stream()
                    .filter(mv -> !exclude.contains(mv))
                    .filter(mv -> effectivePower(mv, level) > 0)
                    .filter(mv -> mv.type == t1 || (t2 != null && mv.type == t2))
                    .collect(Collectors.toList());
        }
        return weightedPick(candidates, mv -> {
            double ep = effectivePower(mv, level);
            return (bossTier ? powerSelectionWeight(ep) : 1.0) * categoryPreference(mv, profile)
                    * practicalValueWeight(mv, ability, exclude)
                    * availabilityWeight(mv) * aiUsabilityWeight(mv) * speciesRepeatWeight(mv)
                    * teamRepeatWeight(mv, level)
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
        Set<Type> holes = new HashSet<>(tt.notVeryEffectiveWhenAttacking(stabType));
        holes.addAll(tt.immuneWhenAttacking(stabType));
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
        // Strongly prefer super-effective coverage, but as a weight (not a hard gate): a neutral coverage move can
        // still occasionally win, so bosses read as authored rather than perfectly optimized.
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
                    * availabilityWeight(mv) * aiUsabilityWeight(mv) * speciesRepeatWeight(mv)
                    * teamRepeatWeight(mv, level) * accuracyWeight(mv);
        });
    }

    // The Pokemon's true offensive blind spots: types resisting BOTH of its types (== STAB holes if mono-type).
    private Set<Type> computeBlindSpots(Species pk, TypeTable tt, Set<Type> stabHoles) {
        Type t1 = pk.getPrimaryType(false);
        Type t2 = pk.getSecondaryType(false);
        if (t2 == null || t1 == t2) {
            return stabHoles;
        }
        Set<Type> h1 = new HashSet<>(tt.notVeryEffectiveWhenAttacking(t1));
        h1.addAll(tt.immuneWhenAttacking(t1));
        Set<Type> h2 = new HashSet<>(tt.notVeryEffectiveWhenAttacking(t2));
        h2.addAll(tt.immuneWhenAttacking(t2));
        h1.retainAll(h2);
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

    // Slot 3: a non-redundant good status move, picked FLAT among the eligible candidates (Batch 5 / Q3). The
    // stat-boost gate lives in the candidate filter via isRedundantStatusMove: an Attack-only booster is only
    // eligible if a physical attack was already picked, a Sp.Atk-only booster only if a special attack was, based
    // on the actually-picked attacks. Beyond that gate the choice is deliberately even - the old synergy bonus and
    // the accuracy (reliability) lean are dropped for status, so boss status reads as varied rather than optimised.
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
        // Flat pick among eligible candidates, tempered by teamRepeatWeight so the team trends toward varied status
        // (status moves have effectivePower 0, so only the exact-move repeat tally applies here) and by
        // availabilityWeight so universal status TMs (Toxic / Protect / Substitute / Double Team) no longer flood
        // the slot purely by being learnable by nearly the whole dex.
        return weightedPick(candidates, mv -> teamRepeatWeight(mv, level) * availabilityWeight(mv)
                * aiUsabilityWeight(mv) * speciesRepeatWeight(mv));
    }

    // Global fallback for any unfillable slot: a damaging move softly weighted toward stronger picks. Level-
    // appropriateness is already handled by the hard power-band filter on the pool, so no level->power term here.
    private Move pickBestDamaging(List<Move> pool, List<Move> exclude, int level, int ability) {
        List<Move> damaging = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> effectivePower(mv, level) > 0)
                .collect(Collectors.toList());
        // No-duplicate-attacking-type guard: a fallback slot should not hand out a second attack of a type the
        // mon already attacks with (relaxed automatically if that would leave nothing damaging to pick).
        damaging = withoutDuplicateAttackingType(damaging, exclude, level);
        return weightedPick(damaging, mv ->
                powerSelectionWeight(effectivePower(mv, level)) * practicalValueWeight(mv, ability, exclude)
                        * availabilityWeight(mv) * aiUsabilityWeight(mv) * speciesRepeatWeight(mv));
    }

    // Slot 2 for Regular-tier trainers: a plain second attacking move. Unlike the boss coverage slot it does NOT
    // hole-target super-effective types, and it is NOT power-weighted toward the strongest option - the flat draw
    // is deliberate. Power-weighting let one ubiquitous high-BP TM (e.g. Secret Power in Gen 3) dominate this slot
    // across the whole cast, which reads as "optimal", not "authored". Level-appropriateness is enforced by the
    // hard power-band filter on the pool. A committed attacker is still nudged toward its category (phys/special
    // lean), the generic-neutral penalty demotes always-neutral filler, the practical-value discount keeps
    // charge/recharge moves rare, and the no-duplicate-attacking-type guard still applies.
    private Move pickRegularSecondAttack(List<Move> pool, List<Move> exclude, int level, int ability,
                                         AttackerProfile profile) {
        List<Move> damaging = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> effectivePower(mv, level) > 0)
                .filter(mv -> isAttackSlotEligible(mv, level))
                .collect(Collectors.toList());
        if (damaging.isEmpty()) {
            // No "good" damaging move: relax to any damaging move (mirrors the STAB slot's fallback) so the slot
            // still fills. pickBestDamaging is the last-resort caller-side fallback if even this returns null.
            damaging = pool.stream()
                    .filter(mv -> !exclude.contains(mv))
                    .filter(mv -> effectivePower(mv, level) > 0)
                    .collect(Collectors.toList());
        }
        damaging = withoutDuplicateAttackingType(damaging, exclude, level);
        // Flat power (no powerSelectionWeight), so a 40 BP move competes evenly with a 70 BP one - variety over
        // optimisation. The generic-neutral penalty demotes always-neutral universal TMs (Normal-type Secret
        // Power, Facade, Return - learnable by nearly the whole dex, super-effective against nothing) which would
        // otherwise flood this slot as flavourless filler. Both are demotions, not bans.
        return weightedPick(damaging, mv -> genericNeutralPenalty(mv) * categoryPreference(mv, profile)
                * practicalValueWeight(mv, ability, exclude) * availabilityWeight(mv) * aiUsabilityWeight(mv)
                * speciesRepeatWeight(mv) * teamRepeatWeight(mv, level));
    }

    // Weight multiplier for the Regular second-attack slot: demotes "always-neutral" attacking moves - those whose
    // type is super-effective against nothing in this ROM's type chart (Normal in every mainline game, but computed
    // from the live TypeTable so it stays correct for custom/randomised type charts). 1.0 for any move that is at
    // least super-effective against something. Tuning knob.
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
                return !(hasType(pk, Type.FIRE) || SUN_BENEFIT_ABILITIES.contains(ability));
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

        // Stat-boost gate (inclusive): an Attack-only booster needs >=1 physical move already picked;
        // a Sp.Atk-only booster needs >=1 special move. Boosters raising both are never gated.
        boolean boostsAtk = raisesUserAttack(mv);
        boolean boostsSpAtk = raisesUserSpecialAttack(mv);
        if (boostsAtk && !boostsSpAtk) {
            return !hasCategory(picked, MoveCategory.PHYSICAL);
        }
        if (boostsSpAtk && !boostsAtk) {
            return !hasCategory(picked, MoveCategory.SPECIAL);
        }
        return false;
    }

    // Remaining slots: reuse the existing synergy-weighted pick + anti-synergy removal, but never pick a
    // redundant status move (so e.g. Spinarak never rolls Sunny Day and powers up the Fire moves it fears).
    private void fillWildcardMoves(TrainerPokemon tp, Species pk, int ability,
                                   List<Move> pool, List<Move> picked, boolean doubles, int level) {
        if (picked.size() >= 4) {
            return;
        }

        // Trainers who fight in doubles (Twins, Couples, gym double battles, ...) should sometimes actually run
        // a double-battle support move, without every such mon carrying identical tech. When the Pokemon can
        // learn one, give it a set chance to slot exactly one before the ordinary wildcard fill.
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

        // Only the anti-synergy REMOVAL passes shape the wildcard pool now. The old additive biases (STAB /
        // ability / stat / atk:spatk-ratio duplication) were dead weight: eligibleWildcards calls .distinct()
        // before the weightedPick, so duplicate copies never changed the odds. Leaving the wildcard a
        // near-uniform draw over the anti-synergy-trimmed pool is the intended "surprise" of the slot.
        double softMoveAntiBias = 0.5;

        if (hasAbilities) {
            working = updateMovesConsideringAbilitySynergies(tp, working);
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
            // On the last slot, drop moves that need a partner move we did not pick (e.g. Spit Up).
            if (slotsLeft == 1) {
                for (Move dependent : new ArrayList<>(distinct)) {
                    if (!GlobalConstants.requiresOtherMove.contains(dependent.number)) {
                        continue;
                    }
                    boolean hasRequired = false;
                    for (Move required : MoveSynergy.requiresOtherMove(dependent, working)) {
                        if (picked.contains(required)) {
                            hasRequired = true;
                            break;
                        }
                    }
                    if (!hasRequired) {
                        working.removeAll(Collections.singletonList(dependent));
                    }
                }
                distinct = eligibleWildcards(working, pk, ability, picked, level);
                if (distinct.isEmpty()) {
                    break;
                }
                if (distinct.size() <= slotsLeft) {
                    picked.addAll(distinct);
                    break;
                }
            }

            // A light practical-value discount so charge/recharge moves are rarer wildcards too; normal moves
            // keep equal odds (weightedPick is uniform when weights match), preserving the wildcard's surprise.
            // Any Sunny Day already picked lives in `picked`, so the SolarBeam sun exemption fires naturally here.
            // teamRepeatWeight then softly steers away from moves/attacking-types earlier teammates already used.
            Move move = weightedPick(distinct,
                    mv -> practicalValueWeight(mv, ability, picked) * teamRepeatWeight(mv, level)
                            * availabilityWeight(mv) * aiUsabilityWeight(mv) * speciesRepeatWeight(mv));
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
                if (eligibleWildcards(working, pk, ability, picked, level).size() <= (4 - picked.size())) {
                    break;
                }
                working.remove(softAnti.get(j));
            }
        }
    }

    // With DOUBLES_SUPPORT_MOVE_CHANCE odds, slot one double-battle support move the Pokemon can learn - the
    // genuinely doubles-only tech (Follow Me, Helping Hand, Rage Powder, Wide Guard, ...). The broad "fine in
    // doubles" damaging moves are left to surface through ordinary wildcard logic, so this stays on-theme.
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

    // The distinct, currently-pickable wildcard moves (excludes already-picked moves, redundant status moves,
    // and - via the no-duplicate-attacking-type guard - any attack of a type the mon already attacks with).
    private List<Move> eligibleWildcards(List<Move> working, Species pk, int ability, List<Move> picked, int level) {
        List<Move> distinct = working.stream()
                .filter(mv -> !picked.contains(mv))
                .filter(mv -> !(mv.category == MoveCategory.STATUS && isRedundantStatusMove(mv, pk, ability, picked)))
                .distinct()
                .collect(Collectors.toList());
        return withoutDuplicateAttackingType(distinct, picked, level);
    }

    // The attacking types already covered by the picked moves (a move counts as attacking when it deals real or
    // synthetic damage, i.e. effectivePower > 0; status moves and gimmicks are ignored).
    private Set<Type> usedAttackingTypes(List<Move> picked, int level) {
        Set<Type> types = new HashSet<>();
        for (Move mv : picked) {
            if (effectivePower(mv, level) > 0) {
                types.add(mv.type);
            }
        }
        return types;
    }

    // No-duplicate-attacking-type guard: drops candidates whose attacking type is already covered by a picked
    // move, so no mon ends up with two attacks of the same type. Status/gimmick moves are never filtered. Falls
    // back to the unfiltered list when the guard would leave nothing to pick, so small / mono-type movepools
    // still fill all four slots (the "always 4 moves" invariant wins over the guard).
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

    // Whether this trainer's Pokemon fight in a format where an ally shares the field, so doubles-support moves
    // (Follow Me, Wide Guard, Helping Hand, ...) are worth keeping. That means Double, Triple and Multi battles
    // - but NOT Single or Rotation battles (in Rotation only one Pokemon per side is active at a time). Battle
    // style is finalized (modifyBattleStyle) before movesets are built, so currBattleStyle is authoritative
    // here. POTENTIAL multi-battle trainers are treated as singles (only guaranteed multi-target formats count).
    private boolean isDoublesFormatBattle(Trainer t) {
        // The base game always runs this trainer as a multi/double battle (kept even under a Single-style setting).
        if (t.getMultiBattleStatus() == Trainer.MultiBattleStatus.ALWAYS) {
            return true;
        }
        // A forced (Single-style) or randomly-assigned battle style is stamped onto the trainer as currBattleStyle.
        BattleStyle style = t.getCurrBattleStyle();
        return style.isBattleStyleChanged() && styleHasBattleAlly(style.getStyle());
    }

    // Double and Triple battles put an ally on the field alongside the Pokemon; Single and Rotation battles
    // (Rotation only has one active Pokemon per side) do not, so ally-support moves are dead weight there.
    private static boolean styleHasBattleAlly(BattleStyle.Style style) {
        return style == BattleStyle.Style.DOUBLE_BATTLE || style == BattleStyle.Style.TRIPLE_BATTLE;
    }

    // Removes enabler-dependent moves whose enabler is absent from the pool, so they can never claim a slot.
    private static void stripUnsupportedDependentMoves(List<Move> pool) {
        Set<Integer> present = new HashSet<>();
        for (Move mv : pool) {
            present.add(mv.number);
        }
        pool.removeIf(mv -> {
            Set<Integer> enablers = DEPENDENT_MOVE_ENABLERS.get(mv.number);
            return enablers != null && Collections.disjoint(present, enablers);
        });
    }

    // Final cross-slot guarantee: drop any dependent whose enabler did not make the chosen set, then backfill to
    // four with the next-best damaging move. The backfill pool excludes every dependent, or pickBestDamaging could
    // re-select the move just removed (Snore and Spit Up are themselves valid damaging moves).
    private void enforceEnablerDependencies(List<Move> picked, List<Move> distinctPool, int level, int ability) {
        Set<Integer> pickedNumbers = new HashSet<>();
        for (Move mv : picked) {
            pickedNumbers.add(mv.number);
        }
        boolean removed = picked.removeIf(mv -> {
            Set<Integer> enablers = DEPENDENT_MOVE_ENABLERS.get(mv.number);
            return enablers != null && Collections.disjoint(pickedNumbers, enablers);
        });
        if (!removed) {
            return;
        }
        List<Move> backfillPool = distinctPool.stream()
                .filter(mv -> !DEPENDENT_MOVE_ENABLERS.containsKey(mv.number))
                .collect(Collectors.toList());
        Move fill;
        while (picked.size() < 4 && (fill = pickBestDamaging(backfillPool, picked, level, ability)) != null) {
            picked.add(fill);
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

    // Removal-only now: prunes moves that clash with the Pokemon's ability (soft ability anti-synergy). The
    // former synergy-ADDITION passes were dropped - they only added duplicate copies that eligibleWildcards
    // collapsed with .distinct() before the pick, so they never influenced anything.
    private List<Move> updateMovesConsideringAbilitySynergies(TrainerPokemon tp, List<Move> movesAtLevel) {
        List<Move> softAbilityMoveAntiSynergyList = MoveSynergy.getSoftAbilityMoveAntiSynergy(
                romHandler.getAbilityForTrainerPokemon(tp), movesAtLevel);
        List<Move> withoutSoftAntiSynergy = new ArrayList<>(movesAtLevel);
        for (Move mv : softAbilityMoveAntiSynergyList) {
            withoutSoftAntiSynergy.remove(mv);
        }
        if (!withoutSoftAntiSynergy.isEmpty()) {
            movesAtLevel = withoutSoftAntiSynergy;
        }
        return movesAtLevel;
    }

    // Removal-only now: prunes moves that clash with the Pokemon's stats (stat anti-synergy). The former
    // synergy-ADDITION pass was dropped for the same reason as in the ability helper above.
    private List<Move> updateMovesConsideringStatSynergies(Species pk, List<Move> movesAtLevel) {
        List<Move> statAntiSynergyList = MoveSynergy.getStatMoveAntiSynergy(pk, movesAtLevel);
        List<Move> withoutStatAntiSynergy = new ArrayList<>(movesAtLevel);
        for (Move mv : statAntiSynergyList) {
            withoutStatAntiSynergy.remove(mv);
        }
        if (!withoutStatAntiSynergy.isEmpty()) {
            movesAtLevel = withoutStatAntiSynergy;
        }
        return movesAtLevel;
    }

    // Classify the Pokemon as a physical, special or mixed attacker from its (ability-adjusted) Attack:Sp.Atk
    // ratio. Only a clear lean (>= ATTACKER_COMMIT_RATIO either way) commits; anything near 1:1 stays mixed.
    private AttackerProfile classifyAttacker(TrainerPokemon tp, Species pk) {
        double ratio = getAtkSpatkRatio(tp, pk);
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

    private double getAtkSpatkRatio(TrainerPokemon tp, Species pk) {
        int spatk = romHandler.generationOfPokemon() == 1 ? pk.getSpecial() : pk.getSpatk();
        double atkSpatkRatio = (double) pk.getAttack() / (double) spatk;
        if (hasAbilities) {
            switch (romHandler.getAbilityForTrainerPokemon(tp)) {
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

    // Writes up to four moves into a trainer Pokemon's move slots, zero-filling any unused slot (and ignoring
    // anything past the fourth move, matching the game's four-move limit). The one place that turns a chosen
    // move list into the Pokemon's actual moveset.
    private static void writeMoves(TrainerPokemon tp, List<Move> moves) {
        for (int i = 0; i < 4; i++) {
            tp.getMoves()[i] = i < moves.size() ? moves.get(i).number : 0;
        }
    }

    // If the list has already been narrowed to four or fewer moves, write it straight into the Pokemon and
    // report that trimming is finished (so trimMoveList can hand back an empty list to its caller). Returns
    // false, leaving tp untouched, while there are still more than four moves to narrow down.
    private static boolean writeMovesetIfSmallEnough(TrainerPokemon tp, List<Move> moves) {
        if (moves.size() > 4) {
            return false;
        }
        writeMoves(tp, moves);
        return true;
    }

    private List<Move> trimMoveList(TrainerPokemon tp, List<Move> movesAtLevel, boolean isDoubleBattle) {
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

        // The upstream "obsolete weaker same-type/category moves" cull is deliberately NOT applied to trainer
        // movesets. It reduced each (type, category) to its single strongest damaging move here - BEFORE the role
        // slots and the team-repeat penalty run - which collapsed a mono-type team's STAB options (every Dragon to
        // Outrage + Dragon Pulse, every Ground to Earthquake) and forced the same STAB onto multiple teammates.
        // Level-appropriateness is already enforced by the hard power-band filter, and move quality by
        // isAttackSlotEligible + the pick-slot weights, so the cull only cost intra-team variety without adding value.

        List<Move> requiresOtherMove = movesAtLevel
                .stream()
                .filter(mv -> GlobalConstants.requiresOtherMove.contains(mv.number)).collect(Collectors.toList());

        for (Move dependentMove : requiresOtherMove) {
            if (MoveSynergy.requiresOtherMove(dependentMove, movesAtLevel).isEmpty()) {
                movesAtLevel.remove(dependentMove);
            }
        }

        if (writeMovesetIfSmallEnough(tp, movesAtLevel)) {
            return new ArrayList<>();
        }

        // Remove hard ability anti-synergy moves

        if (hasAbilities) {
            List<Move> withoutHardAntiSynergy = new ArrayList<>(movesAtLevel);
            withoutHardAntiSynergy.removeAll(MoveSynergy.getHardAbilityMoveAntiSynergy(
                    romHandler.getAbilityForTrainerPokemon(tp),
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
    // source (level-up, egg, TM/HM, tutor). A species that learns a move by two routes counts once (we unify per
    // species number first, then tally). Level is ignored here - this is raw learnability, not level-appropriateness,
    // so a universal move's reach is measured the same way regardless of the power-band filter. Called once per run,
    // after the source caches are warm; cost is O(species x (learnset + TM + tutor)) - a few hundred thousand touches.
    private void buildMoveAvailability() {
        Map<Integer, Set<Integer>> perSpecies = new HashMap<>();   // species number -> distinct learnable move numbers

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

        // TM/HM moves (keyed by Species; boolean[] is 1-indexed against allTMMoves).
        for (Map.Entry<Species, boolean[]> e : allTMCompat.entrySet()) {
            Set<Integer> set = perSpecies.computeIfAbsent(e.getKey().getNumber(), k -> new HashSet<>());
            boolean[] compat = e.getValue();
            for (int i = 0; i < allTMMoves.size(); i++) {
                if (compat[i + 1]) {
                    set.add(allTMMoves.get(i));
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

    // Lazily loads the six move-source caches (once per run) and builds the availability tally from them. Called at
    // the top of getMoveSelectionPoolAtLevel BEFORE the Smeargle branch, so even a run whose only buffed mon is a
    // Smeargle (which builds its pool separately) still has a populated moveAvailability for the pick-slot weights.
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
        if (allTutorCompat == null && romHandler.hasMoveTutors()) {
            allTutorCompat = romHandler.getMoveTutorCompatibility();
        }
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
        // Availability tally (Batch 6): built once from the SAME cached maps - never re-calls the (uncached,
        // per-call-rebuilding) RomHandler getters. See availabilityWeight.
        if (moveAvailability == null) {
            buildMoveAvailability();
        }
    }

    private List<Move> getMoveSelectionPoolAtLevel(TrainerPokemon tp, boolean cyclicEvolutions) {

        ensureMoveSourceCaches();

        // Smeargle special-case: its Sketch move lets it copy ANY move in the game, so with vanilla
        // (UNCHANGED) learnsets its real pool is basically just Sketch. Hand it the full usable move
        // universe instead and let the normal checks-and-balances below run on that. Only when species
        // movesets are UNCHANGED — if they're randomised, Smeargle already gets a randomised learnset.
        if (tp.getSpecies().getNumber() == SpeciesIDs.smeargle
                && settings.getMovesetsMod() == Settings.MovesetsMod.UNCHANGED) {
            return buildSmeargleSketchPool(tp);
        }

        List<Move> moves = romHandler.getMoves();

        // Level-up Moves. These are the mon's OWN learnset moves - collected here so the hard power-band filter
        // below can exempt them (a level-up move is level-appropriate by definition, at any base power).
        List<Move> ownLevelUpMoves = allLevelUpMoves.getOrDefault(tp.getSpecies().getNumber(), List.of())
                .stream()
                .filter(ml -> (ml.level <= tp.getLevel() && ml.level != 0) || (ml.level == 0 && tp.getLevel() >= 30))
                .map(ml -> moves.get(ml.move))
                .distinct()
                .toList();
        Set<Integer> ownLevelUpMoveNumbers = ownLevelUpMoves.stream()
                .map(mv -> mv.number).collect(Collectors.toSet());
        List<Move> moveSelectionPoolAtLevel = new ArrayList<>(ownLevelUpMoves);

        // Pre-Evo Moves (100% availability - the hard power-band filter, not a random roll, keeps them level-
        // appropriate; unlike the mon's own level-up moves these are NOT exempt from that filter).
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
                        .distinct().toList());
            }
        }

        // TM Moves (100% availability - every TM the species can learn enters the pool; the hard power-band
        // filter below removes the level-inappropriate ones).
        boolean[] tmCompat = allTMCompat.get(tp.getSpecies());
        for (int i = 0; i < allTMMoves.size(); i++) {
            int tmMove = allTMMoves.get(i);
            if (tmCompat[i + 1]) {
                moveSelectionPoolAtLevel.add(moves.get(tmMove));
            }
        }

        // Move Tutor Moves (100% availability, same as TMs).
        if (romHandler.hasMoveTutors()) {
            boolean[] tutorCompat = allTutorCompat.get(tp.getSpecies());
            for (int i = 0; i < allTutorMoves.size(); i++) {
                int tutorMove = allTutorMoves.get(i);
                if (tutorCompat[i + 1]) {
                    moveSelectionPoolAtLevel.add(moves.get(tutorMove));
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
                // inherit a far-too-strong move (Petal Dance / Leaf Storm); the hard power-band filter below
                // removes over-level ones and keeps status / gimmick / synthetic-damage moves.
                moveSelectionPoolAtLevel.addAll(allEggMoves.get(firstEvo.getNumber())
                        .stream()
                        .map(moves::get)
                        .toList());
            }
        }

        // Hard power-band filter: remove level-inappropriate attacking moves outright (the mon's own level-up
        // moves, status/gimmick moves, and - at high level - priority weak moves are exempt; see the method doc).
        applyPowerBandFilter(moveSelectionPoolAtLevel, tp.getLevel(), ownLevelUpMoveNumbers);

        return moveSelectionPoolAtLevel.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Builds the candidate move pool for a trainer Smeargle: the full usable move universe (Sketch can
     * copy anything). Excludes only mechanically-unusable / banned moves, then applies the same hard
     * power-band filter every other mon's pool gets, so a low-level Smeargle still only draws level-
     * appropriate attacks. Sketched moves are not learnset moves, so none are exempt as "own level-up"
     * moves (the priority/status exemptions still apply via the filter). Everything downstream
     * (trimMoveList, role slots, redundancy) runs on this list unchanged, so Smeargle goes through the
     * identical checks and balances as every other mon.
     */
    private List<Move> buildSmeargleSketchPool(TrainerPokemon tp) {
        Set<Integer> banned = new HashSet<>();
        banned.addAll(romHandler.getGameBreakingMoves());
        banned.addAll(romHandler.getIllegalMoves());
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        banned.add(MoveIDs.struggle); // not a selectable move
        banned.add(MoveIDs.sketch);   // a trainer Smeargle re-Sketching Sketch is pointless

        List<Move> pool = new ArrayList<>();
        for (Move mv : romHandler.getMoves()) {
            if (mv == null) {
                continue; // move list is indexed by number; index 0 is blank
            }
            if (banned.contains(mv.number)) {
                continue;
            }
            pool.add(mv);
        }
        applyPowerBandFilter(pool, tp.getLevel(), Collections.emptySet());
        return pool.stream().distinct().collect(Collectors.toList());
    }
}

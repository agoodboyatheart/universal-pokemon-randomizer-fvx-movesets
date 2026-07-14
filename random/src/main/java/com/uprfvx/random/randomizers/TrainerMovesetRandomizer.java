package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;
import java.util.stream.Collectors;

public class TrainerMovesetRandomizer extends Randomizer {

    private Map<Integer, List<MoveLearnt>> allLevelUpMoves;
    private Map<Integer, List<Integer>> allEggMoves;
    private Map<Species, boolean[]> allTMCompat, allTutorCompat;
    private List<Integer> allTMMoves, allTutorMoves;
    
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

            // Team-level authoring: track the moves/attacking-types already handed to EARLIER Pokemon on this
            // same trainer, so the choosy slots can softly avoid repeating them (mainline teams feel authored
            // through role variety, not by stacking the same coverage/status on every mon). Reset per trainer.
            teamUsage = new TeamMoveUsage();

            for (TrainerPokemon tp : t.getPokemon()) {
                tp.setResetMoves(false);

                List<Move> movesAtLevel = getMoveSelectionPoolAtLevel(tp, isCyclicEvolutions);

                Species pk = tp.getSpecies();
                int ability = hasAbilities ? romHandler.getAbilityForTrainerPokemon(tp) : 0;

                // Strip intrinsically-redundant situational status moves (weather / Trick Room) up front, so they
                // cannot slip in via a small movepool or the trim / fallback paths that skip the per-slot gate.
                movesAtLevel.removeIf(mv -> isSituationalStatusRedundant(mv, pk, ability));

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
                        secondAttack = pickRegularSecondAttack(distinctPool, picked, level, ability);
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
    private static final double TEAM_TYPE_REPEAT_PENALTY = 0.6;

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

    // How lopsided Attack vs Sp.Atk must be to commit to a category (>=1.15x, i.e. a ~15% edge). Tuning knob.
    private static final double ATTACKER_COMMIT_RATIO = 1.15;
    // Weight multiplier applied to a move matching the attacker's preferred category in the STAB/coverage slots.
    private static final double CATEGORY_PREFERENCE_BONUS = 3.0;

    // Within a single power tier the attack-slot pickers weight moves by effective power, so the strongest in-band
    // move dominates (e.g. Water Pulse 60 crowds out Water Gun / Bubble 40). This exponent softens that power term
    // so weaker same-tier moves surface more often while the strongest stays slightly favoured. 1.0 = the old
    // power-proportional behaviour; 0.5 (square root) gives roughly a 55/45 split for a 60-vs-40 BP pair. The
    // across-tier level gating is unaffected - that is handled separately by levelTierWeight. Tuning knob.
    private static final double POWER_SELECTION_EXPONENT = 0.5;

    // A move's power-based selection weight, softened by POWER_SELECTION_EXPONENT. Multiply by levelTierWeight
    // (across-tier gating) and any other bias to build the final pick weight.
    private static double powerSelectionWeight(double effectivePower) {
        return Math.pow(effectivePower, POWER_SELECTION_EXPONENT);
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
        double power = effectivePower(mv, level);
        if (power <= 0) {
            return false;
        }
        boolean synthetic = isSyntheticDamageMove(mv);
        // Over-level moves are no longer hard-excluded here: the shared level->power-tier soft bias
        // (levelTierWeight) folded into the slot weight functions demotes them instead, so a low-level mon
        // usually gets level-appropriate power but can rarely roll a stronger move.
        // badStrongMoves are hard-banned from attack slots, EXCEPT recharge moves (Hyper Beam - the only recharge
        // move in the list): those are allowed in but heavily demoted by practicalValueWeight, so Hyper Beam and
        // Giga Impact (never banned) are treated consistently. Do NOT edit the shared badStrongMoves list itself -
        // the species moveset randomizer relies on it.
        if (GlobalConstants.badStrongMoves.contains(mv.number) && !mv.isRechargeMove) {
            return false;
        }
        return synthetic
                || mv.isGoodDamaging(romHandler.getPerfectAccuracy())
                || GlobalConstants.goodWeakMoves.contains(mv.number);
    }

    // Slot 1: a STAB attacking move, weighted toward stronger moves within the mon's unlocked power tier
    // (via the shared level->power-tier soft bias) and, for a committed attacker, toward its preferred category.
    private Move pickStabMove(Species pk, int ability, List<Move> pool, int level, AttackerProfile profile,
                              List<Move> exclude, boolean reliable) {
        Type t1 = pk.getPrimaryType(false);
        Type t2 = pk.getSecondaryType(false);
        List<Move> candidates = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> mv.type == t1 || (t2 != null && mv.type == t2))
                .filter(mv -> isAttackSlotEligible(mv, level))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            // No good-damaging STAB move at all: fall back to any damaging STAB (still tier-weighted, so a
            // low-level mon leans toward level-appropriate power). effectivePower > 0 is guaranteed here.
            candidates = pool.stream()
                    .filter(mv -> !exclude.contains(mv))
                    .filter(mv -> effectivePower(mv, level) > 0)
                    .filter(mv -> mv.type == t1 || (t2 != null && mv.type == t2))
                    .collect(Collectors.toList());
        }
        return weightedPick(candidates, mv -> {
            double ep = effectivePower(mv, level);
            return powerSelectionWeight(ep) * levelTierWeight(level, ep) * categoryPreference(mv, profile)
                    * practicalValueWeight(mv, ability, exclude)
                    * (reliable ? accuracyWeight(mv) : 1.0);
        });
    }

    // Slot 2: a coverage move hitting a type that resists the STAB move; SE-gated, blind-spot-weighted, and
    // (for a committed attacker) nudged toward the Pokemon's preferred damage category.
    private Move pickCoverageMove(Species pk, int ability, List<Move> pool, int level, Type stabType,
                                  AttackerProfile profile, List<Move> exclude) {
        if (stabType == null) {
            return null;
        }
        TypeTable tt = romHandler.getTypeTable();
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
            double weight = powerSelectionWeight(ep) * levelTierWeight(level, ep);
            if (coversAny(tt, mv.type, holes, true)) {
                weight *= COVERAGE_SUPER_EFFECTIVE_BONUS;
            }
            if (coversAny(tt, mv.type, blindSpots, false)) {
                weight *= COVERAGE_BLIND_SPOT_BONUS;
            }
            return weight * categoryPreference(mv, profile) * practicalValueWeight(mv, ability, exclude)
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

    // Slot 3: a non-redundant good status move, synergy-weighted to suit the Pokemon.
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
        Set<Integer> synergy = MoveSynergy.getStatMoveSynergy(pk, candidates)
                .stream().map(mv -> mv.number).collect(Collectors.toSet());
        // teamRepeatWeight demotes a status move a teammate already carries (status moves have effectivePower 0,
        // so only the exact-move tally applies here) - the team trends toward varied status, not five Toxics.
        // accuracyWeight (boss slot) leans toward reliable status: Thunder Wave / Toxic (>=90%) over Hypnosis / Sing.
        return weightedPick(candidates,
                mv -> (synergy.contains(mv.number) ? 3.0 : 1.0) * teamRepeatWeight(mv, level) * accuracyWeight(mv));
    }

    // Global fallback for any unfillable slot: a damaging move weighted toward stronger picks within the mon's
    // unlocked power tier (via the shared level->power-tier soft bias), degrading gracefully when the pool holds
    // only over-level moves - those are demoted, not excluded, so a mon always gets some damaging move.
    private Move pickBestDamaging(List<Move> pool, List<Move> exclude, int level, int ability) {
        List<Move> damaging = pool.stream()
                .filter(mv -> !exclude.contains(mv))
                .filter(mv -> effectivePower(mv, level) > 0)
                .collect(Collectors.toList());
        // No-duplicate-attacking-type guard: a fallback slot should not hand out a second attack of a type the
        // mon already attacks with (relaxed automatically if that would leave nothing damaging to pick).
        damaging = withoutDuplicateAttackingType(damaging, exclude, level);
        // Synthetic level/HP-% damage moves report effectivePower == level, so they land in the low tier.
        return weightedPick(damaging, mv -> {
            double ep = effectivePower(mv, level);
            return powerSelectionWeight(ep) * levelTierWeight(level, ep) * practicalValueWeight(mv, ability, exclude);
        });
    }

    // Slot 2 for Regular-tier trainers: a plain, level-appropriate second attacking move. Unlike the boss coverage
    // slot it does NOT hole-target super-effective types, and unlike pickBestDamaging it is NOT power-weighted
    // toward the strongest option - the flat within-tier draw is deliberate. Power-weighting let one ubiquitous
    // high-BP TM (e.g. Secret Power in Gen 3) dominate this slot across the whole cast, which reads as "optimal",
    // not "authored". Level gating (levelTierWeight) still keeps the pick level-appropriate and the practical-value
    // discount keeps charge/recharge moves rare; the no-duplicate-attacking-type guard still applies.
    private Move pickRegularSecondAttack(List<Move> pool, List<Move> exclude, int level, int ability) {
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
        // Flat within-tier weighting: no powerSelectionWeight term, so a 40 BP move competes evenly with a 70 BP
        // one inside the same unlocked tier - variety over optimisation. The one exception is the generic-neutral
        // penalty: without super-effective hole-targeting, always-neutral universal TMs (Normal-type Secret Power,
        // Facade, Return - learnable by nearly the whole dex, super-effective against nothing) otherwise flood this
        // slot on ~half the cast, which reads as flavourless filler, not authored. They are demoted, not banned.
        return weightedPick(damaging, mv -> genericNeutralPenalty(mv)
                * levelTierWeight(level, effectivePower(mv, level)) * practicalValueWeight(mv, ability, exclude)
                * teamRepeatWeight(mv, level));
    }

    // Weight multiplier for the Regular second-attack slot: demotes "always-neutral" attacking moves - those whose
    // type is super-effective against nothing in this ROM's type chart (Normal in every mainline game, but computed
    // from the live TypeTable so it stays correct for custom/randomised type charts). 1.0 for any move that is at
    // least super-effective against something. Tuning knob.
    private static final double GENERIC_NEUTRAL_MOVE_PENALTY = 0.2;

    private double genericNeutralPenalty(Move mv) {
        TypeTable tt = romHandler.getTypeTable();
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
        TypeTable tt = romHandler.getTypeTable();
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
                    mv -> practicalValueWeight(mv, ability, picked) * teamRepeatWeight(mv, level));
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
                working.remove(softAnti.get(j % softAnti.size()));
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

        List<Move> obsoletedMoves = getObsoleteMoves(movesAtLevel);

        // Remove obsoleted moves

        movesAtLevel.removeAll(obsoletedMoves);

        if (writeMovesetIfSmallEnough(tp, movesAtLevel)) {
            return new ArrayList<>();
        }

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

    private List<Move> getObsoleteMoves(List<Move> movesAtLevel) {
        List<Move> obsoletedMoves = new ArrayList<>();
        for (Move mv : movesAtLevel) {
            if (GlobalConstants.cannotObsoleteMoves.contains(mv.number)) {
                continue;
            }
            if (mv.power > 0) {
                List<Move> obsoleteThis = movesAtLevel
                        .stream()
                        .filter(mv2 -> !GlobalConstants.cannotBeObsoletedMoves.contains(mv2.number) &&
                                mv.type == mv2.type &&
                                ((((mv.statChangeMoveType == mv2.statChangeMoveType &&
                                        mv.statChanges[0].equals(mv2.statChanges[0])) ||
                                        (mv2.statChangeMoveType == StatChangeMoveType.NONE_OR_UNKNOWN &&
                                                mv.hasBeneficialStatChange())) &&
                                        mv.absorbPercent >= mv2.absorbPercent &&
                                        !mv.isChargeMove &&
                                        !mv.isRechargeMove) ||
                                        mv2.power * mv2.hitCount <= 30) &&
                                mv.hitratio >= mv2.hitratio &&
                                mv.category == mv2.category &&
                                mv.priority >= mv2.priority &&
                                mv2.power > 0 &&
                                mv.power * mv.hitCount > mv2.power * mv2.hitCount).collect(Collectors.toList());
//                for (Move obsoleted: obsoleteThis) {
//                    System.out.println(obsoleted.name + " obsoleted by " + mv.name);
//                }
                obsoletedMoves.addAll(obsoleteThis);
            } else if (mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_USER ||
                    mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_TARGET) {
                List<Move> obsoleteThis = new ArrayList<>();
                List<Move.StatChange> statChanges1 = new ArrayList<>();
                for (Move.StatChange sc : mv.statChanges) {
                    if (sc.type != StatChangeType.NONE) {
                        statChanges1.add(sc);
                    }
                }
                for (Move mv2 : movesAtLevel
                        .stream()
                        .filter(otherMv -> !otherMv.equals(mv) &&
                                otherMv.power <= 0 &&
                                otherMv.statChangeMoveType == mv.statChangeMoveType &&
                                (otherMv.statusType == mv.statusType ||
                                        otherMv.statusType == StatusType.NONE)).collect(Collectors.toList())) {
                    List<Move.StatChange> statChanges2 = new ArrayList<>();
                    for (Move.StatChange sc : mv2.statChanges) {
                        if (sc.type != StatChangeType.NONE) {
                            statChanges2.add(sc);
                        }
                    }
                    if (statChanges2.size() > statChanges1.size()) {
                        continue;
                    }
                    List<Move.StatChange> statChanges1Filtered = statChanges1
                            .stream()
                            .filter(sc -> !statChanges2.contains(sc)).collect(Collectors.toList());
                    statChanges2.removeAll(statChanges1);
                    if (!statChanges1Filtered.isEmpty() && statChanges2.isEmpty()) {
                        if (!GlobalConstants.cannotBeObsoletedMoves.contains(mv2.number)) {
                            obsoleteThis.add(mv2);
                        }
                        continue;
                    }
                    if (statChanges1Filtered.isEmpty() && statChanges2.isEmpty()) {
                        continue;
                    }
                    boolean maybeBetter = false;
                    for (Move.StatChange sc1 : statChanges1Filtered) {
                        boolean canStillBeBetter = false;
                        for (Move.StatChange sc2 : statChanges2) {
                            if (sc1.type == sc2.type) {
                                canStillBeBetter = true;
                                if ((mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_USER && sc1.stages > sc2.stages) ||
                                        (mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_TARGET && sc1.stages < sc2.stages)) {
                                    maybeBetter = true;
                                } else {
                                    canStillBeBetter = false;
                                }
                            }
                        }
                        if (!canStillBeBetter) {
                            maybeBetter = false;
                            break;
                        }
                    }
                    if (maybeBetter) {
                        if (!GlobalConstants.cannotBeObsoletedMoves.contains(mv2.number)) {
                            obsoleteThis.add(mv2);
                        }
                    }
                }
//                for (Move obsoleted : obsoleteThis) {
//                    System.out.println(obsoleted.name + " obsoleted by " + mv.name);
//                }
                obsoletedMoves.addAll(obsoleteThis);
            }
        }

        return obsoletedMoves.stream().distinct().collect(Collectors.toList());
    }

    private List<Move> getMoveSelectionPoolAtLevel(TrainerPokemon tp, boolean cyclicEvolutions) {

        List<Move> moves = romHandler.getMoves();
        double eggMoveProbability = 0.1;
        double preEvoMoveProbability = 0.5;
        double tmMoveProbability = 0.6;
        double tutorMoveProbability = 0.6;

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

        // Level-up Moves
        List<Move> moveSelectionPoolAtLevel = allLevelUpMoves.get(tp.getSpecies().getNumber())
                .stream()
                .filter(ml -> (ml.level <= tp.getLevel() && ml.level != 0) || (ml.level == 0 && tp.getLevel() >= 30))
                .map(ml -> moves.get(ml.move))
                .distinct()
                .collect(Collectors.toList());

        // Pre-Evo Moves
        if (!cyclicEvolutions) {
            Species preEvo;
            if (romHandler.altFormesCanHaveDifferentEvolutions()) {
                preEvo = tp.getSpecies();
            } else {
                preEvo = tp.getSpecies().getBaseForme();
            }
            while (!preEvo.getEvolutionsTo().isEmpty()) {
                preEvo = preEvo.getEvolutionsTo().get(0).getFrom();
                moveSelectionPoolAtLevel.addAll(allLevelUpMoves.get(preEvo.getNumber())
                        .stream()
                        .filter(ml -> ml.level <= tp.getLevel())
                        .filter(_ -> this.random.nextDouble() < preEvoMoveProbability)
                        .map(ml -> moves.get(ml.move))
                        .distinct().toList());
            }
        }

        // TM Moves
        boolean[] tmCompat = allTMCompat.get(tp.getSpecies());
        for (int i = 0; i < allTMMoves.size(); i++) {
            int tmMove = allTMMoves.get(i);
            if (tmCompat[i + 1]) {
                Move thisMove = moves.get(tmMove);
                if (thisMove.power > 1 && this.random.nextDouble()
                        < tmMoveProbability * levelTierWeight(tp.getLevel(), thisMove.power * thisMove.hitCount)) {
                    moveSelectionPoolAtLevel.add(thisMove);
                } else if ((thisMove.power <= 1 && this.random.nextInt(100) < tp.getLevel()) ||
                        ((thisMove.power <= 1 || this.random.nextDouble()
                                < levelTierWeight(tp.getLevel(), thisMove.power * thisMove.hitCount))
                                && this.random.nextInt(200) < tp.getLevel())) {
                    // The variety roll admits power<=1 moves and, softly, real damaging moves in proportion to
                    // the level->power-tier weight, so over-level moves only rarely slip in rather than never.
                    moveSelectionPoolAtLevel.add(thisMove);
                }
            }
        }

        // Move Tutor Moves
        if (romHandler.hasMoveTutors()) {
            boolean[] tutorCompat = allTutorCompat.get(tp.getSpecies());
            for (int i = 0; i < allTutorMoves.size(); i++) {
                int tutorMove = allTutorMoves.get(i);
                if (tutorCompat[i + 1]) {
                    Move thisMove = moves.get(tutorMove);
                    if (thisMove.power > 1 && this.random.nextDouble()
                            < tutorMoveProbability * levelTierWeight(tp.getLevel(), thisMove.power * thisMove.hitCount)) {
                        moveSelectionPoolAtLevel.add(thisMove);
                    } else if ((thisMove.power <= 1 && this.random.nextInt(100) < tp.getLevel()) ||
                            ((thisMove.power <= 1 || this.random.nextDouble()
                                    < levelTierWeight(tp.getLevel(), thisMove.power * thisMove.hitCount))
                                    && this.random.nextInt(200) < tp.getLevel())) {
                        // The variety roll admits power<=1 moves and, softly, real damaging moves in proportion to
                        // the level->power-tier weight, so over-level moves only rarely slip in rather than never.
                        moveSelectionPoolAtLevel.add(thisMove);
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
                moveSelectionPoolAtLevel.addAll(allEggMoves.get(firstEvo.getNumber())
                        .stream()
                        .filter(egm -> this.random.nextDouble() < eggMoveProbability)
                        .map(moves::get)
                        // Egg moves carry no level requirement, so a low-level mon could otherwise inherit a
                        // far-too-strong move (e.g. Petal Dance / Leaf Storm). Gate real damaging moves by the same
                        // level->power-tier soft bias the TM/tutor pool uses, so over-level ones only rarely slip
                        // in; keep power<=1 moves (status, gimmicks, synthetic level/HP-% damage) always.
                        .filter(m -> m.power <= 1
                                || this.random.nextDouble() < levelTierWeight(tp.getLevel(), m.power * m.hitCount))
                        .collect(Collectors.toList()));
            }
        }

        return moveSelectionPoolAtLevel.stream().distinct().collect(Collectors.toList());
    }
}

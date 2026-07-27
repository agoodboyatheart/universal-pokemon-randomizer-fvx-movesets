package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.gamedata.cueh.BasicSpeciesAction;
import com.uprfvx.romio.gamedata.cueh.EvolvedSpeciesAction;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;
import java.util.stream.Collectors;

public class SpeciesMovesetRandomizer extends Randomizer {

    public SpeciesMovesetRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    public void randomizeMovesLearnt() {
        boolean typeThemed = settings.getMovesetsMod() == Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE;
        boolean noBroken = settings.isBlockBrokenMovesetMoves();
        boolean forceStartingMoves = romHandler.supportsFourStartingMoves() && settings.isStartWithGuaranteedMoves();
        int forceStartingMoveCount = settings.getGuaranteedMoveCount();
        double goodDamagingPercentage =
                settings.isMovesetsForceGoodDamaging() ? settings.getMovesetsGoodDamagingPercent() / 100.0 : 0;
        boolean sensibleMovesets = settings.isSensibleMovesets();
        boolean evolutionMovesForAll = settings.isEvolutionMovesForAll();
        boolean movesetsFollowEvolutions = settings.isMovesetsFollowEvolutions();

        // Get current sets
        Map<Integer, List<MoveLearnt>> movesets = romHandler.getMovesLearnt();

        // Build sets of moves
        List<Move> validMoves = new ArrayList<>();
        List<Move> validDamagingMoves = new ArrayList<>();
        Map<Type, List<Move>> validTypeMoves = new HashMap<>();
        Map<Type, List<Move>> validTypeDamagingMoves = new HashMap<>();
        // When Sensible Movesets is on, widen the damaging pool to include weak (sub-isGoodDamaging) moves so the
        // level curve can actually reach its low end. Without this the pool floor (~50 effective power) sits above
        // centerPower at low levels and the curve is starved - see species-power-curve-structural-flaw.md.
        createSetsOfMoves(noBroken, sensibleMovesets, validMoves, validDamagingMoves, validTypeMoves,
                validTypeDamagingMoves);

        if (movesetsFollowEvolutions) {
            // Basic species are randomized exactly like the plain (non-follow) path below - only evolved
            // species behave differently, via evolvedAction.
            BasicSpeciesAction<Species> independentAction = pkmn -> {
                List<MoveLearnt> moves = movesets.get(pkmn.getNumber());
                if (moves == null || !rSpecService.getAll(true).contains(pkmn)) {
                    return;
                }
                padMovesetSlots(moves, forceStartingMoves, forceStartingMoveCount, evolutionMovesForAll);
                if (copyCosmeticMovesetIfNeeded(pkmn, moves, movesets)) {
                    return;
                }
                randomizeMovesLearntForSpecies(pkmn, moves, 0, typeThemed, sensibleMovesets, goodDamagingPercentage,
                        validMoves, validDamagingMoves, validTypeMoves, validTypeDamagingMoves);
            };

            // An evolved species inherits its pre-evolution's already-finalized picks, earliest-learned first,
            // into as many of its own slots as the pre-evolution has moves to give - this is what makes both
            // the fewer-slots-than-prevo case (truncate, keep the lowest levels) and the more-slots-than-prevo
            // case (leftover slots randomized independently below) fall out of the same min(...) bound, instead
            // of needing two separate rules. Levels are never touched here - only which move fills each of the
            // evolved species' own (fixed) slots changes.
            EvolvedSpeciesAction<Species> evolvedAction = (evFrom, evTo, isFinalEvo) -> {
                List<MoveLearnt> toMoves = movesets.get(evTo.getNumber());
                List<MoveLearnt> fromMoves = movesets.get(evFrom.getNumber());
                if (toMoves == null || fromMoves == null || !rSpecService.getAll(true).contains(evTo)) {
                    return;
                }
                padMovesetSlots(toMoves, forceStartingMoves, forceStartingMoveCount, evolutionMovesForAll);
                if (copyCosmeticMovesetIfNeeded(evTo, toMoves, movesets)) {
                    return;
                }
                int copyCount = Math.min(toMoves.size(), fromMoves.size());
                for (int i = 0; i < copyCount; i++) {
                    toMoves.get(i).move = fromMoves.get(i).move;
                }
                if (copyCount < toMoves.size()) {
                    randomizeMovesLearntForSpecies(evTo, toMoves, copyCount, typeThemed, sensibleMovesets,
                            goodDamagingPercentage, validMoves, validDamagingMoves, validTypeMoves,
                            validTypeDamagingMoves);
                }
            };

            copyUpEvolutionsHelper.apply(true, false, independentAction, evolvedAction, null, independentAction);
        } else {
            for (Integer pkmnNum : movesets.keySet()) {
                List<MoveLearnt> moves = movesets.get(pkmnNum);
                Species pkmn = findSpeciesInPoolWithSpeciesID(rSpecService.getAll(true), pkmnNum);
                if (pkmn == null) {
                    continue;
                }
                padMovesetSlots(moves, forceStartingMoves, forceStartingMoveCount, evolutionMovesForAll);
                if (copyCosmeticMovesetIfNeeded(pkmn, moves, movesets)) {
                    continue;
                }
                randomizeMovesLearntForSpecies(pkmn, moves, 0, typeThemed, sensibleMovesets, goodDamagingPercentage,
                        validMoves, validDamagingMoves, validTypeMoves, validTypeDamagingMoves);
            }
        }

        // Done, save
        romHandler.setMovesLearnt(movesets);
        changesMade = true;
    }

    // 4-starting-moves / evolution-move-for-all padding, applied once per species before any picking or Follow
    // Evolutions copying happens - slot counts must be final before they're used as the follow-evolutions copy
    // bound.
    private void padMovesetSlots(List<MoveLearnt> moves, boolean forceStartingMoves, int forceStartingMoveCount,
                                 boolean evolutionMovesForAll) {
        // 4 starting moves?
        if (forceStartingMoves) {
            int lv1count = 0;
            for (MoveLearnt ml : moves) {
                if (ml.level == 1) {
                    lv1count++;
                }
            }
            if (lv1count < forceStartingMoveCount) {
                for (int i = 0; i < forceStartingMoveCount - lv1count; i++) {
                    MoveLearnt fakeLv1 = new MoveLearnt(0, 1);
                    moves.add(0, fakeLv1);
                }
            }
        }

        if (evolutionMovesForAll) {
            if (moves.get(0).level != 0) {
                MoveLearnt fakeEvoMove = new MoveLearnt(0, 0);
                moves.add(0, fakeEvoMove);
            }
        }
    }

    // Cosmetic/alt formes (e.g. Rotom-Wash) always mirror their base forme's moveset index-for-index - unrelated
    // to evolution, and takes priority over it. Returns true if pkmn was cosmetic and its moveset was copied
    // (caller should stop, not also run independent randomization or evolution-following on it).
    private boolean copyCosmeticMovesetIfNeeded(Species pkmn, List<MoveLearnt> moves,
                                                 Map<Integer, List<MoveLearnt>> movesets) {
        if (!pkmn.isEssentiallyCosmetic()) {
            return false;
        }
        List<MoveLearnt> baseMoves = movesets.get(pkmn.getConceptualBaseForme().getNumber());
        for (int i = 0; i < moves.size(); i++) {
            moves.get(i).move = baseMoves.get(i).move;
        }
        return true;
    }

    // Independently randomizes moves.get(startIndex .. moves.size()-1) for pkmn. startIndex is 0 for a species
    // whose whole moveset is being freshly randomized; when Follow Evolutions is on, it's the copy bound for the
    // leftover tail of an evolved species whose own slot count exceeds what its pre-evolution had to offer.
    private void randomizeMovesLearntForSpecies(Species pkmn, List<MoveLearnt> moves, int startIndex,
                                                boolean typeThemed, boolean sensibleMovesets,
                                                double goodDamagingPercentage, List<Move> validMoves,
                                                List<Move> validDamagingMoves, Map<Type, List<Move>> validTypeMoves,
                                                Map<Type, List<Move>> validTypeDamagingMoves) {
        List<Integer> learnt = new ArrayList<>();
        for (int i = 0; i < startIndex; i++) {
            learnt.add(moves.get(i).move);
        }

        double atkSpAtkRatio = pkmn.getAttackSpecialAttackRatio();

        // Find last lv1 move
        // lv1index ends up as the index of the first non-lv1 move
        int lv1index;
        if (startIndex == 0) {
            lv1index = moves.get(0).level == 1 ? 0 : 1; // Evolution move handling (level 0 = evo move)
            while (lv1index < moves.size() && moves.get(lv1index).level == 1) {
                lv1index++;
            }

            // last lv1 move is 1 before lv1index
            if (lv1index != 0) {
                lv1index--;
            }
        } else {
            // Level-1 breakpoints are always a species' lowest-level entries, so for the leftover tail of a
            // Follow Evolutions species (startIndex > 0) they've already been claimed by inherited slots -
            // nothing left here to force to level 1.
            lv1index = -1;
        }

        // Force a certain amount of good damaging moves depending on the percentage. Which slots get the
        // budget is chosen up front by shuffling the eligible indices, not by consuming the budget in
        // level order - the latter would deterministically front-load "good damaging" moves onto a
        // learnset's lowest levels, since the loop always reaches low-level slots first (see
        // species-power-curve-shuffle-and-scope-review.md Finding B for why the old post-hoc
        // Collections.shuffle existed, and why it broke the level curve instead).
        int goodDamagingCount = (int) Math.round(goodDamagingPercentage * (moves.size() - startIndex));
        Set<Integer> damagingSlotIndices = new HashSet<>();
        if (lv1index >= startIndex) {
            damagingSlotIndices.add(lv1index);
        }
        if (goodDamagingCount > 0) {
            List<Integer> eligibleIndices = new ArrayList<>();
            for (int i = startIndex; i < moves.size(); i++) {
                if (i != lv1index) {
                    eligibleIndices.add(i);
                }
            }
            Collections.shuffle(eligibleIndices, random);
            damagingSlotIndices.addAll(
                    eligibleIndices.subList(0, Math.min(goodDamagingCount, eligibleIndices.size())));
        }

        // Replace moves as needed
        for (int i = startIndex; i < moves.size(); i++) {
            // should this move be forced damaging?
            boolean attemptDamaging = damagingSlotIndices.contains(i);

            // type themed?
            Type typeOfMove = null;
            if (typeThemed) {
                double picked = random.nextDouble();
                if ((pkmn.getPrimaryType(false) == Type.NORMAL && pkmn.getSecondaryType(false) != null) ||
                        (pkmn.getSecondaryType(false) == Type.NORMAL)) {

                    Type otherType = pkmn.getPrimaryType(false) == Type.NORMAL ? pkmn.getSecondaryType(false) : pkmn.getPrimaryType(false);

                    // Normal/OTHER: 10% normal, 30% other, 60% random
                    if (picked < 0.1) {
                        typeOfMove = Type.NORMAL;
                    } else if (picked < 0.4) {
                        typeOfMove = otherType;
                    }
                    // else random
                } else if (pkmn.getSecondaryType(false) != null) {
                    // Primary/Secondary: 20% primary, 20% secondary, 60% random
                    if (picked < 0.2) {
                        typeOfMove = pkmn.getPrimaryType(false);
                    } else if (picked < 0.4) {
                        typeOfMove = pkmn.getSecondaryType(false);
                    }
                    // else random
                } else {
                    // Primary/None: 40% primary, 60% random
                    if (picked < 0.4) {
                        typeOfMove = pkmn.getPrimaryType(false);
                    }
                    // else random
                }
            }

            // select a list to pick a move from that has at least one free
            List<Move> pickList = validMoves;
            if (attemptDamaging) {
                if (typeOfMove != null) {
                    if (validTypeDamagingMoves.containsKey(typeOfMove)
                            && checkForUnusedMove(validTypeDamagingMoves.get(typeOfMove), learnt)) {
                        pickList = validTypeDamagingMoves.get(typeOfMove);
                    } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                        pickList = validDamagingMoves;
                    }
                } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                    pickList = validDamagingMoves;
                }
                MoveCategory forcedCategory = random.nextDouble() < atkSpAtkRatio ? MoveCategory.PHYSICAL : MoveCategory.SPECIAL;
                List<Move> filteredList = pickList.stream().filter(mv -> mv.category == forcedCategory).collect(Collectors.toList());
                if (!filteredList.isEmpty() && checkForUnusedMove(filteredList, learnt)) {
                    pickList = filteredList;
                }
            } else if (typeOfMove != null) {
                if (validTypeMoves.containsKey(typeOfMove)
                        && checkForUnusedMove(validTypeMoves.get(typeOfMove), learnt)) {
                    pickList = validTypeMoves.get(typeOfMove);
                }
            }

            // now pick a move until we get a valid one
            Move mv;
            // Weight every slot's pick toward centerPower(level), not just attemptDamaging ones -
            // Sensible Movesets is deliberately decoupled from Force Good Damaging's slot budget (see
            // species-power-curve-shuffle-and-scope-review.md Finding A). This does not change which
            // slots are damaging vs status - pickList above already fixed that - it only re-weights
            // which move wins within whatever pool was already selected; status/fixed-damage moves get
            // weight 1.0 from sensibleMovesetWeight, so they're unaffected.
            if (sensibleMovesets && moves.get(i).level > 0) {
                List<Move> available = pickList.stream()
                        .filter(candidate -> !learnt.contains(candidate.number))
                        .collect(Collectors.toList());
                int slotLevel = moves.get(i).level;
                mv = weightedPick(available, candidate -> sensibleMovesetWeight(candidate, slotLevel));
            } else {
                mv = pickList.get(random.nextInt(pickList.size()));
                while (learnt.contains(mv.number)) {
                    mv = pickList.get(random.nextInt(pickList.size()));
                }
            }

            learnt.add(mv.number);

        }

        // write all moves for the pokemon
        for (int i = startIndex; i < learnt.size(); i++) {
            moves.get(i).move = learnt.get(i);
            if (i == lv1index) {
                // just in case, set this to lv1
                moves.get(i).level = 1;
            }
        }
    }

    public void randomizeEggMoves() {
        boolean typeThemed = settings.getMovesetsMod() == Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE;
        boolean noBroken = settings.isBlockBrokenMovesetMoves();
        double goodDamagingPercentage =
                settings.isMovesetsForceGoodDamaging() ? settings.getMovesetsGoodDamagingPercent() / 100.0 : 0;

        // Get current sets
        Map<Integer, List<Integer>> movesets = romHandler.getEggMoves();

        // Build sets of moves
        List<Move> validMoves = new ArrayList<>();
        List<Move> validDamagingMoves = new ArrayList<>();
        Map<Type, List<Move>> validTypeMoves = new HashMap<>();
        Map<Type, List<Move>> validTypeDamagingMoves = new HashMap<>();
        // Egg moves are out of Sensible Movesets' scope (no per-move level to weight against), so keep the narrow
        // isGoodDamaging pool here - no widening.
        createSetsOfMoves(noBroken, false, validMoves, validDamagingMoves, validTypeMoves, validTypeDamagingMoves);

        for (Integer pkmnNum : movesets.keySet()) {
            List<Integer> learnt = new ArrayList<>();
            List<Integer> moves = movesets.get(pkmnNum);
            Species pkmn = findSpeciesInPoolWithSpeciesID(rSpecService.getAll(true), pkmnNum);
            if (pkmn == null) {
                continue;
            }

            double atkSpAtkRatio = pkmn.getAttackSpecialAttackRatio();

            if (pkmn.isEssentiallyCosmetic()) {
                for (int i = 0; i < moves.size(); i++) {
                    moves.set(i, movesets.get(pkmn.getConceptualBaseForme().getNumber()).get(i));
                }
                continue;
            }

            // No per-move level here to weight against, so Sensible Movesets' power curve doesn't apply - picks
            // stay uniform (species-tmtutor-moveset-redesign.md Phase 1 scope note).
            // Force a certain amount of good damaging moves depending on the percentage
            int goodDamagingLeft = (int)Math.round(goodDamagingPercentage * moves.size());

            // Replace moves as needed
            for (int i = 0; i < moves.size(); i++) {
                // should this move be forced damaging?
                boolean attemptDamaging = goodDamagingLeft > 0;

                // type themed?
                Type typeOfMove = null;
                if (typeThemed) {
                    double picked = random.nextDouble();
                    if ((pkmn.getPrimaryType(false) == Type.NORMAL && pkmn.getSecondaryType(false) != null) ||
                            (pkmn.getSecondaryType(false) == Type.NORMAL)) {

                        Type otherType = pkmn.getPrimaryType(false) == Type.NORMAL ? pkmn.getSecondaryType(false) : pkmn.getPrimaryType(false);

                        // Normal/OTHER: 10% normal, 30% other, 60% random
                        if (picked < 0.1) {
                            typeOfMove = Type.NORMAL;
                        } else if (picked < 0.4) {
                            typeOfMove = otherType;
                        }
                        // else random
                    } else if (pkmn.getSecondaryType(false) != null) {
                        // Primary/Secondary: 20% primary, 20% secondary, 60% random
                        if (picked < 0.2) {
                            typeOfMove = pkmn.getPrimaryType(false);
                        } else if (picked < 0.4) {
                            typeOfMove = pkmn.getSecondaryType(false);
                        }
                        // else random
                    } else {
                        // Primary/None: 40% primary, 60% random
                        if (picked < 0.4) {
                            typeOfMove = pkmn.getPrimaryType(false);
                        }
                        // else random
                    }
                }

                // select a list to pick a move from that has at least one free
                List<Move> pickList = validMoves;
                if (attemptDamaging) {
                    if (typeOfMove != null) {
                        if (validTypeDamagingMoves.containsKey(typeOfMove)
                                && checkForUnusedMove(validTypeDamagingMoves.get(typeOfMove), learnt)) {
                            pickList = validTypeDamagingMoves.get(typeOfMove);
                        } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                            pickList = validDamagingMoves;
                        }
                    } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                        pickList = validDamagingMoves;
                    }
                    MoveCategory forcedCategory = random.nextDouble() < atkSpAtkRatio ? MoveCategory.PHYSICAL : MoveCategory.SPECIAL;
                    List<Move> filteredList = pickList.stream().filter(mv -> mv.category == forcedCategory).collect(Collectors.toList());
                    if (!filteredList.isEmpty() && checkForUnusedMove(filteredList, learnt)) {
                        pickList = filteredList;
                    }
                } else if (typeOfMove != null) {
                    if (validTypeMoves.containsKey(typeOfMove)
                            && checkForUnusedMove(validTypeMoves.get(typeOfMove), learnt)) {
                        pickList = validTypeMoves.get(typeOfMove);
                    }
                }

                // now pick a move until we get a valid one
                Move mv = pickList.get(random.nextInt(pickList.size()));
                while (learnt.contains(mv.number)) {
                    mv = pickList.get(random.nextInt(pickList.size()));
                }

                goodDamagingLeft--;
                learnt.add(mv.number);
            }

            // write all moves for the pokemon
            Collections.shuffle(learnt, random);
            for (int i = 0; i < learnt.size(); i++) {
                moves.set(i, learnt.get(i));
            }
        }
        // Done, save
        romHandler.setEggMoves(movesets);
        changesMade = true;
    }

    // "Sensible Movesets" power guideline (species-tmtutor-moveset-redesign.md P10) - deliberately NOT the
    // trainer path's continuously level-scaled centerPower curve. Aaron's explicit goal: species learnsets
    // must stay unpredictable (a good-or-bad roll the player builds around), never pushed toward stronger
    // moves as level rises. So this is two independent soft gates, not one moving center: a move at or
    // below LOW_BP_GUIDELINE is (almost) always available regardless of level - only a late taper at very
    // high level discourages an all-weak moveset, and even then only down to a floor, never to zero. A
    // move above LOW_BP_GUIDELINE is rare-but-possible at low level and ramps up to full availability by
    // HIGH_BP_RAMP_END_LEVEL - a soft ceiling, not a hard pool-stage filter (Aaron's explicit ask).
    private static final double LOW_BP_GUIDELINE = 60.0;
    private static final int LOW_BP_TAPER_START_LEVEL = 45;
    private static final int LOW_BP_TAPER_END_LEVEL = 50;
    private static final double LOW_BP_TAPER_FLOOR = 0.5;

    private static final int HIGH_BP_RAMP_START_LEVEL = 30;
    private static final int HIGH_BP_RAMP_END_LEVEL = 50;
    private static final double HIGH_BP_RAMP_FLOOR = 0.12;

    static double sensibleMovesetWeight(Move mv, int level) {
        double effectivePower = mv.power * mv.hitCount;
        if (effectivePower <= 0) {
            return 1.0;
        }
        if (effectivePower <= LOW_BP_GUIDELINE) {
            double taper = rampFraction(level, LOW_BP_TAPER_START_LEVEL, LOW_BP_TAPER_END_LEVEL);
            return 1.0 - taper * (1.0 - LOW_BP_TAPER_FLOOR);
        }
        double ramp = rampFraction(level, HIGH_BP_RAMP_START_LEVEL, HIGH_BP_RAMP_END_LEVEL);
        return HIGH_BP_RAMP_FLOOR + ramp * (1.0 - HIGH_BP_RAMP_FLOOR);
    }

    // 0 at or below startLevel, 1 at or above endLevel, linear in between.
    private static double rampFraction(int level, int startLevel, int endLevel) {
        if (level <= startLevel) {
            return 0.0;
        }
        if (level >= endLevel) {
            return 1.0;
        }
        return (level - startLevel) / (double) (endLevel - startLevel);
    }

    private boolean checkForUnusedMove(List<Move> potentialList, List<Integer> alreadyUsed) {
        for (Move mv : potentialList) {
            if (!alreadyUsed.contains(mv.number)) {
                return true;
            }
        }
        return false;
    }

    private void createSetsOfMoves(boolean noBroken, boolean widenDamagingPool, List<Move> validMoves,
                                   List<Move> validDamagingMoves, Map<Type, List<Move>> validTypeMoves,
                                   Map<Type, List<Move>> validTypeDamagingMoves) {
        List<Move> allMoves = romHandler.getMoves();
        List<Integer> hms = romHandler.getHMMoves();
        Set<Integer> allBanned = new HashSet<>(noBroken ? romHandler.getGameBreakingMoves() : Collections.emptySet());
        allBanned.addAll(hms);
        allBanned.addAll(romHandler.getMovesBannedFromLevelup());
        allBanned.addAll(GlobalConstants.zMoves);
        allBanned.addAll(romHandler.getIllegalMoves());

        for (Move mv : allMoves) {
            if (mv != null && !GlobalConstants.bannedRandomMoves[mv.number] && !allBanned.contains(mv.number)) {
                validMoves.add(mv);
                if (mv.type != null) {
                    if (!validTypeMoves.containsKey(mv.type)) {
                        validTypeMoves.put(mv.type, new ArrayList<>());
                    }
                    validTypeMoves.get(mv.type).add(mv);
                }

                if (!GlobalConstants.bannedForDamagingMove[mv.number]) {
                    // widenDamagingPool (Sensible Movesets): admit any move with real base power, not just
                    // isGoodDamaging (>=50) ones, so the level curve has weak low-level moves to select. The
                    // soft floor in sensibleMovesetWeight suppresses genuine junk at the low end.
                    boolean include = widenDamagingPool
                            ? (mv.category != MoveCategory.STATUS && mv.power * mv.hitCount > 0)
                            : mv.isGoodDamaging(romHandler.getPerfectAccuracy());
                    if (include) {
                        validDamagingMoves.add(mv);
                        if (mv.type != null) {
                            if (!validTypeDamagingMoves.containsKey(mv.type)) {
                                validTypeDamagingMoves.put(mv.type, new ArrayList<>());
                            }
                            validTypeDamagingMoves.get(mv.type).add(mv);
                        }
                    }
                }
            }
        }

        Map<Type,Double> avgTypePowers = new TreeMap<>();
        double totalAvgPower = 0;

        for (Type type: validTypeMoves.keySet()) {
            List<Move> typeMoves = validTypeMoves.get(type);
            int attackingSum = 0;
            for (Move typeMove: typeMoves) {
                if (typeMove.power > 0) {
                    attackingSum += (typeMove.power * typeMove.hitCount);
                }
            }
            double avgTypePower = (double)attackingSum / (double)typeMoves.size();
            avgTypePowers.put(type, avgTypePower);
            totalAvgPower += (avgTypePower);
        }

        totalAvgPower /= validTypeMoves.size();

        // Want the average power of each type to be within 25% both directions
        double minAvg = totalAvgPower * 0.75;
        double maxAvg = totalAvgPower * 1.25;

        // Add extra moves to type lists outside of the range to balance the average power of each type

        for (Type type: avgTypePowers.keySet()) {
            double avgPowerForType = avgTypePowers.get(type);
            List<Move> typeMoves = validTypeMoves.get(type);
            List<Move> alreadyPicked = new ArrayList<>();
            int iterLoops = 0;
            while (avgPowerForType < minAvg && iterLoops < 10000) {
                final double finalAvgPowerForType = avgPowerForType;
                List<Move> strongerThanAvgTypeMoves = typeMoves
                        .stream()
                        .filter(mv -> mv.power * mv.hitCount > finalAvgPowerForType)
                        .collect(Collectors.toList());
                if (strongerThanAvgTypeMoves.isEmpty()) break;
                if (alreadyPicked.containsAll(strongerThanAvgTypeMoves)) {
                    alreadyPicked = new ArrayList<>();
                } else {
                    strongerThanAvgTypeMoves.removeAll(alreadyPicked);
                }
                Move extraMove = strongerThanAvgTypeMoves.get(random.nextInt(strongerThanAvgTypeMoves.size()));
                avgPowerForType = (avgPowerForType * typeMoves.size() + extraMove.power * extraMove.hitCount)
                        / (typeMoves.size() + 1);
                typeMoves.add(extraMove);
                alreadyPicked.add(extraMove);
                iterLoops++;
            }
            iterLoops = 0;
            while (avgPowerForType > maxAvg && iterLoops < 10000) {
                final double finalAvgPowerForType = avgPowerForType;
                List<Move> weakerThanAvgTypeMoves = typeMoves
                        .stream()
                        .filter(mv -> mv.power * mv.hitCount < finalAvgPowerForType)
                        .collect(Collectors.toList());
                if (weakerThanAvgTypeMoves.isEmpty()) break;
                if (alreadyPicked.containsAll(weakerThanAvgTypeMoves)) {
                    alreadyPicked = new ArrayList<>();
                } else {
                    weakerThanAvgTypeMoves.removeAll(alreadyPicked);
                }
                Move extraMove = weakerThanAvgTypeMoves.get(random.nextInt(weakerThanAvgTypeMoves.size()));
                avgPowerForType = (avgPowerForType * typeMoves.size() + extraMove.power * extraMove.hitCount)
                        / (typeMoves.size() + 1);
                typeMoves.add(extraMove);
                alreadyPicked.add(extraMove);
                iterLoops++;
            }
        }
    }

    // Note that this is slow and somewhat hacky.
    // TODO: add to SpeciesSet, hopefully in a less hacky way.
    // (The non-hacky way might be to make it a TreeSet.)
    private Species findSpeciesInPoolWithSpeciesID(Collection<Species> speciesPool, int speciesID) {
        for (Species sp : speciesPool) {
            if (sp.getNumber() == speciesID) {
                return sp;
            }
        }
        return null;
    }

    public void orderDamagingMovesByDamage() {
        Map<Integer, List<MoveLearnt>> movesets = romHandler.getMovesLearnt();
        List<Move> allMoves = romHandler.getMoves();
        for (Integer pkmn : movesets.keySet()) {
            List<MoveLearnt> moves = movesets.get(pkmn);

            // Build up a list of damaging moves and their positions
            List<Integer> damagingMoveIndices = new ArrayList<>();
            List<Move> damagingMoves = new ArrayList<>();
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).level == 0) continue; // Don't reorder evolution move
                Move mv = allMoves.get(moves.get(i).move);
                if (mv.power > 1) {
                    // considered a damaging move for this purpose
                    damagingMoveIndices.add(i);
                    damagingMoves.add(mv);
                }
            }

            // Ties should be sorted randomly, so shuffle the list first.
            Collections.shuffle(damagingMoves, random);

            // Sort the damaging moves by power
            damagingMoves.sort(Comparator.comparingDouble(m -> m.power * m.hitCount));

            // Reassign damaging moves in the ordered positions
            for (int i = 0; i < damagingMoves.size(); i++) {
                moves.get(damagingMoveIndices.get(i)).move = damagingMoves.get(i).number;
            }
        }

        // Done, save
        romHandler.setMovesLearnt(movesets);
        changesMade = true;
    }

    public void metronomeOnlyMode() {
        // TODO: kind of weird place for this to be in, since it affects more than just the Pokemon movesets

        // movesets
        Map<Integer, List<MoveLearnt>> movesets = romHandler.getMovesLearnt();

        MoveLearnt metronomeML = new MoveLearnt(MoveIDs.metronome, 1);

        for (List<MoveLearnt> ms : movesets.values()) {
            if (ms != null && !ms.isEmpty()) {
                ms.clear();
                ms.add(metronomeML);
            }
        }

        romHandler.setMovesLearnt(movesets);

        // trainers
        // run this to remove all custom non-Metronome moves
        List<Trainer> trainers = romHandler.getTrainers();

        for (Trainer t : trainers) {
            for (TrainerPokemon tpk : t.getPokemon()) {
                tpk.setResetMoves(true);
            }
        }

        // tms
        List<Integer> tmMoves = romHandler.getTMMoves();

        Collections.fill(tmMoves, MoveIDs.metronome);

        romHandler.setTMMoves(tmMoves);

        // movetutors
        if (romHandler.hasMoveTutors()) {
            List<Integer> mtMoves = romHandler.getMoveTutorMoves();

            Collections.fill(mtMoves, MoveIDs.metronome);

            romHandler.setMoveTutorMoves(mtMoves);
        }

        // move tweaks
        List<Move> moveData = romHandler.getMoves();

        Move metronome = moveData.get(MoveIDs.metronome);

        metronome.pp = 40;

        List<Integer> hms = romHandler.getHMMoves();

        for (int hm : hms) {
            Move thisHM = moveData.get(hm);
            thisHM.pp = 0;
        }
    }
}

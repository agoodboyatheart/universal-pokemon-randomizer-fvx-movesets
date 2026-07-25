package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.*;
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

        for (Integer pkmnNum : movesets.keySet()) {
            List<Integer> learnt = new ArrayList<>();
            List<MoveLearnt> moves = movesets.get(pkmnNum);
            int lv1AttackingMove = 0;
            Species pkmn = findSpeciesInPoolWithSpeciesID(rSpecService.getAll(true), pkmnNum);
            if (pkmn == null) {
                continue;
            }

            double atkSpAtkRatio = pkmn.getAttackSpecialAttackRatio();

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

            if (pkmn.isEssentiallyCosmetic()) {
                for (int i = 0; i < moves.size(); i++) {
                    moves.get(i).move = movesets.get(pkmn.getConceptualBaseForme().getNumber()).get(i).move;
                }
                continue;
            }

            // Find last lv1 move
            // lv1index ends up as the index of the first non-lv1 move
            int lv1index = moves.get(0).level == 1 ? 0 : 1; // Evolution move handling (level 0 = evo move)
            while (lv1index < moves.size() && moves.get(lv1index).level == 1) {
                lv1index++;
            }

            // last lv1 move is 1 before lv1index
            if (lv1index != 0) {
                lv1index--;
            }

            // Force a certain amount of good damaging moves depending on the percentage
            int goodDamagingLeft = (int)Math.round(goodDamagingPercentage * moves.size());

            // Replace moves as needed
            for (int i = 0; i < moves.size(); i++) {
                // should this move be forced damaging?
                boolean attemptDamaging = i == lv1index || goodDamagingLeft > 0;

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
                if (sensibleMovesets && attemptDamaging && moves.get(i).level > 0) {
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

                if (i == lv1index) {
                    lv1AttackingMove = mv.number;
                } else {
                    goodDamagingLeft--;
                }
                learnt.add(mv.number);

            }

            Collections.shuffle(learnt, random);
            if (learnt.get(lv1index) != lv1AttackingMove) {
                for (int i = 0; i < learnt.size(); i++) {
                    if (learnt.get(i) == lv1AttackingMove) {
                        learnt.set(i, learnt.get(lv1index));
                        learnt.set(lv1index, lv1AttackingMove);
                        break;
                    }
                }
            }

            // write all moves for the pokemon
            for (int i = 0; i < learnt.size(); i++) {
                moves.get(i).move = learnt.get(i);
                if (i == lv1index) {
                    // just in case, set this to lv1
                    moves.get(i).level = 1;
                }
            }
        }
        // Done, save
        romHandler.setMovesLearnt(movesets);
        changesMade = true;
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

    // A damaging move's pick weight is a bell curve peaking at centerPower(level) - the effective power expected
    // at that level - and falling off symmetrically for moves that are too weak OR too strong for the slot. This
    // needs the widened damaging pool (see createSetsOfMoves / widenDamagingPool): with the narrow isGoodDamaging
    // pool the curve's low end (centerPower ~45 at Lv1) sits below the pool floor of ~50 and the weight is starved
    // - see species-power-curve-structural-flaw.md.
    //
    // SPREAD is the one knob: the Gaussian's standard deviation as a fraction of centerPower. Smaller = a tighter,
    // more strongly level-locked curve; larger = looser, preserving more variability (this fork prizes variability
    // over rigid correctness, so this stays deliberately wide). Status / fixed-damage moves (no base power to place
    // on the curve) keep full weight. No boss tier or quality-list exemption - the player filters species pools.
    private static final double SPECIES_POWER_SPREAD = 0.4;

    private static double sensibleMovesetWeight(Move mv, int level) {
        double effectivePower = mv.power * mv.hitCount;
        if (effectivePower <= 0) {
            return 1.0;
        }
        double center = centerPower(level);
        double z = (effectivePower - center) / (SPECIES_POWER_SPREAD * center);
        return Math.exp(-0.5 * z * z);
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

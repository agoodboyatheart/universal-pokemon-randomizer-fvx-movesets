package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.gamedata.cueh.BasicSpeciesAction;
import com.uprfvx.romio.gamedata.cueh.CopyUpEvolutionsHelper;
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

        // Pristine snapshot of vanilla move IDs per species, taken before any randomization mutation -
        // computeBackfillEffectiveLevels needs the PRE-EVOLUTION's original learnset, but movesets' own
        // MoveLearnt objects get overwritten in place as each species is processed below (and, with Follow
        // Evolutions on, prevos are processed before their evolutions) - so a snapshot taken any later
        // would already be corrupted for whichever species happened to be handled first.
        Map<Integer, Set<Integer>> vanillaMoveIdsBySpecies = new HashMap<>();
        for (Map.Entry<Integer, List<MoveLearnt>> entry : movesets.entrySet()) {
            Set<Integer> ids = new HashSet<>();
            for (MoveLearnt ml : entry.getValue()) {
                ids.add(ml.move);
            }
            vanillaMoveIdsBySpecies.put(entry.getKey(), ids);
        }

        // When Sensible Movesets is on, widen the damaging pool to include weak (sub-isGoodDamaging) moves so the
        // level curve can actually reach its low end. Without this the pool floor (~50 effective power) sits above
        // centerPower at low levels and the curve is starved - see species-power-curve-structural-flaw.md.
        MovePools pools = createSetsOfMoves(noBroken, sensibleMovesets);
        List<Type> allTypes = new ArrayList<>(pools.typeDamaging().keySet());
        List<Move> allMoves = romHandler.getMoves();

        if (movesetsFollowEvolutions) {
            // Basic species are randomized exactly like the plain (non-follow) path below - only evolved
            // species behave differently, via evolvedAction.
            BasicSpeciesAction independentAction = pkmn -> {
                List<MoveLearnt> moves = movesets.get(pkmn.getNumber());
                if (moves == null || !rSpecService.getAll(true).contains(pkmn)) {
                    return;
                }
                padMovesetSlots(moves, forceStartingMoves, forceStartingMoveCount, evolutionMovesForAll);
                if (copyCosmeticMovesetIfNeeded(pkmn, moves, movesets)) {
                    return;
                }
                randomizeMovesLearntForSpecies(pkmn, moves, 0, typeThemed, sensibleMovesets, goodDamagingPercentage,
                        pools, allTypes, allMoves, vanillaMoveIdsBySpecies);
            };

            // An evolved species inherits its pre-evolution's already-finalized picks, earliest-learned first,
            // into as many of its own slots as the pre-evolution has moves to give - this is what makes both
            // the fewer-slots-than-prevo case (truncate, keep the lowest levels) and the more-slots-than-prevo
            // case (leftover slots randomized independently below) fall out of the same min(...) bound, instead
            // of needing two separate rules. Levels are never touched here - only which move fills each of the
            // evolved species' own (fixed) slots changes.
            EvolvedSpeciesAction evolvedAction = (evFrom, evTo, isFinalEvo) -> {
                List<MoveLearnt> toMoves = movesets.get(evTo.getNumber());
                List<MoveLearnt> fromMoves = movesets.get(evFrom.getNumber());
                if (toMoves == null || fromMoves == null || !rSpecService.getAll(true).contains(evTo)) {
                    return;
                }
                padMovesetSlots(toMoves, forceStartingMoves, forceStartingMoveCount, evolutionMovesForAll);
                if (copyCosmeticMovesetIfNeeded(evTo, toMoves, movesets)) {
                    return;
                }
                // Measured while toMoves still holds vanilla move IDs. After the copy below they are the
                // pre-evolution's randomized picks, so the "is this slot a relisted prevo move" test would
                // compare randomized content against vanilla IDs and mean nothing.
                Map<Integer, Integer> backfillLevels = sensibleMovesets
                        ? computeBackfillEffectiveLevels(evTo, toMoves, 0, vanillaMoveIdsBySpecies)
                        : Collections.emptyMap();
                int copyCount = Math.min(toMoves.size(), fromMoves.size());
                for (int i = 0; i < copyCount; i++) {
                    toMoves.get(i).move = fromMoves.get(i).move;
                }
                if (copyCount < toMoves.size()) {
                    randomizeMovesLearntForSpecies(evTo, toMoves, copyCount, typeThemed, sensibleMovesets,
                            goodDamagingPercentage, pools, allTypes, allMoves, vanillaMoveIdsBySpecies);
                }
                if (sensibleMovesets) {
                    repairInheritedMoveset(evTo, toMoves, copyCount, backfillLevels, typeThemed, pools,
                            allTypes, allMoves);
                }
            };

            // Alt formes no longer reach basicAction - the helper routes them to altFormeAction/cosmeticAction
            // instead - so both delegate straight back to independentAction, which leaves
            // copyCosmeticMovesetIfNeeded in charge of the cosmetic case exactly as it was before.
            copyUpEvolutionsHelper.apply(new CopyUpEvolutionsHelper.Options
                    .Builder(independentAction, evolvedAction)
                    .altFormeAction((_, altForme) -> independentAction.applyTo(altForme))
                    .cosmeticAction((_, altForme) -> independentAction.applyTo(altForme))
                    .build());
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
                        pools, allTypes, allMoves, vanillaMoveIdsBySpecies);
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
                                                double goodDamagingPercentage, MovePools pools,
                                                List<Type> allTypes, List<Move> allMoves,
                                                Map<Integer, Set<Integer>> vanillaMoveIdsBySpecies) {
        List<Move> validMoves = pools.all();
        List<Move> validDamagingMoves = pools.damaging();
        Map<Type, List<Move>> validTypeMoves = pools.typeAll();
        Map<Type, List<Move>> validTypeDamagingMoves = pools.typeDamaging();
        List<Integer> learnt = new ArrayList<>();
        for (int i = 0; i < startIndex; i++) {
            learnt.add(moves.get(i).move);
        }

        Map<Integer, Integer> backfillEffectiveLevels = sensibleMovesets
                ? computeBackfillEffectiveLevels(pkmn, moves, startIndex, vanillaMoveIdsBySpecies)
                : Collections.emptyMap();

        double atkSpAtkRatio = pkmn.getBaseStats().getAttackSpecialAttackRatio();
        double powerScale = sensibleMovesets ? speciesPowerScale(pkmn) : 1.0;

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
        // Sensible Movesets guarantees an early STAB attacker through role assignment instead. Pinning the
        // level-1 slot to damaging as well would double-load the band vanilla actually keeps ~43% status.
        if (lv1index >= startIndex && !sensibleMovesets) {
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

        // The whole learnset's shape is decided before any slot is filled, so composition (how much status,
        // how much STAB, how wide the coverage palette) is a property of the species rather than an
        // accident of what each independent draw happened to return. Type structure is gated on Prefer Same
        // Type: plain Random means the player asked for type-blind learnsets.
        int[] vanillaThirdAttackCounts = sensibleMovesets
                ? vanillaThirdAttackCounts(moves, startIndex, allMoves)
                : new int[3];
        boolean typeStructured = sensibleMovesets && typeThemed;
        SpeciesLearnsetProfile profile = sensibleMovesets
                ? SpeciesLearnsetProfile.of(pkmn, allTypes, romHandler.generationOfPokemon(), random,
                        vanillaStatusRatio(vanillaThirdAttackCounts, moves.size() - startIndex))
                : null;
        SlotRole[] roles = sensibleMovesets
                ? assignSlotRoles(moves, startIndex, profile, damagingSlotIndices, typeStructured, random,
                        vanillaThirdAttackCounts)
                : null;

        // Replace moves as needed
        for (int i = startIndex; i < moves.size(); i++) {
            // should this move be forced damaging?
            boolean attemptDamaging = damagingSlotIndices.contains(i);

            Move mv;
            if (sensibleMovesets && moves.get(i).level > 0) {
                mv = pickComposedMove(roles[i], i, moves, backfillEffectiveLevels, profile, powerScale,
                        learnt, pools);
                learnt.add(mv.number);
                continue;
            }

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
                List<Move> filteredList = pickList.stream()
                        .filter(candidate -> candidate.category == forcedCategory).collect(Collectors.toList());
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
            mv = pickList.get(random.nextInt(pickList.size()));
            while (learnt.contains(mv.number)) {
                mv = pickList.get(random.nextInt(pickList.size()));
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

        // Egg moves are out of Sensible Movesets' scope (no per-move level to weight against), so keep the narrow
        // isGoodDamaging pool here - no widening.
        MovePools eggPools = createSetsOfMoves(noBroken, false);
        List<Move> validMoves = eggPools.all();
        List<Move> validDamagingMoves = eggPools.damaging();
        Map<Type, List<Move>> validTypeMoves = eggPools.typeAll();
        Map<Type, List<Move>> validTypeDamagingMoves = eggPools.typeDamaging();

        for (Integer pkmnNum : movesets.keySet()) {
            List<Integer> learnt = new ArrayList<>();
            List<Integer> moves = movesets.get(pkmnNum);
            Species pkmn = findSpeciesInPoolWithSpeciesID(rSpecService.getAll(true), pkmnNum);
            if (pkmn == null) {
                continue;
            }

            double atkSpAtkRatio = pkmn.getBaseStats().getAttackSpecialAttackRatio();

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

    // "Sensible Movesets" power banding uses the same hard-ceiling/soft-floor mechanism Better Movesets
    // uses for trainers. A soft weight alone cannot help when the narrowed type/category candidate pool
    // for a slot holds nothing but high-power moves - which is how a level-1 Minun ends up with Volt
    // Tackle and Solarbeam. Ceiling and floor are pool-stage/pick-stage respectively - centerPower,
    // powerCeiling and POWER_FLOOR_FRACTION are shared via the Randomizer base. Species has no
    // Boss/Regular tier split, so it reuses the trainer path's gentler Regular exponent throughout,
    // keeping more surprise at the low end than a Boss trainer's picks get.

    // Hard sliding ceiling (pool stage): removes attacking moves too strong for the slot's level. Status/
    // fixed-damage moves are exempt. Two-stage fallback if capping the narrow (type/category-restricted)
    // pool would leave it empty: first try capping the wider pool the slot's pick would otherwise have
    // come from (the type theme is sacrificed, but the ceiling still holds) - some type x category
    // slices of the real movepool are naturally weak-move-poor (e.g. vanilla Fire Red: only 3/12
    // Fire-type damaging moves are <=60 BP) even though the global pool isn't. Only if that wider pool
    // is ALSO all over-ceiling does this fall back to the narrow pool unfiltered, same as before.
    // A species that never evolves is compensated in vanilla with a materially better kit than one still
    // on its way up - a stronger effect than evolution stage or raw BST alone.
    static double speciesPowerScale(Species pkmn) {
        double normBst = Math.clamp(
                (pkmn.getBaseStats().getBST() - SPECIES_POWER_BST_FLOOR)
                        / (SPECIES_POWER_BST_CEILING - SPECIES_POWER_BST_FLOOR), 0.0, 1.0);
        double scale = SPECIES_POWER_SCALE_MIN + SPECIES_POWER_SCALE_BST_RANGE * normBst;
        if (pkmn.getEvolutionsTo().isEmpty() && pkmn.getEvolutionsFrom().isEmpty()) {
            scale += SPECIES_POWER_SCALE_STANDALONE_BONUS;
        }
        if (pkmn.isLegendary()) {
            scale += SPECIES_POWER_SCALE_LEGENDARY_BONUS;
        }
        return Math.clamp(scale, SPECIES_POWER_SCALE_MIN, SPECIES_POWER_SCALE_MAX);
    }

    static List<Move> applySpeciesPowerCeiling(List<Move> available, List<Move> widerFallbackPool, int level) {
        return applySpeciesPowerCeiling(available, widerFallbackPool, level, 1.0);
    }

    static List<Move> applySpeciesPowerCeiling(List<Move> available, List<Move> widerFallbackPool, int level,
                                               double speciesPowerScale) {
        double ceiling = speciesPowerCeiling(level, speciesPowerScale);
        List<Move> capped = filterUnderCeiling(available, ceiling);
        if (!capped.isEmpty()) {
            return capped;
        }
        List<Move> widerCapped = filterUnderCeiling(widerFallbackPool, ceiling);
        if (!widerCapped.isEmpty()) {
            return widerCapped;
        }
        return available;
    }

    private static List<Move> filterUnderCeiling(List<Move> moves, double ceiling) {
        return moves.stream()
                .filter(mv -> mv.power * mv.hitCount <= 0 || mv.power * mv.hitCount <= ceiling)
                .collect(Collectors.toList());
    }

    // STAB gets an extra fallback rung ahead of the shared ceiling logic: relax the ceiling within the
    // species' own type before ever widening off-type, since STAB's entire purpose is the type match
    // (species-movesets-shape-and-stab-guarantee-design.md Fix B). available here is already the
    // species' own on-type STAB pool (stabCandidates/candidatesForRole already resolved which type, and
    // already widened off-type if - and only if - both of the species' own types were exhausted), so the
    // relaxed fallback rung is simply that same pool, unfiltered by ceiling.
    static List<Move> applySpeciesStabPowerCeiling(List<Move> available, int level, double speciesPowerScale) {
        double ceiling = speciesPowerCeiling(level, speciesPowerScale);
        List<Move> capped = filterUnderCeiling(available, ceiling);
        return capped.isEmpty() ? available : capped;
    }

    // Soft sliding floor (pick stage): demotes, never removes, moves weaker than the slot's level
    // warrants, using the same Regular-tier falloff exponent Better Movesets applies to its Regular
    // trainers.
    static double speciesLevelAppropriatenessWeight(Move mv, int level) {
        return speciesLevelAppropriatenessWeight(mv, level, 1.0);
    }

    static double speciesLevelAppropriatenessWeight(Move mv, int level, double speciesPowerScale) {
        double effectivePower = mv.power * mv.hitCount;
        if (effectivePower <= 0) {
            return 1.0;
        }
        double floor = speciesCenterPower(level) * speciesPowerScale * POWER_FLOOR_FRACTION;
        if (effectivePower >= floor) {
            return 1.0;
        }
        return Math.pow(effectivePower / floor, speciesFloorExponent(level));
    }

    /**
     * What a learnset slot is for. STAB and COVERAGE are only used when the movesets mod is
     * Prefer Same Type - under plain Random every attacking slot is an untyped ATTACK instead, so a
     * player who asked for type-blind learnsets still gets them.
     */
    enum SlotRole { STATUS, STAB, COVERAGE, FILLER, ATTACK, WILDCARD }

    /**
     * Labels every slot in {@code [startIndex, moves.size())} before any move is picked, so a learnset is
     * composed rather than drawn slot-by-slot. {@code forcedDamagingSlots} (the guaranteed first attacker
     * plus whatever Force Good Damaging reserved) are never labelled STATUS.
     */
    static SlotRole[] assignSlotRoles(List<MoveLearnt> moves, int startIndex, SpeciesLearnsetProfile profile,
                                      Set<Integer> forcedDamagingSlots, boolean typeStructured,
                                      Random random, int[] vanillaThirdAttackCounts) {
        int n = moves.size();
        SlotRole[] roles = new SlotRole[n];
        List<Integer> unassigned = new ArrayList<>();
        for (int i = startIndex; i < n; i++) {
            if (!forcedDamagingSlots.contains(i)) {
                unassigned.add(i);
            }
        }

        int wildcardCount = (int) Math.round(SPECIES_WILDCARD_SHARE * unassigned.size());
        Collections.shuffle(unassigned, random);
        for (int w = 0; w < Math.min(wildcardCount, unassigned.size()); w++) {
            roles[unassigned.get(w)] = SlotRole.WILDCARD;
        }
        List<Integer> statusEligible = new ArrayList<>(unassigned.subList(
                Math.min(wildcardCount, unassigned.size()), unassigned.size()));

        // Partition statusEligible into the same three learnset thirds vanillaThirdAttackCounts was
        // measured against, so a third vanilla filled with real attacking moves can't be swept entirely
        // to STATUS by an unlucky statusShare roll (Cresselia, real-Pearl finding:
        // species-movesets-shape-and-stab-guarantee-design.md Fix A).
        List<List<Integer>> thirdEligible = new ArrayList<>();
        thirdEligible.add(new ArrayList<>());
        thirdEligible.add(new ArrayList<>());
        thirdEligible.add(new ArrayList<>());
        for (int idx : statusEligible) {
            thirdEligible.get(thirdIndexFor(idx, startIndex, n)).add(idx);
        }
        // WILDCARD and forced-damaging slots were already taken out of statusEligible above, and they are
        // already not STATUS - so they meet part of vanilla's own attacking count for their third and must
        // not be reserved for a second time. Without this credit the reserve is measured against the whole
        // span (~60% of slots attacking in vanilla) but subtracted from an eligible pool that is only ~85%
        // of it, leaving ~25% capacity against a statusShare target of ~40%: the clamp below then bit on
        // nearly every species and deleted status slots rather than repositioning them, which is what drove
        // the measured status share down to 33.1% against vanilla's 40.7%
        // (species-moveset-pearl-fix-ab-verification-report.md).
        int[] alreadyAttacking = new int[3];
        int[] guaranteedAttacking = new int[3];
        for (int i = startIndex; i < n; i++) {
            int t = thirdIndexFor(i, startIndex, n);
            if (forcedDamagingSlots.contains(i)) {
                alreadyAttacking[t]++;
                guaranteedAttacking[t]++;
            } else if (roles[i] == SlotRole.WILDCARD) {
                alreadyAttacking[t]++;
            }
        }
        int[] capacity = new int[3];
        int totalCapacity = 0;
        for (int t = 0; t < 3; t++) {
            // A WILDCARD slot draws from the whole movepool, so it counts toward vanilla's attacking
            // count only on average - a third left holding nothing but wildcards has no attacking move
            // guaranteed at all. Keep one genuine attacking-role slot reserved wherever vanilla had any,
            // unless a forced-damaging slot is already carrying that guarantee.
            int floor = vanillaThirdAttackCounts[t] > 0 && guaranteedAttacking[t] == 0 ? 1 : 0;
            int reserve = Math.min(thirdEligible.get(t).size(),
                    Math.max(vanillaThirdAttackCounts[t] - alreadyAttacking[t], floor));
            capacity[t] = thirdEligible.get(t).size() - reserve;
            totalCapacity += capacity[t];
        }

        // The flat SPECIES_MIN_ATTACKING_SLOTS floor stays on top of the new per-third reserve as a
        // last-resort backstop for the rare species whose vanilla learnset has zero attacking moves in
        // every third. Trimmed directly out of capacity[] (not just the aggregate totalCapacity) so the
        // proportional distribution below never divides by a total smaller than the sum of the per-third
        // numerators it's weighting - that mismatch was rounding one third's share up far more than
        // intended, starving early-game thirds of attacking slots and making ensureEarlyStabFloor
        // re-promote the opener much more often than its documented ~45% rate.
        int minAttackingReserve = Math.min(SPECIES_MIN_ATTACKING_SLOTS, statusEligible.size());
        int extraReserveNeeded = Math.max(0, minAttackingReserve - (statusEligible.size() - totalCapacity));
        while (extraReserveNeeded > 0) {
            int largest = 0;
            for (int t = 1; t < 3; t++) {
                if (capacity[t] > capacity[largest]) {
                    largest = t;
                }
            }
            if (capacity[largest] <= 0) {
                break;
            }
            capacity[largest]--;
            totalCapacity--;
            extraReserveNeeded--;
        }
        int statusCount = Math.min((int) Math.round(profile.statusShare() * (n - startIndex)), totalCapacity);

        // Distribute the sampled statusCount budget across the three thirds proportionally to each
        // third's own capacity, then run the existing level-band-weighted pickStatusSlot selection
        // independently within each third's own remaining budget.
        int remainingBudget = statusCount;
        int[] spent = new int[3];
        for (int t = 0; t < 3 && remainingBudget > 0; t++) {
            List<Integer> pool = thirdEligible.get(t);
            if (capacity[t] <= 0 || pool.isEmpty()) {
                continue;
            }
            int share = t == 2
                    ? remainingBudget
                    : (int) Math.round(statusCount * (capacity[t] / (double) totalCapacity));
            share = Math.min(share, Math.min(capacity[t], remainingBudget));
            for (int s = 0; s < share && !pool.isEmpty(); s++) {
                int chosen = pickStatusSlot(pool, moves, random);
                roles[pool.remove(chosen)] = SlotRole.STATUS;
                spent[t]++;
                remainingBudget--;
            }
        }

        // A third whose rounded share landed under its own capacity leaves budget unspent, and an early
        // third running out of pool strands the rest - either way the species ends up below its sampled
        // status share for a rounding reason rather than a structural one. Hand the remainder to whichever
        // thirds still have room; each third's capacity cap still holds, so the attacking reserve above is
        // never spent into.
        for (int t = 0; t < 3 && remainingBudget > 0; t++) {
            List<Integer> pool = thirdEligible.get(t);
            while (remainingBudget > 0 && spent[t] < capacity[t] && !pool.isEmpty()) {
                int chosen = pickStatusSlot(pool, moves, random);
                roles[pool.remove(chosen)] = SlotRole.STATUS;
                spent[t]++;
                remainingBudget--;
            }
        }

        for (int i = startIndex; i < n; i++) {
            if (roles[i] == null) {
                roles[i] = attackingRoleFor(moves.get(i).level, typeStructured, random);
            }
        }
        if (typeStructured) {
            setFirstAttackerRole(roles, startIndex, n, random);
            ensureEarlyStabFloor(roles, moves, startIndex, n);
        }
        return roles;
    }

    /**
     * Backstop for the opener roll above: the opener, and every later slot's own independent
     * STAB-vs-COVERAGE roll ({@code attackingRoleFor}), can all miss - real Pearl data found species with
     * no STAB move until level 70-78 this way (Dialga, Raikou;
     * species-moveset-pearl-real-rom-comparison-report.md). If nothing has landed STAB by
     * {@code SPECIES_STAB_FLOOR_LEVEL}, promote the earliest eligible slot at or before it - preferring
     * any slot other than the opener first, so this backstop doesn't quietly re-flip
     * {@code setFirstAttackerRole}'s documented ~45%/55% STAB/Normal-filler split back to STAB every time
     * it fires. Only reaches past the floor level, or touches the opener, if the species has no other
     * attacking-role slot that early at all (rare, since assignSlotRoles' status cap already reserves one)
     * - guaranteeing STAB exists eventually rather than never, same as vanilla's own worst-case stragglers
     * (e.g. Mawile, lv56).
     */
    private static void ensureEarlyStabFloor(SlotRole[] roles, List<MoveLearnt> moves, int startIndex, int n) {
        int openerIndex = -1;
        for (int i = startIndex; i < n; i++) {
            SlotRole role = roles[i];
            if (role != SlotRole.STAB && role != SlotRole.COVERAGE && role != SlotRole.FILLER) {
                continue;
            }
            if (openerIndex == -1) {
                openerIndex = i;
            }
            if (role == SlotRole.STAB && moves.get(i).level <= SPECIES_STAB_FLOOR_LEVEL) {
                return;
            }
        }
        if (promoteEarliestEligible(roles, moves, startIndex, n, openerIndex, true)) {
            return;
        }
        if (promoteEarliestEligible(roles, moves, startIndex, n, -1, true)) {
            return;
        }
        promoteEarliestEligible(roles, moves, startIndex, n, -1, false);
    }

    // withinFloorLevel restricts the search to slots at or before SPECIES_STAB_FLOOR_LEVEL; pass -1 for
    // skipIndex to allow every slot, including the opener.
    private static boolean promoteEarliestEligible(SlotRole[] roles, List<MoveLearnt> moves, int startIndex,
                                                    int n, int skipIndex, boolean withinFloorLevel) {
        for (int i = startIndex; i < n; i++) {
            if (i == skipIndex || (roles[i] != SlotRole.COVERAGE && roles[i] != SlotRole.FILLER)) {
                continue;
            }
            if (withinFloorLevel && moves.get(i).level > SPECIES_STAB_FLOOR_LEVEL) {
                continue;
            }
            roles[i] = SlotRole.STAB;
            return true;
        }
        return false;
    }

    // Weighted by the level band's status density, so utility clusters where vanilla puts it rather than
    // spreading evenly up the learnset.
    private static int pickStatusSlot(List<Integer> eligible, List<MoveLearnt> moves, Random random) {
        double total = 0;
        double[] weights = new double[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            weights[i] = SPECIES_STATUS_BAND_MULTIPLIER[speciesLevelBand(moves.get(eligible.get(i)).level)];
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return i;
            }
        }
        return eligible.size() - 1;
    }

    private static SlotRole attackingRoleFor(int level, boolean typeStructured, Random random) {
        if (!typeStructured) {
            return SlotRole.ATTACK;
        }
        double stabShare = SPECIES_STAB_SHARE_BASE
                + SPECIES_STAB_SHARE_RANGE * Math.min(1.0, level / LEVEL_POWER_SATURATION_LEVEL);
        return random.nextDouble() < stabShare ? SlotRole.STAB : SlotRole.COVERAGE;
    }

    /**
     * Decides what a species opens with. Vanilla splits roughly in half: 45% lead on their own type, and
     * the rest lead on Normal filler and earn their STAB by levelling. Forcing STAB unconditionally
     * overshot the measured band share badly (67% against vanilla's 43%) and took away the "my starter
     * only knows Scratch" opening that makes early levelling feel like progress.
     */
    private static void setFirstAttackerRole(SlotRole[] roles, int startIndex, int n, Random random) {
        SlotRole opener = random.nextDouble() < SPECIES_FIRST_SLOT_STAB_CHANCE
                ? SlotRole.STAB : SlotRole.FILLER;
        for (int i = startIndex; i < n; i++) {
            if (roles[i] == SlotRole.STAB || roles[i] == SlotRole.COVERAGE || roles[i] == SlotRole.ATTACK) {
                roles[i] = opener;
                return;
            }
        }
    }

    // Atk/(Atk+SpA), amplified toward whichever stat the species actually commits to. Clamped short of
    // 0/1 so even a Shuckle-tier split keeps a small chance at the other category.
    static double speciesCategoryLean(double atkSpAtkRatio) {
        return Math.clamp(0.5 + (atkSpAtkRatio - 0.5) * SPECIES_CATEGORY_LEAN_AMPLIFIER, 0.05, 0.95);
    }

    // Status moves are category-neutral - a species' Atk/SpA split says nothing about which Growl it wants.
    static double categoryLeanWeight(Move mv, MoveCategory preferred) {
        if (preferred == null || mv.category == MoveCategory.STATUS) {
            return 1.0;
        }
        return mv.category == preferred ? SPECIES_CATEGORY_BONUS : 1.0;
    }

    // Real vanilla learnsets are per-species standalone tables; an evolved species' table typically
    // re-lists everything its pre-evolution could already learn, bunched at level 1 - confirmed on real
    // ROM data across every generation (species-tmtutor-moveset-redesign.md P11: 70-95% of an evolved
    // species' extra level-1 slots, beyond what a base-stage species has, are literal move-ID matches
    // against the prevo's own vanilla learnset). This flags those slots so move selection can evaluate the
    // P10 guideline at the species' own evolution level instead of level 1 - the slot isn't this species'
    // genuine first move, it's a pre-evolution's already-known move relisted for lookup convenience.
    //
    // vanillaMoveIdsBySpecies must be a snapshot taken before any randomization mutation - the live
    // movesets map's MoveLearnt objects get overwritten in place as each species is processed, so a
    // snapshot taken later would already reflect randomized (not vanilla) content for whichever species
    // happened to be processed first.
    static Map<Integer, Integer> computeBackfillEffectiveLevels(Species pkmn, List<MoveLearnt> moves,
            int startIndex, Map<Integer, Set<Integer>> vanillaMoveIdsBySpecies) {
        Map<Integer, Integer> effectiveLevels = new HashMap<>();
        List<Evolution> prevos = pkmn.getEvolutionsTo();
        if (prevos.isEmpty()) {
            return effectiveLevels;
        }

        Set<Integer> prevoMoveIds = new HashSet<>();
        int evoLevel = Integer.MAX_VALUE;
        for (Evolution evo : prevos) {
            Set<Integer> ids = vanillaMoveIdsBySpecies.get(evo.getFrom().getNumber());
            if (ids != null) {
                prevoMoveIds.addAll(ids);
            }
            evoLevel = Math.min(evoLevel, Math.max(1, evo.getEstimatedEvoLvl()));
        }
        if (prevoMoveIds.isEmpty()) {
            return effectiveLevels;
        }

        for (int i = startIndex; i < moves.size(); i++) {
            MoveLearnt ml = moves.get(i);
            if (ml.level == 1 && ml.move != 0 && prevoMoveIds.contains(ml.move)) {
                effectiveLevels.put(i, evoLevel);
            }
        }
        return effectiveLevels;
    }

    /**
     * Re-checks the STAB and attacking guarantees against an evolved species' OWN types after it has
     * inherited its pre-evolution's picks, overwriting the single earliest slot that can carry them.
     * <p>
     * An evolved species whose slot count is at most its pre-evolution's is a pure copy - the Follow
     * Evolutions path never calls {@code randomizeMovesLearntForSpecies} for it, so
     * {@code assignSlotRoles} and {@code ensureEarlyStabFloor} never evaluate against its own typing at
     * all. Two ways that breaks: a type change across the evolution (Scyther is Bug/Flying, Scizor is
     * Bug/Steel, so a Scyther that drew Flying STAB hands Scizor a learnset with no on-type move
     * anywhere), and truncation (a 4-slot Togekiss takes Togepi's first four, one slot short of its only
     * attacker). Neither can be fixed by choosing a different slice of the pre-evolution's learnset -
     * Scizor's slice is the whole thing - so the invariant is repaired here instead, on the inherited
     * slots, under the evolved species' own types.
     */
    private void repairInheritedMoveset(Species pkmn, List<MoveLearnt> moves, int inheritedCount,
                                        Map<Integer, Integer> backfillLevels, boolean typeThemed,
                                        MovePools pools, List<Type> allTypes, List<Move> allMoves) {
        // Type structure is gated on Prefer Same Type exactly as the main path is: under plain Random the
        // player asked for type-blind learnsets, so only the "has an attacking move at all" half applies.
        boolean typeStructured = typeThemed;
        SpeciesLearnsetProfile profile =
                SpeciesLearnsetProfile.of(pkmn, allTypes, romHandler.generationOfPokemon(), random);
        if (satisfiesInheritedInvariant(moves, profile, typeStructured, allMoves)) {
            return;
        }
        int slot = inheritedRepairSlot(moves, inheritedCount, allMoves);
        if (slot < 0) {
            return;
        }
        // Everything except the slot being replaced, so its own outgoing move can't block its re-pick.
        List<Integer> learnt = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            if (i != slot) {
                learnt.add(moves.get(i).move);
            }
        }
        // Routed through pickComposedMove so the replacement gets the same power ceiling, soft power
        // floor, category lean, ability affinity and priority weighting as every other slot.
        moves.get(slot).move = pickComposedMove(typeStructured ? SlotRole.STAB : SlotRole.ATTACK, slot,
                moves, backfillLevels, profile, profile.powerScale(), learnt, pools).number;
    }

    // The invariant the self-randomized path already guarantees, restated over a finished learnset: a
    // damaging move of one of the species' own types at or before SPECIES_STAB_FLOOR_LEVEL (the same
    // floor ensureEarlyStabFloor enforces), or - with type structure off - simply a damaging move.
    static boolean satisfiesInheritedInvariant(List<MoveLearnt> moves, SpeciesLearnsetProfile profile,
                                               boolean typeStructured, List<Move> allMoves) {
        for (MoveLearnt ml : moves) {
            Move mv = moveById(ml.move, allMoves);
            if (mv == null || mv.category == MoveCategory.STATUS) {
                continue;
            }
            if (!typeStructured) {
                return true;
            }
            if (ml.level <= SPECIES_STAB_FLOOR_LEVEL && profile.stabTypes().contains(mv.type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which inherited slot to sacrifice. Only slots below {@code inheritedCount} are candidates - a
     * longer evolved species' leftover tail was randomized independently and already ran its own
     * guarantees. Within the floor band, an off-type damaging move goes first (a pure re-type, leaving
     * the species' status share untouched), then a status move; a species with nothing in the band at all
     * falls back to its earliest real slot, mirroring ensureEarlyStabFloor's own last resort of placing
     * STAB late rather than never. Evolution-move slots (level 0) are skipped - the composed path never
     * fills them. Returns -1 when there is nothing to repair.
     */
    static int inheritedRepairSlot(List<MoveLearnt> moves, int inheritedCount, List<Move> allMoves) {
        int earliestReal = -1;
        int earliestStatus = -1;
        for (int i = 0; i < Math.min(inheritedCount, moves.size()); i++) {
            MoveLearnt ml = moves.get(i);
            if (ml.level <= 0) {
                continue;
            }
            if (earliestReal == -1) {
                earliestReal = i;
            }
            if (ml.level > SPECIES_STAB_FLOOR_LEVEL) {
                continue;
            }
            Move mv = moveById(ml.move, allMoves);
            if (mv == null) {
                continue;
            }
            // Reaching here means the invariant failed, so any damaging move in the band is off-type.
            if (mv.category != MoveCategory.STATUS) {
                return i;
            }
            if (earliestStatus == -1) {
                earliestStatus = i;
            }
        }
        return earliestStatus != -1 ? earliestStatus : earliestReal;
    }

    // Padded-but-unfilled slots carry move ID 0, and a corrupt ID would throw rather than being ignored.
    private static Move moveById(int moveId, List<Move> allMoves) {
        return moveId <= 0 || moveId >= allMoves.size() ? null : allMoves.get(moveId);
    }

    // Splits [startIndex, n) into three near-equal contiguous thirds and returns which third slot i falls
    // into (0, 1, or 2) - the same partition vanillaThirdAttackCounts is measured against, so
    // assignSlotRoles' per-third reservation lines up with the vanilla shape it targets.
    private static int thirdIndexFor(int i, int startIndex, int n) {
        int span = n - startIndex;
        if (span <= 0) {
            return 0;
        }
        int offset = i - startIndex;
        int firstBoundary = (int) Math.round(span / 3.0);
        int secondBoundary = (int) Math.round(span * 2.0 / 3.0);
        if (offset < firstBoundary) {
            return 0;
        }
        return offset < secondBoundary ? 1 : 2;
    }

    // How many of a species' vanilla (pre-randomization) slots in [startIndex, n) were non-STATUS, per
    // learnset third. moves.get(i).move is still the untouched vanilla move ID here - nothing is
    // overwritten until randomizeMovesLearntForSpecies' final write-back loop runs
    // (species-movesets-shape-and-stab-guarantee-design.md Fix A).
    static int[] vanillaThirdAttackCounts(List<MoveLearnt> moves, int startIndex, List<Move> allMoves) {
        int n = moves.size();
        int[] counts = new int[3];
        for (int i = startIndex; i < n; i++) {
            int moveId = moves.get(i).move;
            if (moveId <= 0 || moveId >= allMoves.size()) {
                continue;
            }
            Move mv = allMoves.get(moveId);
            if (mv != null && mv.category != MoveCategory.STATUS) {
                counts[thirdIndexFor(i, startIndex, n)]++;
            }
        }
        return counts;
    }

    // Centers SpeciesLearnsetProfile.of's statusShare sample on this species' own vanilla ratio instead
    // of the fixed global mean. Falls back to the global mean when there's nothing to measure.
    static double vanillaStatusRatio(int[] thirdAttackCounts, int slotCount) {
        if (slotCount <= 0) {
            return SPECIES_STATUS_SHARE_MEAN;
        }
        int attacking = thirdAttackCounts[0] + thirdAttackCounts[1] + thirdAttackCounts[2];
        return Math.clamp(1.0 - (double) attacking / slotCount, 0.0, 1.0);
    }

    /**
     * Fills one slot under Sensible Movesets: role-scoped pool, hard level/quality ceiling, then a
     * weighted pick over the soft power floor and the species' physical/special lean.
     */
    private Move pickComposedMove(SlotRole role, int slotIndex, List<MoveLearnt> moves,
                                  Map<Integer, Integer> backfillEffectiveLevels,
                                  SpeciesLearnsetProfile profile, double powerScale, List<Integer> learnt,
                                  MovePools pools) {
        int slotLevel = backfillEffectiveLevels.getOrDefault(slotIndex, moves.get(slotIndex).level);
        List<Move> available = candidatesForRole(role, slotLevel, profile, learnt, pools);

        // Coverage is the weaker half of a vanilla learnset, so only its ceiling tightens - pulling the
        // floor down too would push coverage moves toward junk from both directions.
        double ceilingScale = role == SlotRole.COVERAGE
                ? powerScale * SPECIES_COVERAGE_CEILING_FACTOR : powerScale;
        if (role == SlotRole.STAB) {
            available = applySpeciesStabPowerCeiling(available, slotLevel, ceilingScale);
        } else {
            List<Move> wider = unused(
                    role == SlotRole.STATUS || role == SlotRole.WILDCARD ? pools.all() : pools.damaging(), learnt);
            available = applySpeciesPowerCeiling(available, wider, slotLevel, ceilingScale);
        }

        MoveCategory preferredCategory = random.nextDouble() < profile.categoryLean()
                ? MoveCategory.PHYSICAL : MoveCategory.SPECIAL;
        return weightedPick(available,
                candidate -> speciesLevelAppropriatenessWeight(candidate, slotLevel, powerScale)
                        * categoryLeanWeight(candidate, preferredCategory)
                        * abilityAffinityWeight(candidate, profile)
                        * priorityWeight(candidate, profile));
    }

    static double abilityAffinityWeight(Move mv, SpeciesLearnsetProfile profile) {
        return profile.abilityAffinity() != null && profile.abilityAffinity().test(mv)
                ? SPECIES_ABILITY_AFFINITY_BONUS : 1.0;
    }

    // Priority moves are weak by design, so the soft power floor would otherwise bury them at high level -
    // the species path has no goodWeakMoves exemption to lean on, since that would be a curated list.
    static double priorityWeight(Move mv, SpeciesLearnsetProfile profile) {
        return mv.priority > 0 ? profile.priorityBonus() : 1.0;
    }

    // STAB's own fallback order: try the type stabTypeFor would pick for this slot, then the species'
    // other own type if dual-typed, before ever widening off-type. Closes the Scizor zero-STAB gap where
    // the on-type pool for whichever single type stabTypeFor rolled happened to be exhausted even though
    // the species' OTHER type still had legal moves left
    // (species-movesets-shape-and-stab-guarantee-design.md Fix B). Package-private static so it's
    // directly unit-testable without a RomHandler.
    static List<Move> stabCandidates(SpeciesLearnsetProfile profile, List<Integer> learnt, MovePools pools,
                                     Random random) {
        Type first = profile.stabTypeFor(random);
        List<Move> candidates = unused(pools.byType(first), learnt);
        if (!candidates.isEmpty()) {
            return candidates;
        }
        for (Type type : profile.stabTypes()) {
            if (type != first) {
                List<Move> fallback = unused(pools.byType(type), learnt);
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            }
        }
        return candidates;
    }

    /**
     * The candidate pool for one role, narrowest first, widening until something is usable. Returns the
     * unused-move-filtered list; never empty unless the whole movepool is exhausted.
     */
    private List<Move> candidatesForRole(SlotRole role, int level, SpeciesLearnsetProfile profile,
                                         List<Integer> learnt, MovePools pools) {
        List<Move> candidates = switch (role) {
            case STATUS -> unused(pools.status(), learnt);
            case STAB -> stabCandidates(profile, learnt, pools, random);
            case COVERAGE -> unused(pools.byType(coverageTypeFor(level, profile)), learnt);
            case FILLER -> unused(pools.byType(Type.NORMAL), learnt);
            case ATTACK, WILDCARD -> List.of();
        };
        if (!candidates.isEmpty()) {
            return candidates;
        }
        // A type x category slice can be genuinely empty (or fully consumed) on a small movepool; losing
        // the theme is always preferable to leaving the slot unfilled.
        List<Move> wider = role == SlotRole.WILDCARD ? pools.all() : pools.damaging();
        candidates = unused(wider, learnt);
        if (!candidates.isEmpty()) {
            return candidates;
        }
        candidates = unused(pools.all(), learnt);
        // Only reachable if a species has more slots than the ROM has legal moves. Allowing a duplicate
        // beats returning nothing, which would leave the slot empty in the written ROM.
        return candidates.isEmpty() ? pools.all() : candidates;
    }

    // Normal is vanilla's generic filler and it is front-loaded, so an early coverage slot is more likely
    // to reach for it than a late one.
    private Type coverageTypeFor(int level, SpeciesLearnsetProfile profile) {
        double fillerChance = SPECIES_GENERIC_FILLER_EARLY
                + (SPECIES_GENERIC_FILLER_LATE - SPECIES_GENERIC_FILLER_EARLY)
                        * Math.min(1.0, level / LEVEL_POWER_SATURATION_LEVEL);
        if (random.nextDouble() < fillerChance || profile.coverageTypes().isEmpty()) {
            return Type.NORMAL;
        }
        List<Type> palette = new ArrayList<>(profile.coverageTypes());
        return palette.get(random.nextInt(palette.size()));
    }

    private static List<Move> unused(List<Move> pool, List<Integer> learnt) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        return pool.stream().filter(mv -> !learnt.contains(mv.number)).collect(Collectors.toList());
    }

    /** The move pools a species' slots draw from, built once per randomization run. */
    record MovePools(List<Move> all, List<Move> damaging, List<Move> status,
                     Map<Type, List<Move>> typeAll, Map<Type, List<Move>> typeDamaging) {
        List<Move> byType(Type type) {
            return type == null ? List.of() : typeDamaging.getOrDefault(type, List.of());
        }
    }

    private boolean checkForUnusedMove(List<Move> potentialList, List<Integer> alreadyUsed) {
        for (Move mv : potentialList) {
            if (!alreadyUsed.contains(mv.number)) {
                return true;
            }
        }
        return false;
    }

    private MovePools createSetsOfMoves(boolean noBroken, boolean widenDamagingPool) {
        List<Move> validMoves = new ArrayList<>();
        List<Move> validDamagingMoves = new ArrayList<>();
        List<Move> validStatusMoves = new ArrayList<>();
        Map<Type, List<Move>> validTypeMoves = new HashMap<>();
        Map<Type, List<Move>> validTypeDamagingMoves = new HashMap<>();
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

                if (mv.category == MoveCategory.STATUS) {
                    validStatusMoves.add(mv);
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

        return new MovePools(validMoves, validDamagingMoves, validStatusMoves, validTypeMoves,
                validTypeDamagingMoves);
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

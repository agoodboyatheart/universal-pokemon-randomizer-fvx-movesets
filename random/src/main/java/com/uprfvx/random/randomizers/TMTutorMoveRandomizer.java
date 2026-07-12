package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;

/**
 * A randomizer for which moves are found in TMs and at Move Tutors.
 * For compatibility with Pokemon, see {@link TMHMTutorCompatibilityRandomizer}.
 */
public class TMTutorMoveRandomizer extends Randomizer {

    private boolean tmChangesMade;
    private boolean tutorChangesMade;

    public TMTutorMoveRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    /**
     * Returns whether any changes have been made to TM moves.
     */
    public boolean isTMChangesMade() {
        return tmChangesMade;
    }

    /**
     * Returns whether any changes have been made to Move Tutor moves.
     */
    public boolean isTutorChangesMade() {
        return tutorChangesMade;
    }

    public void randomizeTMMoves() {
        boolean noBroken = settings.isBlockBrokenTMMoves();
        boolean preserveField = settings.isKeepFieldMoveTMs();
        double goodDamagingPercentage = settings.isTmsForceGoodDamaging() ? settings.getTmsGoodDamagingPercent() / 100.0 : 0;

        // Pick some random TM moves.
        int tmCount = romHandler.getTMCount();
        List<Move> allMoves = romHandler.getMoves();
        List<Integer> hms = romHandler.getHMMoves();
        List<Integer> oldTMs = romHandler.getTMMoves();
        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? romHandler.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        banned.addAll(romHandler.getIllegalMoves());
        // field moves?
        List<Integer> fieldMoves = romHandler.getFieldMoves();
        int preservedFieldMoveCount = 0;

        if (preserveField) {
            List<Integer> banExistingField = new ArrayList<>(oldTMs);
            banExistingField.retainAll(fieldMoves);
            preservedFieldMoveCount = banExistingField.size();
            banned.addAll(banExistingField);
        }

        // Determine which moves are pickable
        List<Move> usableMoves = new ArrayList<>(allMoves);
        usableMoves.remove(0); // remove null entry
        Set<Move> unusableMoves = new HashSet<>();
        Set<Move> unusableDamagingMoves = new HashSet<>();

        for (Move mv : usableMoves) {
            if (GlobalConstants.bannedRandomMoves[mv.number] || GlobalConstants.zMoves.contains(mv.number) ||
                    hms.contains(mv.number) || banned.contains(mv.number)) {
                unusableMoves.add(mv);
            } else if (GlobalConstants.bannedForDamagingMove[mv.number] || !mv.isGoodDamaging(romHandler.getPerfectAccuracy())) {
                unusableDamagingMoves.add(mv);
            }
        }

        usableMoves.removeAll(unusableMoves);
        List<Move> usableDamagingMoves = new ArrayList<>(usableMoves);
        usableDamagingMoves.removeAll(unusableDamagingMoves);

        // pick (tmCount - preservedFieldMoveCount) moves
        List<Integer> pickedMoves = new ArrayList<>();

        // Force a certain amount of good damaging moves depending on the percentage
        int goodDamagingLeft = (int)Math.round(goodDamagingPercentage * (tmCount - preservedFieldMoveCount));

        for (int i = 0; i < tmCount - preservedFieldMoveCount; i++) {
            Move chosenMove;
            if (goodDamagingLeft > 0 && !usableDamagingMoves.isEmpty()) {
                chosenMove = usableDamagingMoves.get(random.nextInt(usableDamagingMoves.size()));
            } else {
                chosenMove = usableMoves.get(random.nextInt(usableMoves.size()));
            }
            pickedMoves.add(chosenMove.number);
            usableMoves.remove(chosenMove);
            usableDamagingMoves.remove(chosenMove);
            goodDamagingLeft--;
        }

        // shuffle the picked moves because high goodDamagingPercentage
        // will bias them towards early numbers otherwise

        Collections.shuffle(pickedMoves, random);

        // finally, distribute them as tms
        int pickedMoveIndex = 0;
        List<Integer> newTMs = new ArrayList<>();

        for (int i = 0; i < tmCount; i++) {
            if (preserveField && fieldMoves.contains(oldTMs.get(i))) {
                newTMs.add(oldTMs.get(i));
            } else {
                newTMs.add(pickedMoves.get(pickedMoveIndex++));
            }
        }

        romHandler.setTMMoves(newTMs);
        tmChangesMade = true;
    }

    /**
     * Locks each Gym Leader's reward TM (as defined by {@link RomHandler#getGymLeaderTMs()}) to a
     * random move of that gym's assigned type theme. Status moves of the type are valid picks;
     * damaging bias, if wanted, is handled separately by the "Force % Good Damaging Moves" option.
     * <p>
     * Intended to run <b>after</b> both TM-move randomization and trainer-Pokémon randomization, so
     * that {@code gymThemes} reflects the types actually assigned to the gyms. Does nothing if there
     * is no gym-TM data for the game or no assigned themes.
     *
     * @param gymThemes Map of gym group tag (e.g. {@code "GYM1"}) to the type it was assigned.
     */
    public void typeLockGymLeaderTMs(Map<String, Type> gymThemes) {
        Map<String, Integer> gymLeaderTMs = romHandler.getGymLeaderTMs();
        if (gymLeaderTMs.isEmpty() || gymThemes == null || gymThemes.isEmpty()) {
            return;
        }

        boolean noBroken = settings.isBlockBrokenTMMoves();
        boolean preserveField = settings.isKeepFieldMoveTMs();

        List<Move> allMoves = romHandler.getMoves();
        List<Integer> hms = romHandler.getHMMoves();
        List<Integer> currentTMs = new ArrayList<>(romHandler.getTMMoves());
        List<Integer> fieldMoves = romHandler.getFieldMoves();
        int tmCount = romHandler.getTMCount();

        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? romHandler.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        banned.addAll(romHandler.getIllegalMoves());

        boolean changed = false;

        for (Map.Entry<String, Integer> entry : gymLeaderTMs.entrySet()) {
            Type type = gymThemes.get(entry.getKey());
            int tmNumber = entry.getValue();
            if (type == null || tmNumber < 1 || tmNumber > tmCount) {
                // No assigned theme for this gym, or the TM doesn't exist in this game.
                continue;
            }
            int tmIndex = tmNumber - 1;
            int oldMove = currentTMs.get(tmIndex);

            // Don't override a preserved field-move TM.
            if (preserveField && fieldMoves.contains(oldMove)) {
                continue;
            }

            // Pool of usable moves of the gym's type, excluding moves already taught by another TM
            // (to keep TM moves unique). The move currently on this TM stays eligible.
            List<Move> pool = new ArrayList<>();
            for (Move mv : allMoves) {
                if (mv == null || mv.number == 0 || mv.type != type) {
                    continue;
                }
                if (GlobalConstants.bannedRandomMoves[mv.number] || GlobalConstants.zMoves.contains(mv.number)
                        || hms.contains(mv.number) || banned.contains(mv.number)) {
                    continue;
                }
                if (mv.number != oldMove && currentTMs.contains(mv.number)) {
                    continue;
                }
                pool.add(mv);
            }
            if (pool.isEmpty()) {
                // No suitable same-type move available; leave the TM as it is.
                continue;
            }

            Move chosen = pool.get(random.nextInt(pool.size()));
            if (chosen.number != oldMove) {
                currentTMs.set(tmIndex, chosen.number);
                changed = true;
            }
        }

        if (changed) {
            romHandler.setTMMoves(currentTMs);
            tmChangesMade = true;
        }
    }

    public void randomizeMoveTutorMoves() {
        boolean noBroken = settings.isBlockBrokenTutorMoves();
        boolean preserveField = settings.isKeepFieldMoveTutors();
        double goodDamagingPercentage = settings.isTutorsForceGoodDamaging() ? settings.getTutorsGoodDamagingPercent() / 100.0 : 0;

        if (!romHandler.hasMoveTutors()) {
            return;
        }

        // Pick some random Move Tutor moves, excluding TMs.
        List<Move> allMoves = romHandler.getMoves();
        List<Integer> tms = romHandler.getTMMoves();
        List<Integer> oldMTs = romHandler.getMoveTutorMoves();
        int mtCount = oldMTs.size();
        List<Integer> hms = romHandler.getHMMoves();
        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? romHandler.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        banned.addAll(romHandler.getIllegalMoves());

        // field moves?
        List<Integer> fieldMoves = romHandler.getFieldMoves();
        int preservedFieldMoveCount = 0;
        if (preserveField) {
            List<Integer> banExistingField = new ArrayList<>(oldMTs);
            banExistingField.retainAll(fieldMoves);
            preservedFieldMoveCount = banExistingField.size();
            banned.addAll(banExistingField);
        }

        // Determine which moves are pickable
        List<Move> usableMoves = new ArrayList<>(allMoves);
        usableMoves.remove(0); // remove null entry
        Set<Move> unusableMoves = new HashSet<>();
        Set<Move> unusableDamagingMoves = new HashSet<>();

        for (Move mv : usableMoves) {
            if (GlobalConstants.bannedRandomMoves[mv.number] || tms.contains(mv.number) || hms.contains(mv.number)
                    || banned.contains(mv.number) || GlobalConstants.zMoves.contains(mv.number)) {
                unusableMoves.add(mv);
            } else if (GlobalConstants.bannedForDamagingMove[mv.number] || !mv.isGoodDamaging(romHandler.getPerfectAccuracy())) {
                unusableDamagingMoves.add(mv);
            }
        }

        usableMoves.removeAll(unusableMoves);
        List<Move> usableDamagingMoves = new ArrayList<>(usableMoves);
        usableDamagingMoves.removeAll(unusableDamagingMoves);

        // pick (tmCount - preservedFieldMoveCount) moves
        List<Integer> pickedMoves = new ArrayList<>();

        // Force a certain amount of good damaging moves depending on the percentage
        int goodDamagingLeft = (int) Math.round(goodDamagingPercentage * (mtCount - preservedFieldMoveCount));

        for (int i = 0; i < mtCount - preservedFieldMoveCount; i++) {
            Move chosenMove;
            if (goodDamagingLeft > 0 && !usableDamagingMoves.isEmpty()) {
                chosenMove = usableDamagingMoves.get(random.nextInt(usableDamagingMoves.size()));
            } else {
                chosenMove = usableMoves.get(random.nextInt(usableMoves.size()));
            }
            pickedMoves.add(chosenMove.number);
            usableMoves.remove(chosenMove);
            usableDamagingMoves.remove(chosenMove);
            goodDamagingLeft--;
        }

        // shuffle the picked moves because high goodDamagingPercentage
        // will bias them towards early numbers otherwise

        Collections.shuffle(pickedMoves, random);

        // finally, distribute them as tutors
        int pickedMoveIndex = 0;
        List<Integer> newMTs = new ArrayList<>();

        for (Integer oldMT : oldMTs) {
            if (preserveField && fieldMoves.contains(oldMT)) {
                newMTs.add(oldMT);
            } else {
                newMTs.add(pickedMoves.get(pickedMoveIndex++));
            }
        }

        romHandler.setMoveTutorMoves(newMTs);
        tutorChangesMade = true;
    }

}

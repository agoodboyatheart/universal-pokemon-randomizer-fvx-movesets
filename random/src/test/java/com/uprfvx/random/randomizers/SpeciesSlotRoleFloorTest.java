package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the two role-assignment floors added against
 * project_memory/species-moveset-pearl-real-rom-comparison-report.md's real-Pearl findings: a small
 * learnset with a high status roll could previously lose every slot to STATUS/WILDCARD (Togekiss, zero
 * attacking moves ever), and a species could go many independent STAB-vs-COVERAGE rolls without a single
 * hit (Raikou/Dialga, no STAB until level 70-78). No ROM required - {@code assignSlotRoles} is
 * package-private and {@code SpeciesLearnsetProfile} is directly constructible.
 */
public class SpeciesSlotRoleFloorTest {

    private static final int SEED_COUNT = 200;

    private SpeciesLearnsetProfile profileWithStatusShare(double statusShare) {
        return new SpeciesLearnsetProfile(statusShare, List.of(Type.FIRE), Set.of(),
                1.0, 0.5, null, 1.0);
    }

    // --- Floor 1: at least one non-STATUS/WILDCARD slot always survives ---

    @Test
    public void smallLearnsetWithMaxStatusShareStillKeepsAnAttackingSlot() {
        // Togekiss-shaped: 4 slots, all level 1, statusShare pinned at its sampled max.
        for (long seed = 0; seed < SEED_COUNT; seed++) {
            List<MoveLearnt> moves = fourLevelOneSlots();
            SpeciesLearnsetProfile profile = profileWithStatusShare(Randomizer.SPECIES_STATUS_SHARE_MAX);
            SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                    moves, 0, profile, Set.of(), true, new Random(seed));

            boolean hasAttackingRole = false;
            for (SpeciesMovesetRandomizer.SlotRole role : roles) {
                if (role != SpeciesMovesetRandomizer.SlotRole.STATUS
                        && role != SpeciesMovesetRandomizer.SlotRole.WILDCARD) {
                    hasAttackingRole = true;
                    break;
                }
            }
            assertTrue(hasAttackingRole, "seed " + seed + " left zero attacking-eligible slots on a "
                    + "4-slot learnset");
        }
    }

    // --- Floor 2: STAB lands by SPECIES_STAB_FLOOR_LEVEL whenever an attacking slot exists that early ---

    @Test
    public void longLearnsetAlwaysGetsStabByTheFloorLevel() {
        // A 12-slot learnset spanning level 1 to 55, low status share so plenty of attacking slots exist
        // early - isolates the STAB-vs-COVERAGE roll from Floor 1's composition floor.
        int[] levels = {1, 1, 5, 10, 15, 20, 26, 32, 38, 44, 50, 55};
        for (long seed = 0; seed < SEED_COUNT; seed++) {
            List<MoveLearnt> moves = new ArrayList<>();
            for (int level : levels) {
                moves.add(new MoveLearnt(0, level));
            }
            SpeciesLearnsetProfile profile = profileWithStatusShare(Randomizer.SPECIES_STATUS_SHARE_MIN);
            SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                    moves, 0, profile, Set.of(), true, new Random(seed));

            boolean hasEarlyStab = false;
            for (int i = 0; i < roles.length; i++) {
                if (roles[i] == SpeciesMovesetRandomizer.SlotRole.STAB
                        && moves.get(i).level <= Randomizer.SPECIES_STAB_FLOOR_LEVEL) {
                    hasEarlyStab = true;
                    break;
                }
            }
            assertTrue(hasEarlyStab, "seed " + seed + " had no STAB role at or before level "
                    + Randomizer.SPECIES_STAB_FLOOR_LEVEL);
        }
    }

    @Test
    public void plainRandomIsUntouchedByTheStabFloor() {
        // typeStructured=false collapses everything to ATTACK - the STAB floor must not run at all, since
        // a plain-Random learnset has no STAB/COVERAGE distinction to promote.
        List<MoveLearnt> moves = fourLevelOneSlots();
        SpeciesLearnsetProfile profile = profileWithStatusShare(Randomizer.SPECIES_STATUS_SHARE_MIN);
        SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                moves, 0, profile, Set.of(), false, new Random(0));

        for (SpeciesMovesetRandomizer.SlotRole role : roles) {
            assertTrue(role == SpeciesMovesetRandomizer.SlotRole.ATTACK
                    || role == SpeciesMovesetRandomizer.SlotRole.STATUS
                    || role == SpeciesMovesetRandomizer.SlotRole.WILDCARD, "unexpected role " + role);
        }
    }

    private List<MoveLearnt> fourLevelOneSlots() {
        List<MoveLearnt> moves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            moves.add(new MoveLearnt(0, 1));
        }
        return moves;
    }
}

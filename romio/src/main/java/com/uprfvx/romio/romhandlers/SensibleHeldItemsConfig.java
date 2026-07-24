package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.gamedata.Type;

import java.util.List;
import java.util.Map;

/**
 * Per-generation data + capability flags for {@link AbstractRomHandler#getSensibleHeldItemsForGen4Plus}.
 * Nullable map fields represent mechanics that don't exist in that generation's item pool at all
 * (e.g. Gen 4 has no Air Balloon), as opposed to the boolean flags, which gate mechanics that need
 * extra type-table/move-count logic beyond just "is this item in the pool".
 */
public record SensibleHeldItemsConfig(
        List<Integer> generalPurposeConsumableItems,
        List<Integer> generalPurposeItems,
        Map<Type, List<Integer>> typeBoostingItems,
        Map<Type, Integer> consumableTypeBoostingItems,
        Map<Type, Integer> weaknessReducingBerries,
        Map<Integer, List<Integer>> abilityBoostingItems,
        Map<Integer, Integer> consumableAbilityBoostingItems,
        Map<Integer, List<Integer>> speciesBoostingItems,
        Map<Integer, List<Integer>> moveBoostingItems,
        boolean hasAirBalloon,
        boolean hasAssaultVest,
        boolean hasEviolite
) {}

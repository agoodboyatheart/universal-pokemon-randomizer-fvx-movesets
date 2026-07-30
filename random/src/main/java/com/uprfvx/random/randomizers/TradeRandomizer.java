package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.customnames.CustomNamesSet;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TradeRandomizer extends Randomizer {

    public TradeRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    public void randomizeIngameTrades() {
        randomizeGivenTrades();
        randomizeRequestedTrades(null);
    }

    /**
     * Randomizes the "given" species of every in-game trade (plus nickname/OT/IVs/held item, which
     * aren't species-obtainability-sensitive). Does not touch requested species at all, except to
     * keep it equal to the new given species for trades where it was already equal to the old given
     * species (i.e. trades that don't really have a distinct "want", they're a straight handout).
     */
    public void randomizeGivenTrades() {
        boolean randomNickname = settings.isRandomizeInGameTradesNicknames();
        boolean randomOT = settings.isRandomizeInGameTradesOTs();
        boolean randomStats = settings.isRandomizeInGameTradesIVs();
        boolean randomItem = settings.isRandomizeInGameTradesItems();
        boolean similarStrength = settings.isTradeSimilarStrength();
        boolean basicOnly = settings.isTradeBasicOnly();
        boolean noLegendaries = settings.isTradeNoLegendaries();
        CustomNamesSet customNames = getCustomNames();

        // Process trainer names
        List<String> trainerNames = new ArrayList<>();
        // Check for the file
        if (randomOT) {
            int maxOT = romHandler.maxTradeOTNameLength();
            for (String trainername : customNames.trainerNames()) {
                int len = romHandler.internalStringLength(trainername);
                if (len <= maxOT && !trainerNames.contains(trainername)) {
                    trainerNames.add(trainername);
                }
            }
        }

        // Process nicknames
        List<String> nicknames = new ArrayList<>();
        // Check for the file
        if (randomNickname) {
            int maxNN = romHandler.maxTradeNicknameLength();
            for (String nickname : customNames.pokemonNicknames()) {
                int len = romHandler.internalStringLength(nickname);
                if (len <= maxNN && !nicknames.contains(nickname)) {
                    nicknames.add(nickname);
                }
            }
        }

        // get old trades
        List<InGameTrade> trades = romHandler.getInGameTrades();
        List<String> usedOTs = new ArrayList<>();
        List<String> usedNicknames = new ArrayList<>();
        List<Item> possibleItems = new ArrayList<>(romHandler.getAllowedItems());

        SpeciesSet speciesPool = new SpeciesSet(rSpecService.getSpecies(noLegendaries, false, false));
        if (basicOnly) {
            speciesPool = speciesPool.filterBasic(false);
        }
        SpeciesSet givenLeft = new SpeciesSet(speciesPool);

        int nickCount = nicknames.size();
        int trnameCount = trainerNames.size();

        for (InGameTrade trade : trades) {
            // pick new given pokemon
            Species oldgiven = trade.getGivenSpecies();
            Species given = pickTradeSpecies(givenLeft, givenLeft, speciesPool, oldgiven, similarStrength);
            trade.getGivenSpeciesHolder().setSpecies(given);
            randomizeGivenCosmeticForme(trade);

            // requested pokemon?
            if (oldgiven == trade.getRequestedSpecies()) {
                // preserve trades for the same pokemon
                trade.setRequestedSpecies(given);
            }

            // nickname?
            if (randomNickname && nickCount > usedNicknames.size()) {
                String nickname = nicknames.get(random.nextInt(nickCount));
                while (usedNicknames.contains(nickname)) {
                    nickname = nicknames.get(random.nextInt(nickCount));
                }
                usedNicknames.add(nickname);
                trade.setNickname(nickname);
            } else if (trade.getNickname().equalsIgnoreCase(oldgiven.getName())) {
                // change the name for sanity
                trade.setNickname(trade.getGivenSpecies().getName());
            }

            if (randomOT && trnameCount > usedOTs.size()) {
                String ot = trainerNames.get(random.nextInt(trnameCount));
                while (usedOTs.contains(ot)) {
                    ot = trainerNames.get(random.nextInt(trnameCount));
                }
                usedOTs.add(ot);
                trade.setOtName(ot);
                trade.setOtId(random.nextInt(65536));
            }

            if (randomStats) {
                int maxIV = romHandler.hasDVs() ? 16 : 32;
                for (int i = 0; i < trade.getIVs().length; i++) {
                    trade.getIVs()[i] = random.nextInt(maxIV);
                }
            }

            if (randomItem) {
                trade.setHeldItem(possibleItems.get(random.nextInt(possibleItems.size())));
            }
        }

        // things that the game doesn't support should just be ignored
        romHandler.setInGameTrades(trades);
        changesMade = true;
    }

    /**
     * Randomizes the "requested" species of every in-game trade that has a distinct one (skipped
     * entirely unless "Randomize Given and Requested" is on). Must run after {@link #randomizeGivenTrades()}.
     * @param obtainablePool If non-null/non-empty, requested species are preferentially drawn from the
     *                       intersection of the normal filtered pool and this set (falling back to the
     *                       unrestricted filtered pool if that intersection is empty). Pass null for the
     *                       old, unrestricted behavior.
     */
    public void randomizeRequestedTrades(SpeciesSet obtainablePool) {
        boolean randomizeRequest = settings.getInGameTradesMod() == Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED;
        if (!randomizeRequest) {
            return;
        }
        boolean similarStrength = settings.isTradeSimilarStrength();
        boolean basicOnly = settings.isTradeBasicOnly();
        boolean noLegendaries = settings.isTradeNoLegendaries();

        SpeciesSet speciesPool = new SpeciesSet(rSpecService.getSpecies(noLegendaries, false, false));
        if (basicOnly) {
            speciesPool = speciesPool.filterBasic(false);
        }
        if (obtainablePool != null && !obtainablePool.isEmpty()) {
            SpeciesSet obtainableAndFiltered = new SpeciesSet(speciesPool);
            obtainableAndFiltered.retainAll(obtainablePool);
            if (!obtainableAndFiltered.isEmpty()) {
                speciesPool = obtainableAndFiltered;
            }
            // else: fall back to the unrestricted (but still legendary/basic-filtered) pool - an
            // extremely restrictive settings combo could otherwise leave nothing pickable.
        }
        SpeciesSet requestLeft = new SpeciesSet(speciesPool);

        List<InGameTrade> trades = romHandler.getInGameTrades();
        for (InGameTrade trade : trades) {
            Species given = trade.getGivenSpecies();
            if (given == trade.getRequestedSpecies()) {
                // already aligned by randomizeGivenTrades() - nothing left to do
                continue;
            }
            if (trade.getRequestedSpecies() != null) {
                Species oldrequested = trade.getRequestedSpecies();
                SpeciesSet requestCandidates = requestLeft.contains(given) && requestLeft.size() > 1 ?
                        requestLeft.filter(sp -> sp != given) : requestLeft;
                Species request = pickTradeSpecies(requestCandidates, requestLeft, speciesPool, oldrequested,
                        similarStrength);
                trade.setRequestedSpecies(request);
            }
        }

        romHandler.setInGameTrades(trades);
        changesMade = true;
    }

    /**
     * Picks a replacement Species for a trade from candidates, then removes it from left (refilling left
     * from fullPool if left becomes empty as a result).
     */
    private Species pickTradeSpecies(SpeciesSet candidates, SpeciesSet left, SpeciesSet fullPool, Species anchor,
                                      boolean similarStrength) {
        Species picked = similarStrength ?
                candidates.getRandomSimilarStrengthSpecies(anchor, false, random) :
                candidates.getRandomSpecies(random);
        left.remove(picked);
        if (left.isEmpty()) {
            left.addAll(fullPool);
        }
        return picked;
    }

    /**
     * If possible, sets the "given" Species of the given InGameTrade to a random cosmetic forme.<br>
     * Does nothing if InGameTrade doesn't allow alt formes, or if the Species doesn't have any cosmetic alt formes.
     */
    private void randomizeGivenCosmeticForme(InGameTrade trade) {
        SpeciesHolder sh = trade.getGivenSpeciesHolder();
        if (sh.isAltFormeAllowed() && sh.getSpecies().isBaseForme()) {
            Species base = sh.getSpecies().getBaseForme();
            sh.setFormeNumber(base.getRandomCosmeticFormeNumber(random));
        }
    }

}

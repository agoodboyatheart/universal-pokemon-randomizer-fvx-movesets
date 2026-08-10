# tools/ — combined-build interaction tripwire

`branch_tripwire.py` answers one question: **when `local-combined-build` fails a test, is that failure
already failing on one of the branches that feed it, or did the merge create it?**

Every feature branch carries known-failing ROM tests — `TestRomHandler$NotImplementedException` on
generations the test harness cannot fake, plus a few genuinely flaky randomised assertions. That noise
floor is why a combined run showing 36 failures is unreadable by eye. The tripwire records the floor
per branch and subtracts it. What survives is, by construction, an interaction bug, and the config's
`suspects` map names the branches to look at.

## Use

```bash
python tools/branch_tripwire.py plan        # what would run, which baselines are stale — runs nothing
python tools/branch_tripwire.py baseline    # record/refresh the known-failure set for each branch
git checkout local-combined-build
python tools/branch_tripwire.py check       # run the combined build, report ONLY new failures
```

Useful flags:

| Flag | Effect |
|---|---|
| `--branch B [B...]` | (baseline) only these branches |
| `--repeat N` | (baseline) N passes, accumulating flaky failures into the floor |
| `--force` | (baseline) re-record even if current |
| `--no-run` | (check) re-read the last XML instead of rebuilding — free |
| `--verbose` | (check) also list baselined failures that did not recur |
| `--tests SUBSTR...` | restrict the test set; must be the *same* scope for baseline and check |
| `--include-report-only` | also run the print-only profile/extraction harnesses |

`check` exits 1 when there are new failures, 0 when clean. Note that `| tail` masks this — `$?` is
then the pipe's last command, not the script's.

## First run

`baseline` checks out each branch in turn and runs the test set on it, so it needs a clean tracked
working tree and takes a while — the set is ~19 classes and `TrainerRandomizersTest` alone is about
four minutes. Run it once, then it only re-records branches whose tip has actually moved (baselines
are keyed by branch SHA). Scope with `--branch` to spread the cost.

Baselines land in `tools/baselines/<branch>.json` and are worth committing: they are the machine-
checkable replacement for the expected-failure notes previously scattered through `project_memory`.

## Reading the output

- **`0 NEW`** — every failure on the combined build already fails on a contributing branch. The merge
  introduced nothing.
- **New failure listed with suspect branches** — fix it on the suspect branch and re-merge. Never fix
  it on `local-combined-build`; that branch is disposable and a fix made there is lost on the next
  rebuild (this already happened once with `centerPower`, which had to be back-ported later).
- **A single new failure in a randomised assertion** — suspect flakiness before suspecting the merge.
  Re-run, or re-baseline the owning branch with `--repeat 2`, before investigating.

## Maintenance

`tripwire_config.json` holds the branch list, the test set, and two lookup tables:

- `suspects` — test class → branches whose main-source diff touches the code that test covers.
- `sharedSourceHotspots` — files edited by more than one branch, where interaction bugs concentrate.
  `TrainerPokemonRandomizer.java` (5 branches) and `Randomizer.java` (4) are the two to review by hand
  after every rebuild of `local-combined-build`.

Regenerate the underlying data with `git diff --name-only master...<branch> -- '*/main/*'`. Add a new
branch to `branches`, its dedicated test classes to `testSet`, and its ownership to `suspects`.

## Known limitations

- **Isolated-subsystem coverage only.** The golden masters build their randomizer with a private
  `new Random(SEED)`, so they are immune to RNG drift — which is what makes them branch-attributable,
  but also means they cannot see bugs mediated by the shared RNG stream in a real full run
  (`RandomSource` holds one `nonCosmetic` Random for the whole run, so any branch that changes how
  many draws it makes shifts every category downstream). For full-run bugs, isolate by toggling the
  feature's `Settings` flag across several seeds — do **not** try to replay a seed on a single branch,
  because the stream will not line up.
- **`shared-moveset-logic` has no dedicated test class, by design.** Every symbol it adds
  (`weightedPick` and the `TIER_*` constants in `Randomizer.java`; `goodStatusMoves`, `goodWeakMoves`,
  `badStrongMoves` and the fixed-constant-damage gate in `GlobalConstants.java`) is declaration-only on
  that branch — its consumers live on `better-movesets` and `species-power-curve`. There is nothing to
  exercise, and a unit test of those static helpers needs no ROM, so `testROMs` could not consume it
  anyway. Its real failure mode is a *symbol* lost in a merge, which breaks compilation rather than
  producing an attributable test case. It is instead listed under `suspects` for the moveset classes
  whose golden masters would move if its helpers changed.
- **`static-trade-pokemon-filters` shares `TradeRandomizerTest`** with `encounter-anti-duplication`,
  so a failure in that class is ambiguous between the two — but it owns four dedicated *methods*
  inside it (`basicOnlyOnlyPicksBasicPokemon`, `noLegendariesExcludesLegendaries`,
  `similarStrengthDoesNotThrow`, `combinedFiltersDoNotHangOrThrow`), so attribution by method name
  works today.
- **A branch whose fix makes a *master* test pass cannot be regression-checked by `check` alone.**
  `check` subtracts the union of every branch's `knownFailures`, master's included, so if a merge lost
  such a fix the restored failure is absorbed as already-known instead of reported. This is why
  `fix-oras-encounter-reuse` has `OrasEncounterReuseRandomizerTest` rather than relying on
  `WildEncounterRandomizerTest.doNotUsePrematureEvosWorks`, which fails on both ORAS ROMs on master
  and is in master's baseline. A class that exists only on the owning branch cannot be absorbed that
  way. Prefer a branch-only class whenever a branch *fixes* an existing failure.
- **Do not run while another Gradle build is in flight** — the run compiles what is on disk when it
  starts, and `baseline` switches branches underneath it.

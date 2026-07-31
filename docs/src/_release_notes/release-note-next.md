<!-- This is a template for release notes. --> 
<!-- If you are a contributor editing this file as part of a PR, most of the below should be left untouched. -->
<!-- If you are finalizing a release, everything in square brackets should be replaced. -->

[Greeting, short description of the release. Mention if it's a minor or major release, highlight major features.]

<!-- When editing as part of a PR, credit yourself and people in the other categories as appropriate.-->
<!-- When finalizing, any category below can be skipped/removed if there are no people in it. -->
<!-- People on GitHub should be referred to using their ID with the @. E.g. @namehere. 
     For redditors, /u/namehere works for brevity. 
     For people from all other forums, their forum username should be used alongside the forum's name. E.g. "Jane Doe from Spriter's Resource". -->
Thanks to
@AxelElric8 for your code contributions,
@agoodboyatheart for the Gym Leader TM type-lock feature,
[Every person who submitted a solved issue] for reporting Issues,
[Any person on e.g. Reddit who reported solved bugs or suggested implemented features] for [whatever they did],
[Every person who made a new CPG] for the CPG sprites, and
[Community members who helped with some feature] for help with [feature]
[Etc.]!

# How to use

<!-- This [VERSION] and [OS] are the exception, to not replace while finalizing. [VERSION] is automatically replaced by a build script, and [OS] should remain for the end users. -->
Download the Randomizer below by clicking on `UPR_FVX-[VERSION]-[OS].zip`. If you are on Linux or Mac, and don't know if your computer uses x86 or ARM, there are guides on the internet. After downloading, extract the contents of the zip file to a folder on your computer. You can then run the Randomizer by double-clicking the launcher script:

- Windows: Use `launcher.bat`
- Linux: Use `launcher.sh`
- Mac: Use `launcher.command`

# Changelog
## New and Changed Features
<!-- When editing as part of a PR, add your feature/bugfix below. Use (Issue #[issue num]) to denote the associated issue. -->
<!-- Group features by where they appear in the GUI tabs. Namely, use the names of the boxed categories (not necessarily the same as the tab names). This means e.g. a "Pokemon Evolutions" feature would go between "Pokemon Base Stats" and "Static Pokemon". -->
<!-- Below are some example features. They are not expansive, because it is annoying to remove a dozen categories that don't have any new/changed features this release. -->
<!-- (Gen [N]) can be used to denote a feature or bugfix only is relevant when randomizing certain Generations, and (GUI) for GUI stuff. -->

### Pokemon Base Stats
- [The description of a new feature here.] (Issue #[issue num])

### TMs & HMs
- Added a "Gym Leader TMs Match Type" option. When TM moves are randomized and a Foe Pokémon
  type-theme setting is in use (Unchanged, Type Themed, Type Themed (Elite 4/Gyms), Keep Themed,
  or Keep Theme or Primary), each Gym Leader's reward TM is locked to a random move of that gym's
  assigned type — including status moves — so gym TMs stay thematic. Follows the gym's actually
  assigned type, even when Type Themed reassigns it randomly. (Only affects games with authored
  gym-leader TM data.)
- Randomized TM and Move Tutor rosters are now composed to match the shape of a real game's, instead
  of being drawn uniformly from the whole move list. The roster keeps roughly two thirds damaging to
  one third status moves; the damaging half is balanced between physical and special (Gen 4+) rather
  than inheriting the move list's natural lean toward physical; wildly inaccurate moves are thinned
  out to about the rate a real TM list carries; base power is pulled toward the 60-99 range that most
  real TMs sit in, without removing the high-power tail; and (Gen 4+) every type is guaranteed at
  least one damaging TM, so a seed can no longer leave a type with no TM at all. Move Tutors get a
  larger share of high-power moves than TMs do, as they do in the real games. Field-move preservation,
  the TM/Tutor no-duplicates rule, and the "Force % of Good Damaging Moves" options are unaffected.
- Added "Sensible TM Compatibility" and "Sensible Tutor Compatibility" options, each available when
  its pool's compatibility setting is "Random (prefer same type)". Instead of rolling every
  Pokémon/move pair on its own, compatibility is shaped the way a real game shapes it: each Pokémon
  gets a number of moves based on its stats, so a Caterpie learns few and a legendary learns many;
  and each move gets a breadth, so some are near-universal filler while others reach only a handful
  of Pokémon. Overall counts land on the unmodified game's own — which is roughly what randomization
  already gave you for TMs, but about half as many Move Tutor moves, since tutors are far more
  restricted in the real games than randomization has been treating them. A Gym Leader's reward TM is
  never one of the rare ones. With "Follow Evolutions" ticked, an evolution keeps everything its
  pre-evolution could learn and tops up to its own total. HM compatibility is unchanged.

### Static Pokemon
- (Gen 3) [The description of a changed feature here.]

### Misc. Tweaks
- (Gen 4+5) [The description of a misc. tweak addition or change here.]

<!-- Features that don't fit in any of the GUI tabs go in "Misc.". Not to be confused with "Misc. Tweaks". -->
### Misc.
- Added Level Caps section to the log file to track boss levels across generations.

## Bugfixes
- (Gen [N]) Fixed [...]. 
- (GUI) Fixed [...].

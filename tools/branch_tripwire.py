#!/usr/bin/env python3
"""
Interaction tripwire for local-combined-build.

Every feature branch carries its own known-failing ROM tests (TestRomHandler NotImplementedException
on generations the test harness cannot fake, plus a few genuinely flaky randomised assertions). That
noise floor is what makes a combined-build test run hard to read: 36 failures might be entirely
pre-existing, or might hide one real merge interaction.

This tool records the noise floor per branch, then subtracts it from a combined-build run. What is
left over -- a test that fails on local-combined-build but on none of the branches that feed it -- is
by construction an interaction bug, and the config's 'suspects' map names the branches to look at.

Why this cannot just diff two Gradle runs:
  * testROMs sets ignoreFailures=true, so the exit code is meaningless -- results come from the JUnit
    XML in random/build/test-results/testROMs/.
  * That directory is NOT cleared between runs, so a previous branch's XML survives and would be read
    as the current branch's result. Every run here wipes it first.
  * Gradle will report testROMs UP-TO-DATE and skip execution entirely on a repeat invocation, so
    every run passes --rerun-tasks.

Usage:
    python tools/branch_tripwire.py plan                 # show what would run, no builds
    python tools/branch_tripwire.py baseline             # record/refresh every stale branch baseline
    python tools/branch_tripwire.py baseline --branch better-movesets
    python tools/branch_tripwire.py check                # run combined build, report NEW failures only
    python tools/branch_tripwire.py check --no-run       # re-read the last XML without rebuilding

Baselines live in tools/baselines/<branch>.json and are keyed by the branch tip SHA, so a baseline
goes stale automatically when its branch moves and 'baseline' re-records only what changed.
"""

import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TOOLS = REPO / "tools"
CONFIG_PATH = TOOLS / "tripwire_config.json"
BASELINE_DIR = TOOLS / "baselines"
RESULTS_DIR = REPO / "random" / "build" / "test-results" / "testROMs"
TEST_SRC_ROOTS = ("random/src/test/java/",)

# The Bash tool / a bare shell often has no JAVA_HOME even though the JDK is installed.
FALLBACK_JAVA_HOME = r"C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"


# --------------------------------------------------------------------------------------- git helpers

def git(*args, check=True):
    """Run a git command in the repo and return its stripped stdout."""
    result = subprocess.run(
        ["git", "-C", str(REPO), *args],
        capture_output=True, text=True, check=False,
    )
    if check and result.returncode != 0:
        raise RuntimeError("git {} failed:\n{}".format(" ".join(args), result.stderr.strip()))
    return result.stdout.strip()


def current_branch():
    return git("branch", "--show-current")


def tip_sha(branch):
    return git("rev-parse", branch)


def working_tree_is_clean():
    """Tracked-file cleanliness only. Untracked files (custom player graphics, ROMs) are expected."""
    return git("status", "--porcelain", "--untracked-files=no") == ""


def classes_present_on(branch, wanted):
    """
    Subset of `wanted` whose test source file exists on `branch`.

    Branch-specific tests (the golden masters) only exist on their own branch. Filtering per branch
    keeps Gradle from failing on a --tests pattern that matches nothing, and is the correct semantics:
    only a branch that has the test can contribute a baseline for it.
    """
    tree = git("ls-tree", "-r", "--name-only", branch)
    files = set(tree.splitlines())
    present = []
    for name in wanted:
        if any(f.startswith(root) and f.endswith("/" + name + ".java") for f in files for root in TEST_SRC_ROOTS):
            present.append(name)
    return present


# ------------------------------------------------------------------------------------ gradle + JUnit

def gradle_env():
    env = dict(os.environ)
    if not env.get("JAVA_HOME") and Path(FALLBACK_JAVA_HOME).is_dir():
        env["JAVA_HOME"] = FALLBACK_JAVA_HOME
    return env


def run_tests(classes, label):
    """Wipe stale results, run testROMs for `classes`, and return the parsed failure set."""
    if not classes:
        print("  (no tests from the set exist here -- skipping)")
        return set(), 0

    if RESULTS_DIR.exists():
        shutil.rmtree(RESULTS_DIR)

    cmd = [str(REPO / "gradlew.bat"), "random:testROMs", "--rerun-tasks"]
    for name in classes:
        cmd += ["--tests", "*" + name]

    print("  running {} test classes on {} ...".format(len(classes), label))
    # ignoreFailures=true means a non-zero exit code signals a BUILD problem, not test failures.
    result = subprocess.run(cmd, cwd=str(REPO), env=gradle_env(), capture_output=True, text=True)
    if result.returncode != 0:
        tail = "\n".join((result.stdout + result.stderr).splitlines()[-25:])
        raise RuntimeError("Gradle build failed on {} (not a test failure):\n{}".format(label, tail))

    details, total = parse_results()
    print("  -> {} failing / {} test cases".format(len(details), total))
    return set(details), total


PARAM_INDEX = re.compile(r"^\[\d+\]\s*")


def normalize_case_name(name):
    """
    Drop the leading "[13] " invocation index from a @ParameterizedTest display name.

    The index is positional over the parameter list, so it is NOT stable across branches: a branch
    that adds one ROM case shifts every index after it, and "[13] Fire Red (U) 1.0" on one branch is
    "[14] Fire Red (U) 1.0" on another. Keying on it makes every single failure look new. The ROM
    name that follows is the real identity.
    """
    return PARAM_INDEX.sub("", name or "")


def normalize_key(key):
    """Normalize a stored baseline key, so baselines recorded before this rule stay usable."""
    if "::" not in key:
        return key
    head, name = key.split("::", 1)
    return "{}::{}".format(head, normalize_case_name(name))


def method_from_trace(cls, trace):
    """
    Recover the failing test method name from a stack trace.

    Necessary because JUnit writes @ParameterizedTest cases as name="[13] Fire Red (U) 1.0" with the
    method name nowhere in the attributes -- so two parameterized methods in one class (say
    betterMovesets_DoesNotCauseCrash and betterMovesets_DoesNotCauseUbiquitousMove) produce testcase
    elements that are identical apart from the trace. Keying on class+name alone silently merges them
    and halves the failure count.

    Frames run deepest-first, so the LAST frame in the test class is the test method itself rather
    than a helper it called.
    """
    pattern = re.compile(r"\bat\s+[\w.$]*\b" + re.escape(cls) + r"\.(\w+)\(")
    found = pattern.findall(trace or "")
    return found[-1] if found else "?"


def parse_results():
    """
    Read every TEST-*.xml and return ({key: (summary, site)}, total_testcase_count).

    Key is "Class.method::displayName" -- the ROM parameter is part of the identity, which is exactly
    the granularity attribution needs (a failure on Platinum but not on Emerald is a real distinction).
    """
    details = {}
    total = 0
    for path in glob.glob(str(RESULTS_DIR / "TEST-*.xml")):
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            total += 1
            cls = (case.get("classname") or "?").split(".")[-1]
            for node in list(case.findall("failure")) + list(case.findall("error")):
                trace = node.text or ""
                method = method_from_trace(cls, trace)
                message = (node.get("message") or node.get("type") or "").strip().splitlines()
                summary = message[0][:140] if message else (node.get("type") or "")
                site = ""
                for line in trace.splitlines():
                    if cls in line and "at " in line:
                        site = line.strip()
                        break
                key = "{}.{}::{}".format(cls, method, normalize_case_name(case.get("name")))
                details[key] = (summary, site)
    return details, total


# ---------------------------------------------------------------------------------------- baselines

def baseline_path(branch):
    return BASELINE_DIR / (branch.replace("/", "_") + ".json")


def load_baseline(branch):
    path = baseline_path(branch)
    if not path.exists():
        return None
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    data["knownFailures"] = [normalize_key(k) for k in data.get("knownFailures", [])]
    return data


def save_baseline(branch, sha, classes, failures, total, repeats):
    BASELINE_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "branch": branch,
        "sha": sha,
        "repeats": repeats,
        "testClasses": sorted(classes),
        "totalTestCases": total,
        "knownFailures": sorted(failures),
    }
    with open(baseline_path(branch), "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")


def baseline_is_stale(branch):
    existing = load_baseline(branch)
    if existing is None:
        return True, "no baseline recorded"
    if existing.get("sha") != tip_sha(branch):
        return True, "branch moved since baseline ({} -> {})".format(
            existing.get("sha", "?")[:8], tip_sha(branch)[:8])
    return False, "up to date ({} known failures)".format(len(existing.get("knownFailures", [])))


# ------------------------------------------------------------------------------------------ commands

def cmd_plan(config, args):
    wanted = test_set(config, args)
    print("Test set ({} classes):".format(len(wanted)))
    for name in wanted:
        print("  {}".format(name))
    print("\nBranch baselines:")
    for branch in config["branches"]:
        stale, why = baseline_is_stale(branch)
        present = classes_present_on(branch, wanted)
        print("  {:<30} {:<10} {:<45} {} classes".format(
            branch, "STALE" if stale else "ok", why, len(present)))
    print("\nA full baseline run executes the set once per stale branch, and checking out each branch")
    print("forces a recompile. Budget accordingly, or scope with --branch.")


def cmd_baseline(config, args):
    wanted = test_set(config, args)
    targets = args.branch or config["branches"]

    todo = []
    for branch in targets:
        stale, why = baseline_is_stale(branch)
        if stale or args.force:
            todo.append(branch)
        else:
            print("skip {} -- {}".format(branch, why))
    if not todo:
        print("\nEvery baseline is current. Nothing to do.")
        return 0

    print("\nBaselining {} branch(es): {}".format(len(todo), ", ".join(todo)))
    original = current_branch()

    # Results are buffered and written only after the original branch is restored. tools/ is tracked
    # on master alone, so checking out a feature branch deletes it from the working tree; a baseline
    # written during that window would be an untracked file that blocks the checkout back to master
    # ("untracked working tree files would be overwritten"). Buffering sidesteps the whole problem,
    # and also means an aborted run leaves no half-written baseline behind.
    pending = []
    try:
        for branch in todo:
            print("\n=== {} ===".format(branch))
            git("checkout", branch)
            present = classes_present_on(branch, wanted)
            accumulated = set()
            total = 0
            # Repeats accumulate flaky failures into the baseline so they do not later masquerade
            # as interaction bugs. One pass is enough for a first cut; use --repeat 2+ to harden.
            for pass_number in range(args.repeat):
                if args.repeat > 1:
                    print("  pass {}/{}".format(pass_number + 1, args.repeat))
                failures, total = run_tests(present, branch)
                accumulated |= failures
            pending.append((branch, tip_sha(branch), present, accumulated, total))
            print("  {} known failures (write deferred until the branch is restored)"
                  .format(len(accumulated)))
    finally:
        if current_branch() != original:
            print("\nrestoring branch {}".format(original))
            git("checkout", original)
        for branch, sha, present, accumulated, total in pending:
            save_baseline(branch, sha, present, accumulated, total, args.repeat)
            print("wrote {}".format(baseline_path(branch).relative_to(REPO)))
    return 0


def cmd_check(config, args):
    wanted = test_set(config, args)
    combined = config["combinedBranch"]

    if not args.no_run:
        if current_branch() != combined:
            print("ERROR: check runs on {}, but you are on {}.".format(combined, current_branch()),
                  file=sys.stderr)
            return 2
        present = classes_present_on(combined, wanted)
        print("=== {} ===".format(combined))
        run_tests(present, combined)

    details, total = parse_results()
    observed = set(details)
    if total == 0:
        print("No test results found. Run without --no-run first.", file=sys.stderr)
        return 2

    known = set()
    contributors = {}
    missing_baselines = []
    baselined_classes = set()
    for branch in config["branches"]:
        data = load_baseline(branch)
        if data is None:
            missing_baselines.append(branch)
            continue
        stale, why = baseline_is_stale(branch)
        if stale:
            print("WARNING: baseline for {} is stale -- {}".format(branch, why))
        baselined_classes.update(data.get("testClasses", []))
        for failure in data["knownFailures"]:
            known.add(failure)
            contributors.setdefault(failure, []).append(branch)

    if missing_baselines:
        print("WARNING: no baseline for {} -- failures owned by those branches will look new."
              .format(", ".join(missing_baselines)))

    # A baseline recorded under --tests covers only the classes it ran. Any class checked here but
    # never baselined anywhere reports 100% of its failures as new, which reads as a merge disaster
    # rather than as missing data -- so say so explicitly.
    unbaselined = sorted(set(classes_present_on(combined, wanted)) - baselined_classes)
    if unbaselined:
        print("WARNING: no branch baseline covers {} -- every failure in {} will be reported as new."
              .format(", ".join(unbaselined), "them" if len(unbaselined) > 1 else "it"))

    new_failures = sorted(observed - known)
    healed = sorted(known - observed)
    suspects = config.get("suspects", {})

    print("\n" + "=" * 78)
    print("{} failing / {} test cases on {}".format(len(observed), total, combined))
    print("{} explained by branch baselines, {} NEW".format(len(observed) - len(new_failures), len(new_failures)))
    print("=" * 78)

    if not new_failures:
        print("\nNo unexplained failures. Every failure on the combined build already fails on a")
        print("branch that feeds it, so the merge introduced no new breakage.")
    else:
        print("\nNEW FAILURES -- fail on the combined build but on no contributing branch.")
        print("These are interaction bugs. Fix them on the suspect branch, never here.\n")
        for failure in new_failures:
            cls = failure.split("::")[0].split(".")[0]
            summary, site = details.get(failure, ("", ""))
            print("  {}".format(failure))
            if summary:
                print("      {}".format(summary))
            if site:
                print("      {}".format(site))
            candidates = suspects.get(cls)
            if candidates:
                print("      suspect branches: {}".format(", ".join(candidates)))
            elif candidates == []:
                print("      suspect branches: none mapped -- likely upstream/master")
            else:
                print("      suspect branches: {} not in the suspects map (add it)".format(cls))
            print("")

    if healed and args.verbose:
        print("\nKnown branch failures that did NOT occur here ({}):".format(len(healed)))
        for failure in healed:
            print("  {}  [baselined on: {}]".format(failure, ", ".join(contributors.get(failure, []))))
        print("\nThese are usually flaky randomised assertions rather than real fixes.")

    return 1 if new_failures else 0


def test_set(config, args):
    wanted = list(config["testSet"])
    if args.include_report_only:
        wanted += config.get("reportOnly", [])
    if args.tests:
        wanted = [name for name in wanted if any(part in name for part in args.tests)]
    return wanted


# ---------------------------------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)

    # Shared options live on a parent parser rather than the top-level one: as top-level options their
    # nargs="+" list would swallow the subcommand name that follows them.
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--tests", nargs="+", metavar="SUBSTR",
                        help="only run test classes whose name contains one of these substrings")
    common.add_argument("--include-report-only", action="store_true",
                        help="also run the print-only profile/extraction harnesses")

    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("plan", parents=[common],
                   help="show the test set and which baselines are stale; runs nothing")

    baseline_parser = sub.add_parser("baseline", parents=[common],
                                     help="record the known-failure set for each branch")
    baseline_parser.add_argument("--branch", nargs="+", help="only baseline these branches")
    baseline_parser.add_argument("--force", action="store_true", help="re-record even if current")
    baseline_parser.add_argument("--repeat", type=int, default=1,
                                 help="passes per branch; >1 accumulates flaky failures (default 1)")

    check_parser = sub.add_parser("check", parents=[common],
                                  help="run the combined build and report only new failures")
    check_parser.add_argument("--no-run", action="store_true",
                              help="re-read the existing XML instead of running Gradle")
    check_parser.add_argument("--verbose", action="store_true",
                              help="also list baselined failures that did not recur")

    args = parser.parse_args()

    with open(CONFIG_PATH, encoding="utf-8") as handle:
        config = json.load(handle)

    needs_checkout = args.command == "baseline"
    if needs_checkout and not working_tree_is_clean():
        print("ERROR: tracked files are modified. This command checks out other branches, which would",
              file=sys.stderr)
        print("       either fail or carry your changes across. Commit or stash first.", file=sys.stderr)
        return 2

    if args.command == "plan":
        return cmd_plan(config, args) or 0
    if args.command == "baseline":
        return cmd_baseline(config, args)
    return cmd_check(config, args)


if __name__ == "__main__":
    sys.exit(main())

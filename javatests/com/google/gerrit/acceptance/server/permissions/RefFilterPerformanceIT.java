// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.server.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;

import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.ChangesByProjectCache;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.TagSetHolder;
import com.google.gerrit.server.permissions.DefaultRefFilter;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.ProjectControl;
import com.google.gerrit.server.permissions.ReadAccessClassifier;
import com.google.gerrit.server.permissions.ReadAccessClassifier.Decision;
import com.google.gerrit.server.permissions.RefVisibilityControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

/**
 * Performance tests for {@link DefaultRefFilter} comparing the optimised path (with {@link
 * ReadAccessClassifier}) against the legacy path (with {@link NoOpReadAccessClassifier}).
 *
 * <p>Both paths call the exact same {@link DefaultRefFilter#filter} code. The only controlled
 * variable is which {@link ReadAccessClassifier} is used: the real one that short-circuits most
 * refs, or a no-op that falls through to the full per-ref ACL evaluation for every ref.
 *
 * <p>Scenarios:
 *
 * <ul>
 *   <li>Most refs visible, one blocked — the typical case; classifier short-circuits the majority.
 *   <li>Many refs blocked — classifier advantage shrinks as more refs need a full check.
 *   <li>All refs require full check (per-user pattern) — worst case; classifier adds no speedup.
 *   <li>All refs visible (fast path) — no full loop runs; classifier adds no overhead.
 * </ul>
 */
public class RefFilterPerformanceIT extends AbstractDaemonTest {

  /**
   * Always returns {@link Decision#NEEDS_FULL_CHECK}, simulating the pre-classifier code path where
   * every ref triggered a full {@code controlForRef()} evaluation.
   */
  static class NoOpReadAccessClassifier extends ReadAccessClassifier {
    @Inject
    NoOpReadAccessClassifier(@Assisted ProjectControl projectControl) {
      super(projectControl);
    }

    @Override
    public Decision classify(String refName) {
      return Decision.NEEDS_FULL_CHECK;
    }
  }

  private static final int NUM_BRANCHES = 1_000;
  private static final int NUM_TAGS = 200;
  private static final int WARMUP_ITERATIONS = 5;
  private static final int MEASURE_ITERATIONS = 50;

  /**
   * Number of independent outer repetitions used by {@link #repeatBenchmark} and {@link
   * #repeatBenchmarkWithTagCacheInvalidation} to compute mean ± standard deviation.
   */
  private static final int REPEAT_RUNS = 10;

  @Inject private ProjectOperations projectOperations;
  @Inject private ProjectControl.Factory projectControlFactory;
  @Inject private ReadAccessClassifier.Factory classifierFactory;
  @Inject private TagCache tagCache;

  @Inject
  @Named("git_tags")
  private Cache<String, TagSetHolder> gitTagsCache;

  @Inject private PermissionBackend permissionBackend;
  @Inject private RefVisibilityControl refVisibilityControl;
  @Inject @GerritServerConfig private Config gerritConfig;
  @Inject private DefaultRefFilter.Metrics refFilterMetrics;
  @Inject private ChangesByProjectCache changesByProjectCache;
  @Inject private ChangeData.Factory changeDataFactoryForFilter;

  private final TestMetricMaker testMetricMaker = TestMetricMaker.getInstance();

  // ---------------------------------------------------------------------------
  // Scenarios
  // ---------------------------------------------------------------------------

  /**
   * Most refs visible, one blocked. The full filter loop runs because neither existing fast path
   * fires. The classifier short-circuits almost all refs (VISIBLE), calling the full ACL check only
   * for the blocked branch.
   */
  @Test
  public void mostRefsVisible_oneBlocked() throws Exception {
    Project.NameKey project = createProjectWithBranches(NUM_BRANCHES);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/secret").group(REGISTERED_USERS))
        .update();
    gApi.projects().name(project.get()).branch("secret").create(new BranchInput());
    ImmutableList<Ref> refs = getAllRefs(project);

    RunStats stats = repeatBenchmark("mostRefsVisible_oneBlocked", project, refs);
    BenchmarkResult result = stats.last;

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    assertWithMessage("classifier_shortcut_count should increase with each filter call")
        .that(result.shortcutCount)
        .isGreaterThan(0);
    assertWithMessage("expected optimised path to be at least 2x faster than legacy")
        .that(stats.meanSpeedup)
        .isAtLeast(2.0);
  }

  /**
   * Many refs blocked (10% of branches from the same set). The classifier returns VISIBLE for the
   * unblocked 90% and NEEDS_FULL_CHECK for the blocked 10%, so the shortcut count is lower than the
   * one-blocked scenario and the speedup is smaller but still significant.
   */
  @Test
  public void manyRefsBlocked() throws Exception {
    int numBlocked = NUM_BRANCHES / 10;
    Project.NameKey project = createProjectWithBranches(NUM_BRANCHES);
    var update =
        projectOperations
            .project(project)
            .forUpdate()
            .add(allow(READ).ref("refs/*").group(REGISTERED_USERS));
    // Block every 10th branch from the existing set, not extra branches.
    for (int i = 0; i < numBlocked; i++) {
      update.add(block(READ).ref("refs/heads/branch-" + (i * 10)).group(REGISTERED_USERS));
    }
    update.update();
    ImmutableList<Ref> refs = getAllRefs(project);

    RunStats stats =
        repeatBenchmark("manyRefsBlocked (" + numBlocked + " blocked)", project, refs);
    BenchmarkResult result = stats.last;

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    assertWithMessage("classifier_shortcut_count should increase with each filter call")
        .that(result.shortcutCount)
        .isGreaterThan(0);
    // Fewer shortcuts than mostRefsVisible_oneBlocked because 10% of refs
    // require a full ACL check instead of being short-circuited.
    long expectedMaxShortcuts = ((long) (NUM_BRANCHES * 0.95)) * MEASURE_ITERATIONS;
    assertWithMessage("shortcut count should be less than in the one-blocked scenario")
        .that(result.shortcutCount)
        .isLessThan(expectedMaxShortcuts);
    assertWithMessage("expected optimised path to be at least 2x faster than legacy")
        .that(stats.meanSpeedup)
        .isAtLeast(2.0);
  }

  /**
   * Per-user {@code ${username}} pattern with branches that actually match. Half the branches are
   * named {@code refs/heads/<username>/feature-*} and match the per-user pattern; the other half
   * are plain {@code branch-*} and are invisible (no broad allow, pattern is the only grant). The
   * classifier returns NEEDS_FULL_CHECK for the per-user branches (correct: they are visible to
   * this user) and INVISIBLE for the others.
   */
  @Test
  public void perUserPatternWithMatchingBranches() throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    // Remove inherited All-Projects READ so only the per-user pattern grants access.
    projectOperations
        .project(allProjects)
        .forUpdate()
        .remove(permissionKey(READ).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .remove(permissionKey(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    // Plain branches not covered by the per-user pattern.
    for (int i = 0; i < NUM_BRANCHES / 2; i++) {
      gApi.projects().name(project.get()).branch("branch-" + i).create(new BranchInput());
    }
    // Branches that match refs/heads/<username>/*.
    for (int i = 0; i < NUM_BRANCHES / 2; i++) {
      gApi.projects()
          .name(project.get())
          .branch(user.username() + "/feature-" + i)
          .create(new BranchInput());
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/${username}/*").group(REGISTERED_USERS))
        .update();
    ImmutableList<Ref> refs = getAllRefs(project);

    RunStats stats = repeatBenchmark("perUserPatternWithMatchingBranches", project, refs);
    BenchmarkResult result = stats.last;

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    // The per-user branches are routed to NEEDS_FULL_CHECK; the classifier
    // cannot short-circuit them, so shortcutCount should be 0.
    assertWithMessage("no shortcuts expected: all refs need full check or are INVISIBLE")
        .that(result.shortcutCount)
        .isEqualTo(0);
  }

  /**
   * Many branches plus reachable tags at branch tips. The classifier speeds up the
   * branch-visibility pass that determines which branches are used as starting points for the
   * tag-reachability walk. Tags themselves are always deferred to the rev-walk in {@link
   * DefaultRefFilter} and are never short-circuited by the classifier directly; the speedup comes
   * entirely from the faster branch pass that produces the visible-branch set fed into {@link
   * com.google.gerrit.server.git.TagMatcher}.
   *
   * <p>Setup: {@value #NUM_BRANCHES} branches (most visible, one blocked) + {@value #NUM_TAGS}
   * lightweight tags each pointing at the tip of one of those branches.
   */
  @Test
  public void withTagsReachableFromVisibleBranches() throws Exception {
    Project.NameKey project = createProjectWithBranchesAndTags(NUM_BRANCHES, NUM_TAGS);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/secret").group(REGISTERED_USERS))
        .update();
    gApi.projects().name(project.get()).branch("secret").create(new BranchInput());
    ImmutableList<Ref> refs = getAllRefs(project);

    RunStats stats = repeatBenchmark("withTagsReachableFromVisibleBranches", project, refs);
    BenchmarkResult result = stats.last;

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    assertWithMessage("classifier_shortcut_count should increase with each filter call")
        .that(result.shortcutCount)
        .isGreaterThan(0);
    assertWithMessage("expected optimised path to be at least 2x faster than legacy")
        .that(stats.meanSpeedup)
        .isAtLeast(2.0);
  }

  /**
   * Tags pointing at non-tip commits with a broad {@code ALLOW refs/*} ACL. Each branch is advanced
   * by one commit after tagging its original tip, so every tag now points to a parent commit rather
   * than the current branch tip. The tag cache is invalidated before each measurement call to force
   * {@link com.google.gerrit.server.git.TagSet#build} to run a full commit-graph walk on every
   * invocation, simulating a cold-cache server or post-GC repository access.
   *
   * <p>The classifier still speeds up the branch-visibility pass (most branches are VISIBLE via the
   * broad allow), but the dominant cost shifts to the tag-reachability rev-walk. The speedup ratio
   * is therefore smaller than in {@link #withTagsReachableFromVisibleBranches}, where the TagSet
   * cache is warm and the reachability check is O(1) bitmap lookup.
   *
   * <p>Setup: {@value #NUM_BRANCHES} branches (one blocked) + {@value #NUM_TAGS} tags, each behind
   * its branch tip by one commit.
   */
  @Test
  public void tagsNotAtBranchTips_broadAllow() throws Exception {
    Project.NameKey project = createProjectWithBranchesAndNonTipTags(NUM_BRANCHES, NUM_TAGS);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/secret").group(REGISTERED_USERS))
        .update();
    gApi.projects().name(project.get()).branch("secret").create(new BranchInput());
    ImmutableList<Ref> refs = getAllRefs(project);

    RunStats stats =
        repeatBenchmarkWithTagCacheInvalidation(
            "tagsNotAtBranchTips_broadAllow", project, refs);
    BenchmarkResult result = stats.last;

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    assertWithMessage("classifier_shortcut_count should increase with each filter call")
        .that(result.shortcutCount)
        .isGreaterThan(0);
    // The tag rev-walk dominates; a 1.5x speedup is still meaningful.
    assertWithMessage("expected optimised path to be at least 1.5x faster than legacy")
        .that(stats.meanSpeedup)
        .isAtLeast(1.5);
  }

  /**
   * Tags at non-tip commits with per-branch {@code DENY} rules suppressing an inherited allow.
   *
   * <p>The own project adds {@code DENY READ refs/heads/branch-0} … {@code branch-<NUM_TAGS-1>} for
   * {@code REGISTERED_USERS}. Access to those branches relies on the All-Projects {@code ALLOW READ
   * refs/heads/*} for {@code Anonymous Users} (which covers all users including identified ones).
   * Because {@code DENY} uses the {@code SeenRule} mechanism in {@link
   * com.google.gerrit.server.permissions.PermissionCollection#calculateAllowRules}, a child DENY on
   * a specific ref suppresses the parent ALLOW for the same {@code (section, group)} tuple — so the
   * denied branches are genuinely inaccessible.
   *
   * <p>The DENY sections go into {@code blockDenyMatchers} in the classifier, so every denied
   * branch gets {@code NEEDS_FULL_CHECK} instead of {@code VISIBLE}. For the 800 non-denied
   * branches the classifier still returns {@code VISIBLE} (own-project {@code ALLOW refs/*} and
   * All-Projects {@code ALLOW refs/heads/*} both match). However, because {@code
   * canPerformReadOnAllRefs} iterates every READ pattern and the DENY branches fail the check,
   * {@code hasReadOnRefsStar} stays {@code false} — so the fast-path does not fire and the per-ref
   * loop runs.
   *
   * <p>Compared to {@link #tagsNotAtBranchTips_broadAllow}:
   *
   * <ul>
   *   <li>The DENY-rule scenario produces fewer classifier shortcuts (only the non-denied branches
   *       are shortcut), and those shortcuts cost the classifier more time per ref (it must first
   *       check all 200 DENY matchers before reaching the allow matcher).
   *   <li>The tag-reachability rev-walk cost is the same (same repo, same commit graph).
   *   <li>Net speedup is lower than with a one-block broad allow.
   * </ul>
   */
  @Test
  public void tagsNotAtBranchTips_denyRules() throws Exception {
    Project.NameKey project = createProjectWithBranchesAndNonTipTags(NUM_BRANCHES, NUM_TAGS);
    // Own project has DENY on the tagged branches only — no own-project ALLOW.
    // Access to non-denied branches comes from the All-Projects inherited READ.
    // The DENY suppresses the parent allow for matching refs via the SeenRule
    // mechanism; the fast-path does not fire because canPerformReadOnAllRefs
    // finds the denied branches and returns false.
    var update = projectOperations.project(project).forUpdate();
    for (int i = 0; i < NUM_TAGS; i++) {
      update.add(deny(READ).ref("refs/heads/branch-" + i).group(REGISTERED_USERS));
    }
    update.update();
    ImmutableList<Ref> refs = getAllRefs(project);

    RunStats statsDeny =
        repeatBenchmarkWithTagCacheInvalidation(
            "tagsNotAtBranchTips_denyRules", project, refs);

    // Reconfigure: remove DENY rules and add an explicit own-project ALLOW
    // refs/* + one BLOCK, so the comparison has the same broad-allow shape
    // as tagsNotAtBranchTips_broadAllow.
    var reconfigure =
        projectOperations
            .project(project)
            .forUpdate()
            .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
            .add(block(READ).ref("refs/heads/secret").group(REGISTERED_USERS));
    for (int i = 0; i < NUM_TAGS; i++) {
      reconfigure.remove(permissionKey(READ).ref("refs/heads/branch-" + i).group(REGISTERED_USERS));
    }
    reconfigure.update();
    RunStats statsBroadAllow =
        repeatBenchmarkWithTagCacheInvalidation(
            "tagsNotAtBranchTips_denyRules (baseline: broad allow)", project, refs);

    BenchmarkResult resultDeny = statsDeny.last;
    BenchmarkResult resultBroadAllow = statsBroadAllow.last;
    assertThat(resultDeny.optimisedResult).containsExactlyElementsIn(resultDeny.legacyResult);
    assertThat(resultBroadAllow.optimisedResult)
        .containsExactlyElementsIn(resultBroadAllow.legacyResult);

    // DENY rules add 200 extra matchers that must be checked before the allow
    // matcher, so shortcuts are fewer (only the 800 non-denied branches) and
    // the speedup is lower than the broad-allow baseline.
    assertWithMessage("deny-rule scenario should have fewer shortcuts than broad-allow baseline")
        .that(resultDeny.shortcutCount)
        .isLessThan(resultBroadAllow.shortcutCount);
    System.err.printf(
        "%nCOMPARISON: deny-rule speedup=%.2fx±%.2f  shortcuts=%d%n"
            + "            broad-allow speedup=%.2fx±%.2f  shortcuts=%d%n",
        statsDeny.meanSpeedup,
        statsDeny.stddevSpeedup,
        resultDeny.shortcutCount,
        statsBroadAllow.meanSpeedup,
        statsBroadAllow.stddevSpeedup,
        resultBroadAllow.shortcutCount);
  }

  /**
   * Runs all six ACL × tag-structure combinations and prints the results as a formatted matrix.
   *
   * <p>The two ACL configurations are:
   *
   * <ol>
   *   <li><b>Broad allow</b>: own-project {@code ALLOW READ refs/*} for {@code REGISTERED_USERS} +
   *       one {@code BLOCK} on {@code refs/heads/secret}. The classifier short-circuits almost all
   *       branches as {@code VISIBLE}.
   *   <li><b>200 DENY rules</b>: per-branch {@code DENY READ refs/heads/branch-0…199} in the own
   *       project. Access to non-denied branches comes from the inherited All-Projects {@code ALLOW
   *       READ refs/heads/*}. The DENY rules suppress that parent allow via the {@code SeenRule}
   *       mechanism, so branches 0-199 are genuinely inaccessible. The classifier routes those 200
   *       branches to {@code NEEDS_FULL_CHECK} and short-circuits the remaining 800 as {@code
   *       VISIBLE}.
   * </ol>
   *
   * <p>The three tag-structure columns are:
   *
   * <ol>
   *   <li><b>No tags</b> — branch-only repo; tag-reachability walk never runs. Tag cache is warm
   *       (no invalidation).
   *   <li><b>Tags at branch tips</b> — {@value #NUM_TAGS} lightweight tags each pointing at the
   *       current tip of a branch. Tag cache is warm; {@link
   *       com.google.gerrit.server.git.TagSet#build} is not re-run each call.
   *   <li><b>Tags not at tips (cold cache)</b> — same branch/tag count, but each branch has been
   *       advanced by one commit past its tag target. The tag cache is invalidated before every
   *       filter call, forcing a full {@link com.google.gerrit.server.git.TagSet#build}
   *       commit-graph walk on every invocation.
   * </ol>
   *
   * <p>Expected output format (values are illustrative):
   *
   * <pre>
   * ┌─────────────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┐
   * │ ACL                         │ no tags              │ tips (warm cache)    │ non-tips (cold cache)│
   * ├─────────────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
   * │ broad-allow + 1 block       │  1.2ms  5.4ms  4.5x  │  1.4ms  6.3ms  4.5x │  9.0ms 22.5ms  2.5x  │
   * │ 200 DENY rules              │  2.1ms  7.3ms  3.5x  │  2.3ms  8.1ms  3.5x │ 10.3ms 19.6ms  1.9x  │
   * └─────────────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┘
   *                                 opt / legacy / speedup
   * </pre>
   */
  @Test
  public void tagFilterMatrix() throws Exception {
    // -----------------------------------------------------------------------
    // Build projects for each of the 6 cells.
    // -----------------------------------------------------------------------

    // Row 1: broad-allow ACL
    Project.NameKey baNoTags = createProjectWithBranches(NUM_BRANCHES);
    applyBroadAllowAcl(baNoTags);

    Project.NameKey baTipTags = createProjectWithBranchesAndTags(NUM_BRANCHES, NUM_TAGS);
    applyBroadAllowAcl(baTipTags);

    Project.NameKey baNonTipTags = createProjectWithBranchesAndNonTipTags(NUM_BRANCHES, NUM_TAGS);
    applyBroadAllowAcl(baNonTipTags);

    // Row 2: 200 DENY rules
    Project.NameKey drNoTags = createProjectWithBranches(NUM_BRANCHES);
    applyDenyRulesAcl(drNoTags);

    Project.NameKey drTipTags = createProjectWithBranchesAndTags(NUM_BRANCHES, NUM_TAGS);
    applyDenyRulesAcl(drTipTags);

    Project.NameKey drNonTipTags = createProjectWithBranchesAndNonTipTags(NUM_BRANCHES, NUM_TAGS);
    applyDenyRulesAcl(drNonTipTags);

    // -----------------------------------------------------------------------
    // Run benchmarks (warm cache for no-tags and tip-tags; cold for non-tips).
    // -----------------------------------------------------------------------
    RunStats ba_noTags =
        repeatBenchmark("broad-allow / no tags", baNoTags, getAllRefs(baNoTags));
    RunStats ba_tipTags =
        repeatBenchmark("broad-allow / tip tags", baTipTags, getAllRefs(baTipTags));
    RunStats ba_nonTipTags =
        repeatBenchmarkWithTagCacheInvalidation(
            "broad-allow / non-tip tags (cold cache)", baNonTipTags, getAllRefs(baNonTipTags));

    RunStats dr_noTags =
        repeatBenchmark("200-DENY / no tags", drNoTags, getAllRefs(drNoTags));
    RunStats dr_tipTags =
        repeatBenchmark("200-DENY / tip tags", drTipTags, getAllRefs(drTipTags));
    RunStats dr_nonTipTags =
        repeatBenchmarkWithTagCacheInvalidation(
            "200-DENY / non-tip tags (cold cache)", drNonTipTags, getAllRefs(drNonTipTags));

    // -----------------------------------------------------------------------
    // Correctness checks.
    // -----------------------------------------------------------------------
    assertThat(ba_noTags.last.optimisedResult)
        .containsExactlyElementsIn(ba_noTags.last.legacyResult);
    assertThat(ba_tipTags.last.optimisedResult)
        .containsExactlyElementsIn(ba_tipTags.last.legacyResult);
    assertThat(ba_nonTipTags.last.optimisedResult)
        .containsExactlyElementsIn(ba_nonTipTags.last.legacyResult);
    assertThat(dr_noTags.last.optimisedResult)
        .containsExactlyElementsIn(dr_noTags.last.legacyResult);
    assertThat(dr_tipTags.last.optimisedResult)
        .containsExactlyElementsIn(dr_tipTags.last.legacyResult);
    assertThat(dr_nonTipTags.last.optimisedResult)
        .containsExactlyElementsIn(dr_nonTipTags.last.legacyResult);

    // Classifier shortcuts fire for both ACL configs when tags are not dominating.
    assertWithMessage("broad-allow / no tags: expected shortcuts")
        .that(ba_noTags.last.shortcutCount)
        .isGreaterThan(0);
    assertWithMessage("200-DENY / no tags: expected shortcuts for the 800 non-denied branches")
        .that(dr_noTags.last.shortcutCount)
        .isGreaterThan(0);
    // Fewer shortcuts in the deny-rules row (only 800 of 1000 branches shortcut).
    assertWithMessage("DENY-rules row should have fewer shortcuts than broad-allow row")
        .that(dr_noTags.last.shortcutCount)
        .isLessThan(ba_noTags.last.shortcutCount);

    // -----------------------------------------------------------------------
    // Print formatted matrix (avg ± σ across REPEAT_RUNS repetitions).
    // -----------------------------------------------------------------------
    String sep = "─".repeat(32) + "┼" + ("─".repeat(28) + "┼").repeat(2) + "─".repeat(28);
    System.err.printf("%n");
    System.err.printf(
        "%-32s│%-28s│%-28s│%-28s%n",
        " ACL",
        "  no tags",
        "  tips (warm cache)",
        "  non-tips (cold cache)");
    System.err.printf("%s%n", sep);
    printStatsMatrixRow(" broad-allow + 1 block", ba_noTags, ba_tipTags, ba_nonTipTags);
    printStatsMatrixRow(" 200 DENY rules", dr_noTags, dr_tipTags, dr_nonTipTags);
    System.err.printf(
        "%-32s│%-28s│%-28s│%-28s%n",
        "",
        "  opt±σ / legacy±σ / speedup±σ",
        "",
        "");
  }

  /** Formats one matrix row as three {@link RunStats} cells (avg ± σ). */
  private static void printStatsMatrixRow(
      String label, RunStats noTags, RunStats tipTags, RunStats nonTipTags) {
    System.err.printf(
        "%-32s│%s│%s│%s%n",
        label,
        statsMatrixCell(noTags),
        statsMatrixCell(tipTags),
        statsMatrixCell(nonTipTags));
  }

  private static String statsMatrixCell(RunStats s) {
    return String.format(
        " %4.1f±%3.1f→%4.1f±%3.1f (%3.1f±%3.1fx) ",
        s.meanOptimisedMs,
        s.stddevOptimisedMs,
        s.meanLegacyMs,
        s.stddevLegacyMs,
        s.meanSpeedup,
        s.stddevSpeedup);
  }

  /** Applies {@code ALLOW READ refs/*} + one {@code BLOCK} on {@code refs/heads/secret}. */
  private void applyBroadAllowAcl(Project.NameKey project) throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/secret").group(REGISTERED_USERS))
        .update();
  }

  /**
   * Applies {@code DENY READ refs/heads/branch-0..199} in the own project. Access to non-denied
   * branches comes from the inherited All-Projects {@code ALLOW READ refs/heads/*}. The DENY rules
   * suppress the parent allow via the {@code SeenRule} mechanism, so branches 0-199 are genuinely
   * inaccessible.
   */
  private void applyDenyRulesAcl(Project.NameKey project) throws Exception {
    var update = projectOperations.project(project).forUpdate();
    for (int i = 0; i < NUM_TAGS; i++) {
      update.add(deny(READ).ref("refs/heads/branch-" + i).group(REGISTERED_USERS));
    }
    update.update();
  }

  /**
   * All refs visible — the existing {@code allRefsAreVisible} fast path fires before the per-ref
   * loop, so the classifier's per-ref work is never reached. Both paths should be equally fast (no
   * loop), and the classifier adds no overhead.
   */
  @Test
  public void allRefsVisible_fastPathFires() throws Exception {
    Project.NameKey project = createProjectWithBranches(NUM_BRANCHES);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    ImmutableList<Ref> refs = getAllRefs(project);

    RunStats stats = repeatBenchmark("allRefsVisible_fastPathFires", project, refs);
    BenchmarkResult result = stats.last;

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    // No shortcuts from the classifier — the fast path short-circuits the loop
    // entirely before the classifier is ever invoked.
    assertWithMessage("no classifier shortcuts expected when fast path fires")
        .that(result.shortcutCount)
        .isEqualTo(0);
  }

  // ---------------------------------------------------------------------------
  // Report accumulation
  // ---------------------------------------------------------------------------

  /**
   * Thread-safe list of benchmark results accumulated across all test methods. Populated by
   * {@link #repeatBenchmark} and {@link #repeatBenchmarkWithTagCacheInvalidation}; consumed by
   * {@link #flushMarkdownReport()}.
   */
  private static final List<ReportEntry> REPORT_ENTRIES = new CopyOnWriteArrayList<>();

  /**
   * One row in the markdown report, carrying the scenario label, raw per-run samples, and
   * aggregated {@link RunStats}.
   */
  private static final class ReportEntry {
    /** Human-readable scenario name (used as the table row label). */
    final String label;

    /** {@code true} when the tag cache was invalidated before every filter call. */
    final boolean coldTagCache;

    /** Raw per-run optimised latency samples (ms), length == {@link #REPEAT_RUNS}. */
    final List<Double> optimisedSamples;

    /** Raw per-run legacy latency samples (ms), length == {@link #REPEAT_RUNS}. */
    final List<Double> legacySamples;

    /** Raw per-run speedup samples, length == {@link #REPEAT_RUNS}. */
    final List<Double> speedupSamples;

    /** Aggregated statistics computed from the samples above. */
    final RunStats stats;

    ReportEntry(
        String label,
        boolean coldTagCache,
        List<Double> optimisedSamples,
        List<Double> legacySamples,
        List<Double> speedupSamples,
        RunStats stats) {
      this.label = label;
      this.coldTagCache = coldTagCache;
      this.optimisedSamples = Collections.unmodifiableList(new ArrayList<>(optimisedSamples));
      this.legacySamples = Collections.unmodifiableList(new ArrayList<>(legacySamples));
      this.speedupSamples = Collections.unmodifiableList(new ArrayList<>(speedupSamples));
      this.stats = stats;
    }
  }

  /**
   * Returns the path where the report is written.
   *
   * <p>Output location (first match wins):
   * <ol>
   *   <li>{@code $TEST_UNDECLARED_OUTPUTS_DIR/RefFilterPerformanceIT.md} — Bazel's dedicated
   *       directory for test artifacts, automatically surfaced in {@code bazel-testlogs}.
   *   <li>{@code <workspace-root>/RefFilterPerformanceIT.md} — fallback for local IDE runs.
   * </ol>
   */
  private static Path resolveReportPath() {
    String outputDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (outputDir != null && !outputDir.isEmpty()) {
      return Paths.get(outputDir, "RefFilterPerformanceIT.md");
    }
    String workspaceDir = System.getenv("BUILD_WORKSPACE_DIRECTORY");
    if (workspaceDir == null || workspaceDir.isEmpty()) {
      workspaceDir = System.getProperty("user.dir");
    }
    return Paths.get(workspaceDir, "RefFilterPerformanceIT.md");
  }

  /**
   * Rewrites the full markdown report to disk with the current contents of {@link
   * #REPORT_ENTRIES}. Called after every {@link #repeatBenchmark} /
   * {@link #repeatBenchmarkWithTagCacheInvalidation} invocation so the file is always up-to-date
   * regardless of test lifecycle hooks.
   */
  private static void flushMarkdownReport() {
    if (REPORT_ENTRIES.isEmpty()) {
      return;
    }
    try {
      Path reportPath = resolveReportPath();
      Files.createDirectories(reportPath.getParent());
      try (PrintWriter w =
          new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
        writeMarkdown(w);
      }
      System.err.printf("%n[RefFilterPerformanceIT] Report updated: %s%n", reportPath);
    } catch (IOException e) {
      System.err.printf("%n[RefFilterPerformanceIT] WARNING: could not write report: %s%n", e);
    }
  }

  /** Renders the full markdown report to {@code w}. */
  private static void writeMarkdown(PrintWriter w) {
    w.println("# RefFilter Performance Report");
    w.println();
    w.printf("Generated: %s  %n", Instant.now());
    w.printf(
        "Configuration: **%d scenarios**, **%d repetitions × %d iterations** per scenario  %n",
        REPORT_ENTRIES.size(), REPEAT_RUNS, MEASURE_ITERATIONS);
    w.println();
    w.println("## Summary");
    w.println();
    w.println(
        "Each row is one benchmark scenario. Latencies are averages over "
            + REPEAT_RUNS
            + " independent repetitions; ± values are the population standard deviation.  ");
    w.println("`cold` = tag cache invalidated before every filter call.");
    w.println();
    // Summary table header
    w.println(
        "| Scenario | Cache | Opt avg (ms) | Opt σ (ms) "
            + "| Legacy avg (ms) | Legacy σ (ms) | Speedup avg | Speedup σ |");
    w.println("|---|---|---:|---:|---:|---:|---:|---:|");
    for (ReportEntry e : REPORT_ENTRIES) {
      RunStats s = e.stats;
      w.printf(
          "| %s | %s | %.3f | %.3f | %.3f | %.3f | %.2fx | %.2fx |%n",
          mdEscape(e.label),
          e.coldTagCache ? "cold" : "warm",
          s.meanOptimisedMs,
          s.stddevOptimisedMs,
          s.meanLegacyMs,
          s.stddevLegacyMs,
          s.meanSpeedup,
          s.stddevSpeedup);
    }
    w.println();

    // Per-scenario detail sections
    w.println("## Per-Scenario Detail");
    w.println();
    for (ReportEntry e : REPORT_ENTRIES) {
      w.printf("### %s%n", mdEscape(e.label));
      w.println();
      w.printf(
          "- Tag cache: **%s**%n",
          e.coldTagCache ? "cold (invalidated per call)" : "warm");
      w.printf("- Repetitions: **%d** × %d timed iterations%n", REPEAT_RUNS, MEASURE_ITERATIONS);
      w.println();
      w.println(
          "| Run | Opt (ms) | Legacy (ms) | Speedup |");
      w.println("|---:|---:|---:|---:|");
      for (int i = 0; i < e.optimisedSamples.size(); i++) {
        w.printf(
            "| %d | %.3f | %.3f | %.2fx |%n",
            i + 1,
            e.optimisedSamples.get(i),
            e.legacySamples.get(i),
            e.speedupSamples.get(i));
      }
      // Summary row
      RunStats s = e.stats;
      w.printf(
          "| **avg±σ** | **%.3f±%.3f** | **%.3f±%.3f** | **%.2f±%.2fx** |%n",
          s.meanOptimisedMs,
          s.stddevOptimisedMs,
          s.meanLegacyMs,
          s.stddevLegacyMs,
          s.meanSpeedup,
          s.stddevSpeedup);
      w.println();
    }
  }

  /** Escapes pipe characters in markdown table cells. */
  private static String mdEscape(String s) {
    return s.replace("|", "\\|");
  }

  // ---------------------------------------------------------------------------
  // Benchmark infrastructure
  // ---------------------------------------------------------------------------

  /**
   * Aggregated statistics over {@link #REPEAT_RUNS} independent repetitions of a benchmark.
   *
   * <p>Each repetition calls the underlying {@link #benchmark} or {@link
   * #benchmarkWithTagCacheInvalidation} method once, which internally runs
   * {@link #MEASURE_ITERATIONS} timed filter calls. The per-repetition average latency is then
   * collected here to produce a mean ± standard deviation over repetitions.
   */
  private static final class RunStats {
    /** Result from the final (last) repetition; used for correctness assertions. */
    final BenchmarkResult last;

    final double meanOptimisedMs;
    final double stddevOptimisedMs;
    final double meanLegacyMs;
    final double stddevLegacyMs;
    final double meanSpeedup;
    final double stddevSpeedup;

    RunStats(
        BenchmarkResult last,
        List<Double> optimisedSamples,
        List<Double> legacySamples,
        List<Double> speedupSamples) {
      this.last = last;
      this.meanOptimisedMs = mean(optimisedSamples);
      this.stddevOptimisedMs = stddev(optimisedSamples, meanOptimisedMs);
      this.meanLegacyMs = mean(legacySamples);
      this.stddevLegacyMs = stddev(legacySamples, meanLegacyMs);
      this.meanSpeedup = mean(speedupSamples);
      this.stddevSpeedup = stddev(speedupSamples, meanSpeedup);
    }

    private static double mean(List<Double> vals) {
      double sum = 0;
      for (double v : vals) sum += v;
      return sum / vals.size();
    }

    private static double stddev(List<Double> vals, double mean) {
      double sumSq = 0;
      for (double v : vals) sumSq += (v - mean) * (v - mean);
      return Math.sqrt(sumSq / vals.size());
    }
  }

  private static final class BenchmarkResult {
    final ImmutableList<Ref> optimisedResult;
    final ImmutableList<Ref> legacyResult;
    final long shortcutCount;
    final double speedup;

    /** Average per-call latency for the legacy (no-classifier) path, in milliseconds. */
    final double legacyMs;

    /** Average per-call latency for the optimised (classifier) path, in milliseconds. */
    final double optimisedMs;

    BenchmarkResult(
        ImmutableList<Ref> optimisedResult,
        ImmutableList<Ref> legacyResult,
        long shortcutCount,
        double speedup,
        double legacyMs,
        double optimisedMs) {
      this.optimisedResult = optimisedResult;
      this.legacyResult = legacyResult;
      this.shortcutCount = shortcutCount;
      this.speedup = speedup;
      this.legacyMs = legacyMs;
      this.optimisedMs = optimisedMs;
    }
  }

  /**
   * Runs {@link #benchmark} {@link #REPEAT_RUNS} times and returns aggregated {@link RunStats}.
   *
   * <p>Prints a per-run detail table followed by a summary row (mean ± standard deviation).
   */
  private RunStats repeatBenchmark(
      String label, Project.NameKey project, ImmutableList<Ref> refs) throws Exception {
    List<Double> optSamples = new ArrayList<>();
    List<Double> legSamples = new ArrayList<>();
    List<Double> speedupSamples = new ArrayList<>();
    BenchmarkResult last = null;

    System.err.printf("%n┌── REPEAT benchmark: %s (%d runs × %d iterations) ──┐%n",
        label, REPEAT_RUNS, MEASURE_ITERATIONS);
    System.err.printf("  %-4s  %10s  %10s  %8s%n", "Run", "Optimised", "Legacy", "Speedup");
    System.err.printf("  %-4s  %10s  %10s  %8s%n", "───", "─────────", "──────", "───────");
    for (int run = 1; run <= REPEAT_RUNS; run++) {
      last = benchmark(label + " [run " + run + "]", project, refs);
      optSamples.add(last.optimisedMs);
      legSamples.add(last.legacyMs);
      speedupSamples.add(last.speedup);
      System.err.printf("  %-4d  %8.3f ms  %8.3f ms  %6.2fx%n",
          run, last.optimisedMs, last.legacyMs, last.speedup);
    }
    RunStats stats = new RunStats(last, optSamples, legSamples, speedupSamples);
    System.err.printf("  %-4s  %10s  %10s  %8s%n", "───", "─────────", "──────", "───────");
    System.err.printf("  %-4s  %7.3f±%4.3f  %7.3f±%4.3f  %5.2f±%4.2fx%n",
        "avg",
        stats.meanOptimisedMs, stats.stddevOptimisedMs,
        stats.meanLegacyMs, stats.stddevLegacyMs,
        stats.meanSpeedup, stats.stddevSpeedup);
    System.err.printf("└── END: %s ──┘%n", label);
    REPORT_ENTRIES.add(
        new ReportEntry(label, /* coldTagCache= */ false, optSamples, legSamples, speedupSamples, stats));
    flushMarkdownReport();
    return stats;
  }

  /**
   * Runs {@link #benchmarkWithTagCacheInvalidation} {@link #REPEAT_RUNS} times and returns
   * aggregated {@link RunStats}.
   *
   * <p>Prints a per-run detail table followed by a summary row (mean ± standard deviation).
   */
  private RunStats repeatBenchmarkWithTagCacheInvalidation(
      String label, Project.NameKey project, ImmutableList<Ref> refs) throws Exception {
    List<Double> optSamples = new ArrayList<>();
    List<Double> legSamples = new ArrayList<>();
    List<Double> speedupSamples = new ArrayList<>();
    BenchmarkResult last = null;

    System.err.printf("%n┌── REPEAT benchmark (cold tag cache): %s (%d runs × %d iterations) ──┐%n",
        label, REPEAT_RUNS, MEASURE_ITERATIONS);
    System.err.printf("  %-4s  %10s  %10s  %8s%n", "Run", "Optimised", "Legacy", "Speedup");
    System.err.printf("  %-4s  %10s  %10s  %8s%n", "───", "─────────", "──────", "───────");
    for (int run = 1; run <= REPEAT_RUNS; run++) {
      last = benchmarkWithTagCacheInvalidation(label + " [run " + run + "]", project, refs);
      optSamples.add(last.optimisedMs);
      legSamples.add(last.legacyMs);
      speedupSamples.add(last.speedup);
      System.err.printf("  %-4d  %8.3f ms  %8.3f ms  %6.2fx%n",
          run, last.optimisedMs, last.legacyMs, last.speedup);
    }
    RunStats stats = new RunStats(last, optSamples, legSamples, speedupSamples);
    System.err.printf("  %-4s  %10s  %10s  %8s%n", "───", "─────────", "──────", "───────");
    System.err.printf("  %-4s  %7.3f±%4.3f  %7.3f±%4.3f  %5.2f±%4.2fx%n",
        "avg",
        stats.meanOptimisedMs, stats.stddevOptimisedMs,
        stats.meanLegacyMs, stats.stddevLegacyMs,
        stats.meanSpeedup, stats.stddevSpeedup);
    System.err.printf("└── END: %s ──┘%n", label);
    REPORT_ENTRIES.add(
        new ReportEntry(label, /* coldTagCache= */ true, optSamples, legSamples, speedupSamples, stats));
    flushMarkdownReport();
    return stats;
  }

  /**
   * Like {@link #benchmark} but invalidates the tag cache for {@code project} before each filter
   * call. This forces {@link com.google.gerrit.server.git.TagSet#build} to do a full commit-graph
   * walk on every invocation, so the measurement captures tag-reachability rev-walk cost rather
   * than the cached bitmap lookup path.
   */
  private BenchmarkResult benchmarkWithTagCacheInvalidation(
      String label, Project.NameKey project, ImmutableList<Ref> refs) throws Exception {
    // Warm up: prime internal structures (JIT, project cache, ACL cache) without
    // measuring. Tag cache is intentionally invalidated each call even during
    // warm-up, so those calls exercise the same code path as the measured ones.
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      gitTagsCache.invalidate(project.get());
      @SuppressWarnings("unused")
      var unused1 = filterOptimised(project, refs);
      gitTagsCache.invalidate(project.get());
      @SuppressWarnings("unused")
      var unused2 = filterLegacy(project, refs);
    }

    testMetricMaker.reset();
    Stopwatch optimisedTimer = Stopwatch.createStarted();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      gitTagsCache.invalidate(project.get());
      @SuppressWarnings("unused")
      var unused = filterOptimised(project, refs);
    }
    long optimisedNs = optimisedTimer.elapsed(TimeUnit.NANOSECONDS);
    long shortcutCount =
        testMetricMaker.getCount("permissions/ref_filter/classifier_shortcut_count");

    Stopwatch legacyTimer = Stopwatch.createStarted();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      gitTagsCache.invalidate(project.get());
      @SuppressWarnings("unused")
      var unused = filterLegacy(project, refs);
    }
    long legacyNs = legacyTimer.elapsed(TimeUnit.NANOSECONDS);

    double speedup = (double) legacyNs / Math.max(optimisedNs, 1);

    System.err.printf(
        "%nSCENARIO: %s (%d refs, %d iterations, tag cache invalidated per call):%n"
            + "  Legacy    (per-ref ACL evaluation): %d ms  (avg %.3f ms/call)%n"
            + "  Optimised (ReadAccessClassifier):   %d ms  (avg %.3f ms/call)%n"
            + "  Speedup: %.2fx, classifier shortcuts: %d%n",
        label,
        refs.size(),
        MEASURE_ITERATIONS,
        TimeUnit.NANOSECONDS.toMillis(legacyNs),
        legacyNs / 1e6 / MEASURE_ITERATIONS,
        TimeUnit.NANOSECONDS.toMillis(optimisedNs),
        optimisedNs / 1e6 / MEASURE_ITERATIONS,
        speedup,
        shortcutCount);

    return new BenchmarkResult(
        filterOptimised(project, refs),
        filterLegacy(project, refs),
        shortcutCount,
        speedup,
        legacyNs / 1e6 / MEASURE_ITERATIONS,
        optimisedNs / 1e6 / MEASURE_ITERATIONS);
  }

  private BenchmarkResult benchmark(String label, Project.NameKey project, ImmutableList<Ref> refs)
      throws Exception {
    // Warm up both paths.
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      @SuppressWarnings("unused")
      var unused1 = filterOptimised(project, refs);
      @SuppressWarnings("unused")
      var unused2 = filterLegacy(project, refs);
    }

    // Measure optimised (real ReadAccessClassifier).
    testMetricMaker.reset();
    Stopwatch optimisedTimer = Stopwatch.createStarted();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      @SuppressWarnings("unused")
      var unused = filterOptimised(project, refs);
    }
    long optimisedNs = optimisedTimer.elapsed(TimeUnit.NANOSECONDS);
    long shortcutCount =
        testMetricMaker.getCount("permissions/ref_filter/classifier_shortcut_count");

    // Measure legacy (NoOpReadAccessClassifier: full ACL check per ref).
    Stopwatch legacyTimer = Stopwatch.createStarted();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      @SuppressWarnings("unused")
      var unused = filterLegacy(project, refs);
    }
    long legacyNs = legacyTimer.elapsed(TimeUnit.NANOSECONDS);

    double speedup = (double) legacyNs / Math.max(optimisedNs, 1);

    System.err.printf(
        "%nSCENARIO: %s (%d refs, %d iterations):%n"
            + "  Legacy    (per-ref ACL evaluation): %d ms  (avg %.3f ms/call)%n"
            + "  Optimised (ReadAccessClassifier):   %d ms  (avg %.3f ms/call)%n"
            + "  Speedup: %.2fx, classifier shortcuts: %d%n",
        label,
        refs.size(),
        MEASURE_ITERATIONS,
        TimeUnit.NANOSECONDS.toMillis(legacyNs),
        legacyNs / 1e6 / MEASURE_ITERATIONS,
        TimeUnit.NANOSECONDS.toMillis(optimisedNs),
        optimisedNs / 1e6 / MEASURE_ITERATIONS,
        speedup,
        shortcutCount);

    return new BenchmarkResult(
        filterOptimised(project, refs),
        filterLegacy(project, refs),
        shortcutCount,
        speedup,
        legacyNs / 1e6 / MEASURE_ITERATIONS,
        optimisedNs / 1e6 / MEASURE_ITERATIONS);
  }

  private ImmutableList<Ref> filterOptimised(Project.NameKey project, ImmutableList<Ref> refs)
      throws Exception {
    ProjectControl control = freshControl(project);
    DefaultRefFilter filter =
        new DefaultRefFilter(
            tagCache,
            permissionBackend,
            refVisibilityControl,
            gerritConfig,
            refFilterMetrics,
            changesByProjectCache,
            changeDataFactoryForFilter,
            classifierFactory,
            control);
    try (Repository repo = repoManager.openRepository(project)) {
      return filter.filter(refs, repo, RefFilterOptions.defaults());
    }
  }

  private ImmutableList<Ref> filterLegacy(Project.NameKey project, ImmutableList<Ref> refs)
      throws Exception {
    ProjectControl control = freshControl(project);
    DefaultRefFilter legacyFilter =
        new DefaultRefFilter(
            tagCache,
            permissionBackend,
            refVisibilityControl,
            gerritConfig,
            refFilterMetrics,
            changesByProjectCache,
            changeDataFactoryForFilter,
            projectControl -> new NoOpReadAccessClassifier(projectControl),
            control);
    try (Repository repo = repoManager.openRepository(project)) {
      return legacyFilter.filter(refs, repo, RefFilterOptions.defaults());
    }
  }

  /** Returns a fresh {@link ProjectControl} so each filter call starts with an empty ACL cache. */
  private ProjectControl freshControl(Project.NameKey project) throws Exception {
    ProjectState state =
        projectCache.get(project).orElseThrow(() -> new IllegalStateException("project not found"));
    return projectControlFactory.create(identifiedUserFactory.create(user.id()), state);
  }

  private Project.NameKey createProjectWithBranches(int numBranches) throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    for (int i = 0; i < numBranches; i++) {
      gApi.projects().name(project.get()).branch("branch-" + i).create(new BranchInput());
    }
    return project;
  }

  /**
   * Creates a project with {@code numBranches} branches and {@code numTags} lightweight tags, each
   * tag pointing at the tip of {@code refs/heads/branch-<i mod numBranches>}.
   */
  private Project.NameKey createProjectWithBranchesAndTags(int numBranches, int numTags)
      throws Exception {
    Project.NameKey project = createProjectWithBranches(numBranches);
    for (int i = 0; i < numTags; i++) {
      String targetBranch = "branch-" + (i % numBranches);
      String branchRevision =
          gApi.projects().name(project.get()).branch(targetBranch).get().revision;
      TagInput tagInput = new TagInput();
      tagInput.ref = "v" + i + ".0";
      tagInput.revision = branchRevision;
      gApi.projects().name(project.get()).tag(tagInput.ref).create(tagInput);
    }
    return project;
  }

  /**
   * Creates a project with {@code numBranches} branches and {@code numTags} lightweight tags where
   * each tag points at a <em>non-tip</em> commit. Concretely:
   *
   * <ol>
   *   <li>Create all branches (each starts at the repo's initial commit).
   *   <li>Tag the current tip of {@code branch-<i mod numBranches>} as {@code v<i>.0}.
   *   <li>Advance {@code branch-<i mod numBranches>} by one additional commit, so the tag now
   *       points to a parent rather than the branch tip.
   * </ol>
   *
   * <p>When the {@link com.google.gerrit.server.git.TagCache} is invalidated before a filter call,
   * the reachability check must do a full commit-graph walk ({@link
   * com.google.gerrit.server.git.TagSet#build}) to determine whether each tag is reachable from a
   * visible branch.
   */
  private Project.NameKey createProjectWithBranchesAndNonTipTags(int numBranches, int numTags)
      throws Exception {
    Project.NameKey project = createProjectWithBranches(numBranches);
    try (Repository repo = repoManager.openRepository(project)) {
      for (int i = 0; i < numTags; i++) {
        String branchName = "branch-" + (i % numBranches);
        String refName = "refs/heads/" + branchName;

        // Record the current branch tip — this is where the tag will point.
        Ref branchRef = repo.exactRef(refName);
        ObjectId tagTarget = branchRef.getObjectId();

        // Create a lightweight tag at the current tip.
        TagInput tagInput = new TagInput();
        tagInput.ref = "v" + i + ".0";
        tagInput.revision = tagTarget.name();
        gApi.projects().name(project.get()).tag(tagInput.ref).create(tagInput);

        // Advance the branch by one empty commit so the tag is now behind the tip.
        try (ObjectInserter inserter = repo.newObjectInserter();
            RevWalk rw = new RevWalk(repo)) {
          PersonIdent ident = serverIdent.get();
          CommitBuilder cb = new CommitBuilder();
          cb.setParentId(tagTarget);
          cb.setTreeId(rw.parseCommit(tagTarget).getTree());
          cb.setMessage("advance branch-" + i);
          cb.setAuthor(ident);
          cb.setCommitter(ident);
          ObjectId newTip = inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_COMMIT, cb.build());
          inserter.flush();

          try (var ctx = openTestRefUpdateContext()) {
            RefUpdate ru = repo.updateRef(refName);
            ru.setExpectedOldObjectId(tagTarget);
            ru.setNewObjectId(newTip);
            ru.update(rw);
          }
        }
      }
    }
    return project;
  }

  private ImmutableList<Ref> getAllRefs(Project.NameKey project) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return ImmutableList.copyOf(repo.getRefDatabase().getRefs());
    }
  }
}

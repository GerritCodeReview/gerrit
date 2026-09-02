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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.ChangesByProjectCache;
import com.google.gerrit.server.git.TagCache;
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
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
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
  private static final int WARMUP_ITERATIONS = 5;
  private static final int MEASURE_ITERATIONS = 50;

  @Inject private ProjectOperations projectOperations;
  @Inject private ProjectControl.Factory projectControlFactory;
  @Inject private ReadAccessClassifier.Factory classifierFactory;
  @Inject private TagCache tagCache;
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
   * Most refs visible, one blocked. The full filter loop runs because neither
   * existing fast path fires. The classifier short-circuits almost all refs
   * (VISIBLE), calling the full ACL check only for the blocked branch.
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

    BenchmarkResult result = benchmark("mostRefsVisible_oneBlocked", project, refs);

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    assertWithMessage("classifier_shortcut_count should increase with each filter call")
        .that(result.shortcutCount)
        .isGreaterThan(0);
    assertWithMessage("expected optimised path to be at least 2x faster than legacy")
        .that(result.speedup)
        .isAtLeast(2.0);
  }

  /**
   * Many refs blocked (10% of branches from the same set). The classifier
   * returns VISIBLE for the unblocked 90% and NEEDS_FULL_CHECK for the blocked
   * 10%, so the shortcut count is lower than the one-blocked scenario and the
   * speedup is smaller but still significant.
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

    BenchmarkResult result = benchmark("manyRefsBlocked (" + numBlocked + " blocked)", project, refs);

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
        .that(result.speedup)
        .isAtLeast(2.0);
  }

  /**
   * Per-user {@code ${username}} pattern with branches that actually match.
   * Half the branches are named {@code refs/heads/<username>/feature-*} and
   * match the per-user pattern; the other half are plain {@code branch-*} and
   * are invisible (no broad allow, pattern is the only grant). The classifier
   * returns NEEDS_FULL_CHECK for the per-user branches (correct: they are
   * visible to this user) and INVISIBLE for the others.
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

    BenchmarkResult result = benchmark("perUserPatternWithMatchingBranches", project, refs);

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    // The per-user branches are routed to NEEDS_FULL_CHECK; the classifier
    // cannot short-circuit them, so shortcutCount should be 0.
    assertWithMessage("no shortcuts expected: all refs need full check or are INVISIBLE")
        .that(result.shortcutCount)
        .isEqualTo(0);
  }

  /**
   * All refs visible — the existing {@code allRefsAreVisible} fast path fires
   * before the per-ref loop, so the classifier's per-ref work is never
   * reached. Both paths should be equally fast (no loop), and the classifier
   * adds no overhead.
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

    BenchmarkResult result = benchmark("allRefsVisible_fastPathFires", project, refs);

    assertThat(result.optimisedResult).containsExactlyElementsIn(result.legacyResult);
    // No shortcuts from the classifier — the fast path short-circuits the loop
    // entirely before the classifier is ever invoked.
    assertWithMessage("no classifier shortcuts expected when fast path fires")
        .that(result.shortcutCount)
        .isEqualTo(0);
  }

  // ---------------------------------------------------------------------------
  // Benchmark infrastructure
  // ---------------------------------------------------------------------------

  private static final class BenchmarkResult {
    final ImmutableList<Ref> optimisedResult;
    final ImmutableList<Ref> legacyResult;
    final long shortcutCount;
    final double speedup;

    BenchmarkResult(
        ImmutableList<Ref> optimisedResult,
        ImmutableList<Ref> legacyResult,
        long shortcutCount,
        double speedup) {
      this.optimisedResult = optimisedResult;
      this.legacyResult = legacyResult;
      this.shortcutCount = shortcutCount;
      this.speedup = speedup;
    }
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
        filterOptimised(project, refs), filterLegacy(project, refs), shortcutCount, speedup);
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
        projectCache
            .get(project)
            .orElseThrow(() -> new IllegalStateException("project not found"));
    return projectControlFactory.create(identifiedUserFactory.create(user.id()), state);
  }

  private Project.NameKey createProjectWithBranches(int numBranches) throws Exception {
    Project.NameKey project = projectOperations.newProject().create();
    for (int i = 0; i < numBranches; i++) {
      gApi.projects().name(project.get()).branch("branch-" + i).create(new BranchInput());
    }
    return project;
  }

  private ImmutableList<Ref> getAllRefs(Project.NameKey project) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return ImmutableList.copyOf(repo.getRefDatabase().getRefs());
    }
  }
}

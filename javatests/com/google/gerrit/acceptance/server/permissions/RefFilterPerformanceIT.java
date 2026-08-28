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
import static com.google.gerrit.entities.Permission.READ;
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
import org.junit.Before;
import org.junit.Test;

/**
 * Performance test for {@link DefaultRefFilter} comparing the optimised path (with {@link
 * ReadAccessClassifier}) against the legacy path (with {@link NoOpReadAccessClassifier}).
 *
 * <p>Both paths call the exact same {@link DefaultRefFilter#filter} code. The only controlled
 * variable is which {@link ReadAccessClassifier} is used: the real one that short-circuits most
 * refs, or a no-op that falls through to the full per-ref ACL evaluation for every ref.
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
  @Inject private DefaultRefFilter.Factory refFilterFactory;
  // Dependencies forwarded to the manually-constructed legacy DefaultRefFilter.
  @Inject private TagCache tagCache;
  @Inject private PermissionBackend permissionBackend;
  @Inject private RefVisibilityControl refVisibilityControl;
  @Inject @GerritServerConfig private Config gerritConfig;
  @Inject private DefaultRefFilter.Metrics refFilterMetrics;
  @Inject private ChangesByProjectCache changesByProjectCache;
  @Inject private ChangeData.Factory changeDataFactoryForFilter;

  private final TestMetricMaker testMetricMaker = TestMetricMaker.getInstance();

  private Project.NameKey testProject;
  private ImmutableList<Ref> refs;

  @Before
  public void setUpProjectAndRefs() throws Exception {
    testProject = projectOperations.newProject().create();

    // ALLOW READ refs/* with one BLOCK: defeats both existing fast paths so the
    // full per-ref loop runs, exercising the classifier on every ref.
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/secret").group(REGISTERED_USERS))
        .update();

    for (int i = 0; i < NUM_BRANCHES; i++) {
      gApi.projects().name(testProject.get()).branch("branch-" + i).create(new BranchInput());
    }
    gApi.projects().name(testProject.get()).branch("secret").create(new BranchInput());

    try (Repository repo = repoManager.openRepository(testProject)) {
      refs = ImmutableList.copyOf(repo.getRefDatabase().getRefs());
    }

    // Optimised: DefaultRefFilter using the real injected ReadAccessClassifier.
    // Legacy: same DefaultRefFilter code, but NoOpReadAccessClassifier is passed
    // as the factory — constructed directly because a child injector cannot
    // override a binding already present in the parent's PrivateModule.
    // Both are created fresh per filter() call to avoid ACL result cache sharing.
  }

  @Test
  public void classifierShortcutsRefVisibilityChecks() throws Exception {
    // Warm up both paths.
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      @SuppressWarnings("unused")
      var unused1 = filterOptimised();
      @SuppressWarnings("unused")
      var unused2 = filterLegacy();
    }

    // Measure optimised (real ReadAccessClassifier).
    testMetricMaker.reset();
    Stopwatch optimisedTimer = Stopwatch.createStarted();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      @SuppressWarnings("unused")
      var unused = filterOptimised();
    }
    long optimisedNs = optimisedTimer.elapsed(TimeUnit.NANOSECONDS);
    long shortcutCount =
        testMetricMaker.getCount("permissions/ref_filter/classifier_shortcut_count");

    // Measure legacy (NoOpReadAccessClassifier: full ACL check per ref).
    Stopwatch legacyTimer = Stopwatch.createStarted();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      @SuppressWarnings("unused")
      var unused = filterLegacy();
    }
    long legacyNs = legacyTimer.elapsed(TimeUnit.NANOSECONDS);

    double speedup = (double) legacyNs / Math.max(optimisedNs, 1);
    System.err.printf(
        "%nREF FILTER PERFORMANCE (%d refs, %d iterations):%n"
            + "  Legacy    (per-ref ACL evaluation): %d ms  (avg %.3f ms/call)%n"
            + "  Optimised (ReadAccessClassifier):   %d ms  (avg %.3f ms/call)%n"
            + "  Speedup: %.2fx, classifier shortcuts: %d%n",
        refs.size(),
        MEASURE_ITERATIONS,
        TimeUnit.NANOSECONDS.toMillis(legacyNs),
        legacyNs / 1e6 / MEASURE_ITERATIONS,
        TimeUnit.NANOSECONDS.toMillis(optimisedNs),
        optimisedNs / 1e6 / MEASURE_ITERATIONS,
        speedup,
        shortcutCount);

    // Behavioural parity: both paths must produce the same visible ref set.
    assertThat(filterOptimised()).containsExactlyElementsIn(filterLegacy());

    // Deterministic proof the optimisation fired: the classifier must have
    // short-circuited at least one ref per filter call during the measured run.
    assertWithMessage("classifier_shortcut_count should increase with each filter call")
        .that(shortcutCount)
        .isGreaterThan(0);

    // Wall-time comparison: now that both paths run identical DefaultRefFilter
    // code, the speedup reflects only the classifier's contribution.
    assertWithMessage("expected optimised path to be at least 2x faster than legacy")
        .that(speedup)
        .isAtLeast(2.0);
  }

  private ImmutableList<Ref> filterOptimised() throws Exception {
    try (Repository repo = repoManager.openRepository(testProject)) {
      return refFilterFactory
          .create(freshControl())
          .filter(refs, repo, RefFilterOptions.defaults());
    }
  }

  private ImmutableList<Ref> filterLegacy() throws Exception {
    ProjectControl control = freshControl();
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
    try (Repository repo = repoManager.openRepository(testProject)) {
      return legacyFilter.filter(refs, repo, RefFilterOptions.defaults());
    }
  }

  /** Returns a fresh {@link ProjectControl} so each filter call starts with an empty ACL cache. */
  private ProjectControl freshControl() throws Exception {
    ProjectState state =
        projectCache
            .get(testProject)
            .orElseThrow(() -> new IllegalStateException("project not found"));
    return projectControlFactory.create(identifiedUserFactory.create(user.id()), state);
  }
}

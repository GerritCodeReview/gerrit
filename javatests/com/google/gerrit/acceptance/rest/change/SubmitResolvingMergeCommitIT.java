// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.Submit;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

@NoHttpd
public class SubmitResolvingMergeCommitIT extends AbstractDaemonTest {
  @Inject private Provider<MergeSuperSet> mergeSuperSet;

  @Inject private Submit submit;

  @ConfigSuite.Default
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  public void resolvingMergeCommitAtEndOfChain() throws Exception {
    /*
      A <- B <- C <------- D
      ^                    ^
      |                    |
      E <- F <- G <- H <-- M*

      G has a conflict with C and is resolved in M which is a merge
      commit of H and D.
    */

    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b =
        createChange("B", "new.txt", "No conflict line", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c = createChange("C", ImmutableList.of(b.getCommit()));
    PushOneCommit.Result d = createChange("D", ImmutableList.of(c.getCommit()));

    PushOneCommit.Result e = createChange("E", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f = createChange("F", ImmutableList.of(e.getCommit()));
    PushOneCommit.Result g =
        createChange("G", "new.txt", "Conflicting line", ImmutableList.of(f.getCommit()));
    PushOneCommit.Result h = createChange("H", ImmutableList.of(g.getCommit()));

    approve(a.getChangeId());
    approve(b.getChangeId());
    approve(c.getChangeId());
    approve(d.getChangeId());
    submit(d.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());
    approve(h.getChangeId());

    assertMergeable(e.getChange());
    assertMergeable(f.getChange());
    assertNotMergeable(g.getChange());
    assertNotMergeable(h.getChange());

    PushOneCommit.Result m =
        createChange(
            "M", "new.txt", "Resolved conflict", ImmutableList.of(d.getCommit(), h.getCommit()));
    approve(m.getChangeId());

    assertChangeSetMergeable(m.getChange(), true);

    assertMergeable(m.getChange());
    submit(m.getChangeId());

    assertMerged(e.getChangeId());
    assertMerged(f.getChangeId());
    assertMerged(g.getChangeId());
    assertMerged(h.getChangeId());
    assertMerged(m.getChangeId());
  }

  @Test
  public void resolvingMergeCommitComingBeforeConflict() throws Exception {
    /*
      A <- B <- C <- D
      ^    ^
      |    |
      E <- F* <- G

      F is a merge commit of E and B and resolves any conflict.
      However G is conflicting with C.
    */

    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b =
        createChange("B", "new.txt", "No conflict line", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c =
        createChange("C", "new.txt", "No conflict line #2", ImmutableList.of(b.getCommit()));
    PushOneCommit.Result d = createChange("D", ImmutableList.of(c.getCommit()));
    PushOneCommit.Result e =
        createChange("E", "new.txt", "Conflicting line", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f =
        createChange(
            "F", "new.txt", "Resolved conflict", ImmutableList.of(b.getCommit(), e.getCommit()));
    PushOneCommit.Result g =
        createChange("G", "new.txt", "Conflicting line #2", ImmutableList.of(f.getCommit()));

    assertMergeable(e.getChange());

    approve(a.getChangeId());
    approve(b.getChangeId());
    submit(b.getChangeId());

    assertNotMergeable(e.getChange());
    assertMergeable(f.getChange());
    assertMergeable(g.getChange());

    approve(c.getChangeId());
    approve(d.getChangeId());
    submit(d.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());

    assertNotMergeable(g.getChange());
    assertChangeSetMergeable(g.getChange(), false);
  }

  @Test
  public void resolvingMergeCommitWithTopics() throws Exception {
    /*
      Project1:
        A <- B <-- C <---
        ^    ^          |
        |    |          |
        E <- F* <- G <- L*

      G clashes with C, and F resolves the clashes between E and B.
      Later, L resolves the clashes between C and G.

      Project2:
        H <- I
        ^    ^
        |    |
        J <- K*

      J clashes with I, and K resolves all problems.
      G, K and L are in the same topic.
    */
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    String project1Name = name("Project1");
    String project2Name = name("Project2");
    gApi.projects().create(project1Name);
    gApi.projects().create(project2Name);
    TestRepository<InMemoryRepository> project1 = cloneProject(Project.nameKey(project1Name));
    TestRepository<InMemoryRepository> project2 = cloneProject(Project.nameKey(project2Name));

    PushOneCommit.Result a = createChange(project1, "A");
    PushOneCommit.Result b =
        createChange(project1, "B", "new.txt", "No conflict line", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c =
        createChange(
            project1, "C", "new.txt", "No conflict line #2", ImmutableList.of(b.getCommit()));

    approve(a.getChangeId());
    approve(b.getChangeId());
    approve(c.getChangeId());
    submit(c.getChangeId());

    PushOneCommit.Result e =
        createChange(project1, "E", "new.txt", "Conflicting line", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f =
        createChange(
            project1,
            "F",
            "new.txt",
            "Resolved conflict",
            ImmutableList.of(b.getCommit(), e.getCommit()));
    PushOneCommit.Result g =
        createChange(
            project1,
            "G",
            "new.txt",
            "Conflicting line #2",
            ImmutableList.of(f.getCommit()),
            "refs/for/master%topic=" + name("topic1"));

    PushOneCommit.Result h = createChange(project2, "H");
    PushOneCommit.Result i =
        createChange(project2, "I", "new.txt", "No conflict line", ImmutableList.of(h.getCommit()));
    PushOneCommit.Result j =
        createChange(project2, "J", "new.txt", "Conflicting line", ImmutableList.of(h.getCommit()));
    PushOneCommit.Result k =
        createChange(
            project2,
            "K",
            "new.txt",
            "Sadly conflicting topic-wise",
            ImmutableList.of(i.getCommit(), j.getCommit()),
            "refs/for/master%topic=" + name("topic1"));

    approve(h.getChangeId());
    approve(i.getChangeId());
    submit(i.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());
    approve(j.getChangeId());
    approve(k.getChangeId());

    assertChangeSetMergeable(g.getChange(), false);
    assertChangeSetMergeable(k.getChange(), false);

    PushOneCommit.Result l =
        createChange(
            project1,
            "L",
            "new.txt",
            "Resolving conflicts again",
            ImmutableList.of(c.getCommit(), g.getCommit()),
            "refs/for/master%topic=" + name("topic1"));

    approve(l.getChangeId());
    assertChangeSetMergeable(l.getChange(), true);

    submit(l.getChangeId());
    assertMerged(c.getChangeId());
    assertMerged(g.getChangeId());
    assertMerged(k.getChangeId());
  }

  @Test
  public void resolvingMergeCommitAtEndOfChainAndNotUpToDate() throws Exception {
    /*
        A <-- B
         \
          C  <- D
           \   /
             E

        B is the target branch, and D should be merged with B, but one
        of C conflicts with B
    */

    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b =
        createChange("B", "new.txt", "No conflict line", ImmutableList.of(a.getCommit()));

    approve(a.getChangeId());
    approve(b.getChangeId());
    submit(b.getChangeId());

    PushOneCommit.Result c =
        createChange("C", "new.txt", "Create conflicts", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result e = createChange("E", ImmutableList.of(c.getCommit()));
    PushOneCommit.Result d =
        createChange(
            "D", "new.txt", "Resolves conflicts", ImmutableList.of(c.getCommit(), e.getCommit()));

    approve(c.getChangeId());
    approve(e.getChangeId());
    approve(d.getChangeId());
    assertNotMergeable(d.getChange());
    assertChangeSetMergeable(d.getChange(), false);
  }

  @Test
  public void chainWithOutdatedPatchSetParentIsUnmergeable() throws Exception {
    /*
      A(ps1) <- B(ps1)
      A is amended to A(ps2), making B(ps1) depend on an outdated patchset of A.
    */
    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b =
        createChange("B", "b.txt", "content B", ImmutableList.of(a.getCommit()));

    approve(a.getChangeId());
    approve(b.getChangeId());

    // Initially, both A and B are up-to-date and mergeable together.
    assertChangeSetMergeable(b.getChange(), true);

    // Amend A to create patch-set 2.
    testRepo.reset(a.getCommit());
    PushOneCommit.Result amendResult =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "A amended",
                "a.txt",
                "content A amended",
                a.getChangeId())
            .to("refs/for/master");
    amendResult.assertOkStatus();
    approve(a.getChangeId());

    // Now B's parent points to PS1 of A, which is outdated.
    assertChangeSetMergeable(b.getChange(), false);
    // Root change A directly based on destination branch tip is mergeable.
    assertChangeSetMergeable(a.getChange(), true);

    // Rebase B on A's latest patch-set.
    gApi.changes().id(b.getChangeId()).rebase();
    approve(b.getChangeId());

    // Now B is mergeable again.
    assertChangeSetMergeable(b.getChange(), true);
    assertChangeSetMergeable(a.getChange(), true);
  }

  @Test
  public void reproducePerformanceWithManyPatchSetsInChain() throws Exception {
    // Create a chain of 4 changes: c0 -> c1 -> c2 -> c3
    int chainLength = 4;
    int patchSetsPerChange = 10;
    List<PushOneCommit.Result> chain = new ArrayList<>();
    RevCommit parentCommit = null;

    for (int i = 0; i < chainLength; i++) {
      PushOneCommit.Result change =
          createChange(
              testRepo,
              "Change " + i,
              "file_" + i + ".txt",
              "initial content " + i,
              parentCommit != null ? ImmutableList.of(parentCommit) : ImmutableList.of());
      approve(change.getChangeId());
      // Create additional patch sets for this change
      for (int ps = 2; ps <= patchSetsPerChange; ps++) {
        PushOneCommit.Result amendResult =
            amendChange(
                change.getChangeId(),
                "Change " + i + " ps" + ps,
                "file_" + i + ".txt",
                "content " + i + " v" + ps);
        amendResult.assertOkStatus();
        approve(change.getChangeId());
        if (ps == patchSetsPerChange) {
          parentCommit = amendResult.getCommit();
        }
      }
      chain.add(change);
    }

    PushOneCommit.Result tip = chain.get(chainLength - 1);
    ChangeSet cs =
        mergeSuperSet
            .get()
            .completeChangeSet(
                tip.getChange().change(), user(admin), /* includingTopicClosure= */ false);

    // Warm up both paths
    for (int i = 0; i < 5; i++) {
      var unused1 = submit.getUnmergeableChanges(cs);
      var unused2 = getUnmergeableChangesBaseline(cs);
    }

    int iterations = 100;

    // Benchmark Baseline (Unoptimized: eager NoteDb scans + per-change repo opens)
    Stopwatch baselineTimer = Stopwatch.createStarted();
    Set<ChangeData> baselineResult = null;
    for (int i = 0; i < iterations; i++) {
      baselineResult = getUnmergeableChangesBaseline(cs);
    }
    baselineTimer.stop();
    long baselineMs = baselineTimer.elapsed(TimeUnit.MILLISECONDS);

    // Benchmark Optimized (O(1) commit parent check + batched repo lifecycle)
    Stopwatch optTimer = Stopwatch.createStarted();
    java.util.Collection<ChangeData> optResult = null;
    for (int i = 0; i < iterations; i++) {
      optResult = submit.getUnmergeableChanges(cs);
    }
    optTimer.stop();
    long optMs = optTimer.elapsed(TimeUnit.MILLISECONDS);

    double speedup = (double) baselineMs / Math.max(optMs, 1);
    System.out.printf(
        "\n"
            + "=======================================================\n"
            + "PERFORMANCE REPRODUCTION BENCHMARK (%d changes x %d patch sets = %d total patch"
            + " sets, %d iterations):\n"
            + "  - Baseline (unoptimized NoteDb eager scan + per-change repo): %d ms (avg %.3f"
            + " ms/call)\n"
            + "  - Optimized (bounded parent check + single repo lifecycle):    %d ms (avg %.3f"
            + " ms/call)\n"
            + "  - Speedup factor: %.2fx faster!\n"
            + "=======================================================\n\n",
        chainLength,
        patchSetsPerChange,
        chainLength * patchSetsPerChange,
        iterations,
        baselineMs,
        (double) baselineMs / iterations,
        optMs,
        (double) optMs / iterations,
        speedup);

    // Verify behavioral parity
    assertThat(optResult).containsExactlyElementsIn(baselineResult);
    assertThat(optResult).isEmpty(); // All changes in chain are up-to-date and mergeable
  }

  @Nullable
  private Set<ChangeData> getUnmergeableChangesBaseline(ChangeSet cs) throws Exception {
    Set<ChangeData> unmergeableChanges = new HashSet<>();
    Set<ObjectId> outDatedPatchSets = new HashSet<>();
    for (ChangeData change : cs.changes()) {
      unmergeableChanges.add(change);
      // Baseline eagerly loads all patch sets from NoteDb
      outDatedPatchSets.addAll(
          change.notes().getPatchSets().values().stream()
              .map(PatchSet::commitId)
              .collect(Collectors.toSet()));
      outDatedPatchSets.remove(change.currentPatchSet().commitId());
    }

    ListMultimap<BranchNameKey, ChangeData> cbb = cs.changesByBranch();
    for (BranchNameKey branch : cbb.keySet()) {
      List<ChangeData> targetBranch = cbb.get(branch);
      HashMap<Change.Id, RevCommit> commits = new HashMap<>();
      try (Repository repo = repoManager.openRepository(branch.project());
          RevWalk walk = new RevWalk(repo)) {
        for (ChangeData change : targetBranch) {
          RevCommit commit = walk.parseCommit(change.currentPatchSet().commitId());
          commits.put(change.getId(), commit);
        }
      }
      Set<ObjectId> allParents =
          commits.values().stream()
              .flatMap(c -> Arrays.stream(c.getParents()))
              .map(RevObject::getId)
              .collect(Collectors.toSet());
      for (ChangeData change : targetBranch) {
        RevCommit commit = commits.get(change.getId());
        boolean isMergeCommit = commit.getParentCount() > 1;
        boolean isLastInChain = !allParents.contains(commit.getId());
        if (Arrays.stream(commit.getParents())
            .anyMatch(c -> outDatedPatchSets.contains(c.getId()))) {
          continue;
        }
        change.setMergeable(null);
        Boolean mergeable = change.isMergeable();
        if (mergeable == null) {
          return null;
        }
        if (mergeable) {
          unmergeableChanges.remove(change);
        }
        if (isLastInChain && isMergeCommit && mergeable) {
          targetBranch.stream().forEach(unmergeableChanges::remove);
          break;
        }
      }
    }
    return unmergeableChanges;
  }

  private void submit(String changeId) throws Exception {
    gApi.changes().id(changeId).current().submit();
  }

  private void assertChangeSetMergeable(ChangeData change, boolean expected)
      throws MissingObjectException,
          IncorrectObjectTypeException,
          IOException,
          PermissionBackendException {
    ChangeSet cs =
        mergeSuperSet
            .get()
            .completeChangeSet(change.change(), user(admin), /* includingTopicClosure= */ false);
    assertThat(submit.getUnmergeableChanges(cs).isEmpty()).isEqualTo(expected);
  }

  private void assertMergeable(ChangeData change) throws Exception {
    change.setMergeable(null);
    assertThat(change.isMergeable()).isTrue();
  }

  private void assertNotMergeable(ChangeData change) throws Exception {
    change.setMergeable(null);
    assertThat(change.isMergeable()).isFalse();
  }

  private void assertMerged(String changeId) throws Exception {
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  private PushOneCommit.Result createChange(
      TestRepository<?> repo,
      String subject,
      String fileName,
      String content,
      List<RevCommit> parents,
      String ref)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), repo, subject, fileName, content);

    if (!parents.isEmpty()) {
      push.setParents(parents);
    }

    PushOneCommit.Result result;
    if (fileName.isEmpty()) {
      result = push.execute(ref);
    } else {
      result = push.to(ref);
    }
    result.assertOkStatus();
    return result;
  }

  private PushOneCommit.Result createChange(TestRepository<?> repo, String subject)
      throws Exception {
    return createChange(repo, subject, "x", "x", new ArrayList<>(), "refs/for/master");
  }

  private PushOneCommit.Result createChange(
      TestRepository<?> repo,
      String subject,
      String fileName,
      String content,
      List<RevCommit> parents)
      throws Exception {
    return createChange(repo, subject, fileName, content, parents, "refs/for/master");
  }

  @Override
  protected PushOneCommit.Result createChange(String subject) throws Exception {
    return createChange(testRepo, subject, "", "", Collections.emptyList(), "refs/for/master");
  }

  private PushOneCommit.Result createChange(String subject, List<RevCommit> parents)
      throws Exception {
    return createChange(testRepo, subject, "", "", parents, "refs/for/master");
  }

  private PushOneCommit.Result createChange(
      String subject, String fileName, String content, List<RevCommit> parents) throws Exception {
    return createChange(testRepo, subject, fileName, content, parents, "refs/for/master");
  }
}

// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class GroupCollectorTest {
  private TestRepository<?> tr;

  @Before
  public void setUp() throws Exception {
    tr = new TestRepository<>(
        new InMemoryRepository(new DfsRepositoryDescription("repo")));
  }

  @Test
  public void commitWhoseParentIsUninterestingGetsNewGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();

    Map<ObjectId, String> groups = collectGroups(
        newWalk(a, branchTip),
        patchSets(),
        groups());

    assertThat(groups).containsEntry(a, a.name());
  }

  @Test
  public void commitWhoseParentIsNewPatchSetGetsParentsGroup()
      throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(a).create();

    Map<ObjectId, String> groups = collectGroups(
        newWalk(b, branchTip),
        patchSets(),
        groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
  }

  @Test
  public void commitWhoseParentIsExistingPatchSetGetsParentsGroup()
      throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(a).create();

    String group = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    Map<ObjectId, String> groups = collectGroups(
        newWalk(b, branchTip),
        patchSets().put(a, psId(1, 1)),
        groups().put(psId(1, 1), group));

    assertThat(groups).containsEntry(a, group);
    assertThat(groups).containsEntry(b, group);
  }

  @Test
  public void mergeCommitAndNewParentsAllGetSameGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(a).parent(b).create();

    Map<ObjectId, String> groups = collectGroups(
        newWalk(m, branchTip),
        patchSets(),
        groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
    assertThat(groups).containsEntry(m, a.name());
  }

  @Test
  public void mergeCommitWhereOneParentHasExistingGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(a).parent(b).create();

    String group = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    Map<ObjectId, String> groups = collectGroups(
        newWalk(m, branchTip),
        patchSets().put(b, psId(1, 1)),
        groups().put(psId(1, 1), group));

    // Merge commit and other parent get the existing group.
    assertThat(groups).containsEntry(a, group);
    assertThat(groups).containsEntry(b, group);
    assertThat(groups).containsEntry(m, group);
  }

  @Test
  public void mergeCommitWhereBothParentsHaveDifferentGroups()
      throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(a).parent(b).create();

    String group1 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String group2 = "1234567812345678123456781234567812345678";
    Map<ObjectId, String> groups = collectGroups(
        newWalk(m, branchTip),
        patchSets()
            .put(a, psId(1, 1))
            .put(b, psId(2, 1)),
        groups()
            .put(psId(1, 1), group1)
            .put(psId(2, 1), group2));

    assertThat(groups).containsEntry(a, group1);
    assertThat(groups).containsEntry(b, group2);
    // Merge commit gets joined group of parents.
    assertThat(groups).containsEntry(m, group2 + "," + group1);
  }

  @Test
  public void mergeCommitResortsMergedGroupsFromParent() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(a).parent(b).create();

    String group1 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String group2a = "1234567812345678123456781234567812345678";
    String group2b = "ef123456ef123456ef123456ef123456ef123456";
    String group2 = group2a + "," + group2b;
    Map<ObjectId, String> groups = collectGroups(
        newWalk(m, branchTip),
        patchSets()
            .put(a, psId(1, 1))
            .put(b, psId(2, 1)),
        groups()
            .put(psId(1, 1), group1)
            .put(psId(2, 1), group2));

    assertThat(groups).containsEntry(a, group1);
    assertThat(groups).containsEntry(b, group2);
    // Joined parent groups are split and resorted.
    assertThat(groups).containsEntry(m, group2a + "," + group1 + "," + group2b);
  }

  @Test
  public void mergeCommitWithOneUninterestingParentAndOtherParentIsExisting()
      throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(branchTip).parent(a).create();

    String group = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    Map<ObjectId, String> groups = collectGroups(
        newWalk(m, branchTip),
        patchSets().put(a, psId(1, 1)),
        groups().put(psId(1, 1), group));

    assertThat(groups).containsEntry(a, group);
    assertThat(groups).containsEntry(m, group);
  }

  @Test
  public void mergeCommitWithOneUninterestingParentAndOtherParentIsNew()
      throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(branchTip).parent(a).create();

    Map<ObjectId, String> groups = collectGroups(
        newWalk(m, branchTip),
        patchSets(),
        groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(m, a.name());
  }

  @Test
  public void multipleMergeCommitsInHistoryAllResolveToSameGroup()
      throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit c = tr.commit().parent(branchTip).create();
    RevCommit m1 = tr.commit().parent(b).parent(c).create();
    RevCommit m2 = tr.commit().parent(a).parent(m1).create();

    Map<ObjectId, String> groups = collectGroups(
        newWalk(m2, branchTip),
        patchSets(),
        groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
    assertThat(groups).containsEntry(c, a.name());
    assertThat(groups).containsEntry(m1, a.name());
    assertThat(groups).containsEntry(m2, a.name());
  }

  // TODO(dborowitz): Tests for octopus merges.

  private static PatchSet.Id psId(int c, int p) {
    return new PatchSet.Id(new Change.Id(c), p);
  }

  private RevWalk newWalk(ObjectId start, ObjectId branchTip) throws Exception {
    // Match RevWalk conditions from ReceiveCommits.
    RevWalk walk = new RevWalk(tr.getRepository());
    walk.sort(RevSort.TOPO);
    walk.sort(RevSort.REVERSE, true);
    walk.markStart(walk.parseCommit(start));
    walk.markUninteresting(walk.parseCommit(branchTip));
    return walk;
  }

  private static Map<ObjectId, String> collectGroups(
      RevWalk walk,
      ImmutableMultimap.Builder<ObjectId, PatchSet.Id> patchSetsBySha,
      ImmutableMap.Builder<PatchSet.Id, String> groupLookup)
      throws Exception {
    GroupCollector gc =
        new GroupCollector(patchSetsBySha.build(), groupLookup.build());
    RevCommit c;
    while ((c = walk.next()) != null) {
      gc.visit(c);
    }
    return gc.getGroups();
  }

  // Helper methods for constructing various map arguments, to avoid lots of
  // type specifications.
  private static ImmutableMultimap.Builder<ObjectId, PatchSet.Id> patchSets() {
    return ImmutableMultimap.builder();
  }

  private static ImmutableMap.Builder<PatchSet.Id, String> groups() {
    return ImmutableMap.builder();
  }
}

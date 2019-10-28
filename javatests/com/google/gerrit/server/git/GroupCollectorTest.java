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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SortedSetMultimap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.git.receive.ReceivePackRefCache;
import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class GroupCollectorTest {
  private TestRepository<?> tr;

  @Before
  public void setUp() throws Exception {
    tr = new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription("repo")));
  }

  @Test
  public void commitWhoseParentIsUninterestingGetsNewGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();

    SortedSetMultimap<ObjectId, String> groups = collectGroups(newWalk(a, branchTip), groups());

    assertThat(groups).containsEntry(a, a.name());
  }

  @Test
  public void commitWhoseParentIsNewPatchSetGetsParentsGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(a).create();

    SortedSetMultimap<ObjectId, String> groups = collectGroups(newWalk(b, branchTip), groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
  }

  @Test
  public void commitWhoseParentIsExistingPatchSetGetsParentsGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    createRef(psId(1, 1), a, tr);
    RevCommit b = tr.commit().parent(a).create();

    String group = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    SortedSetMultimap<ObjectId, String> groups =
        collectGroups(newWalk(b, branchTip), groups().put(psId(1, 1), group));

    assertThat(groups).containsEntry(a, group);
    assertThat(groups).containsEntry(b, group);
  }

  @Test
  public void commitWhoseParentIsExistingPatchSetWithNoGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(a).create();

    SortedSetMultimap<ObjectId, String> groups = collectGroups(newWalk(b, branchTip), groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
  }

  @Test
  public void mergeCommitAndNewParentsAllGetSameGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(a).parent(b).create();

    SortedSetMultimap<ObjectId, String> groups = collectGroups(newWalk(m, branchTip), groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
    assertThat(groups).containsEntry(m, a.name());
  }

  @Test
  public void mergeCommitWhereOneParentHasExistingGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    createRef(psId(1, 1), a, tr);
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(a).parent(b).create();

    String group = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    SortedSetMultimap<ObjectId, String> groups =
        collectGroups(newWalk(m, branchTip), groups().put(psId(1, 1), group));

    // Merge commit and other parent get the existing group.
    assertThat(groups).containsEntry(a, group);
    assertThat(groups).containsEntry(b, group);
    assertThat(groups).containsEntry(m, group);
  }

  @Test
  public void mergeCommitWhereBothParentsHaveDifferentGroups() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    createRef(psId(1, 1), a, tr);
    RevCommit b = tr.commit().parent(branchTip).create();
    createRef(psId(2, 1), b, tr);
    RevCommit m = tr.commit().parent(a).parent(b).create();

    String group1 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String group2 = "1234567812345678123456781234567812345678";
    SortedSetMultimap<ObjectId, String> groups =
        collectGroups(
            newWalk(m, branchTip), groups().put(psId(1, 1), group1).put(psId(2, 1), group2));

    assertThat(groups).containsEntry(a, group1);
    assertThat(groups).containsEntry(b, group2);
    // Merge commit gets joined group of parents.
    assertThat(groups.asMap()).containsEntry(m, ImmutableSet.of(group1, group2));
  }

  @Test
  public void mergeCommitMergesGroupsFromParent() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    createRef(psId(1, 1), a, tr);
    RevCommit b = tr.commit().parent(branchTip).create();
    createRef(psId(2, 1), b, tr);
    RevCommit m = tr.commit().parent(a).parent(b).create();

    String group1 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String group2a = "1234567812345678123456781234567812345678";
    String group2b = "ef123456ef123456ef123456ef123456ef123456";
    SortedSetMultimap<ObjectId, String> groups =
        collectGroups(
            newWalk(m, branchTip),
            groups().put(psId(1, 1), group1).put(psId(2, 1), group2a).put(psId(2, 1), group2b));

    assertThat(groups).containsEntry(a, group1);
    assertThat(groups.asMap()).containsEntry(b, ImmutableSet.of(group2a, group2b));
    // Joined parent groups are split and resorted.
    assertThat(groups.asMap()).containsEntry(m, ImmutableSet.of(group1, group2a, group2b));
  }

  @Test
  public void mergeCommitWithOneUninterestingParentAndOtherParentIsExisting() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    createRef(psId(1, 1), a, tr);
    RevCommit m = tr.commit().parent(branchTip).parent(a).create();

    String group = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    SortedSetMultimap<ObjectId, String> groups =
        collectGroups(newWalk(m, branchTip), groups().put(psId(1, 1), group));

    assertThat(groups).containsEntry(a, group);
    assertThat(groups).containsEntry(m, group);
  }

  @Test
  public void mergeCommitWithOneUninterestingParentAndOtherParentIsNew() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(branchTip).parent(a).create();

    SortedSetMultimap<ObjectId, String> groups = collectGroups(newWalk(m, branchTip), groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(m, a.name());
  }

  @Test
  public void multipleMergeCommitsInHistoryAllResolveToSameGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(branchTip).create();
    RevCommit c = tr.commit().parent(branchTip).create();
    RevCommit m1 = tr.commit().parent(b).parent(c).create();
    RevCommit m2 = tr.commit().parent(a).parent(m1).create();

    SortedSetMultimap<ObjectId, String> groups = collectGroups(newWalk(m2, branchTip), groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
    assertThat(groups).containsEntry(c, a.name());
    assertThat(groups).containsEntry(m1, a.name());
    assertThat(groups).containsEntry(m2, a.name());
  }

  @Test
  public void mergeCommitWithDuplicatedParentGetsParentsGroup() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit m = tr.commit().parent(a).parent(a).create();
    tr.getRevWalk().parseBody(m);
    assertThat(m.getParentCount()).isEqualTo(2);
    assertThat(m.getParent(0)).isEqualTo(m.getParent(1));

    SortedSetMultimap<ObjectId, String> groups = collectGroups(newWalk(m, branchTip), groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(m, a.name());
  }

  @Test
  public void mergeCommitWithOneNewParentAndTwoExistingPatchSets() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    createRef(psId(1, 1), a, tr);
    RevCommit b = tr.commit().parent(branchTip).create();
    createRef(psId(2, 1), b, tr);
    RevCommit c = tr.commit().parent(b).create();
    RevCommit m = tr.commit().parent(a).parent(c).create();

    String group1 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String group2 = "1234567812345678123456781234567812345678";
    SortedSetMultimap<ObjectId, String> groups =
        collectGroups(
            newWalk(m, branchTip), groups().put(psId(1, 1), group1).put(psId(2, 1), group2));

    assertThat(groups).containsEntry(a, group1);
    assertThat(groups).containsEntry(b, group2);
    assertThat(groups).containsEntry(c, group2);
    assertThat(groups.asMap()).containsEntry(m, ImmutableSet.of(group1, group2));
  }

  @Test
  public void collectGroupsForMultipleTipsInParallel() throws Exception {
    RevCommit branchTip = tr.commit().create();
    RevCommit a = tr.commit().parent(branchTip).create();
    RevCommit b = tr.commit().parent(a).create();
    RevCommit c = tr.commit().parent(branchTip).create();
    RevCommit d = tr.commit().parent(c).create();

    RevWalk rw = newWalk(b, branchTip);
    rw.markStart(rw.parseCommit(d));
    // Schema upgrade case: all commits are existing patch sets, but none have
    // groups assigned yet.
    SortedSetMultimap<ObjectId, String> groups = collectGroups(rw, groups());

    assertThat(groups).containsEntry(a, a.name());
    assertThat(groups).containsEntry(b, a.name());
    assertThat(groups).containsEntry(c, c.name());
    assertThat(groups).containsEntry(d, c.name());
  }

  // TODO(dborowitz): Tests for octopus merges.

  private static PatchSet.Id psId(int c, int p) {
    return PatchSet.id(Change.id(c), p);
  }

  private static void createRef(PatchSet.Id psId, ObjectId id, TestRepository<?> tr)
      throws IOException {
    RefUpdate ru = tr.getRepository().updateRef(psId.toRefName());
    ru.setNewObjectId(id);
    assertThat(ru.update()).isEqualTo(RefUpdate.Result.NEW);
  }

  private RevWalk newWalk(ObjectId start, ObjectId branchTip) throws Exception {
    // Match RevWalk conditions from ReceiveCommits.
    RevWalk rw = new RevWalk(tr.getRepository());
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);
    rw.markStart(rw.parseCommit(start));
    rw.markUninteresting(rw.parseCommit(branchTip));
    return rw;
  }

  private SortedSetMultimap<ObjectId, String> collectGroups(
      RevWalk rw, ImmutableListMultimap.Builder<PatchSet.Id, String> groupLookup) throws Exception {
    ImmutableListMultimap<PatchSet.Id, String> groups = groupLookup.build();
    GroupCollector gc =
        new GroupCollector(
            ReceivePackRefCache.noCache(tr.getRepository().getRefDatabase()), (s) -> groups.get(s));
    RevCommit c;
    while ((c = rw.next()) != null) {
      gc.visit(c);
    }
    return gc.getGroups();
  }

  private static ImmutableListMultimap.Builder<PatchSet.Id, String> groups() {
    return ImmutableListMultimap.builder();
  }
}

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

package com.google.gerrit.server.change;

import static com.google.common.collect.Collections2.permutations;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.change.WalkSorter.PatchSetData;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gerrit.testutil.InMemoryRepositoryManager.Repo;
import com.google.gerrit.testutil.TestChanges;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class WalkSorterTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private Account.Id userId;
  private InMemoryRepositoryManager repoManager;

  @Before
  public void setUp() {
    userId = new Account.Id(1);
    repoManager = new InMemoryRepositoryManager();
  }

  @Test
  public void seriesOfChanges() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c1_1 = p.commit().create();
    RevCommit c2_1 = p.commit().parent(c1_1).create();
    RevCommit c3_1 = p.commit().parent(c2_1).create();

    ChangeData cd1 = newChange(p, c1_1);
    ChangeData cd2 = newChange(p, c2_1);
    ChangeData cd3 = newChange(p, c3_1);

    List<ChangeData> changes = ImmutableList.of(cd1, cd2, cd3);
    WalkSorter sorter = new WalkSorter(repoManager);

    assertSorted(
        sorter,
        changes,
        ImmutableList.of(
            patchSetData(cd3, c3_1), patchSetData(cd2, c2_1), patchSetData(cd1, c1_1)));

    // Add new patch sets whose commits are in reverse order, so output is in
    // reverse order.
    RevCommit c3_2 = p.commit().create();
    RevCommit c2_2 = p.commit().parent(c3_2).create();
    RevCommit c1_2 = p.commit().parent(c2_2).create();

    addPatchSet(cd1, c1_2);
    addPatchSet(cd2, c2_2);
    addPatchSet(cd3, c3_2);

    assertSorted(
        sorter,
        changes,
        ImmutableList.of(
            patchSetData(cd1, c1_2), patchSetData(cd2, c2_2), patchSetData(cd3, c3_2)));
  }

  @Test
  public void subsetOfSeriesOfChanges() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c1_1 = p.commit().create();
    RevCommit c2_1 = p.commit().parent(c1_1).create();
    RevCommit c3_1 = p.commit().parent(c2_1).create();

    ChangeData cd1 = newChange(p, c1_1);
    ChangeData cd3 = newChange(p, c3_1);

    List<ChangeData> changes = ImmutableList.of(cd1, cd3);
    WalkSorter sorter = new WalkSorter(repoManager);

    assertSorted(
        sorter, changes, ImmutableList.of(patchSetData(cd3, c3_1), patchSetData(cd1, c1_1)));
  }

  @Test
  public void seriesOfChangesAtSameTimestamp() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c0 = p.commit().tick(0).create();
    RevCommit c1 = p.commit().tick(0).parent(c0).create();
    RevCommit c2 = p.commit().tick(0).parent(c1).create();
    RevCommit c3 = p.commit().tick(0).parent(c2).create();
    RevCommit c4 = p.commit().tick(0).parent(c3).create();

    RevWalk rw = p.getRevWalk();
    rw.parseCommit(c1);
    assertThat(rw.parseCommit(c2).getCommitTime()).isEqualTo(c1.getCommitTime());
    assertThat(rw.parseCommit(c3).getCommitTime()).isEqualTo(c1.getCommitTime());
    assertThat(rw.parseCommit(c4).getCommitTime()).isEqualTo(c1.getCommitTime());

    ChangeData cd1 = newChange(p, c1);
    ChangeData cd2 = newChange(p, c2);
    ChangeData cd3 = newChange(p, c3);
    ChangeData cd4 = newChange(p, c4);

    List<ChangeData> changes = ImmutableList.of(cd1, cd2, cd3, cd4);
    WalkSorter sorter = new WalkSorter(repoManager);

    assertSorted(
        sorter,
        changes,
        ImmutableList.of(
            patchSetData(cd4, c4),
            patchSetData(cd3, c3),
            patchSetData(cd2, c2),
            patchSetData(cd1, c1)));
  }

  @Test
  public void seriesOfChangesWithReverseTimestamps() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c0 = p.commit().tick(-1).create();
    RevCommit c1 = p.commit().tick(-1).parent(c0).create();
    RevCommit c2 = p.commit().tick(-1).parent(c1).create();
    RevCommit c3 = p.commit().tick(-1).parent(c2).create();
    RevCommit c4 = p.commit().tick(-1).parent(c3).create();

    RevWalk rw = p.getRevWalk();
    rw.parseCommit(c1);
    assertThat(rw.parseCommit(c2).getCommitTime()).isLessThan(c1.getCommitTime());
    assertThat(rw.parseCommit(c3).getCommitTime()).isLessThan(c2.getCommitTime());
    assertThat(rw.parseCommit(c4).getCommitTime()).isLessThan(c3.getCommitTime());

    ChangeData cd1 = newChange(p, c1);
    ChangeData cd2 = newChange(p, c2);
    ChangeData cd3 = newChange(p, c3);
    ChangeData cd4 = newChange(p, c4);

    List<ChangeData> changes = ImmutableList.of(cd1, cd2, cd3, cd4);
    WalkSorter sorter = new WalkSorter(repoManager);

    assertSorted(
        sorter,
        changes,
        ImmutableList.of(
            patchSetData(cd4, c4),
            patchSetData(cd3, c3),
            patchSetData(cd2, c2),
            patchSetData(cd1, c1)));
  }

  @Test
  public void subsetOfSeriesOfChangesWithReverseTimestamps() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c0 = p.commit().tick(-1).create();
    RevCommit c1 = p.commit().tick(-1).parent(c0).create();
    RevCommit c2 = p.commit().tick(-1).parent(c1).create();
    RevCommit c3 = p.commit().tick(-1).parent(c2).create();
    RevCommit c4 = p.commit().tick(-1).parent(c3).create();

    RevWalk rw = p.getRevWalk();
    rw.parseCommit(c1);
    assertThat(rw.parseCommit(c2).getCommitTime()).isLessThan(c1.getCommitTime());
    assertThat(rw.parseCommit(c3).getCommitTime()).isLessThan(c2.getCommitTime());
    assertThat(rw.parseCommit(c4).getCommitTime()).isLessThan(c3.getCommitTime());

    ChangeData cd1 = newChange(p, c1);
    ChangeData cd2 = newChange(p, c2);
    ChangeData cd4 = newChange(p, c4);

    List<ChangeData> changes = ImmutableList.of(cd1, cd2, cd4);
    WalkSorter sorter = new WalkSorter(repoManager);
    List<PatchSetData> expected =
        ImmutableList.of(patchSetData(cd4, c4), patchSetData(cd2, c2), patchSetData(cd1, c1));

    for (List<ChangeData> list : permutations(changes)) {
      // Not inOrder(); since child of c2 is missing, partial topo sort isn't
      // guaranteed to work.
      assertThat(sorter.sort(list)).containsExactlyElementsIn(expected);
    }
  }

  @Test
  public void seriesOfChangesAtSameTimestampWithRootCommit() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c1 = p.commit().tick(0).create();
    RevCommit c2 = p.commit().tick(0).parent(c1).create();
    RevCommit c3 = p.commit().tick(0).parent(c2).create();
    RevCommit c4 = p.commit().tick(0).parent(c3).create();

    RevWalk rw = p.getRevWalk();
    rw.parseCommit(c1);
    assertThat(rw.parseCommit(c2).getCommitTime()).isEqualTo(c1.getCommitTime());
    assertThat(rw.parseCommit(c3).getCommitTime()).isEqualTo(c1.getCommitTime());
    assertThat(rw.parseCommit(c4).getCommitTime()).isEqualTo(c1.getCommitTime());

    ChangeData cd1 = newChange(p, c1);
    ChangeData cd2 = newChange(p, c2);
    ChangeData cd3 = newChange(p, c3);
    ChangeData cd4 = newChange(p, c4);

    List<ChangeData> changes = ImmutableList.of(cd1, cd2, cd3, cd4);
    WalkSorter sorter = new WalkSorter(repoManager);

    assertSorted(
        sorter,
        changes,
        ImmutableList.of(
            patchSetData(cd4, c4),
            patchSetData(cd3, c3),
            patchSetData(cd2, c2),
            patchSetData(cd1, c1)));
  }

  @Test
  public void projectsSortedByName() throws Exception {
    TestRepository<Repo> pa = newRepo("a");
    TestRepository<Repo> pb = newRepo("b");
    RevCommit c1 = pa.commit().create();
    RevCommit c2 = pb.commit().create();
    RevCommit c3 = pa.commit().parent(c1).create();
    RevCommit c4 = pb.commit().parent(c2).create();

    ChangeData cd1 = newChange(pa, c1);
    ChangeData cd2 = newChange(pb, c2);
    ChangeData cd3 = newChange(pa, c3);
    ChangeData cd4 = newChange(pb, c4);

    assertSorted(
        new WalkSorter(repoManager),
        ImmutableList.of(cd1, cd2, cd3, cd4),
        ImmutableList.of(
            patchSetData(cd3, c3),
            patchSetData(cd1, c1),
            patchSetData(cd4, c4),
            patchSetData(cd2, c2)));
  }

  @Test
  public void restrictToPatchSets() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c1_1 = p.commit().create();
    RevCommit c2_1 = p.commit().parent(c1_1).create();

    ChangeData cd1 = newChange(p, c1_1);
    ChangeData cd2 = newChange(p, c2_1);

    // Add new patch sets whose commits are in reverse order.
    RevCommit c2_2 = p.commit().create();
    RevCommit c1_2 = p.commit().parent(c2_2).create();

    addPatchSet(cd1, c1_2);
    addPatchSet(cd2, c2_2);

    List<ChangeData> changes = ImmutableList.of(cd1, cd2);
    WalkSorter sorter = new WalkSorter(repoManager);

    assertSorted(
        sorter, changes, ImmutableList.of(patchSetData(cd1, c1_2), patchSetData(cd2, c2_2)));

    // If we restrict to PS1 of each change, the sorter uses that commit.
    sorter.includePatchSets(
        ImmutableSet.of(new PatchSet.Id(cd1.getId(), 1), new PatchSet.Id(cd2.getId(), 1)));
    assertSorted(
        sorter, changes, ImmutableList.of(patchSetData(cd2, 1, c2_1), patchSetData(cd1, 1, c1_1)));
  }

  @Test
  public void restrictToPatchSetsOmittingWholeProject() throws Exception {
    TestRepository<Repo> pa = newRepo("a");
    TestRepository<Repo> pb = newRepo("b");
    RevCommit c1 = pa.commit().create();
    RevCommit c2 = pa.commit().create();

    ChangeData cd1 = newChange(pa, c1);
    ChangeData cd2 = newChange(pb, c2);

    List<ChangeData> changes = ImmutableList.of(cd1, cd2);
    WalkSorter sorter =
        new WalkSorter(repoManager)
            .includePatchSets(ImmutableSet.of(cd1.currentPatchSet().getId()));

    assertSorted(sorter, changes, ImmutableList.of(patchSetData(cd1, c1)));
  }

  @Test
  public void retainBody() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c = p.commit().message("message").create();
    ChangeData cd = newChange(p, c);

    List<ChangeData> changes = ImmutableList.of(cd);
    RevCommit actual =
        new WalkSorter(repoManager).setRetainBody(true).sort(changes).iterator().next().commit();
    assertThat(actual.getRawBuffer()).isNotNull();
    assertThat(actual.getShortMessage()).isEqualTo("message");

    actual =
        new WalkSorter(repoManager).setRetainBody(false).sort(changes).iterator().next().commit();
    assertThat(actual.getRawBuffer()).isNull();
  }

  @Test
  public void oneChange() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c = p.commit().create();
    ChangeData cd = newChange(p, c);

    List<ChangeData> changes = ImmutableList.of(cd);
    WalkSorter sorter = new WalkSorter(repoManager);

    assertSorted(sorter, changes, ImmutableList.of(patchSetData(cd, c)));
  }

  private ChangeData newChange(TestRepository<Repo> tr, ObjectId id) throws Exception {
    Project.NameKey project = tr.getRepository().getDescription().getProject();
    Change c = TestChanges.newChange(project, userId);
    ChangeData cd = ChangeData.createForTest(project, c.getId(), 1);
    cd.setChange(c);
    cd.currentPatchSet().setRevision(new RevId(id.name()));
    cd.setPatchSets(ImmutableList.of(cd.currentPatchSet()));
    return cd;
  }

  private PatchSet addPatchSet(ChangeData cd, ObjectId id) throws Exception {
    TestChanges.incrementPatchSet(cd.change());
    PatchSet ps = new PatchSet(cd.change().currentPatchSetId());
    ps.setRevision(new RevId(id.name()));
    List<PatchSet> patchSets = new ArrayList<>(cd.patchSets());
    patchSets.add(ps);
    cd.setPatchSets(patchSets);
    return ps;
  }

  private TestRepository<Repo> newRepo(String name) throws Exception {
    return new TestRepository<>(repoManager.createRepository(new Project.NameKey(name)));
  }

  private static PatchSetData patchSetData(ChangeData cd, RevCommit commit) throws Exception {
    return PatchSetData.create(cd, cd.currentPatchSet(), commit);
  }

  private static PatchSetData patchSetData(ChangeData cd, int psId, RevCommit commit)
      throws Exception {
    return PatchSetData.create(cd, cd.patchSet(new PatchSet.Id(cd.getId(), psId)), commit);
  }

  private static void assertSorted(
      WalkSorter sorter, List<ChangeData> changes, List<PatchSetData> expected) throws Exception {
    for (List<ChangeData> list : permutations(changes)) {
      assertThat(sorter.sort(list)).containsExactlyElementsIn(expected).inOrder();
    }
  }
}

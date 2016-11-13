// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.GetRelated.ChangeAndCommit;
import com.google.gerrit.server.change.GetRelated.RelatedInfo;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GetRelatedIT extends AbstractDaemonTest {
  private String systemTimeZone;

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Inject private ChangeEditUtil editUtil;

  @Inject private ChangeEditModifier editModifier;

  @Inject private BatchUpdate.Factory updateFactory;

  @Inject private ChangesCollection changes;

  @Test
  public void getRelatedNoResult() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    assertRelated(push.to("refs/for/master").getPatchSetId());
  }

  @Test
  public void getRelatedLinear() throws Exception {
    // 1,1---2,1
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps1_1)) {
      assertRelated(ps, changeAndCommit(ps2_1, c2_1, 1), changeAndCommit(ps1_1, c1_1, 1));
    }
  }

  @Test
  public void getRelatedLinearSeparatePushes() throws Exception {
    // 1,1---2,1
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();

    testRepo.reset(c1_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    String oldETag = changes.parse(ps1_1.getParentKey()).getETag();

    testRepo.reset(c2_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Push of change 2 should not affect groups (or anything else) of change 1.
    assertThat(changes.parse(ps1_1.getParentKey()).getETag()).isEqualTo(oldETag);

    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps1_1)) {
      assertRelated(ps, changeAndCommit(ps2_1, c2_1, 1), changeAndCommit(ps1_1, c1_1, 1));
    }
  }

  @Test
  public void getRelatedReorder() throws Exception {
    // 1,1---2,1
    //
    // 2,2---1,2

    // Create two commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Swap the order of commits and push again.
    testRepo.reset("HEAD~2");
    RevCommit c2_2 = testRepo.cherryPick(c2_1);
    RevCommit c1_2 = testRepo.cherryPick(c1_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_1);
    PatchSet.Id ps2_2 = getPatchSetId(c2_1);

    for (PatchSet.Id ps : ImmutableList.of(ps2_2, ps1_2)) {
      assertRelated(ps, changeAndCommit(ps1_2, c1_2, 2), changeAndCommit(ps2_2, c2_2, 2));
    }

    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps1_1)) {
      assertRelated(ps, changeAndCommit(ps2_1, c2_1, 2), changeAndCommit(ps1_1, c1_1, 2));
    }
  }

  @Test
  public void getRelatedAmendParentChange() throws Exception {
    // 1,1---2,1
    //
    // 1,2

    // Create two commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Amend parent change and push.
    testRepo.reset("HEAD~1");
    RevCommit c1_2 = amendBuilder().add("c.txt", "2").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_2);

    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps1_1)) {
      assertRelated(ps, changeAndCommit(ps2_1, c2_1, 1), changeAndCommit(ps1_1, c1_1, 2));
    }

    assertRelated(ps1_2, changeAndCommit(ps2_1, c2_1, 1), changeAndCommit(ps1_2, c1_2, 2));
  }

  @Test
  public void getRelatedReorderAndExtend() throws Exception {
    // 1,1---2,1
    //
    // 2,2---1,2---3,1

    // Create two commits and push.
    ObjectId initial = repo().exactRef("HEAD").getObjectId();
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Swap the order of commits, create a new commit on top, and push again.
    testRepo.reset(initial);
    RevCommit c2_2 = testRepo.cherryPick(c2_1);
    RevCommit c1_2 = testRepo.cherryPick(c1_1);
    RevCommit c3_1 = commitBuilder().add("c.txt", "3").message("subject: 3").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_1);
    PatchSet.Id ps2_2 = getPatchSetId(c2_1);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    for (PatchSet.Id ps : ImmutableList.of(ps3_1, ps2_2, ps1_2)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_1, c3_1, 1),
          changeAndCommit(ps1_2, c1_2, 2),
          changeAndCommit(ps2_2, c2_2, 2));
    }

    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps1_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_1, c3_1, 1),
          changeAndCommit(ps2_1, c2_1, 2),
          changeAndCommit(ps1_1, c1_1, 2));
    }
  }

  @Test
  public void getRelatedReworkSeries() throws Exception {
    // 1,1---2,1---3,1
    //
    // 1,2---2,2---3,2

    // Create three commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "1").message("subject: 2").create();
    RevCommit c3_1 = commitBuilder().add("b.txt", "1").message("subject: 3").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    // Amend all changes change and push.
    testRepo.reset(c1_1);
    RevCommit c1_2 = amendBuilder().add("a.txt", "2").create();
    RevCommit c2_2 =
        commitBuilder().add("b.txt", "2").message(parseBody(c2_1).getFullMessage()).create();
    RevCommit c3_2 =
        commitBuilder().add("b.txt", "3").message(parseBody(c3_1).getFullMessage()).create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_2);
    PatchSet.Id ps2_2 = getPatchSetId(c2_2);
    PatchSet.Id ps3_2 = getPatchSetId(c3_2);

    for (PatchSet.Id ps : ImmutableList.of(ps1_1, ps2_1, ps3_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_1, c3_1, 2),
          changeAndCommit(ps2_1, c2_1, 2),
          changeAndCommit(ps1_1, c1_1, 2));
    }

    for (PatchSet.Id ps : ImmutableList.of(ps1_2, ps2_2, ps3_2)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_2, c3_2, 2),
          changeAndCommit(ps2_2, c2_2, 2),
          changeAndCommit(ps1_2, c1_2, 2));
    }
  }

  @Test
  public void getRelatedReworkThenExtendInTheMiddleOfSeries() throws Exception {
    // 1,1---2,1---3,1
    //
    // 1,2---2,2---3,2
    //   \---4,1

    // Create three commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "1").message("subject: 2").create();
    RevCommit c3_1 = commitBuilder().add("b.txt", "1").message("subject: 3").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    // Amend all changes change and push.
    testRepo.reset(c1_1);
    RevCommit c1_2 = amendBuilder().add("a.txt", "2").create();
    RevCommit c2_2 =
        commitBuilder().add("b.txt", "2").message(parseBody(c2_1).getFullMessage()).create();
    RevCommit c3_2 =
        commitBuilder().add("b.txt", "3").message(parseBody(c3_1).getFullMessage()).create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_2);
    PatchSet.Id ps2_2 = getPatchSetId(c2_2);
    PatchSet.Id ps3_2 = getPatchSetId(c3_2);

    // Add one more commit 4,1 based on 1,2.
    testRepo.reset(c1_2);
    RevCommit c4_1 = commitBuilder().add("d.txt", "4").message("subject: 4").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps4_1 = getPatchSetId(c4_1);

    // 1,1 is related indirectly to 4,1.
    assertRelated(
        ps1_1,
        changeAndCommit(ps4_1, c4_1, 1),
        changeAndCommit(ps3_1, c3_1, 2),
        changeAndCommit(ps2_1, c2_1, 2),
        changeAndCommit(ps1_1, c1_1, 2));

    // 2,1 and 3,1 don't include 4,1 since we don't walk forward after walking
    // backward.
    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps3_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_1, c3_1, 2),
          changeAndCommit(ps2_1, c2_1, 2),
          changeAndCommit(ps1_1, c1_1, 2));
    }

    // 1,2 is related directly to 4,1, and the 2-3 parallel branch stays intact.
    assertRelated(
        ps1_2,
        changeAndCommit(ps4_1, c4_1, 1),
        changeAndCommit(ps3_2, c3_2, 2),
        changeAndCommit(ps2_2, c2_2, 2),
        changeAndCommit(ps1_2, c1_2, 2));

    // 4,1 is only related to 1,2, since we don't walk forward after walking
    // backward.
    assertRelated(ps4_1, changeAndCommit(ps4_1, c4_1, 1), changeAndCommit(ps1_2, c1_2, 2));

    // 2,2 and 3,2 don't include 4,1 since we don't walk forward after walking
    // backward.
    for (PatchSet.Id ps : ImmutableList.of(ps2_2, ps3_2)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_2, c3_2, 2),
          changeAndCommit(ps2_2, c2_2, 2),
          changeAndCommit(ps1_2, c1_2, 2));
    }
  }

  @Test
  public void getRelatedCrissCrossDependency() throws Exception {
    // 1,1---2,1---3,2
    //
    // 1,2---2,2---3,1

    // Create two commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Amend both changes change and push.
    testRepo.reset(c1_1);
    RevCommit c1_2 = amendBuilder().add("a.txt", "2").create();
    RevCommit c2_2 =
        commitBuilder().add("b.txt", "2").message(parseBody(c2_1).getFullMessage()).create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_2);
    PatchSet.Id ps2_2 = getPatchSetId(c2_2);

    // PS 3,1 depends on 2,2.
    RevCommit c3_1 = commitBuilder().add("c.txt", "1").message("subject: 3").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    // PS 3,2 depends on 2,1.
    testRepo.reset(c2_1);
    RevCommit c3_2 =
        commitBuilder().add("c.txt", "2").message(parseBody(c3_1).getFullMessage()).create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps3_2 = getPatchSetId(c3_2);

    for (PatchSet.Id ps : ImmutableList.of(ps1_1, ps2_1, ps3_2)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_2, c3_2, 2),
          changeAndCommit(ps2_1, c2_1, 2),
          changeAndCommit(ps1_1, c1_1, 2));
    }

    for (PatchSet.Id ps : ImmutableList.of(ps1_2, ps2_2, ps3_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_1, c3_1, 2),
          changeAndCommit(ps2_2, c2_2, 2),
          changeAndCommit(ps1_2, c1_2, 2));
    }
  }

  @Test
  public void getRelatedParallelDescendentBranches() throws Exception {
    // 1,1---2,1---3,1
    //   \---4,1---5,1
    //    \--6,1---7,1

    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    RevCommit c3_1 = commitBuilder().add("c.txt", "3").message("subject: 3").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    testRepo.reset(c1_1);
    RevCommit c4_1 = commitBuilder().add("d.txt", "4").message("subject: 4").create();
    RevCommit c5_1 = commitBuilder().add("e.txt", "5").message("subject: 5").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps4_1 = getPatchSetId(c4_1);
    PatchSet.Id ps5_1 = getPatchSetId(c5_1);

    testRepo.reset(c1_1);
    RevCommit c6_1 = commitBuilder().add("f.txt", "6").message("subject: 6").create();
    RevCommit c7_1 = commitBuilder().add("g.txt", "7").message("subject: 7").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps6_1 = getPatchSetId(c6_1);
    PatchSet.Id ps7_1 = getPatchSetId(c7_1);

    // All changes are related to 1,1, keeping each of the parallel branches
    // intact.
    assertRelated(
        ps1_1,
        changeAndCommit(ps7_1, c7_1, 1),
        changeAndCommit(ps6_1, c6_1, 1),
        changeAndCommit(ps5_1, c5_1, 1),
        changeAndCommit(ps4_1, c4_1, 1),
        changeAndCommit(ps3_1, c3_1, 1),
        changeAndCommit(ps2_1, c2_1, 1),
        changeAndCommit(ps1_1, c1_1, 1));

    // The 2-3 branch is only related back to 1, not the other branches.
    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps3_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_1, c3_1, 1),
          changeAndCommit(ps2_1, c2_1, 1),
          changeAndCommit(ps1_1, c1_1, 1));
    }

    // The 4-5 branch is only related back to 1, not the other branches.
    for (PatchSet.Id ps : ImmutableList.of(ps4_1, ps5_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps5_1, c5_1, 1),
          changeAndCommit(ps4_1, c4_1, 1),
          changeAndCommit(ps1_1, c1_1, 1));
    }

    // The 6-7 branch is only related back to 1, not the other branches.
    for (PatchSet.Id ps : ImmutableList.of(ps6_1, ps7_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps7_1, c7_1, 1),
          changeAndCommit(ps6_1, c6_1, 1),
          changeAndCommit(ps1_1, c1_1, 1));
    }
  }

  @Test
  public void getRelatedEdit() throws Exception {
    // 1,1---2,1---3,1
    //   \---2,E---/

    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    RevCommit c3_1 = commitBuilder().add("c.txt", "3").message("subject: 3").create();
    pushHead(testRepo, "refs/for/master", false);

    Change ch2 = getChange(c2_1).change();
    editModifier.createEdit(ch2, getPatchSet(ch2.currentPatchSetId()));
    editModifier.modifyFile(
        editUtil.byChange(ch2).get(), "a.txt", RawInputUtil.create(new byte[] {'a'}));
    ObjectId editRev = ObjectId.fromString(editUtil.byChange(ch2).get().getRevision().get());

    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);
    PatchSet.Id ps2_edit = new PatchSet.Id(ch2.getId(), 0);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    for (PatchSet.Id ps : ImmutableList.of(ps1_1, ps2_1, ps3_1)) {
      assertRelated(
          ps,
          changeAndCommit(ps3_1, c3_1, 1),
          changeAndCommit(ps2_1, c2_1, 1),
          changeAndCommit(ps1_1, c1_1, 1));
    }

    assertRelated(
        ps2_edit,
        changeAndCommit(ps3_1, c3_1, 1),
        changeAndCommit(new PatchSet.Id(ch2.getId(), 0), editRev, 1),
        changeAndCommit(ps1_1, c1_1, 1));
  }

  @Test
  public void pushNewPatchSetWhenParentHasNullGroup() throws Exception {
    // 1,1---2,1
    //   \---2,2

    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id psId1_1 = getPatchSetId(c1_1);
    PatchSet.Id psId2_1 = getPatchSetId(c2_1);

    for (PatchSet.Id psId : ImmutableList.of(psId1_1, psId2_1)) {
      assertRelated(psId, changeAndCommit(psId2_1, c2_1, 1), changeAndCommit(psId1_1, c1_1, 1));
    }

    // Pretend PS1,1 was pushed before the groups field was added.
    clearGroups(psId1_1);
    indexer.index(changeDataFactory.create(db, project, psId1_1.getParentKey()));

    // PS1,1 has no groups, so disappeared from related changes.
    assertRelated(psId2_1);

    RevCommit c2_2 = testRepo.amend(c2_1).add("c.txt", "2").create();
    testRepo.reset(c2_2);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id psId2_2 = getPatchSetId(c2_2);

    // Push updated the group for PS1,1, so it shows up in related changes even
    // though a new patch set was not pushed.
    assertRelated(psId2_2, changeAndCommit(psId2_2, c2_2, 2), changeAndCommit(psId1_1, c1_1, 1));
  }

  @Test
  public void getRelatedForStaleChange() throws Exception {
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();

    RevCommit c2_1 = commitBuilder().add("b.txt", "1").message("subject: 1").create();
    pushHead(testRepo, "refs/for/master", false);

    RevCommit c2_2 = testRepo.amend(c2_1).add("b.txt", "2").create();
    testRepo.reset(c2_2);

    disableChangeIndexWrites();
    try {
      pushHead(testRepo, "refs/for/master", false);
    } finally {
      enableChangeIndexWrites();
    }

    PatchSet.Id psId1_1 = getPatchSetId(c1_1);
    PatchSet.Id psId2_1 = getPatchSetId(c2_1);
    PatchSet.Id psId2_2 = new PatchSet.Id(psId2_1.changeId, psId2_1.get() + 1);

    assertRelated(psId2_2, changeAndCommit(psId2_2, c2_2, 2), changeAndCommit(psId1_1, c1_1, 1));
  }

  private List<ChangeAndCommit> getRelated(PatchSet.Id ps) throws Exception {
    return getRelated(ps.getParentKey(), ps.get());
  }

  private List<ChangeAndCommit> getRelated(Change.Id changeId, int ps) throws Exception {
    String url = String.format("/changes/%d/revisions/%d/related", changeId.get(), ps);
    RestResponse r = adminRestSession.get(url);
    r.assertOK();
    return newGson().fromJson(r.getReader(), RelatedInfo.class).changes;
  }

  private RevCommit parseBody(RevCommit c) throws Exception {
    testRepo.getRevWalk().parseBody(c);
    return c;
  }

  private PatchSet.Id getPatchSetId(ObjectId c) throws Exception {
    return getChange(c).change().currentPatchSetId();
  }

  private ChangeData getChange(ObjectId c) throws Exception {
    return Iterables.getOnlyElement(queryProvider.get().byCommit(c));
  }

  private static ChangeAndCommit changeAndCommit(
      PatchSet.Id psId, ObjectId commitId, int currentRevisionNum) {
    ChangeAndCommit result = new ChangeAndCommit();
    result._changeNumber = psId.getParentKey().get();
    result.commit = new CommitInfo();
    result.commit.commit = commitId.name();
    result._revisionNumber = psId.get();
    result._currentRevisionNumber = currentRevisionNum;
    result.status = "NEW";
    return result;
  }

  private void clearGroups(final PatchSet.Id psId) throws Exception {
    try (BatchUpdate bu = updateFactory.create(db, project, user(user), TimeUtil.nowTs())) {
      bu.addOp(
          psId.getParentKey(),
          new BatchUpdate.Op() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws OrmException {
              PatchSet ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
              psUtil.setGroups(ctx.getDb(), ctx.getUpdate(psId), ps, ImmutableList.<String>of());
              ctx.bumpLastUpdatedOn(false);
              return true;
            }
          });
      bu.execute();
    }
  }

  private void assertRelated(PatchSet.Id psId, ChangeAndCommit... expected) throws Exception {
    List<ChangeAndCommit> actual = getRelated(psId);
    assertThat(actual).named("related to " + psId).hasSize(expected.length);
    for (int i = 0; i < actual.size(); i++) {
      String name = "index " + i + " related to " + psId;
      ChangeAndCommit a = actual.get(i);
      ChangeAndCommit e = expected[i];
      assertThat(a._changeNumber).named("change ID of " + name).isEqualTo(e._changeNumber);
      // Don't bother checking changeId; assume _changeNumber is sufficient.
      assertThat(a._revisionNumber).named("revision of " + name).isEqualTo(e._revisionNumber);
      assertThat(a.commit.commit).named("commit of " + name).isEqualTo(e.commit.commit);
      assertThat(a._currentRevisionNumber)
          .named("current revision of " + name)
          .isEqualTo(e._currentRevisionNumber);
    }
  }
}

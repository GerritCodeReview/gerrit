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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.extensions.common.testing.EditInfoSubject.assertThat;
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.UseTimezone;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.IndexOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.GetRelatedOption;
import com.google.gerrit.extensions.api.changes.RelatedChangeAndCommitInfo;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.server.change.GetRelatedChangesUtil;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
@UseClockStep
@UseTimezone(timezone = "US/Eastern")
public class GetRelatedIT extends AbstractDaemonTest {
  private static final int MAX_TERMS = 10;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("index", null, "maxTerms", MAX_TERMS);
    return cfg;
  }

  @Inject private AccountOperations accountOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private IndexOperations.Change changeIndexOperations;

  @Inject private IndexConfig indexConfig;
  @Inject private ChangesCollection changes;

  @Test
  public void getRelatedNoResult() throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
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
    String oldETag = changes.parse(ps1_1.changeId()).getETag();

    testRepo.reset(c2_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Push of change 2 should not affect groups (or anything else) of change 1.
    assertThat(changes.parse(ps1_1.changeId()).getETag()).isEqualTo(oldETag);

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
    String changeId2 = ch2.getKey().get();
    gApi.changes().id(changeId2).edit().create();
    gApi.changes().id(changeId2).edit().modifyFile("a.txt", RawInputUtil.create(new byte[] {'a'}));
    Optional<EditInfo> edit = getEdit(changeId2);
    assertThat(edit).isPresent();
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    ObjectId editRev = ObjectId.fromString(edit.get().commit.commit);

    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);
    PatchSet.Id ps2_edit = PatchSet.id(ch2.getId(), 0);
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
        changeAndCommit(PatchSet.id(ch2.getId(), 0), editRev, 1),
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
    indexer.index(changeDataFactory.create(project, psId1_1.changeId()));

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
  @GerritConfig(name = "index.autoReindexIfStale", value = "false")
  public void getRelatedForStaleChange() throws Exception {
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();

    RevCommit c2_1 = commitBuilder().add("b.txt", "1").message("subject: 1").create();
    pushHead(testRepo, "refs/for/master", false);

    RevCommit c2_2 = testRepo.amend(c2_1).add("b.txt", "2").create();
    testRepo.reset(c2_2);

    try (AutoCloseable ignored = changeIndexOperations.disableWrites()) {
      pushHead(testRepo, "refs/for/master", false);
    }

    PatchSet.Id psId1_1 = getPatchSetId(c1_1);
    PatchSet.Id psId2_1 = getPatchSetId(c2_1);
    PatchSet.Id psId2_2 = PatchSet.id(psId2_1.changeId(), psId2_1.get() + 1);

    assertRelated(psId2_2, changeAndCommit(psId2_2, c2_2, 2), changeAndCommit(psId1_1, c1_1, 1));
  }

  @Test
  public void getRelatedManyGroups() throws Exception {
    RevCommit last = null;
    int n = 2 * MAX_TERMS;
    assertThat(n).isGreaterThan(indexConfig.maxTerms());
    for (int i = 1; i <= n; i++) {
      TestRepository<?>.CommitBuilder cb = last != null ? amendBuilder() : commitBuilder();
      last = cb.add("a.txt", Integer.toString(i)).message("subject: " + i).create();
      testRepo.reset(last);
      assertPushOk(pushHead(testRepo, "refs/for/master", false), "refs/for/master");
    }

    ChangeData cd = getChange(last);
    assertThat(cd.patchSets()).hasSize(n);
    assertThat(GetRelatedChangesUtil.getAllGroups(cd.notes().getPatchSets().values())).hasSize(n);

    assertRelated(cd.change().currentPatchSetId());
  }

  @Test
  public void getRelatedManyChanges() throws Exception {
    List<ObjectId> commitIds = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      commitIds.add(commitBuilder().add(i + ".txt", "i").message("subject: " + i).create().copy());
    }
    pushHead(testRepo, "refs/for/master", false);

    List<RelatedChangeAndCommitInfo> expected = new ArrayList<>(commitIds.size());
    for (ObjectId commitId : commitIds) {
      expected.add(changeAndCommit(getPatchSetId(commitId), commitId, 1));
    }
    Collections.reverse(expected);

    PatchSet.Id lastPsId = getPatchSetId(Iterables.getLast(commitIds));
    assertRelated(lastPsId, expected);

    Account.Id accountId = accountOperations.newAccount().create();
    AccountGroup.UUID groupUuid = groupOperations.newGroup().addMember(accountId).create();
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.QUERY_LIMIT).group(groupUuid).range(0, 2))
        .update();
    requestScopeOperations.setApiUser(accountId);

    assertRelated(lastPsId, expected);
  }

  @Test
  public void stateOfRelatedChangesMatchesDocumentedValues() throws Exception {
    // Set up three related changes, one new, the other abandoned, and the third merged.
    RevCommit commit1 =
        commitBuilder().add("a.txt", "File content 1").message("Subject 1").create();
    RevCommit commit2 =
        commitBuilder().add("b.txt", "File content 2").message("Subject 2").create();
    RevCommit commit3 =
        commitBuilder().add("c.txt", "File content 3").message("Subject 3").create();
    pushHead(testRepo, "refs/for/master", false);
    Change change1 = getChange(commit1).change();
    Change change2 = getChange(commit2).change();
    Change change3 = getChange(commit3).change();
    gApi.changes().id(change1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(change1.getChangeId()).current().submit();
    gApi.changes().id(change2.getChangeId()).abandon();

    List<RelatedChangeAndCommitInfo> relatedChanges =
        gApi.changes().id(change3.getChangeId()).current().related().changes;

    // Ensure that our REST API returns the states exactly as documented (and required by the
    // frontend).
    assertThat(relatedChanges)
        .comparingElementsUsing(getRelatedChangeToStatusCorrespondence())
        .containsExactly("NEW", "ABANDONED", "MERGED");
  }

  @Test
  public void submittable() throws Exception {
    RevCommit c1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    RevCommit c3 = commitBuilder().add("c.txt", "3").message("subject: 3").create();
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1 = getPatchSetId(c1);
    PatchSet.Id ps2 = getPatchSetId(c2);
    PatchSet.Id ps3 = getPatchSetId(c3);

    for (RevCommit c : ImmutableList.of(c1, c3)) {
      gApi.changes()
          .id(getChange(c).change().getChangeId())
          .current()
          .review(ReviewInput.approve());
    }

    for (PatchSet.Id ps : ImmutableList.of(ps3, ps2, ps1)) {
      assertRelated(
          ps,
          Arrays.asList(
              changeAndCommit(ps3, c3, 1, true),
              changeAndCommit(ps2, c2, 1, false),
              changeAndCommit(ps1, c1, 1, true)),
          GetRelatedOption.SUBMITTABLE);
    }
  }

  private static Correspondence<RelatedChangeAndCommitInfo, String>
      getRelatedChangeToStatusCorrespondence() {
    return Correspondence.transforming(
        relatedChangeAndCommitInfo -> relatedChangeAndCommitInfo.status, "has status");
  }

  private RevCommit parseBody(RevCommit c) throws Exception {
    testRepo.getRevWalk().parseBody(c);
    return c;
  }

  private PatchSet.Id getPatchSetId(ObjectId c) {
    return getChange(c).change().currentPatchSetId();
  }

  private ChangeData getChange(ObjectId c) {
    return Iterables.getOnlyElement(queryProvider.get().byCommit(c));
  }

  private RelatedChangeAndCommitInfo changeAndCommit(
      PatchSet.Id psId, ObjectId commitId, int currentRevisionNum) {
    return changeAndCommit(psId, commitId, currentRevisionNum, null);
  }

  private RelatedChangeAndCommitInfo changeAndCommit(
      PatchSet.Id psId, ObjectId commitId, int currentRevisionNum, @Nullable Boolean submittable) {
    RelatedChangeAndCommitInfo result = new RelatedChangeAndCommitInfo();
    result.project = project.get();
    result._changeNumber = psId.changeId().get();
    result.commit = new CommitInfo();
    result.commit.commit = commitId.name();
    result._revisionNumber = psId.get();
    result._currentRevisionNumber = currentRevisionNum;
    result.status = "NEW";
    result.submittable = submittable;
    return result;
  }

  private void clearGroups(PatchSet.Id psId) throws Exception {
    try (RefUpdateContext ctx = openTestRefUpdateContext()) {
      try (BatchUpdate bu = batchUpdateFactory.create(project, user(user), TimeUtil.now())) {
        bu.addOp(
            psId.changeId(),
            new BatchUpdateOp() {
              @Override
              public boolean updateChange(ChangeContext ctx) {
                ctx.getUpdate(psId).setGroups(ImmutableList.of());
                return true;
              }
            });
        bu.execute();
      }
    }
  }

  private void assertRelated(PatchSet.Id psId, RelatedChangeAndCommitInfo... expected)
      throws Exception {
    assertRelated(psId, Arrays.asList(expected));
  }

  private void assertRelated(
      PatchSet.Id psId, List<RelatedChangeAndCommitInfo> expected, GetRelatedOption... options)
      throws Exception {
    List<RelatedChangeAndCommitInfo> actual =
        gApi.changes()
            .id(psId.changeId().get())
            .revision(psId.get())
            .related(
                options.length > 0
                    ? EnumSet.copyOf(Arrays.asList(options))
                    : EnumSet.noneOf(GetRelatedOption.class))
            .changes;
    assertWithMessage("related to " + psId).that(actual).hasSize(expected.size());
    for (int i = 0; i < actual.size(); i++) {
      String name = "index " + i + " related to " + psId;
      RelatedChangeAndCommitInfo a = actual.get(i);
      RelatedChangeAndCommitInfo e = expected.get(i);
      assertWithMessage("project of " + name).that(a.project).isEqualTo(e.project);
      assertWithMessage("change ID of " + name).that(a._changeNumber).isEqualTo(e._changeNumber);
      // Don't bother checking changeId; assume _changeNumber is sufficient.
      assertWithMessage("revision of " + name).that(a._revisionNumber).isEqualTo(e._revisionNumber);
      assertWithMessage("commit of " + name).that(a.commit.commit).isEqualTo(e.commit.commit);
      assertWithMessage("current revision of " + name)
          .that(a._currentRevisionNumber)
          .isEqualTo(e._currentRevisionNumber);
      assertThat(a.status).isEqualTo(e.status);
      assertThat(a.submittable).isEqualTo(e.submittable);
    }
  }
}

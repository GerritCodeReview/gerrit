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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ChangeKind.MERGE_FIRST_PARENT_UPDATE;
import static com.google.gerrit.extensions.client.ChangeKind.NO_CHANGE;
import static com.google.gerrit.extensions.client.ChangeKind.NO_CODE_CHANGE;
import static com.google.gerrit.extensions.client.ChangeKind.REWORK;
import static com.google.gerrit.extensions.client.ChangeKind.TRIVIAL_REBASE;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static java.util.Comparator.comparing;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.change.ChangeKindCreator;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class StickyApprovalsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ChangeOperations changeOperations;
  @Inject private ChangeKindCreator changeKindCreator;

  @Inject
  @Named("change_kind")
  private Cache<ChangeKindCacheImpl.Key, ChangeKind> changeKindCache;

  @Before
  public void setup() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      // Overwrite "Code-Review" label that is inherited from All-Projects.
      // This way changes to the "Code Review" label don't affect other tests.
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer that you didn't submit this"),
              value(-2, "Do not submit"));
      codeReview.setCopyAllScoresIfNoChange(false);
      u.getConfig().upsertLabelType(codeReview.build());

      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      verified.setCopyAllScoresIfNoChange(false);
      u.getConfig().upsertLabelType(verified.build());

      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(
            allowLabel(TestLabels.verified().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();
  }

  @Test
  public void notSticky() throws Exception {
    assertNotSticky(
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE));
  }

  @Test
  public void stickyOnAnyScore() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, 1, -1);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 0, changeKind);
      assertVotes(c, user, 1, 0, changeKind);
    }
  }

  @Test
  public void stickyWhenCopyConditionIsTrue() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("is:ANY"));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, 1, -1);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 0, changeKind);
      assertVotes(c, user, 1, 0, changeKind);
    }
  }

  @Test
  public void stickyEvenWhenUserCantSeeUploaderInGroup() throws Exception {
    // user can't see admin group
    try (ProjectConfigUpdate u = updateProject(project)) {
      String administratorsUUID = gApi.groups().query("name:Administrators").get().get(0).id;
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyCondition("approverin:" + administratorsUUID));
      u.save();
    }

    String changeId = createChange().getChangeId();
    approve(changeId);
    amendChange(changeId);
    vote(user, changeId, 1, -1); // Invalidate cache
    requestScopeOperations.setApiUser(user.id());
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, 1, -1);
  }

  @Test
  public void stickyOnMinScore() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyMinScore(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, -1, 1);
      vote(user, changeId, -2, -1);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, -2, 0, changeKind);
    }
  }

  @Test
  public void stickyWhenEitherBooleanConfigsOrCopyConditionAreTrue() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyCondition("is:MAX").setCopyMinScore(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, -2, -1);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 0, changeKind);
      assertVotes(c, user, -2, 0, changeKind);
    }
  }

  @Test
  public void stickyOnMaxScore() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyMaxScore(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, 1, -1);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
    }
  }

  @Test
  public void stickyOnCopyValues() throws Exception {
    TestAccount user2 = accountCreator.user2();

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyValues(ImmutableList.of((short) -1, (short) 1)));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, -1, 1);
      vote(user, changeId, -2, -1);
      vote(user2, changeId, 1, -1);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, -1, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
      assertVotes(c, user2, 1, 0, changeKind);
    }
  }

  @Test
  public void stickyOnTrivialRebase() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAllScoresOnTrivialRebase(true));
      u.save();
    }

    String changeId = changeKindCreator.createChange(TRIVIAL_REBASE, testRepo, admin);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    changeKindCreator.updateChange(changeId, NO_CHANGE, testRepo, admin, project);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, NO_CHANGE);
    assertVotes(c, user, -2, 0, NO_CHANGE);

    changeKindCreator.updateChange(changeId, TRIVIAL_REBASE, testRepo, admin, project);
    c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, TRIVIAL_REBASE);
    assertVotes(c, user, -2, 0, TRIVIAL_REBASE);

    assertNotSticky(EnumSet.of(REWORK, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE));

    // check that votes are sticky when trivial rebase is done by cherry-pick
    testRepo.reset(projectOperations.project(project).getHead("master"));
    changeId = createChange().getChangeId();
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    String cherryPickChangeId =
        changeKindCreator.cherryPick(changeId, TRIVIAL_REBASE, testRepo, admin, project);
    c = detailedChange(cherryPickChangeId);
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, -2, 0);

    // check that votes are not sticky when rework is done by cherry-pick
    testRepo.reset(projectOperations.project(project).getHead("master"));
    changeId = createChange().getChangeId();
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    cherryPickChangeId = changeKindCreator.cherryPick(changeId, REWORK, testRepo, admin, project);
    c = detailedChange(cherryPickChangeId);
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void stickyOnNoCodeChange() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.VERIFIED, b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    String changeId = changeKindCreator.createChange(NO_CODE_CHANGE, testRepo, admin);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    changeKindCreator.updateChange(changeId, NO_CHANGE, testRepo, admin, project);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 0, 1, NO_CHANGE);
    assertVotes(c, user, 0, -1, NO_CHANGE);

    changeKindCreator.updateChange(changeId, NO_CODE_CHANGE, testRepo, admin, project);
    c = detailedChange(changeId);
    assertVotes(c, admin, 0, 1, NO_CODE_CHANGE);
    assertVotes(c, user, 0, -1, NO_CODE_CHANGE);

    assertNotSticky(EnumSet.of(REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE));
  }

  @Test
  public void stickyOnMergeFirstParentUpdate() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresOnMergeFirstParentUpdate(true));
      u.save();
    }

    String changeId = changeKindCreator.createChange(MERGE_FIRST_PARENT_UPDATE, testRepo, admin);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    changeKindCreator.updateChange(changeId, NO_CHANGE, testRepo, admin, project);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, NO_CHANGE);
    assertVotes(c, user, -2, 0, NO_CHANGE);

    changeKindCreator.updateChange(changeId, MERGE_FIRST_PARENT_UPDATE, testRepo, admin, project);
    c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, MERGE_FIRST_PARENT_UPDATE);
    assertVotes(c, user, -2, 0, MERGE_FIRST_PARENT_UPDATE);

    assertNotSticky(EnumSet.of(REWORK, NO_CODE_CHANGE, TRIVIAL_REBASE));
  }

  @Test
  public void notStickyWithCopyOnNoChangeWhenSecondParentIsUpdated() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfNoChange(true));
      u.save();
    }

    String changeId = changeKindCreator.createChangeForMergeCommit(testRepo, admin);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    changeKindCreator.updateSecondParent(changeId, testRepo, admin);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 0, 0, null);
    assertVotes(c, user, 0, 0, null);
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsAdded_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsAdded();
  }

  @Test
  public void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsAdded_withCopyCondition()
      throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsAdded();
  }

  private void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsAdded()
      throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("new file")
        .content("new content")
        .create();
    ChangeInfo c = detailedChange(changeId.toString());

    // no votes are copied since the list of files changed.
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileAlreadyExists_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileAlreadyExists();
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileAlreadyExists_withCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }

    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileAlreadyExists();
  }

  private void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileAlreadyExists()
      throws Exception {
    // create "existing file" and submit it.
    String existingFile = "existing file";
    Change.Id prep =
        changeOperations
            .newChange()
            .project(project)
            .file(existingFile)
            .content("content")
            .create();
    vote(admin, prep.toString(), 2, 1);
    gApi.changes().id(prep.get()).current().submit();

    Change.Id changeId = changeOperations.newChange().project(project).create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file(existingFile)
        .content("new content")
        .create();
    ChangeInfo c = detailedChange(changeId.toString());

    // no votes are copied since the list of files changed ("existing file" was added to the
    // change).
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsDeleted_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsDeleted();
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsDeleted_withCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsDeleted();
  }

  private void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsDeleted()
      throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations.change(changeId).newPatchset().file("file").delete().create();
    ChangeInfo c = detailedChange(changeId.toString());

    // no votes are copied since the list of files changed.
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void
      stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModified_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModified();
  }

  @Test
  public void
      stickyWithCopyAllScoreIfListOfFilesDidNotChangeWhenFileIsModifiedDueToRebase_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Modify f.txt in change 1. Approve and submit the first change
    gApi.changes().id(r.getChangeId()).edit().modifyFile("f.txt", RawInputUtil.create("content"));
    gApi.changes().id(r.getChangeId()).edit().publish();
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    revision.review(ReviewInput.approve().label(LabelId.VERIFIED, 1));
    revision.submit();

    // Add an approval whose score should be copied on change 2.
    gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.recommend());

    // Rebase the second change. The rebase adds f1.txt.
    gApi.changes().id(r2.getChangeId()).rebase();

    // The code-review approval is copied for the second change between PS1 and PS2 since the only
    // modified file is due to rebase.
    List<PatchSetApproval> patchSetApprovals =
        r2.getChange().notes().getApprovalsWithCopied().values().stream()
            .sorted(comparing(a -> a.patchSetId().get()))
            .collect(toImmutableList());
    PatchSetApproval nonCopied = patchSetApprovals.get(0);
    PatchSetApproval copied = patchSetApprovals.get(1);
    assertCopied(nonCopied, /* psId= */ 1, LabelId.CODE_REVIEW, (short) 1, false);
    assertCopied(copied, /* psId= */ 2, LabelId.CODE_REVIEW, (short) 1, true);
  }

  @Test
  public void stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModified_withCopyCondition()
      throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModified();
  }

  private void stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModified()
      throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations.change(changeId).newPatchset().file("file").content("new content").create();
    ChangeInfo c = detailedChange(changeId.toString());

    // only code review votes are copied since copyAllScoresIfListOfFilesDidNotChange is
    // configured for that label, and list of files didn't change.
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, -2, 0);
  }

  @TestProjectInput(createEmptyCommit = false)
  public void
      stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedAsInitialCommit_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedAsInitialCommit();
  }

  @TestProjectInput(createEmptyCommit = false)
  public void
      stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedAsInitialCommit_withCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedAsInitialCommit();
  }

  private void stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedAsInitialCommit()
      throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations.change(changeId).newPatchset().file("file").content("new content").create();
    ChangeInfo c = detailedChange(changeId.toString());

    // only code review votes are copied since copyAllScoresIfListOfFilesDidNotChange is
    // configured for that label, and list of files didn't change.
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, -2, 0);
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedOnEarlierPatchset_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedOnEarlierPatchset();
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedOnEarlierPatchset_withCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedOnEarlierPatchset();
  }

  private void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedOnEarlierPatchset()
          throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations.change(changeId).newPatchset().file("new file").content("content").create();
    changeOperations
        .change(changeId)
        .newPatchset()
        .file("new file")
        .content("new content")
        .create();
    ChangeInfo c = detailedChange(changeId.toString());

    // Don't copy over votes since ps1->ps2 should copy over, but ps2->ps3 should not.
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsRenamed_withoutCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsRenamed();
  }

  @Test
  public void
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsRenamed_withCopyCondition()
          throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsRenamed();
  }

  private void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsRenamed()
      throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations.change(changeId).newPatchset().file("file").renameTo("new_file").create();
    ChangeInfo c = detailedChange(changeId.toString());

    // no votes are copied since the list of files changed (rename).
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void removedVotesNotSticky() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAllScoresOnTrivialRebase(true));
      u.getConfig().updateLabelType(LabelId.VERIFIED, b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    for (ChangeKind changeKind :
        EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE, MERGE_FIRST_PARENT_UPDATE, NO_CHANGE)) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, 2, 1);
      vote(user, changeId, -2, -1);

      // Remove votes by re-voting with 0
      vote(admin, changeId, 0, 0);
      vote(user, changeId, 0, 0);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, null);
      assertVotes(c, user, 0, 0, null);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
    }
  }

  @Test
  public void stickyAcrossMultiplePatchSets() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyMaxScore(true));
      u.getConfig().updateLabelType(LabelId.VERIFIED, b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    String changeId = changeKindCreator.createChange(REWORK, testRepo, admin);
    vote(admin, changeId, 2, 1);

    for (int i = 0; i < 5; i++) {
      changeKindCreator.updateChange(changeId, NO_CODE_CHANGE, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 2, 1, NO_CODE_CHANGE);
    }

    changeKindCreator.updateChange(changeId, REWORK, testRepo, admin, project);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, REWORK);
  }

  @Test
  public void stickyAcrossMultiplePatchSetsDoNotRegressPerformance() throws Exception {
    // The purpose of this test is to make sure that we compute change kind only against the parent
    // patch set. Change kind is a heavy operation. In prior version of Gerrit, we computed the
    // change kind against all prior patch sets. This is a regression that made Gerrit do expensive
    // work in O(num-patch-sets). This test ensures that we aren't regressing.
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyMaxScore(true));
      u.getConfig().updateLabelType(LabelId.VERIFIED, b -> b.setCopyAllScoresIfNoCodeChange(true));
      u.save();
    }

    String changeId = changeKindCreator.createChange(REWORK, testRepo, admin);
    vote(admin, changeId, 2, 1);
    changeKindCreator.updateChange(changeId, NO_CODE_CHANGE, testRepo, admin, project);
    changeKindCreator.updateChange(changeId, NO_CODE_CHANGE, testRepo, admin, project);
    changeKindCreator.updateChange(changeId, NO_CODE_CHANGE, testRepo, admin, project);

    Map<Integer, ObjectId> revisions = new HashMap<>();
    gApi.changes()
        .id(changeId)
        .get()
        .revisions
        .forEach(
            (revId, revisionInfo) ->
                revisions.put(revisionInfo._number, ObjectId.fromString(revId)));
    assertThat(revisions.size()).isEqualTo(4);
    assertChangeKindCacheContains(revisions.get(3), revisions.get(4));
    assertChangeKindCacheContains(revisions.get(2), revisions.get(3));
    assertChangeKindCacheContains(revisions.get(1), revisions.get(2));

    assertChangeKindCacheDoesNotContain(revisions.get(1), revisions.get(4));
    assertChangeKindCacheDoesNotContain(revisions.get(2), revisions.get(4));
    assertChangeKindCacheDoesNotContain(revisions.get(1), revisions.get(3));
  }

  @Test
  public void copyMinMaxAcrossMultiplePatchSets() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyMaxScore(true));
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyMinScore(true));
      u.save();
    }

    // Vote max score on PS1
    String changeId = changeKindCreator.createChange(REWORK, testRepo, admin);
    vote(admin, changeId, 2, 1);

    // Have someone else vote min score on PS2
    changeKindCreator.updateChange(changeId, REWORK, testRepo, admin, project);
    vote(user, changeId, -2, 0);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, REWORK);
    assertVotes(c, user, -2, 0, REWORK);

    // No vote changes on PS3
    changeKindCreator.updateChange(changeId, REWORK, testRepo, admin, project);
    c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, REWORK);
    assertVotes(c, user, -2, 0, REWORK);

    // Both users revote on PS4
    changeKindCreator.updateChange(changeId, REWORK, testRepo, admin, project);
    vote(admin, changeId, 1, 1);
    vote(user, changeId, 1, 1);
    c = detailedChange(changeId);
    assertVotes(c, admin, 1, 1, REWORK);
    assertVotes(c, user, 1, 1, REWORK);

    // New approvals shouldn't carry through to PS5
    changeKindCreator.updateChange(changeId, REWORK, testRepo, admin, project);
    c = detailedChange(changeId);
    assertVotes(c, admin, 0, 0, REWORK);
    assertVotes(c, user, 0, 0, REWORK);
  }

  @Test
  public void copyWithListOfFilesUnchanged_withoutCopyCondition() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              LabelId.CODE_REVIEW, b -> b.setCopyAllScoresIfListOfFilesDidNotChange(true));
      u.save();
    }
    copyWithListOfFilesUnchanged();
  }

  @Test
  public void copyWithListOfFilesUnchanged_withCopyCondition() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    copyWithListOfFilesUnchanged();
  }

  private void copyWithListOfFilesUnchanged() throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    vote(admin, changeId.toString(), 2, 1);
    vote(user, changeId.toString(), -2, -1);

    changeOperations.change(changeId).newPatchset().file("file").content("new content").create();
    ChangeInfo c = detailedChange(changeId.toString());

    // Code-Review votes are copied over from ps1-> ps2 since the list of files were unchanged.
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, -2, 0);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("file")
        .content("very new content")
        .create();
    c = detailedChange(changeId.toString());

    // Code-Review votes are copied over from ps1-> ps3 since the list of files were unchanged.
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, -2, 0);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file("new file")
        .content("new content")
        .create();

    c = detailedChange(changeId.toString());
    // Code-Review votes are not copied over from ps1-> ps4 since a file was added.
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);

    changeOperations.change(changeId).newPatchset().file("file").content("content").create();

    c = detailedChange(changeId.toString());
    // Code-Review votes are not copied over from ps1 -> ps5 since a file was added on ps4.
    // Although the list of files is the same between ps4->ps5, we don't copy votes from before
    // ps4.
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void copyWithListOfFilesUnchangedButAddedMergeList() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyCondition("has:unchanged-files"));
      u.save();
    }
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();
    Change.Id dummyParentChangeId = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    Map<String, FileInfo> changedFilesFirstPatchset =
        gApi.changes().id(changeId.get()).current().files();

    assertThat(changedFilesFirstPatchset.keySet()).containsExactly("/COMMIT_MSG", "/MERGE_LIST");

    // Make a Code-Review vote that should be sticky.
    gApi.changes().id(changeId.get()).current().review(ReviewInput.approve());

    changeOperations
        .change(changeId)
        .newPatchset()
        .parent()
        .patchset(PatchSet.id(dummyParentChangeId, 1))
        .create();

    Map<String, FileInfo> changedFilesSecondPatchset =
        gApi.changes().id(changeId.get()).current().files();

    // Only "/MERGE_LIST" was removed.
    assertThat(changedFilesSecondPatchset.keySet()).containsExactly("/COMMIT_MSG");
    ApprovalInfo approvalInfo =
        Iterables.getOnlyElement(
            gApi.changes().id(changeId.get()).current().votes().get(LabelId.CODE_REVIEW));
    assertThat(approvalInfo._accountId).isEqualTo(admin.id().get());
    assertThat(approvalInfo.value).isEqualTo(2);
  }

  @Test
  public void deleteStickyVote() throws Exception {
    String label = LabelId.CODE_REVIEW;
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyMaxScore(true));
      u.save();
    }

    // Vote max score on PS1
    String changeId = changeKindCreator.createChange(REWORK, testRepo, admin);
    vote(admin, changeId, label, 2);
    assertVotes(detailedChange(changeId), admin, label, 2, null);
    changeKindCreator.updateChange(changeId, REWORK, testRepo, admin, project);
    assertVotes(detailedChange(changeId), admin, label, 2, REWORK);

    // Delete vote that was copied via sticky approval
    deleteVote(admin, changeId, label);
    assertVotes(detailedChange(changeId), admin, label, 0, REWORK);
  }

  @Test
  public void canVoteMultipleTimesOnNewPatchsets() throws Exception {
    // Code-Review will be sticky.
    String label = LabelId.CODE_REVIEW;
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyAnyScore(true));
      u.save();
    }

    PushOneCommit.Result r = createChange();

    // Add a new vote.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +2 vote.
    amendChange(r.getChangeId());

    // Post without changing the vote.
    input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // There is a vote both on patchset 1 and on patchset 2, although both votes are Code-Review +2.
    assertThat(r.getChange().approvals().get(PatchSet.id(r.getChange().getId(), 1))).hasSize(1);
    assertThat(r.getChange().approvals().get(PatchSet.id(r.getChange().getId(), 2))).hasSize(1);
  }

  @Test
  public void stickyVoteStoredOnUpload() throws Exception {
    // Code-Review will be sticky.
    String label = LabelId.CODE_REVIEW;
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyAnyScore(true));
      u.save();
    }

    PushOneCommit.Result r = createChange();
    // Add a new vote.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    input.tag = "tag";
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make new patchsets, keeping the Code-Review +2 vote.
    for (int i = 0; i < 9; i++) {
      amendChange(r.getChangeId());
    }

    List<PatchSetApproval> patchSetApprovals =
        r.getChange().notes().getApprovalsWithCopied().values().stream()
            .sorted(comparing(a -> a.patchSetId().get()))
            .collect(toImmutableList());

    for (int i = 0; i < 10; i++) {
      int patchSet = i + 1;
      assertThat(patchSetApprovals.get(i).patchSetId().get()).isEqualTo(patchSet);
      assertThat(patchSetApprovals.get(i).accountId().get()).isEqualTo(admin.id().get());
      assertThat(patchSetApprovals.get(i).realAccountId().get()).isEqualTo(admin.id().get());
      assertThat(patchSetApprovals.get(i).label()).isEqualTo(LabelId.CODE_REVIEW);
      assertThat(patchSetApprovals.get(i).value()).isEqualTo((short) 2);
      assertThat(patchSetApprovals.get(i).tag().get()).isEqualTo("tag");
      if (patchSet == 1) {
        assertThat(patchSetApprovals.get(i).copied()).isFalse();
      } else {
        assertThat(patchSetApprovals.get(i).copied()).isTrue();
      }
    }
  }

  @Test
  public void stickyVoteStoredOnRebase() throws Exception {
    // Code-Review will be sticky.
    String label = LabelId.CODE_REVIEW;
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyAnyScore(true));
      u.save();
    }

    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Approve and submit the first change
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    revision.review(ReviewInput.approve().label(LabelId.VERIFIED, 1));
    revision.submit();

    // Add an approval whose score should be copied.
    gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.recommend());

    // Rebase the second change
    gApi.changes().id(r2.getChangeId()).rebase();

    List<PatchSetApproval> patchSetApprovals =
        r2.getChange().notes().getApprovalsWithCopied().values().stream()
            .sorted(comparing(a -> a.patchSetId().get()))
            .collect(toImmutableList());
    PatchSetApproval nonCopied = patchSetApprovals.get(0);
    PatchSetApproval copied = patchSetApprovals.get(1);
    assertCopied(nonCopied, 1, LabelId.CODE_REVIEW, (short) 1, /* copied= */ false);
    assertCopied(copied, 2, LabelId.CODE_REVIEW, (short) 1, /* copied= */ true);
  }

  @Test
  public void stickyVoteStoredOnUploadWithRealAccount() throws Exception {
    // Give "user" permission to vote on behalf of other users.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .impersonation(true)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // Code-Review will be sticky.
    String label = LabelId.CODE_REVIEW;
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyAnyScore(true));
      u.save();
    }

    PushOneCommit.Result r = createChange();

    // Add a new vote as user
    requestScopeOperations.setApiUser(user.id());
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
    input.onBehalfOf = admin.email();
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +1 vote.
    amendChange(r.getChangeId());

    List<PatchSetApproval> patchSetApprovals =
        r.getChange().notes().getApprovalsWithCopied().values().stream()
            .sorted(comparing(a -> a.patchSetId().get()))
            .collect(toImmutableList());

    PatchSetApproval nonCopied = patchSetApprovals.get(0);
    assertThat(nonCopied.patchSetId().get()).isEqualTo(1);
    assertThat(nonCopied.accountId().get()).isEqualTo(admin.id().get());
    assertThat(nonCopied.realAccountId().get()).isEqualTo(user.id().get());
    assertThat(nonCopied.label()).isEqualTo(LabelId.CODE_REVIEW);
    assertThat(nonCopied.value()).isEqualTo((short) 1);
    assertThat(nonCopied.copied()).isFalse();

    PatchSetApproval copied = patchSetApprovals.get(1);
    assertThat(copied.patchSetId().get()).isEqualTo(2);
    assertThat(copied.accountId().get()).isEqualTo(admin.id().get());
    assertThat(copied.realAccountId().get()).isEqualTo(user.id().get());
    assertThat(copied.label()).isEqualTo(LabelId.CODE_REVIEW);
    assertThat(copied.value()).isEqualTo((short) 1);
    assertThat(copied.copied()).isTrue();
  }

  @Test
  public void stickyVoteStoredOnUploadWithRealAccountAndTag() throws Exception {
    // Give "user" permission to vote on behalf of other users.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .impersonation(true)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    // Code-Review will be sticky.
    String label = LabelId.CODE_REVIEW;
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyAnyScore(true));
      u.save();
    }

    PushOneCommit.Result r = createChange();

    // Add a new vote as user
    requestScopeOperations.setApiUser(user.id());
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
    input.onBehalfOf = admin.email();
    input.tag = "tag";
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +1 vote.
    amendChange(r.getChangeId());

    List<PatchSetApproval> patchSetApprovals =
        r.getChange().notes().getApprovalsWithCopied().values().stream()
            .sorted(comparing(a -> a.patchSetId().get()))
            .collect(toImmutableList());

    PatchSetApproval nonCopied = patchSetApprovals.get(0);
    assertThat(nonCopied.patchSetId().get()).isEqualTo(1);
    assertThat(nonCopied.accountId().get()).isEqualTo(admin.id().get());
    assertThat(nonCopied.realAccountId().get()).isEqualTo(user.id().get());
    assertThat(nonCopied.label()).isEqualTo(LabelId.CODE_REVIEW);
    assertThat(nonCopied.value()).isEqualTo((short) 1);
    assertThat(nonCopied.tag().get()).isEqualTo("tag");
    assertThat(nonCopied.copied()).isFalse();

    PatchSetApproval copied = patchSetApprovals.get(1);
    assertThat(copied.patchSetId().get()).isEqualTo(2);
    assertThat(copied.accountId().get()).isEqualTo(admin.id().get());
    assertThat(copied.realAccountId().get()).isEqualTo(user.id().get());
    assertThat(copied.label()).isEqualTo(LabelId.CODE_REVIEW);
    assertThat(copied.value()).isEqualTo((short) 1);
    assertThat(nonCopied.tag().get()).isEqualTo("tag");
    assertThat(copied.copied()).isTrue();
  }

  @Test
  public void stickyVoteStoredCanBeRemoved() throws Exception {
    // Code-Review will be sticky.
    String label = LabelId.CODE_REVIEW;
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(label, b -> b.setCopyAnyScore(true));
      u.save();
    }

    PushOneCommit.Result r = createChange();

    // Add a new vote
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +2 vote.
    amendChange(r.getChangeId());
    assertVotes(detailedChange(r.getChangeId()), admin, label, 2, null);

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.noScore());

    PatchSetApproval nonCopiedSecondPatchsetRemovedVote =
        Iterables.getOnlyElement(
            r.getChange()
                .notes()
                .getApprovalsWithCopied()
                .get(r.getChange().change().currentPatchSetId()));

    assertThat(nonCopiedSecondPatchsetRemovedVote.patchSetId().get()).isEqualTo(2);
    assertThat(nonCopiedSecondPatchsetRemovedVote.accountId().get()).isEqualTo(admin.id().get());
    assertThat(nonCopiedSecondPatchsetRemovedVote.label()).isEqualTo(LabelId.CODE_REVIEW);
    // The vote got removed since the latest patch-set only has one vote and it's "0".
    assertThat(nonCopiedSecondPatchsetRemovedVote.value()).isEqualTo((short) 0);
    assertThat(nonCopiedSecondPatchsetRemovedVote.copied()).isFalse();
  }

  private void assertChangeKindCacheContains(ObjectId prior, ObjectId next) {
    ChangeKind kind =
        changeKindCache.getIfPresent(ChangeKindCacheImpl.Key.create(prior, next, "recursive"));
    assertThat(kind).isNotNull();
  }

  private void assertChangeKindCacheDoesNotContain(ObjectId prior, ObjectId next) {
    ChangeKind kind =
        changeKindCache.getIfPresent(ChangeKindCacheImpl.Key.create(prior, next, "recursive"));
    assertThat(kind).isNull();
  }

  private ChangeInfo detailedChange(String changeId) throws Exception {
    return gApi.changes().id(changeId).get(DETAILED_LABELS, CURRENT_REVISION, CURRENT_COMMIT);
  }

  private void assertNotSticky(Set<ChangeKind> changeKinds) throws Exception {
    for (ChangeKind changeKind : changeKinds) {
      testRepo.reset(projectOperations.project(project).getHead("master"));

      String changeId = changeKindCreator.createChange(changeKind, testRepo, admin);
      vote(admin, changeId, +2, 1);
      vote(user, changeId, -2, -1);

      changeKindCreator.updateChange(changeId, changeKind, testRepo, admin, project);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
    }
  }

  private void vote(TestAccount user, String changeId, String label, int vote) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label(label, vote));
  }

  private void vote(TestAccount user, String changeId, int codeReviewVote, int verifiedVote)
      throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ReviewInput in =
        new ReviewInput()
            .label(LabelId.CODE_REVIEW, codeReviewVote)
            .label(LabelId.VERIFIED, verifiedVote);
    gApi.changes().id(changeId).current().review(in);
  }

  private void deleteVote(TestAccount user, String changeId, String label) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).reviewer(user.id().toString()).deleteVote(label);
  }

  private void assertVotes(ChangeInfo c, TestAccount user, int codeReviewVote, int verifiedVote) {
    assertVotes(c, user, codeReviewVote, verifiedVote, null);
  }

  private void assertVotes(
      ChangeInfo c, TestAccount user, int codeReviewVote, int verifiedVote, ChangeKind changeKind) {
    assertVotes(c, user, LabelId.CODE_REVIEW, codeReviewVote, changeKind);
    assertVotes(c, user, LabelId.VERIFIED, verifiedVote, changeKind);
  }

  private void assertVotes(
      ChangeInfo c, TestAccount user, String label, int expectedVote, ChangeKind changeKind) {
    Integer vote = 0;
    if (c.labels.get(label) != null && c.labels.get(label).all != null) {
      for (ApprovalInfo approval : c.labels.get(label).all) {
        if (approval._accountId == user.id().get()) {
          vote = approval.value;
          break;
        }
      }
    }

    String name = "label = " + label;
    if (changeKind != null) {
      name += "; changeKind = " + changeKind.name();
    }
    assertWithMessage(name).that(vote).isEqualTo(expectedVote);
  }

  private void assertCopied(
      PatchSetApproval approval, int psId, String label, short value, boolean copied) {
    assertThat(approval.patchSetId().get()).isEqualTo(psId);
    assertThat(approval.label()).isEqualTo(label);
    assertThat(approval.value()).isEqualTo(value);
    assertThat(approval.copied()).isEqualTo(copied);
  }
}

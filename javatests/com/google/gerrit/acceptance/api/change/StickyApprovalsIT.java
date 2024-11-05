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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.Comparator.comparing;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.change.ChangeKindCreator;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class StickyApprovalsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ChangeOperations changeOperations;
  @Inject private ChangeKindCreator changeKindCreator;
  @Inject private ExtensionRegistry extensionRegistry;

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
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      u.getConfig().upsertLabelType(codeReview.build());

      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:any"));

    updateCodeReviewLabel(b -> b.setCopyCondition("changekind:REWORK"));

    // changekind:REWORK should match all kind of changes so that approvals are always copied.
    // This means setting changekind:REWORK is equivalent to setting is:ANY and we can do the same
    // assertions for both cases.
    testStickyOnAnyScore();
  }

  private void testStickyOnAnyScore() throws Exception {
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
  public void stickyOnMinScore() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:min"));

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
  public void stickyOnMaxScore() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:max"));

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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:\"-1\" OR is:1"));

    TestAccount user2 = accountCreator.user2();

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
    updateCodeReviewLabel(b -> b.setCopyCondition("changekind:" + TRIVIAL_REBASE.name()));

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
    updateVerifiedLabel(b -> b.setCopyCondition("changekind:" + NO_CODE_CHANGE.name()));

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
    updateCodeReviewLabel(
        b -> b.setCopyCondition("changekind:" + MERGE_FIRST_PARENT_UPDATE.name()));

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
  public void notStickyOnNoChangeForNonMergeIfCopyingIsConfiguredForMergeFirstParentUpdate()
      throws Exception {
    updateCodeReviewLabel(
        b -> b.setCopyCondition("changekind:" + MERGE_FIRST_PARENT_UPDATE.name()));

    // Create a change with a non-merge commit
    String changeId = changeKindCreator.createChange(REWORK, testRepo, admin);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    // Make a NO_CHANGE update (expect that votes are not copied since it's not a merge change).
    changeKindCreator.updateChange(changeId, NO_CHANGE, testRepo, admin, project);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 0, 0, NO_CHANGE);
    assertVotes(c, user, -0, 0, NO_CHANGE);
  }

  @Test
  public void notStickyWithCopyOnNoChangeWhenSecondParentIsUpdated() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("changekind:" + NO_CHANGE.name()));

    String changeId = changeKindCreator.createChangeForMergeCommit(testRepo, admin);
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    changeKindCreator.updateSecondParent(changeId, testRepo, admin);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 0, 0, null);
    assertVotes(c, user, 0, 0, null);
  }

  @Test
  public void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsAdded()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
  public void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileAlreadyExists()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
  public void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsDeleted()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
  public void stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModified()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
  public void stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedDueToRebase()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
    ImmutableList<PatchSetApproval> patchSetApprovals =
        r2.getChange().notes().getApprovals().all().values().stream()
            .sorted(comparing(a -> a.patchSetId().get()))
            .collect(toImmutableList());
    PatchSetApproval nonCopied = patchSetApprovals.get(0);
    PatchSetApproval copied = patchSetApprovals.get(1);
    assertCopied(nonCopied, /* psId= */ 1, LabelId.CODE_REVIEW, (short) 1, false);
    assertCopied(copied, /* psId= */ 2, LabelId.CODE_REVIEW, (short) 1, true);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void stickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedAsInitialCommit()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
      notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsModifiedOnEarlierPatchset()
          throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
  public void notStickyWithCopyAllScoresIfListOfFilesDidNotChangeWhenFileIsRenamed()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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
  public void copyWithListOfFilesUnchanged() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

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

  // This test doesn't work without copy condition (when
  // setCopyAllScoresIfListOfFilesDidNotChange=true).
  // This is because magic files are not ignored for setCopyAllScoresIfListOfFilesDidNotChange.
  @Test
  public void stickyIfFilesUnchanged_magicFilesAreIgnored() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("has:unchanged-files"));

    Change.Id parent1ChangeId = changeOperations.newChange().project(project).create();
    Change.Id parent2ChangeId = changeOperations.newChange().project(project).create();
    Change.Id dummyParentChangeId = changeOperations.newChange().project(project).create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    // The change is for a merge commit. It doesn't touch any files, but contains /COMMIT_MSG and
    // /MERGE_LIST as magic files.
    Map<String, FileInfo> changedFilesFirstPatchset =
        gApi.changes().id(changeId.get()).current().files();
    assertThat(changedFilesFirstPatchset.keySet()).containsExactly("/COMMIT_MSG", "/MERGE_LIST");

    // Add a Code-Review+2 vote.
    approve(changeId.toString());

    // Create a new patch set with a non-merge commit.
    changeOperations
        .change(changeId)
        .newPatchset()
        .parent()
        .patchset(PatchSet.id(dummyParentChangeId, 1))
        .create();

    // Since the new patch set is not a merge commit, it no longer contains the magic /MERGE_LIST
    // file.
    Map<String, FileInfo> changedFilesSecondPatchset =
        gApi.changes().id(changeId.get()).current().files();
    assertThat(changedFilesSecondPatchset.keySet())
        .containsExactly("/COMMIT_MSG"); // /MERGE_LIST is no longer present

    // The only file difference between the 2 patch sets is the magic /MERGE_LIST file.
    // Since magic files are ignored when checking whether the files between the patch sets differ,
    // has:unchanged-file should evaluate to true and we expect that the vote was copied.
    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, admin, 2, 0);
  }

  @Test
  public void approvalsAreStickyIfUploaderMatchesUploaderinCondition() throws Exception {
    TestAccount userForWhomApprovalsAreSticky =
        accountCreator.create(
            /* username= */ null,
            /* email= */ "userForWhomApprovalsAreSticky@example.com",
            /* fullName= */ "User For Whom Approvals Are Sticky",
            /* displayName= */ null);
    String usersForWhomApprovalsAreStickyUuid =
        groupOperations
            .newGroup()
            .name("Users-for-whom-approvals-are-sticky")
            .addMember(userForWhomApprovalsAreSticky.id())
            .create()
            .get();

    updateCodeReviewLabel(
        b -> b.setCopyCondition("uploaderin:" + usersForWhomApprovalsAreStickyUuid));

    PushOneCommit.Result r = createChange();

    // Add Code-Review+2 by the admin user.
    approve(r.getChangeId());

    // Add Code-Review+1 by user.
    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());

    // Create a new patch set by userForWhomApprovalsAreSticky (approavls are sticky).
    TestRepository<InMemoryRepository> userTestRepo =
        cloneProject(project, userForWhomApprovalsAreSticky);
    GitUtil.fetch(userTestRepo, r.getPatchSet().refName() + ":ps");
    userTestRepo.reset("ps");
    amendChange(r.getChangeId(), "refs/for/master", userForWhomApprovalsAreSticky, userTestRepo)
        .assertOkStatus();
    ChangeInfo c = detailedChange(r.getChangeId());
    assertThat(c.revisions.get(c.currentRevision).uploader._accountId)
        .isEqualTo(userForWhomApprovalsAreSticky.id().get());

    // Approvals are sticky.
    assertVotes(c, admin, 2, 0);
    assertVotes(c, user, 1, 0);

    // Create a new patch set by admin user (approvals are not sticky).
    amendChange(r.getChangeId()).assertOkStatus();

    // Approvals are not sticky.
    c = detailedChange(r.getChangeId());
    assertVotes(c, admin, 0, 0);
    assertVotes(c, user, 0, 0);
  }

  @Test
  public void approvalsThatMatchApproverinConditionAreSticky() throws Exception {
    TestAccount userWhoseApprovalsAreSticky = accountCreator.create();
    String usersWhoseApprovalsAreStickyUuid =
        groupOperations
            .newGroup()
            .name("Users-whose-approvals-are-sticky")
            .addMember(userWhoseApprovalsAreSticky.id())
            .create()
            .get();

    updateCodeReviewLabel(
        b -> b.setCopyCondition("approverin:" + usersWhoseApprovalsAreStickyUuid));

    PushOneCommit.Result r = createChange();

    // Add Code-Review+2 by the admin user (not sticky).
    approve(r.getChangeId());

    // Add Code-Review+1 by userWhoseApprovalsAreSticky (sticky).
    requestScopeOperations.setApiUser(userWhoseApprovalsAreSticky.id());
    recommend(r.getChangeId());

    requestScopeOperations.setApiUser(admin.id());
    amendChange(r.getChangeId()).assertOkStatus();

    ChangeInfo c = detailedChange(r.getChangeId());
    assertVotes(c, admin, 0, 0);
    assertVotes(c, userWhoseApprovalsAreSticky, 1, 0);
  }

  @Test
  public void approvalsThatMatchApproverinConditionAreStickyEvenIfUserCantSeeTheGroup()
      throws Exception {
    String administratorsUUID = gApi.groups().query("name:Administrators").get().get(0).id;

    // Verify that user can't see the admin group.
    requestScopeOperations.setApiUser(user.id());
    ResourceNotFoundException notFound =
        assertThrows(
            ResourceNotFoundException.class, () -> gApi.groups().id(administratorsUUID).get());
    assertThat(notFound).hasMessageThat().isEqualTo("Not found: " + administratorsUUID);

    requestScopeOperations.setApiUser(admin.id());
    updateCodeReviewLabel(b -> b.setCopyCondition("approverin:" + administratorsUUID));

    PushOneCommit.Result r = createChange();

    // Add Code-Review+2 by the admin user.
    approve(r.getChangeId());

    // Create a new patch set by user.
    // Approvals are copied on creation of the new patch set. The approval of the admin user is
    // expected to be sticky although the group that is configured for the 'approverin' predicate is
    // not visible to the user.
    TestRepository<InMemoryRepository> userTestRepo = cloneProject(project, user);
    GitUtil.fetch(userTestRepo, r.getPatchSet().refName() + ":ps");
    userTestRepo.reset("ps");
    amendChange(r.getChangeId(), "refs/for/master", user, userTestRepo).assertOkStatus();

    ChangeInfo c = detailedChange(r.getChangeId());
    assertThat(c.revisions.get(c.currentRevision).uploader._accountId).isEqualTo(user.id().get());

    // Assert that the approval of the admin user was copied to the new patch set.
    assertVotes(c, admin, 2, 0);
  }

  @Test
  public void removedVotesNotSticky() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("changekind:" + TRIVIAL_REBASE.name()));
    updateVerifiedLabel(b -> b.setCopyCondition("changekind:" + NO_CODE_CHANGE.name()));

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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:MAX"));
    updateVerifiedLabel(b -> b.setCopyCondition("changekind:" + NO_CODE_CHANGE.name()));

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

    updateCodeReviewLabel(b -> b.setCopyCondition("is:MAX"));
    updateVerifiedLabel(b -> b.setCopyCondition("changekind:" + NO_CODE_CHANGE.name()));

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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:MAX OR is:MIN"));

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
  public void deleteStickyVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:MAX"));

    String label = LabelId.CODE_REVIEW;

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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();

    // Add a new vote.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +2 vote.
    r = amendChange(r.getChangeId());

    // Verify that the approval has been copied to patch set 2 (use the currentApprovals() method
    // rather than the approvals() method since the latter one filters out copied approvals).
    List<PatchSetApproval> approvalsPs2 = r.getChange().currentApprovals();
    assertThat(approvalsPs2).hasSize(1);
    assertThat(Iterables.getOnlyElement(approvalsPs2).copied()).isTrue();

    // Post without changing the vote.
    input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // There is a vote both on patch set 1 and on patch set 2, although both votes are Code-Review
    // +2. The approval on patch set 2 is no longer copied since it was reapplied.
    assertThat(r.getChange().approvals().get(PatchSet.id(r.getChange().getId(), 1))).hasSize(1);
    approvalsPs2 = r.getChange().currentApprovals();
    assertThat(approvalsPs2).hasSize(1);
    assertThat(Iterables.getOnlyElement(approvalsPs2).copied()).isFalse();
  }

  @Test
  public void copiedVoteIsNotReapplied_onVoteOnOtherLabel() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();

    // Add vote that will be copied.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Create a new patchset, the Code-Review +2 vote is copied.
    r = amendChange(r.getChangeId());

    // Verify that the approval has been copied to patch set 2 (use the currentApprovals() method
    // rather than the approvals() method since the latter one filters out copied approvals).
    List<PatchSetApproval> approvalsPs2 = r.getChange().currentApprovals();
    assertThat(approvalsPs2).hasSize(1);
    assertThat(Iterables.getOnlyElement(approvalsPs2).copied()).isTrue();

    // Vote on another label. This shouldn't touch the copied approval.
    input = new ReviewInput().label(LabelId.VERIFIED, 1);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Patch set 2 has 2 approvals now, one copied approval for the Code-Review label and one
    // non-copied
    // approval for the Verified label.
    approvalsPs2 = r.getChange().currentApprovals();
    assertThat(approvalsPs2).hasSize(2);
    assertThat(
            Iterables.getOnlyElement(
                    approvalsPs2.stream()
                        .filter(psa -> LabelId.CODE_REVIEW.equals(psa.label()))
                        .collect(toImmutableList()))
                .copied())
        .isTrue();
    assertThat(
            Iterables.getOnlyElement(
                    approvalsPs2.stream()
                        .filter(psa -> LabelId.VERIFIED.equals(psa.label()))
                        .collect(toImmutableList()))
                .copied())
        .isFalse();
  }

  @Test
  public void copiedVoteIsNotReapplied_onRebase() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();

    // Create a sibling change
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Add vote that will be copied.
    approve(r2.getChangeId());

    // Verify that that the approval exists and is not copied.
    List<PatchSetApproval> approvalsPs2 = r2.getChange().currentApprovals();
    assertThat(approvalsPs2).hasSize(1);
    assertThat(Iterables.getOnlyElement(approvalsPs2).copied()).isFalse();

    // Approve, verify and submit the first change.
    approve(r.getChangeId());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.VERIFIED, 1));
    gApi.changes().id(r.getChangeId()).current().submit();

    // Rebase the second change, the approval should be sticky.
    gApi.changes().id(r2.getChangeId()).rebase();

    // Verify that the approval has been copied to patch set 2 (use the currentApprovals() method
    // rather than the approvals() method since the latter one filters out copied approvals).
    approvalsPs2 = changeDataFactory.create(project, r2.getChange().getId()).currentApprovals();
    assertThat(approvalsPs2).hasSize(1);
    assertThat(Iterables.getOnlyElement(approvalsPs2).copied()).isTrue();
  }

  @Test
  public void stickyVoteStoredOnUpload() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    // Add a new vote.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    input.tag = "tag";
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make new patchsets, keeping the Code-Review +2 vote.
    for (int i = 0; i < 9; i++) {
      amendChange(r.getChangeId());
    }

    ImmutableList<PatchSetApproval> patchSetApprovals =
        r.getChange().notes().getApprovals().all().values().stream()
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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

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

    ImmutableList<PatchSetApproval> patchSetApprovals =
        r2.getChange().notes().getApprovals().all().values().stream()
            .sorted(comparing(a -> a.patchSetId().get()))
            .collect(toImmutableList());
    PatchSetApproval nonCopied = patchSetApprovals.get(0);
    PatchSetApproval copied = patchSetApprovals.get(1);
    assertCopied(nonCopied, 1, LabelId.CODE_REVIEW, (short) 1, /* copied= */ false);
    assertCopied(copied, 2, LabelId.CODE_REVIEW, (short) 1, /* copied= */ true);
  }

  @Test
  public void stickyVoteStoredOnUploadWithRealAccount() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

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

    PushOneCommit.Result r = createChange();

    // Add a new vote as user
    requestScopeOperations.setApiUser(user.id());
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
    input.onBehalfOf = admin.email();
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +1 vote.
    amendChange(r.getChangeId());

    ImmutableList<PatchSetApproval> patchSetApprovals =
        r.getChange().notes().getApprovals().all().values().stream()
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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

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

    PushOneCommit.Result r = createChange();

    // Add a new vote as user
    requestScopeOperations.setApiUser(user.id());
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
    input.onBehalfOf = admin.email();
    input.tag = "tag";
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +1 vote.
    amendChange(r.getChangeId());

    ImmutableList<PatchSetApproval> patchSetApprovals =
        r.getChange().notes().getApprovals().all().values().stream()
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
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();

    // Add a new vote
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);

    // Make a new patchset, keeping the Code-Review +2 vote.
    amendChange(r.getChangeId());
    assertVotes(detailedChange(r.getChangeId()), admin, LabelId.CODE_REVIEW, 2, null);

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.noScore());

    PatchSetApproval nonCopiedSecondPatchsetRemovedVote =
        Iterables.getOnlyElement(
            r.getChange()
                .notes()
                .getApprovals()
                .all()
                .get(r.getChange().change().currentPatchSetId()));

    assertThat(nonCopiedSecondPatchsetRemovedVote.patchSetId().get()).isEqualTo(2);
    assertThat(nonCopiedSecondPatchsetRemovedVote.accountId().get()).isEqualTo(admin.id().get());
    assertThat(nonCopiedSecondPatchsetRemovedVote.label()).isEqualTo(LabelId.CODE_REVIEW);
    // The vote got removed since the latest patch-set only has one vote and it's "0".
    assertThat(nonCopiedSecondPatchsetRemovedVote.value()).isEqualTo((short) 0);
    assertThat(nonCopiedSecondPatchsetRemovedVote.copied()).isFalse();
  }

  @Test
  public void reviewerStickyVotingCanBeRemoved() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();

    // Add a new vote by user
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend());
    requestScopeOperations.setApiUser(admin.id());

    // Make a new patchset, keeping the Code-Review +1 vote.
    amendChange(r.getChangeId());
    assertVotes(detailedChange(r.getChangeId()), user, LabelId.CODE_REVIEW, 1, null);

    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();

    assertThat(r.getChange().notes().getApprovals().all()).isEmpty();

    // Changes message has info about vote removed.
    assertThat(Iterables.getLast(gApi.changes().id(r.getChangeId()).messages()).message)
        .contains("Code-Review+1 by User");
  }

  @Test
  public void sticky_copiedToLatestPatchSetFromSubmitRecords() throws Exception {
    updateVerifiedLabel(b -> b.setNoBlockFunction());

    // This test is covering the backfilling logic for changes which have been submitted, based on
    // copied approvals, before Gerrit persisted copied votes as Copied-Label footers in NoteDb. It
    // verifies that for such changes copied approvals are returned from the API even if the copied
    // votes were not persisted as Copied-Label footers.
    //
    // In other words, this test verifies that given a change that was approved by a copied vote and
    // then submitted and for which the copied approval is not persisted as a Copied-Label footer in
    // NoteDb the copied approval is backfilled from the corresponding Submitted-With footer that
    // got written to NoteDb on submit.
    //
    // Creating such a change would be possible by running the old Gerrit code from before Gerrit
    // persisted copied labels as Copied-Label footers. However since this old Gerrit code is no
    // longer available, the test needs to apply a trick to create a change in this state. It
    // configures a fake submit rule, that pretends that an approval for a non-sticky label from an
    // old patch set is still present on the current patch set and allows to submit the change.
    // Since the label is non-sticky no Copied-Label footer is written for it. On submit the fake
    // submit rule results in a Submitted-With footer that records the label as approved (although
    // the label is actually not present on the current patch set). This is exactly the change state
    // that we would have had by running the old code if submit was based on a copied label. As
    // result of the backfilling logic we expect that this "copied" label (the label that is
    // mentioned in the Submitted-With footer) is returned from the API.
    try (Registration registration =
        extensionRegistry.newRegistration().add(new TestSubmitRule(user.id()))) {
      // We want to add a vote on PS1, then not copy it to PS2, but include it in submit records
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();

      // Vote on patch-set 1
      vote(admin, changeId, 2, 1);
      vote(user, changeId, 1, -1);

      // Upload patch-set 2. Change user's "Verified" vote on PS2.
      changeOperations
          .change(Change.id(r.getChange().getId().get()))
          .newPatchset()
          .file("new_file")
          .content("content")
          .commitMessage("Upload PS2")
          .create();
      vote(admin, changeId, 2, 1);
      vote(user, changeId, 1, 1);

      // Upload patch-set 3
      changeOperations
          .change(Change.id(r.getChange().getId().get()))
          .newPatchset()
          .file("another_file")
          .content("content")
          .commitMessage("Upload PS3")
          .create();
      vote(admin, changeId, 2, 1);

      ImmutableList<PatchSetApproval> patchSetApprovals =
          notesFactory.create(project, r.getChange().getId()).getApprovals().all().values().stream()
              .sorted(comparing(a -> a.patchSetId().get()))
              .collect(toImmutableList());

      // There's no verified approval on PS#3.
      assertThat(
              patchSetApprovals.stream()
                  .filter(
                      a ->
                          a.accountId().equals(user.id())
                              && a.label().equals(TestLabels.verified().getName())
                              && a.patchSetId().get() == 3)
                  .collect(Collectors.toList()))
          .isEmpty();

      // Submit the change. The TestSubmitRule will store a "submit record" containing a label
      // voted by user, but the latest patch-set does not have an approval for this user, hence
      // it will be copied if we request approvals after the change is merged.
      requestScopeOperations.setApiUser(admin.id());
      gApi.changes().id(changeId).current().submit();

      patchSetApprovals =
          notesFactory.create(project, r.getChange().getId()).getApprovals().all().values().stream()
              .sorted(comparing(a -> a.patchSetId().get()))
              .collect(toImmutableList());

      // Get the copied approval for user on PS3 for the "Verified" label.
      PatchSetApproval verifiedApproval =
          patchSetApprovals.stream()
              .filter(
                  a ->
                      a.accountId().equals(user.id())
                          && a.label().equals(TestLabels.verified().getName())
                          && a.patchSetId().get() == 3)
              .collect(MoreCollectors.onlyElement());

      assertCopied(
          verifiedApproval,
          /* psId= */ 3,
          TestLabels.verified().getName(),
          (short) 1,
          /* copied= */ true);
    }
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

  private void updateCodeReviewLabel(Consumer<LabelType.Builder> update) throws Exception {
    updateLabel(LabelId.CODE_REVIEW, update);
  }

  private void updateVerifiedLabel(Consumer<LabelType.Builder> update) throws Exception {
    updateLabel(LabelId.VERIFIED, update);
  }

  private void updateLabel(String labelName, Consumer<LabelType.Builder> update) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(labelName, update);
      u.save();
    }
  }

  /**
   * Test submit rule that always return a passing record with a "Verified" label applied by {@link
   * TestSubmitRule#userAccountId}.
   */
  private static class TestSubmitRule implements SubmitRule {
    Account.Id userAccountId;

    TestSubmitRule(Account.Id userAccountId) {
      this.userAccountId = userAccountId;
    }

    @Override
    public Optional<SubmitRecord> evaluate(ChangeData changeData) {
      SubmitRecord record = new SubmitRecord();
      record.ruleName = "testSubmitRule";
      record.status = SubmitRecord.Status.OK;
      SubmitRecord.Label label = new SubmitRecord.Label();
      label.label = "Verified";
      label.status = SubmitRecord.Label.Status.OK;
      label.appliedBy = userAccountId;
      record.labels = Arrays.asList(label);
      return Optional.of(record);
    }
  }
}

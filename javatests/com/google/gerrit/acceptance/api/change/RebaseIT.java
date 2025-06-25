// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.extensions.client.ChangeKind.MERGE_FIRST_PARENT_UPDATE;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestExtensions.TestCommitValidationListener;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.RelatedChangeAndCommitInfo;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RebaseChainInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  RebaseIT.RebaseViaRevisionApi.class, //
  RebaseIT.RebaseViaChangeApi.class, //
  RebaseIT.RebaseChain.class, //
})
public class RebaseIT {
  public abstract static class Base extends AbstractDaemonTest {
    @Inject protected ChangeOperations changeOperations;
    @Inject protected RequestScopeOperations requestScopeOperations;
    @Inject protected ProjectOperations projectOperations;
    @Inject protected ExtensionRegistry extensionRegistry;
    @Inject protected TestMetricMaker testMetricMaker;
    @Inject protected AccountOperations accountOperations;

    @FunctionalInterface
    protected interface RebaseCall {
      void call(String id) throws RestApiException;
    }

    @FunctionalInterface
    protected interface RebaseCallWithInput {
      void call(String id, RebaseInput in) throws RestApiException;
    }

    protected RebaseCall rebaseCall;
    protected RebaseCallWithInput rebaseCallWithInput;

    protected void init(RebaseCall call, RebaseCallWithInput callWithInput) {
      this.rebaseCall = call;
      this.rebaseCallWithInput = callWithInput;
    }

    @Test
    public void rebaseChange() throws Exception {
      RevCommit initialHead = projectOperations.project(project).getHead("master");

      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();
      RevCommit commitThatIsBeingRebased = r2.getCommit();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Add an approval whose score should be copied on trivial rebase
      gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.recommend());

      // Rebase the second change
      rebaseCall.call(r2.getChangeId());

      verifyRebaseForChange(
          r2.getChange().getId(),
          initialHead,
          commitThatIsBeingRebased.name(),
          r.getCommit().name(),
          true,
          2);

      // Rebasing the second change again should fail
      verifyChangeIsUpToDate(r2);
    }

    @Test
    public void rebaseChangeOntoTargetBranchWhenParentChangeDeletedHasBeenDeleted()
        throws Exception {
      // Create a chain with 2 changes
      Change.Id changeId1 =
          changeOperations
              .newChange()
              .project(project)
              .file("a.txt")
              .content("A content")
              .createV1();
      Change.Id changeId2 =
          changeOperations
              .newChange()
              .project(project)
              .childOf()
              .change(changeId1)
              .file("b.txt")
              .content("B content")
              .createV1();

      ObjectId change1PatchSetCommit =
          changeOperations.change(changeId1).currentPatchset().get().commitId();

      // Delete the first change
      gApi.changes().id(project.get(), changeId1.get()).delete();

      String newBase = projectOperations.project(project).getHead("refs/heads/master").name();
      String commitThatIsBeingRebased =
          gApi.changes().id(project.get(), changeId2.get()).get().currentRevision;

      rebaseCall.call(changeId2.toString());

      verifyRebaseForChange(
          changeId2,
          change1PatchSetCommit,
          commitThatIsBeingRebased,
          newBase,
          /* shouldHaveApproval= */ false,
          2);
    }

    @Test
    public void rebaseChangeOntoOtherChangeWhenParentChangeDeletedHasBeenDeleted()
        throws Exception {
      // Create a chain with 2 changes
      Change.Id changeId1 =
          changeOperations
              .newChange()
              .project(project)
              .file("a.txt")
              .content("A content")
              .createV1();
      Change.Id changeId2 =
          changeOperations
              .newChange()
              .project(project)
              .childOf()
              .change(changeId1)
              .file("b.txt")
              .content("B content")
              .createV1();

      ObjectId change1PatchSetCommit =
          changeOperations.change(changeId1).currentPatchset().get().commitId();

      // Delete the first change
      gApi.changes().id(project.get(), changeId1.get()).delete();

      Change.Id changeIdOther =
          changeOperations
              .newChange()
              .project(project)
              .file("c.txt")
              .content("C content")
              .createV1();

      String newBase = gApi.changes().id(project.get(), changeIdOther.get()).get().currentRevision;
      String commitThatIsBeingRebased =
          gApi.changes().id(project.get(), changeId2.get()).get().currentRevision;

      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.base = changeIdOther.toString();
      rebaseCallWithInput.call(changeId2.toString(), rebaseInput);

      verifyRebaseForChange(
          changeId2,
          change1PatchSetCommit,
          commitThatIsBeingRebased,
          newBase,
          /* shouldHaveApproval= */ false,
          2);
    }

    @Test
    public void rebaseMerge() throws Exception {
      // Create a new project for this test so that we can configure a copy condition without
      // affecting any other tests. Copy Code-Review approvals if change kind is
      // MERGE_FIRST_PARENT_UPDATE. MERGE_FIRST_PARENT_UPDATE is the change kind when a merge commit
      // is rebased without conflicts.
      Project.NameKey project = projectOperations.newProject().create();
      try (ProjectConfigUpdate u = updateProject(project)) {
        LabelType.Builder codeReview =
            labelBuilder(
                    LabelId.CODE_REVIEW,
                    value(2, "Looks good to me, approved"),
                    value(1, "Looks good to me, but someone else must approve"),
                    value(0, "No score"),
                    value(-1, "I would prefer this is not submitted as is"),
                    value(-2, "This shall not be submitted"))
                .setCopyCondition("changekind:" + MERGE_FIRST_PARENT_UPDATE.name());
        u.getConfig().upsertLabelType(codeReview.build());
        u.save();
      }

      String file1 = "foo/a.txt";
      String file2 = "bar/b.txt";
      String file3 = "baz/c.txt";

      // Create an initial change that adds file1, so that we can modify it later.
      Change.Id initialChange =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file1)
              .content("base content")
              .createV1();
      approveAndSubmit(initialChange);

      // Create another branch
      String branchName = "foo";
      BranchInput branchInput = new BranchInput();
      branchInput.ref = branchName;
      branchInput.revision = projectOperations.project(project).getHead("master").name();
      gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

      // Create a change in master that touches file1.
      Change.Id baseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file1)
              .content("master content")
              .createV1();
      ObjectId baseChangeCommit =
          changeOperations.change(baseChangeInMaster).currentPatchset().get().commitId();
      approveAndSubmit(baseChangeInMaster);

      // Create a change in the other branch and that touches file1 and creates file2.
      Change.Id changeInOtherBranch =
          changeOperations
              .newChange()
              .project(project)
              .branch(branchName)
              .file(file1)
              .content("other content")
              .file(file2)
              .content("content")
              .createV1();
      approveAndSubmit(changeInOtherBranch);

      // Create a merge change with a conflict resolution for file1. file2 has the same content as
      // in the other branch (no conflict on file2).
      Change.Id mergeChangeId =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .mergeOfButBaseOnFirst()
              .tipOfBranch("master")
              .and()
              .tipOfBranch(branchName)
              .file(file1)
              .content("merged content")
              .file(file2)
              .content("content")
              .createV1();
      String commitThatIsBeingRebased = getCurrentRevision(mergeChangeId);

      // Create a change in master onto which the merge change can be rebased. This change touches
      // an unrelated file (file3) so that there is no conflict on rebase.
      Change.Id newBaseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file3)
              .content("other content")
              .createV1();
      approveAndSubmit(newBaseChangeInMaster);

      // Add an approval whose score should be copied on rebase.
      gApi.changes().id(mergeChangeId.get()).current().review(ReviewInput.recommend());

      // Rebase the merge change
      rebaseCall.call(mergeChangeId.toString());

      verifyRebaseForChange(
          mergeChangeId,
          baseChangeCommit,
          commitThatIsBeingRebased,
          ImmutableList.of(
              getCurrentRevision(newBaseChangeInMaster), getCurrentRevision(changeInOtherBranch)),
          /* shouldHaveApproval= */ true,
          /* shouldHaveConflicts,= */ false,
          /* expectedNumRevisions= */ 2);

      // Verify the file contents.
      assertThat(getFileContent(mergeChangeId, file1)).isEqualTo("merged content");
      assertThat(getFileContent(mergeChangeId, file2)).isEqualTo("content");
      assertThat(getFileContent(mergeChangeId, file3)).isEqualTo("other content");

      // Rebasing the merge change again should fail
      verifyChangeIsUpToDate(mergeChangeId.toString());
    }

    @Test
    public void rebaseMergeWithConflict_fails() throws Exception {
      String file1 = "foo/a.txt";
      String file2 = "bar/b.txt";

      // Create an initial change that adds file1, so that we can modify it later.
      Change.Id initialChange =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file1)
              .content("base content")
              .createV1();
      approveAndSubmit(initialChange);

      // Create another branch
      String branchName = "foo";
      BranchInput branchInput = new BranchInput();
      branchInput.ref = branchName;
      branchInput.revision = projectOperations.project(project).getHead("master").name();
      gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

      // Create a change in master that touches file1.
      Change.Id baseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file1)
              .content("master content")
              .createV1();
      approveAndSubmit(baseChangeInMaster);

      // Create a change in the other branch and that touches file1 and creates file2.
      Change.Id changeInOtherBranch =
          changeOperations
              .newChange()
              .project(project)
              .branch(branchName)
              .file(file1)
              .content("other content")
              .file(file2)
              .content("content")
              .createV1();
      approveAndSubmit(changeInOtherBranch);

      // Create a merge change with a conflict resolution for file1. file2 has the same content as
      // in the other branch (no conflict on file2).
      Change.Id mergeChangeId =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .mergeOfButBaseOnFirst()
              .tipOfBranch("master")
              .and()
              .tipOfBranch(branchName)
              .file(file1)
              .content("merged content")
              .file(file2)
              .content("content")
              .createV1();

      // Create a change in master onto which the merge change can be rebased. This change touches
      // file1 again so that there is a conflict on rebase.
      Change.Id newBaseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file1)
              .content("conflicting content")
              .createV1();
      approveAndSubmit(newBaseChangeInMaster);

      // Try to rebase the merge change
      MergeConflictException mergeConflictException =
          assertThrows(
              MergeConflictException.class, () -> rebaseCall.call(mergeChangeId.toString()));
      assertThat(mergeConflictException)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Change %s could not be rebased due to a conflict during merge.\n"
                      + "\n"
                      + "merge conflict(s):\n"
                      + "%s",
                  mergeChangeId, file1));
    }

    @Test
    public void rebaseMergeWithConflictWithConflictsAllowed() throws Exception {
      testRebaseMergeWithConflictWithConflictsAllowed(/* useDiff3= */ false);
    }

    @Test
    @GerritConfig(name = "change.diff3ConflictView", value = "true")
    public void rebaseMergeWithConflictWithConflictsAllowedUsingDiff3() throws Exception {
      testRebaseMergeWithConflictWithConflictsAllowed(/* useDiff3= */ true);
    }

    private void testRebaseMergeWithConflictWithConflictsAllowed(boolean useDiff3)
        throws Exception {
      // Create a new project for this test so that we can configure a copy condition without
      // affecting any other tests. Copy Code-Review approvals if change kind is
      // MERGE_FIRST_PARENT_UPDATE. MERGE_FIRST_PARENT_UPDATE is the change kind when a merge commit
      // is rebased without conflicts.
      Project.NameKey project = projectOperations.newProject().create();
      try (ProjectConfigUpdate u = updateProject(project)) {
        LabelType.Builder codeReview =
            labelBuilder(
                    LabelId.CODE_REVIEW,
                    value(2, "Looks good to me, approved"),
                    value(1, "Looks good to me, but someone else must approve"),
                    value(0, "No score"),
                    value(-1, "I would prefer this is not submitted as is"),
                    value(-2, "This shall not be submitted"))
                .setCopyCondition("changekind:" + MERGE_FIRST_PARENT_UPDATE.name());
        u.getConfig().upsertLabelType(codeReview.build());
        u.save();
      }

      String file = "foo/a.txt";

      // Create an initial change that adds a file, so that we can modify it later.
      Change.Id initialChange =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file)
              .content("base content")
              .createV1();
      approveAndSubmit(initialChange);

      // Create another branch
      String branchName = "foo";
      BranchInput branchInput = new BranchInput();
      branchInput.ref = branchName;
      branchInput.revision = projectOperations.project(project).getHead("master").name();
      gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

      // Create a change in master that touches the file.
      String baseChangeSubject = "base change";
      String baseChangeBaseContent = "base content";
      Change.Id baseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .commitMessage(baseChangeSubject)
              .file(file)
              .content(baseChangeBaseContent)
              .createV1();
      ObjectId baseChangeCommit =
          changeOperations.change(baseChangeInMaster).currentPatchset().get().commitId();
      approveAndSubmit(baseChangeInMaster);

      // Create a change in the other branch and that also touches the file.
      Change.Id changeInOtherBranch =
          changeOperations
              .newChange()
              .project(project)
              .branch(branchName)
              .file(file)
              .content("other content")
              .createV1();
      approveAndSubmit(changeInOtherBranch);

      // Create a merge change with a conflict resolution.
      String mergeCommitMessage = "Merge";
      String mergeContent = "merged content";
      Change.Id mergeChangeId =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .commitMessage(mergeCommitMessage)
              .mergeOfButBaseOnFirst()
              .tipOfBranch("master")
              .and()
              .tipOfBranch(branchName)
              .file(file)
              .content(mergeContent)
              .createV1();
      String commitThatIsBeingRebased = getCurrentRevision(mergeChangeId);

      // Create a change in master onto which the merge change can be rebased. This change touches
      // the file again so that there is a conflict on rebase.
      String newBaseCommitMessage = "Foo";
      String newBaseContent = "conflicting content";
      Change.Id newBaseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .commitMessage(newBaseCommitMessage)
              .file(file)
              .content(newBaseContent)
              .createV1();
      approveAndSubmit(newBaseChangeInMaster);

      // Add an approval whose score should NOT be copied on rebase (since there is a conflict the
      // change kind should be REWORK).
      gApi.changes().id(mergeChangeId.get()).current().review(ReviewInput.recommend());

      // Rebase the merge change with conflicts allowed.
      TestWorkInProgressStateChangedListener wipStateChangedListener =
          new TestWorkInProgressStateChangedListener();
      try (ExtensionRegistry.Registration registration =
          extensionRegistry.newRegistration().add(wipStateChangedListener)) {
        RebaseInput rebaseInput = new RebaseInput();
        rebaseInput.allowConflicts = true;
        rebaseCallWithInput.call(mergeChangeId.toString(), rebaseInput);
      }
      assertThat(wipStateChangedListener.invoked).isTrue();
      assertThat(wipStateChangedListener.wip).isTrue();

      String baseCommit = getCurrentRevision(newBaseChangeInMaster);
      verifyRebaseForChange(
          mergeChangeId,
          baseChangeCommit,
          commitThatIsBeingRebased,
          ImmutableList.of(baseCommit, getCurrentRevision(changeInOtherBranch)),
          /* shouldHaveApproval= */ false,
          /* shouldHaveConflicts,= */ true,
          /* expectedNumRevisions= */ 2);

      // Verify the file contents.
      assertThat(getFileContent(mergeChangeId, file))
          .isEqualTo(
              "<<<<<<< PATCH SET ("
                  + commitThatIsBeingRebased
                  + " "
                  + mergeCommitMessage
                  + ")\n"
                  + mergeContent
                  + "\n"
                  + (useDiff3
                      ? String.format(
                          "||||||| BASE      (%s %s)\n%s\n",
                          baseChangeCommit.getName(), baseChangeSubject, baseChangeBaseContent)
                      : "")
                  + "=======\n"
                  + newBaseContent
                  + "\n"
                  + ">>>>>>> BASE      ("
                  + baseCommit
                  + " "
                  + newBaseCommitMessage
                  + ")\n");

      // Verify that a change message has been posted on the change that informs about the conflict
      // and the outdated vote.
      List<ChangeMessageInfo> messages = gApi.changes().id(mergeChangeId.get()).messages();
      assertThat(messages).hasSize(3);
      assertThat(Iterables.getLast(messages).message)
          .isEqualTo(
              "Patch Set 2: Patch Set 1 was rebased\n\n"
                  + "The following files contain Git conflicts:\n"
                  + "* "
                  + file
                  + "\n\n"
                  + "Outdated Votes:\n"
                  + "* Code-Review+1"
                  + " (copy condition: \"changekind:MERGE_FIRST_PARENT_UPDATE\")\n");

      // Rebasing the merge change again should fail
      verifyChangeIsUpToDate(mergeChangeId.toString());
    }

    @Test
    public void rebaseMergeWithConflict_strategyAcceptTheirs() throws Exception {
      rebaseMergeWithConflict_strategy("theirs");
    }

    @Test
    public void rebaseMergeWithConflict_strategyAcceptOurs() throws Exception {
      rebaseMergeWithConflict_strategy("ours");
    }

    private void rebaseMergeWithConflict_strategy(String strategy) throws Exception {
      String file = "foo/a.txt";

      // Create an initial change that adds a file, so that we can modify it later.
      Change.Id initialChange =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file)
              .content("base content")
              .createV1();
      approveAndSubmit(initialChange);

      // Create another branch
      String branchName = "foo";
      BranchInput branchInput = new BranchInput();
      branchInput.ref = branchName;
      branchInput.revision = projectOperations.project(project).getHead("master").name();
      gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

      // Create a change in master that touches the file.
      Change.Id baseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file)
              .content("master content")
              .createV1();
      ObjectId baseChangeCommit =
          changeOperations.change(baseChangeInMaster).currentPatchset().get().commitId();
      approveAndSubmit(baseChangeInMaster);

      // Create a change in the other branch and that also touches the file.
      Change.Id changeInOtherBranch =
          changeOperations
              .newChange()
              .project(project)
              .branch(branchName)
              .file(file)
              .content("other content")
              .createV1();
      approveAndSubmit(changeInOtherBranch);

      // Create a merge change with a conflict resolution for the file.
      String mergeContent = "merged content";
      Change.Id mergeChangeId =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .mergeOfButBaseOnFirst()
              .tipOfBranch("master")
              .and()
              .tipOfBranch(branchName)
              .file(file)
              .content(mergeContent)
              .createV1();
      String commitThatIsBeingRebased = getCurrentRevision(mergeChangeId);

      // Create a change in master onto which the merge change can be rebased.  This change touches
      // the file again so that there is a conflict on rebase.
      String newBaseContent = "conflicting content";
      Change.Id newBaseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file)
              .content(newBaseContent)
              .createV1();
      approveAndSubmit(newBaseChangeInMaster);

      // Rebase the merge change with setting a merge strategy
      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.strategy = strategy;
      rebaseCallWithInput.call(mergeChangeId.toString(), rebaseInput);

      verifyRebaseForChange(
          mergeChangeId,
          baseChangeCommit,
          commitThatIsBeingRebased,
          ImmutableList.of(
              getCurrentRevision(newBaseChangeInMaster), getCurrentRevision(changeInOtherBranch)),
          /* shouldHaveApproval= */ false,
          /* shouldHaveConflicts,= */ false,
          /* expectedNumRevisions= */ 2);

      // Verify the file contents.
      assertThat(getFileContent(mergeChangeId, file))
          .isEqualTo(strategy.equals("theirs") ? newBaseContent : mergeContent);

      // Rebasing the merge change again should fail
      verifyChangeIsUpToDate(mergeChangeId.toString());
    }

    @Test
    public void rebaseWithCommitterEmail() throws Exception {
      // Create three changes with the same parent
      PushOneCommit.Result r1 = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r3 = createChange();

      // Create new user with a secondary email and with permission to rebase
      Account.Id userWithSecondaryEmail =
          accountOperations
              .newAccount()
              .preferredEmail("preferred@domain.org")
              .addSecondaryEmail("secondary@domain.org")
              .create();
      projectOperations
          .project(project)
          .forUpdate()
          .add(allow(Permission.REBASE).ref("refs/heads/master").group(REGISTERED_USERS))
          .update();

      // Approve and submit the r1
      RevisionApi revision = gApi.changes().id(r1.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase r2 as the new user with its primary email
      RebaseInput ri = new RebaseInput();
      ri.committerEmail = "preferred@domain.org";
      requestScopeOperations.setApiUser(userWithSecondaryEmail);
      rebaseCallWithInput.call(r2.getChangeId(), ri);
      assertThat(r2.getChange().getCommitter().getEmailAddress()).isEqualTo(ri.committerEmail);

      // Approve and submit the r3
      requestScopeOperations.setApiUser(admin.id());
      revision = gApi.changes().id(r3.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase r2 as the new user with its secondary email
      ri = new RebaseInput();
      ri.committerEmail = "secondary@domain.org";
      requestScopeOperations.setApiUser(userWithSecondaryEmail);
      rebaseCallWithInput.call(r2.getChangeId(), ri);
      assertThat(r2.getChange().getCommitter().getEmailAddress()).isEqualTo(ri.committerEmail);
    }

    @Test
    public void cannotRebaseWithInvalidCommitterEmail() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result c1 = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result c2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(c1.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase the second change with invalid committer email
      RebaseInput ri = new RebaseInput();
      ri.committerEmail = "invalid@example.com";
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> rebaseCallWithInput.call(c2.getChangeId(), ri));
      assertThat(thrown)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Cannot rebase using committer email '%s' as it is not a registered email of "
                      + "the user on whose behalf the rebase operation is performed",
                  ri.committerEmail));
    }

    @Test
    public void rebaseAbandonedChange() throws Exception {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
      gApi.changes().id(changeId).abandon();
      ChangeInfo info = info(changeId);
      assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);

      ResourceConflictException thrown =
          assertThrows(ResourceConflictException.class, () -> rebaseCall.call(changeId));
      assertThat(thrown)
          .hasMessageThat()
          .contains("Change " + r.getChange().getId() + " is abandoned");
    }

    @Test
    public void rebaseOntoAbandonedChange() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Abandon the first change
      String changeId = r.getChangeId();
      assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
      gApi.changes().id(changeId).abandon();
      ChangeInfo info = info(changeId);
      assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);

      RebaseInput ri = new RebaseInput();
      ri.base = r.getCommit().name();

      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> rebaseCallWithInput.call(r2.getChangeId(), ri));
      assertThat(thrown).hasMessageThat().contains("base change is abandoned: " + changeId);
    }

    @Test
    public void rebaseOntoSelf() throws Exception {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String commit = r.getCommit().name();
      RebaseInput ri = new RebaseInput();
      ri.base = commit;
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class, () -> rebaseCallWithInput.call(changeId, ri));
      assertThat(thrown)
          .hasMessageThat()
          .contains("cannot rebase change " + r.getChange().getId() + " onto itself");
    }

    @Test
    public void rebaseChangeBaseRecursion() throws Exception {
      PushOneCommit.Result r1 = createChange();
      PushOneCommit.Result r2 = createChange();

      RebaseInput ri = new RebaseInput();
      ri.base = r2.getCommit().name();
      String expectedMessage =
          "base change "
              + r2.getChangeId()
              + " is a descendant of the current change - recursion not allowed";
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> rebaseCallWithInput.call(r1.getChangeId(), ri));
      assertThat(thrown).hasMessageThat().contains(expectedMessage);
    }

    @Test
    public void rebaseChangeAfterUpdatingPreferredEmail() throws Exception {
      String emailOne = "email1@example.com";
      Account.Id testUser = accountOperations.newAccount().preferredEmail(emailOne).create();

      // Create two changes both with the same parent
      Change.Id c1 = changeOperations.newChange().project(project).owner(testUser).createV1();
      Change.Id c2 = changeOperations.newChange().project(project).owner(testUser).createV1();

      // Approve and submit the first change
      gApi.changes().id(c1.get()).current().review(ReviewInput.approve());
      gApi.changes().id(c1.get()).current().submit();

      // Change preferred email for the user
      String emailTwo = "email2@example.com";
      accountOperations.account(testUser).forUpdate().preferredEmail(emailTwo).update();
      requestScopeOperations.setApiUser(testUser);

      // Rebase the second change
      gApi.changes().id(c2.get()).rebase();
      assertThat(gApi.changes().id(c2.get()).get().getCurrentRevision().commit.committer.email)
          .isEqualTo(emailOne);
    }

    @Test
    public void cannotRebaseOntoBaseThatIsNotPresentInTargetBranch() throws Exception {
      ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();

      BranchInput branchInput = new BranchInput();
      branchInput.revision = initial.getName();
      gApi.projects().name(project.get()).branch("foo").create(branchInput);

      PushOneCommit.Result r1 =
          pushFactory
              .create(admin.newIdent(), testRepo, "Change on foo branch", "a.txt", "a-content")
              .to("refs/for/foo");
      approve(r1.getChangeId());
      gApi.changes().id(r1.getChangeId()).current().submit();

      // reset HEAD in order to create a sibling of the first change
      testRepo.reset(initial);

      PushOneCommit.Result r2 =
          pushFactory
              .create(admin.newIdent(), testRepo, "Change on master branch", "b.txt", "b-content")
              .to("refs/for/master");

      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.base = r1.getCommit().getName();
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> rebaseCallWithInput.call(r2.getChangeId(), rebaseInput));
      assertThat(thrown)
          .hasMessageThat()
          .contains(
              String.format(
                  "base change is targeting wrong branch: %s,refs/heads/foo", project.get()));

      rebaseInput.base = "refs/heads/foo";
      thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> rebaseCallWithInput.call(r2.getChangeId(), rebaseInput));
      assertThat(thrown)
          .hasMessageThat()
          .contains(
              String.format(
                  "base revision is missing from the destination branch: %s", rebaseInput.base));
    }

    @Test
    public void rebaseChangeWithValidBaseCommit() throws Exception {
      RevCommit desiredBase =
          createNewCommitWithoutChangeId(/* branch= */ "refs/heads/master", "file", "content");
      PushOneCommit.Result child = createChange();
      RebaseInput ri = new RebaseInput();

      // rebase child onto desiredBase (referenced by commit)
      ri.base = desiredBase.getName();
      rebaseCallWithInput.call(child.getChangeId(), ri);

      PatchSet ps2 = child.getPatchSet();
      assertThat(ps2.id().get()).isEqualTo(2);
      RevisionInfo childInfo =
          get(child.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT).getCurrentRevision();
      assertThat(childInfo.commit.parents.get(0).commit).isEqualTo(desiredBase.name());
    }

    @Test
    public void rebaseChangeWithRefsHeadsMaster() throws Exception {
      RevCommit desiredBase =
          createNewCommitWithoutChangeId(/* branch= */ "refs/heads/master", "file", "content");
      PushOneCommit.Result child = createChange();
      RebaseInput ri = new RebaseInput();

      // rebase child onto desiredBase (referenced by ref)
      ri.base = "refs/heads/master";
      rebaseCallWithInput.call(child.getChangeId(), ri);

      PatchSet ps2 = child.getPatchSet();
      assertThat(ps2.id().get()).isEqualTo(2);
      RevisionInfo childInfo =
          get(child.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT).getCurrentRevision();
      assertThat(childInfo.commit.parents.get(0).commit).isEqualTo(desiredBase.name());
    }

    @Test
    public void cannotRebaseChangeWithInvalidBaseCommit() throws Exception {
      // Create another branch and push the desired parent commit to it.
      String branchName = "foo";
      BranchInput branchInput = new BranchInput();
      branchInput.ref = branchName;
      branchInput.revision = projectOperations.project(project).getHead("master").name();
      gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);
      RevCommit desiredBase =
          createNewCommitWithoutChangeId(/* branch= */ "refs/heads/foo", "file", "content");
      // Create the child commit on "master".
      PushOneCommit.Result child = createChange();
      RebaseInput ri = new RebaseInput();

      // Try to rebase child onto desiredBase (referenced by commit)
      ri.base = desiredBase.getName();
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> rebaseCallWithInput.call(child.getChangeId(), ri));

      assertThat(thrown)
          .hasMessageThat()
          .contains(
              String.format("base revision is missing from the destination branch: %s", ri.base));
    }

    @Test
    public void rebaseUpToDateChange() throws Exception {
      PushOneCommit.Result r = createChange();
      verifyChangeIsUpToDate(r);
    }

    @Test
    public void rebaseDoesNotAddWorkInProgress() throws Exception {
      PushOneCommit.Result r = createChange();

      // create an unrelated change so that we can rebase
      testRepo.reset("HEAD~1");
      PushOneCommit.Result unrelated = createChange();
      gApi.changes().id(unrelated.getChangeId()).current().review(ReviewInput.approve());
      gApi.changes().id(unrelated.getChangeId()).current().submit();

      rebaseCall.call(r.getChangeId());

      // change is still ready for review after rebase
      assertThat(gApi.changes().id(r.getChangeId()).get().workInProgress).isNull();
    }

    @Test
    public void rebaseDoesNotRemoveWorkInProgress() throws Exception {
      PushOneCommit.Result r = createChange();
      change(r).setWorkInProgress();

      // create an unrelated change so that we can rebase
      testRepo.reset("HEAD~1");
      PushOneCommit.Result unrelated = createChange();
      gApi.changes().id(unrelated.getChangeId()).current().review(ReviewInput.approve());
      gApi.changes().id(unrelated.getChangeId()).current().submit();

      rebaseCall.call(r.getChangeId());

      // change is still work in progress after rebase
      assertThat(gApi.changes().id(r.getChangeId()).get().workInProgress).isTrue();
    }

    @Test
    public void rebaseAsUploaderInAttentionSet() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      TestAccount admin2 = accountCreator.admin2();
      requestScopeOperations.setApiUser(admin2.id());
      amendChangeWithUploader(r2, project, admin2);
      gApi.changes()
          .id(r2.getChangeId())
          .addToAttentionSet(new AttentionSetInput(admin2.id().toString(), "manual update"));

      rebaseCall.call(r2.getChangeId());
    }

    @Test
    public void rebaseOnChangeNumber() throws Exception {
      String branchTip = testRepo.getRepository().exactRef("HEAD").getObjectId().name();
      PushOneCommit.Result r1 = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      RevisionInfo ri2 =
          get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT).getCurrentRevision();
      assertThat(ri2.commit.parents.get(0).commit).isEqualTo(branchTip);

      Change.Id id1 = r1.getChange().getId();
      RebaseInput in = new RebaseInput();
      in.base = id1.toString();
      rebaseCallWithInput.call(r2.getChangeId(), in);

      Change.Id id2 = r2.getChange().getId();
      ri2 = get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT).getCurrentRevision();
      assertThat(ri2.commit.parents.get(0).commit).isEqualTo(r1.getCommit().name());

      List<RelatedChangeAndCommitInfo> related =
          gApi.changes().id(id2.get()).revision(ri2._number).related().changes;
      assertThat(related).hasSize(2);
      assertThat(related.get(0)._changeNumber).isEqualTo(id2.get());
      assertThat(related.get(0)._revisionNumber).isEqualTo(2);
      assertThat(related.get(1)._changeNumber).isEqualTo(id1.get());
      assertThat(related.get(1)._revisionNumber).isEqualTo(1);
    }

    @Test
    public void rebaseOnClosedChange() throws Exception {
      String branchTip = testRepo.getRepository().exactRef("HEAD").getObjectId().name();
      PushOneCommit.Result r1 = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      RevisionInfo ri2 =
          get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT).getCurrentRevision();
      assertThat(ri2.commit.parents.get(0).commit).isEqualTo(branchTip);

      // Submit first change.
      Change.Id id1 = r1.getChange().getId();
      gApi.changes().id(id1.get()).current().review(ReviewInput.approve());
      gApi.changes().id(id1.get()).current().submit();

      // Rebase second change on first change.
      RebaseInput in = new RebaseInput();
      in.base = id1.toString();
      rebaseCallWithInput.call(r2.getChangeId(), in);

      Change.Id id2 = r2.getChange().getId();
      ri2 = get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT).getCurrentRevision();
      assertThat(ri2.commit.parents.get(0).commit).isEqualTo(r1.getCommit().name());

      assertThat(gApi.changes().id(id2.get()).revision(ri2._number).related().changes).isEmpty();
    }

    @Test
    public void rebaseOnNonExistingChange() throws Exception {
      String changeId = createChange().getChangeId();
      RebaseInput in = new RebaseInput();
      in.base = "999999";
      UnprocessableEntityException exception =
          assertThrows(
              UnprocessableEntityException.class, () -> rebaseCallWithInput.call(changeId, in));
      assertThat(exception).hasMessageThat().contains("Base change not found: " + in.base);
    }

    @Test
    public void rebaseNotAllowedWithoutPermission() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase the second
      String changeId = r2.getChangeId();
      requestScopeOperations.setApiUser(user.id());
      AuthException thrown = assertThrows(AuthException.class, () -> rebaseCall.call(changeId));
      assertThat(thrown)
          .hasMessageThat()
          .isEqualTo(
              "rebase not permitted (change owners and users with the 'Submit' or 'Rebase'"
                  + " permission can rebase if they have the 'Push' permission)");
    }

    @Test
    public void rebaseAllowedWithPermission() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      projectOperations
          .project(project)
          .forUpdate()
          .add(allow(Permission.REBASE).ref("refs/heads/master").group(REGISTERED_USERS))
          .update();

      // Rebase the second
      String changeId = r2.getChangeId();
      requestScopeOperations.setApiUser(user.id());
      rebaseCall.call(changeId);

      // Verify that the committer has been updated
      GitPerson committer =
          gApi.changes().id(r2.getChangeId()).get().getCurrentRevision().commit.committer;
      assertThat(committer.name).isEqualTo(user.fullName());
      assertThat(committer.email).isEqualTo(user.email());
    }

    @Test
    public void rebaseNotAllowedWithoutPushPermission() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      projectOperations
          .project(project)
          .forUpdate()
          .add(allow(Permission.REBASE).ref("refs/heads/master").group(REGISTERED_USERS))
          .add(block(Permission.PUSH).ref("refs/for/*").group(REGISTERED_USERS))
          .update();

      // Rebase the second
      String changeId = r2.getChangeId();
      requestScopeOperations.setApiUser(user.id());
      AuthException thrown = assertThrows(AuthException.class, () -> rebaseCall.call(changeId));
      assertThat(thrown)
          .hasMessageThat()
          .isEqualTo(
              "rebase not permitted (change owners and users with the 'Submit' or 'Rebase'"
                  + " permission can rebase if they have the 'Push' permission)");
    }

    @Test
    public void rebaseNotAllowedForOwnerWithoutPushPermission() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      projectOperations
          .project(project)
          .forUpdate()
          .add(block(Permission.PUSH).ref("refs/for/*").group(REGISTERED_USERS))
          .update();

      // Rebase the second
      String changeId = r2.getChangeId();
      AuthException thrown = assertThrows(AuthException.class, () -> rebaseCall.call(changeId));
      assertThat(thrown)
          .hasMessageThat()
          .isEqualTo(
              "rebase not permitted (change owners and users with the 'Submit' or 'Rebase'"
                  + " permission can rebase if they have the 'Push' permission)");
    }

    @Test
    public void rebaseWithValidationOptions() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.validationOptions = ImmutableMap.of("key", "value");

      TestCommitValidationListener testCommitValidationListener =
          new TestCommitValidationListener();
      try (ExtensionRegistry.Registration unusedRegistration =
          extensionRegistry.newRegistration().add(testCommitValidationListener)) {
        // Rebase the second change
        rebaseCallWithInput.call(r2.getChangeId(), rebaseInput);
        assertThat(testCommitValidationListener.receiveEvent.pushOptions)
            .containsExactly("key", "value");
      }
    }

    @Test
    public void rebaseChangeWhenChecksRefExists() throws Exception {
      RevCommit initialHead = projectOperations.project(project).getHead("master");

      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Create checks ref
      try (TestRepository<Repository> testRepo =
          new TestRepository<>(repoManager.openRepository(project))) {
        testRepo.update(
            RefNames.changeRefPrefix(r2.getChange().getId()) + "checks",
            testRepo.commit().message("Empty commit"));
      }

      // Rebase the second change
      rebaseCall.call(r2.getChangeId());

      verifyRebaseForChange(
          r2.getChange().getId(),
          initialHead,
          r2.getCommit().name(),
          r.getCommit().name(),
          /* shouldHaveApproval= */ false,
          /* expectedNumRevisions= */ 2);
    }

    protected void approveAndSubmit(Change.Id changeId) throws Exception {
      approve(Integer.toString(changeId.get()));
      gApi.changes().id(changeId.get()).current().submit();
    }

    protected String getCurrentRevision(Change.Id changeId) throws RestApiException {
      return gApi.changes().id(changeId.get()).get(CURRENT_REVISION).currentRevision;
    }

    protected String getFileContent(Change.Id changeId, String file)
        throws RestApiException, IOException {
      BinaryResult bin = gApi.changes().id(changeId.get()).current().file(file).content();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      bin.writeTo(os);
      return new String(os.toByteArray(), UTF_8);
    }

    protected void verifyRebaseForChange(
        Change.Id changeId,
        ObjectId base,
        String commitThatIsBeingRebased,
        Change.Id baseChangeId,
        boolean shouldHaveApproval)
        throws RestApiException {
      verifyRebaseForChange(
          changeId, base, commitThatIsBeingRebased, baseChangeId, shouldHaveApproval, 2);
    }

    protected void verifyRebaseForChange(
        Change.Id changeId,
        ObjectId base,
        String commitThatIsBeingRebased,
        Change.Id baseChangeId,
        boolean shouldHaveApproval,
        int expectedNumRevisions)
        throws RestApiException {
      verifyRebaseForChange(
          changeId,
          base,
          commitThatIsBeingRebased,
          ImmutableList.of(getCurrentRevision(baseChangeId)),
          shouldHaveApproval,
          /* shouldHaveConflicts,= */ false,
          expectedNumRevisions);
    }

    protected void verifyRebaseForChange(
        Change.Id changeId,
        ObjectId base,
        String commitThatIsBeingRebased,
        String parentCommit,
        boolean shouldHaveApproval,
        int expectedNumRevisions)
        throws RestApiException {
      verifyRebaseForChange(
          changeId,
          base,
          commitThatIsBeingRebased,
          ImmutableList.of(parentCommit),
          shouldHaveApproval, /* shouldHaveConflicts,= */
          false,
          expectedNumRevisions);
    }

    protected void verifyRebaseForChange(
        Change.Id changeId,
        ObjectId base,
        String commitThatIsBeingRebased,
        List<String> parentCommits,
        boolean shouldHaveApproval,
        boolean shouldHaveConflicts,
        int expectedNumRevisions)
        throws RestApiException {
      ChangeInfo info =
          gApi.changes().id(changeId.get()).get(CURRENT_REVISION, CURRENT_COMMIT, DETAILED_LABELS);

      RevisionInfo r = info.getCurrentRevision();
      assertThat(r._number).isEqualTo(expectedNumRevisions);
      assertThat(r.realUploader).isNull();

      // check conflicts info
      assertThat(r.conflicts).isNotNull();
      assertThat(r.conflicts.base).isEqualTo(base.getName());
      assertThat(r.conflicts.ours).isEqualTo(commitThatIsBeingRebased);
      assertThat(r.conflicts.theirs).isEqualTo(parentCommits.get(0));
      assertThat(r.conflicts.containsConflicts).isEqualTo(shouldHaveConflicts);

      // ...and the parent should be correct
      assertThat(r.commit.parents).hasSize(parentCommits.size());
      for (int parentNum = 0; parentNum < parentCommits.size(); parentNum++) {
        assertWithMessage("parent commit " + parentNum + " for change " + changeId)
            .that(r.commit.parents.get(parentNum).commit)
            .isEqualTo(parentCommits.get(parentNum));
      }

      // ...and the committer and description should be correct
      GitPerson committer = r.commit.committer;
      assertThat(committer.name).isEqualTo(admin.fullName());
      assertThat(committer.email).isEqualTo(admin.email());
      String description = r.description;
      assertThat(description).isEqualTo("Rebase");

      if (shouldHaveApproval) {
        // ...and the approval was copied
        LabelInfo cr = info.labels.get(LabelId.CODE_REVIEW);
        assertThat(cr).isNotNull();
        assertThat(cr.all).isNotNull();
        assertThat(cr.all).hasSize(1);
        assertThat(cr.all.get(0).value).isEqualTo(1);
      }
    }

    protected void verifyChangeIsUpToDate(PushOneCommit.Result r) {
      verifyChangeIsUpToDate(r.getChangeId());
    }

    protected void verifyChangeIsUpToDate(String changeId) {
      ResourceConflictException thrown =
          assertThrows(ResourceConflictException.class, () -> rebaseCall.call(changeId));
      assertThat(thrown).hasMessageThat().contains("Change is already up to date");
    }

    protected static class TestWorkInProgressStateChangedListener
        implements WorkInProgressStateChangedListener {
      boolean invoked;
      Boolean wip;

      @Override
      public void onWorkInProgressStateChanged(WorkInProgressStateChangedListener.Event event) {
        this.invoked = true;
        this.wip =
            event.getChange().workInProgress != null ? event.getChange().workInProgress : false;
      }
    }
  }

  public abstract static class Rebase extends Base {
    @Test
    public void rebaseChangeBase() throws Exception {
      PushOneCommit.Result r1 = createChange();
      PushOneCommit.Result r2 = createChange();
      PushOneCommit.Result r3 = createChange();
      RebaseInput ri = new RebaseInput();

      // rebase r3 directly onto master (break dep. towards r2)
      ri.base = "";
      rebaseCallWithInput.call(r3.getChangeId(), ri);
      PatchSet ps3 = r3.getPatchSet();
      assertThat(ps3.id().get()).isEqualTo(2);

      // rebase r2 onto r3 (referenced by ref)
      ri.base = ps3.id().toRefName();
      rebaseCallWithInput.call(r2.getChangeId(), ri);
      PatchSet ps2 = r2.getPatchSet();
      assertThat(ps2.id().get()).isEqualTo(2);

      // rebase r1 onto r2 (referenced by commit)
      ri.base = ps2.commitId().name();
      rebaseCallWithInput.call(r1.getChangeId(), ri);
      PatchSet ps1 = r1.getPatchSet();
      assertThat(ps1.id().get()).isEqualTo(2);

      // rebase r1 onto r3 (referenced by change number)
      ri.base = String.valueOf(r3.getChange().getId().get());
      rebaseCallWithInput.call(r1.getChangeId(), ri);
      assertThat(r1.getPatchSetId().get()).isEqualTo(3);
    }

    private void rebaseWithConflict_strategy(String strategy) throws Exception {
      String patchSetSubject = "patch set change";
      String patchSetContent = "patch set content";
      String baseSubject = "base change";
      String baseContent = "base content";
      String expectedContent = strategy.equals("theirs") ? baseContent : patchSetContent;

      RevCommit initialHead = projectOperations.project(project).getHead("master");

      PushOneCommit.Result r1 = createChange(baseSubject, PushOneCommit.FILE_NAME, baseContent);
      gApi.changes()
          .id(r1.getChangeId())
          .revision(r1.getCommit().name())
          .review(ReviewInput.approve());
      gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).submit();

      testRepo.reset("HEAD~1");
      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(),
              testRepo,
              patchSetSubject,
              PushOneCommit.FILE_NAME,
              patchSetContent);
      PushOneCommit.Result r2 = push.to("refs/for/master");
      r2.assertOkStatus();

      String changeId = r2.getChangeId();
      RevCommit patchSet = r2.getCommit();
      RevCommit base = r1.getCommit();

      TestWorkInProgressStateChangedListener wipStateChangedListener =
          new TestWorkInProgressStateChangedListener();
      try (ExtensionRegistry.Registration registration =
          extensionRegistry.newRegistration().add(wipStateChangedListener)) {
        RebaseInput rebaseInput = new RebaseInput();
        rebaseInput.strategy = strategy;

        testMetricMaker.reset();
        ChangeInfo changeInfo =
            gApi.changes().id(changeId).revision(patchSet.name()).rebaseAsInfo(rebaseInput);
        assertThat(changeInfo.containsGitConflicts).isNull();
        assertThat(changeInfo.workInProgress).isNull();

        // field1 is on_behalf_of_uploader, field2 is rebase_chain, field3 is allow_conflicts
        assertThat(testMetricMaker.getCount("change/count_rebases", false, false, false))
            .isEqualTo(1);
      }
      assertThat(wipStateChangedListener.invoked).isFalse();
      assertThat(wipStateChangedListener.wip).isNull();

      // To get the revisions, we must retrieve the change with more change options.
      ChangeInfo changeInfo =
          gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
      assertThat(changeInfo.revisions).hasSize(2);
      RevisionInfo currentRevision = changeInfo.getCurrentRevision();
      assertThat(currentRevision.commit.parents.get(0).commit).isEqualTo(base.name());
      assertThat(currentRevision.conflicts).isNotNull();
      assertThat(currentRevision.conflicts.base).isEqualTo(initialHead.name());
      assertThat(currentRevision.conflicts.ours).isEqualTo(patchSet.name());
      assertThat(currentRevision.conflicts.theirs).isEqualTo(base.name());
      assertThat(currentRevision.conflicts.containsConflicts).isFalse();

      // Verify that the file content in the created patch set is correct.
      BinaryResult bin =
          gApi.changes().id(changeId).current().file(PushOneCommit.FILE_NAME).content();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      bin.writeTo(os);
      String fileContent = new String(os.toByteArray(), UTF_8);
      assertThat(fileContent).isEqualTo(expectedContent);

      // Verify the message that has been posted on the change.
      List<ChangeMessageInfo> messages = gApi.changes().id(changeId).messages();
      assertThat(messages).hasSize(2);
      assertThat(Iterables.getLast(messages).message)
          .isEqualTo("Patch Set 2: Patch Set 1 was rebased");
    }

    @Test
    public void rebaseWithConflict_strategyAcceptTheirs() throws Exception {
      rebaseWithConflict_strategy("theirs");
    }

    @Test
    public void rebaseWithConflict_strategyAcceptOurs() throws Exception {
      rebaseWithConflict_strategy("ours");
    }

    @Test
    public void rebaseWithConflictWithCnflictsAllowed() throws Exception {
      testRebaseWithConflictWithConflictsAllowed(/* useDiff3= */ false);
    }

    @Test
    @GerritConfig(name = "change.diff3ConflictView", value = "true")
    public void rebaseWithConflictWithConflictsAllowedUsingDiff3() throws Exception {
      testRebaseWithConflictWithConflictsAllowed(/* useDiff3= */ true);
    }

    private void testRebaseWithConflictWithConflictsAllowed(boolean useDiff3) throws Exception {
      String patchSetSubject = "patch set change";
      String patchSetContent = "patch set content";
      String baseSubject = "base change";
      String baseContent = "base content";

      RevCommit initialHead = projectOperations.project(project).getHead("master");

      PushOneCommit.Result r1 = createChange(baseSubject, PushOneCommit.FILE_NAME, baseContent);
      gApi.changes()
          .id(r1.getChangeId())
          .revision(r1.getCommit().name())
          .review(ReviewInput.approve());
      gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).submit();

      testRepo.reset("HEAD~1");
      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(),
              testRepo,
              patchSetSubject,
              PushOneCommit.FILE_NAME,
              patchSetContent);
      PushOneCommit.Result r2 = push.to("refs/for/master");
      r2.assertOkStatus();

      String changeId = r2.getChangeId();
      RevCommit patchSet = r2.getCommit();
      RevCommit base = r1.getCommit();

      TestWorkInProgressStateChangedListener wipStateChangedListener =
          new TestWorkInProgressStateChangedListener();
      try (ExtensionRegistry.Registration registration =
          extensionRegistry.newRegistration().add(wipStateChangedListener)) {
        RebaseInput rebaseInput = new RebaseInput();
        rebaseInput.allowConflicts = true;
        testMetricMaker.reset();
        ChangeInfo changeInfo =
            gApi.changes().id(changeId).revision(patchSet.name()).rebaseAsInfo(rebaseInput);
        assertThat(changeInfo.containsGitConflicts).isTrue();
        assertThat(changeInfo.workInProgress).isTrue();

        // field1 is on_behalf_of_uploader, field2 is rebase_chain, field3 is allow_conflicts
        assertThat(testMetricMaker.getCount("change/count_rebases", false, false, true))
            .isEqualTo(1);
      }
      assertThat(wipStateChangedListener.invoked).isTrue();
      assertThat(wipStateChangedListener.wip).isTrue();

      // To get the revisions, we must retrieve the change with more change options.
      ChangeInfo changeInfo =
          gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
      assertThat(changeInfo.revisions).hasSize(2);

      RevisionInfo currentRevision = changeInfo.getCurrentRevision();
      assertThat(currentRevision.commit.parents.get(0).commit).isEqualTo(base.name());
      assertThat(currentRevision.conflicts).isNotNull();
      assertThat(currentRevision.conflicts.base).isEqualTo(initialHead.name());
      assertThat(currentRevision.conflicts.ours).isEqualTo(patchSet.name());
      assertThat(currentRevision.conflicts.theirs).isEqualTo(base.name());
      assertThat(currentRevision.conflicts.containsConflicts).isTrue();

      // Verify that the file content in the created patch set is correct.
      // We expect that it has conflict markers to indicate the conflict.
      BinaryResult bin =
          gApi.changes().id(changeId).current().file(PushOneCommit.FILE_NAME).content();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      bin.writeTo(os);
      String fileContent = new String(os.toByteArray(), UTF_8);
      assertThat(fileContent)
          .isEqualTo(
              "<<<<<<< PATCH SET ("
                  + patchSet.getName()
                  + " "
                  + patchSetSubject
                  + ")\n"
                  + patchSetContent
                  + "\n"
                  + (useDiff3
                      ? String.format(
                          "||||||| BASE      (%s %s)\n",
                          initialHead.getName(), initialHead.getShortMessage())
                      : "")
                  + "=======\n"
                  + baseContent
                  + "\n"
                  + ">>>>>>> BASE      ("
                  + base.getName()
                  + " "
                  + baseSubject
                  + ")\n");

      // Verify the message that has been posted on the change.
      List<ChangeMessageInfo> messages = gApi.changes().id(changeId).messages();
      assertThat(messages).hasSize(2);
      assertThat(Iterables.getLast(messages).message)
          .isEqualTo(
              "Patch Set 2: Patch Set 1 was rebased\n\n"
                  + "The following files contain Git conflicts:\n"
                  + "* "
                  + PushOneCommit.FILE_NAME
                  + "\n");
    }

    @Test
    public void rebaseWithConflict_conflictsForbidden() throws Exception {
      PushOneCommit.Result r1 = createChange();
      gApi.changes()
          .id(r1.getChangeId())
          .revision(r1.getCommit().name())
          .review(ReviewInput.approve());
      gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).submit();

      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(),
              testRepo,
              PushOneCommit.SUBJECT,
              PushOneCommit.FILE_NAME,
              "other content",
              "If09d8782c1e59dd0b33de2b1ec3595d69cc10ad5");
      PushOneCommit.Result r2 = push.to("refs/for/master");
      r2.assertOkStatus();
      ResourceConflictException exception =
          assertThrows(ResourceConflictException.class, () -> rebaseCall.call(r2.getChangeId()));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Change %s could not be rebased due to a conflict during merge.\n\n"
                      + "merge conflict(s):\n%s",
                  r2.getChange().getId(), PushOneCommit.FILE_NAME));
    }

    @Test
    @GerritConfig(name = "core.useGitattributesForMerge", value = "true")
    public void rebaseWithAttributes_UnionContentMerge() throws Exception {
      PushOneCommit pushAttributes =
          pushFactory.create(
              admin.newIdent(),
              testRepo,
              "add merge=union to gitattributes",
              ".gitattributes",
              "*.txt merge=union");
      PushOneCommit.Result r1 = pushAttributes.to("refs/heads/master");

      PushOneCommit.Result r2 = createChange();
      gApi.changes()
          .id(r2.getChangeId())
          .revision(r2.getCommit().name())
          .review(ReviewInput.approve());
      gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).submit();

      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(),
              testRepo,
              PushOneCommit.SUBJECT,
              PushOneCommit.FILE_NAME,
              "other content",
              "I3bf2c82554e83abc759154e85db94c7ebb079c70");
      PushOneCommit.Result r3 = push.to("refs/for/master");
      r3.assertOkStatus();
      String changeId = r3.getChangeId();
      RevCommit patchSet = r3.getCommit();
      RevCommit base = r2.getCommit();
      RebaseInput rebaseInput = new RebaseInput();
      rebaseInput.strategy = "recursive";
      ChangeInfo changeInfo =
          gApi.changes().id(changeId).revision(patchSet.name()).rebaseAsInfo(rebaseInput);
      assertThat(changeInfo.containsGitConflicts).isNull();
      assertThat(changeInfo.workInProgress).isNull();

      RevisionInfo currentRevision =
          gApi.changes().id(changeId).get(CURRENT_REVISION).getCurrentRevision();
      assertThat(currentRevision.conflicts).isNotNull();
      assertThat(currentRevision.conflicts.base).isEqualTo(r1.getCommit().name());
      assertThat(currentRevision.conflicts.ours).isEqualTo(patchSet.name());
      assertThat(currentRevision.conflicts.theirs).isEqualTo(base.name());
      assertThat(currentRevision.conflicts.containsConflicts).isFalse();

      // Verify that the file content in the created patch set is correct.
      // We expect that it has no conflict markers and the content of both changes.
      BinaryResult bin =
          gApi.changes().id(changeId).current().file(PushOneCommit.FILE_NAME).content();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      bin.writeTo(os);
      String fileContent = new String(os.toByteArray(), UTF_8);
      assertThat(fileContent).isEqualTo("other content" + "\n" + PushOneCommit.FILE_CONTENT);
    }

    @Test
    public void rebaseFromRelationChainToClosedChange() throws Exception {
      PushOneCommit.Result r1 = createChange();
      testRepo.reset("HEAD~1");

      createChange();
      PushOneCommit.Result r3 = createChange();

      // Submit first change.
      Change.Id id1 = r1.getChange().getId();
      gApi.changes().id(id1.get()).current().review(ReviewInput.approve());
      gApi.changes().id(id1.get()).current().submit();

      // Rebase third change on first change.
      RebaseInput in = new RebaseInput();
      in.base = id1.toString();
      rebaseCallWithInput.call(r3.getChangeId(), in);

      Change.Id id3 = r3.getChange().getId();
      RevisionInfo ri3 =
          get(r3.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT).getCurrentRevision();
      assertThat(ri3.commit.parents.get(0).commit).isEqualTo(r1.getCommit().name());

      assertThat(gApi.changes().id(id3.get()).revision(ri3._number).related().changes).isEmpty();
    }

    @Test
    public void testCountRebasesMetric() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase the second change
      testMetricMaker.reset();
      rebaseCallWithInput.call(r2.getChangeId(), new RebaseInput());
      // field1 is on_behalf_of_uploader, field2 is rebase_chain, field3 is allow_conflicts
      assertThat(testMetricMaker.getCount("change/count_rebases", false, false, false))
          .isEqualTo(1);
      assertThat(testMetricMaker.getCount("change/count_rebases", true, false, false)).isEqualTo(0);
      assertThat(testMetricMaker.getCount("change/count_rebases", true, true, false)).isEqualTo(0);
      assertThat(testMetricMaker.getCount("change/count_rebases", false, true, false)).isEqualTo(0);
    }

    @Test
    public void rebaseActionEnabledIfChangeCanBeRebased() throws Exception {
      Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).createV1();
      Change.Id changeToBeRebased = changeOperations.newChange().project(project).createV1();

      // Change cannot be rebased since its parent commit is the same commit as the HEAD of the
      // destination branch.
      RevisionInfo currentRevisionInfo =
          gApi.changes().id(changeToBeRebased.get()).get().getCurrentRevision();
      assertThat(currentRevisionInfo.actions).containsKey("rebase");
      ActionInfo rebaseActionInfo = currentRevisionInfo.actions.get("rebase");
      assertThat(rebaseActionInfo.enabled).isNull();

      // Approve and submit the change that will be the new base for the chain so that the chain is
      // rebasable.
      gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
      gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

      // Change can be rebased since its parent commit differs from the commit at the HEAD of the
      // destination branch.
      currentRevisionInfo = gApi.changes().id(changeToBeRebased.get()).get().getCurrentRevision();
      assertThat(currentRevisionInfo.actions).containsKey("rebase");
      rebaseActionInfo = currentRevisionInfo.actions.get("rebase");
      assertThat(rebaseActionInfo.enabled).isTrue();
    }

    @Test
    public void rebaseActionEnabledIfChangeHasAParentChange() throws Exception {
      Change.Id change1 = changeOperations.newChange().project(project).createV1();
      Change.Id change2 =
          changeOperations.newChange().project(project).childOf().change(change1).createV1();

      // change1 cannot be rebased since its parent commit is the same commit as the HEAD of the
      // destination branch.
      RevisionInfo currentRevisionInfo =
          gApi.changes().id(change1.get()).get().getCurrentRevision();
      assertThat(currentRevisionInfo.actions).containsKey("rebase");
      ActionInfo rebaseActionInfo = currentRevisionInfo.actions.get("rebase");
      assertThat(rebaseActionInfo.enabled).isNull();

      // change2 can be rebased to break the relation to change1
      currentRevisionInfo = gApi.changes().id(change2.get()).get().getCurrentRevision();
      assertThat(currentRevisionInfo.actions).containsKey("rebase");
      rebaseActionInfo = currentRevisionInfo.actions.get("rebase");
      assertThat(rebaseActionInfo.enabled).isTrue();
    }
  }

  public static class RebaseViaRevisionApi extends Rebase {
    @Before
    public void setUp() throws Exception {
      init(
          id -> gApi.changes().id(id).current().rebase(),
          (id, in) -> gApi.changes().id(id).current().rebase(in));
    }

    @Test
    public void rebaseOutdatedPatchSet() throws Exception {
      String fileName1 = "a.txt";
      String fileContent1 = "some content";
      String fileName2 = "b.txt";
      String fileContent2Ps1 = "foo";
      String fileContent2Ps2 = "foo/bar";

      // Create two changes both with the same parent touching disjunct files
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo, PushOneCommit.SUBJECT, fileName1, fileContent1)
              .to("refs/for/master");
      r.assertOkStatus();
      String changeId1 = r.getChangeId();
      testRepo.reset("HEAD~1");
      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(), testRepo, PushOneCommit.SUBJECT, fileName2, fileContent2Ps1);
      PushOneCommit.Result r2 = push.to("refs/for/master");
      r2.assertOkStatus();
      String changeId2 = r2.getChangeId();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(changeId1).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Amend the second change so that it has 2 patch sets
      amendChange(
              changeId2,
              "refs/for/master",
              admin,
              testRepo,
              PushOneCommit.SUBJECT,
              fileName2,
              fileContent2Ps2)
          .assertOkStatus();
      assertThat(gApi.changes().id(changeId2).get().getCurrentRevision()._number).isEqualTo(2);

      // Rebase the first patch set of the second change
      gApi.changes().id(changeId2).revision(1).rebase();

      // Second change should have 3 patch sets
      assertThat(gApi.changes().id(changeId2).get().getCurrentRevision()._number).isEqualTo(3);

      // ... and the committer and description should be correct
      ChangeInfo info = gApi.changes().id(changeId2).get(CURRENT_REVISION, CURRENT_COMMIT);
      GitPerson committer = info.getCurrentRevision().commit.committer;
      assertThat(committer.name).isEqualTo(admin.fullName());
      assertThat(committer.email).isEqualTo(admin.email());
      String description = info.getCurrentRevision().description;
      assertThat(description).isEqualTo("Rebase");

      // ... and the file contents should match with patch set 1 based on change1
      assertThat(gApi.changes().id(changeId2).current().file(fileName1).content().asString())
          .isEqualTo(fileContent1);
      assertThat(gApi.changes().id(changeId2).current().file(fileName2).content().asString())
          .isEqualTo(fileContent2Ps1);
    }
  }

  public static class RebaseViaChangeApi extends Rebase {
    @Before
    public void setUp() throws Exception {
      init(id -> gApi.changes().id(id).rebase(), (id, in) -> gApi.changes().id(id).rebase(in));
    }
  }

  public static class RebaseChain extends Base {
    @Before
    public void setUp() throws Exception {
      init(
          id -> {
            @SuppressWarnings("unused")
            Object unused = gApi.changes().id(id).rebaseChain();
          },
          (id, in) -> {
            @SuppressWarnings("unused")
            Object unused = gApi.changes().id(id).rebaseChain(in);
          });
    }

    @Override
    protected void verifyChangeIsUpToDate(String changeId) {
      ResourceConflictException thrown =
          assertThrows(ResourceConflictException.class, () -> rebaseCall.call(changeId));
      assertThat(thrown).hasMessageThat().contains("The whole chain is already up to date.");
    }

    @Override
    @Test
    public void rebaseWithCommitterEmail() throws Exception {
      // Create changes with the following hierarchy:
      // * HEAD
      //   * r1
      //   * r2

      PushOneCommit.Result r1 = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r1.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Create new user with a secondary email and with permission to rebase
      Account.Id userWithSecondaryEmail =
          accountOperations
              .newAccount()
              .preferredEmail("preferred@domain.org")
              .addSecondaryEmail("secondary@domain.org")
              .create();
      projectOperations
          .project(project)
          .forUpdate()
          .add(allow(Permission.REBASE).ref("refs/heads/master").group(REGISTERED_USERS))
          .update();

      // Rebase the chain through r2 with the new user and with its secondary email.
      RebaseInput ri = new RebaseInput();
      ri.committerEmail = "secondary@domain.org";
      requestScopeOperations.setApiUser(userWithSecondaryEmail);
      BadRequestException exception =
          assertThrows(
              BadRequestException.class, () -> gApi.changes().id(r2.getChangeId()).rebaseChain(ri));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo("committer_email is not supported when rebasing a chain");
    }

    @Override
    @Test
    public void cannotRebaseWithInvalidCommitterEmail() throws Exception {
      // Create two changes both with the same parent
      PushOneCommit.Result c1 = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result c2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(c1.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase the second change with invalid committer email
      RebaseInput ri = new RebaseInput();
      ri.committerEmail = "invalid@example.com";
      BadRequestException exception =
          assertThrows(
              BadRequestException.class, () -> gApi.changes().id(c2.getChangeId()).rebaseChain(ri));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo("committer_email is not supported when rebasing a chain");
    }

    @Test
    public void rebaseChain() throws Exception {
      // Create changes with the following hierarchy:
      // * HEAD
      //   * r (merged)
      //   * r2
      //     * r3
      //       * r4
      //         *r5
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      RevCommit head = projectOperations.project(project).getHead("master");
      PushOneCommit.Result r2 = createChange();
      String r2PatchSet1 = getCurrentRevision(r2.getChange().getId());
      PushOneCommit.Result r3 = createChange();
      String r3PatchSet1 = getCurrentRevision(r3.getChange().getId());
      PushOneCommit.Result r4 = createChange();
      String r4PatchSet1 = getCurrentRevision(r4.getChange().getId());
      PushOneCommit.Result r5 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Add an approval whose score should be copied on trivial rebase
      gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.recommend());
      gApi.changes().id(r3.getChangeId()).current().review(ReviewInput.recommend());

      // Rebase the chain through r4.
      verifyRebaseChainResponse(
          gApi.changes().id(r4.getChangeId()).rebaseChain(), false, r2, r3, r4);

      // Only r2, r3 and r4 are rebased.
      verifyRebaseForChange(
          r2.getChange().getId(), head, r2.getCommit().name(), r.getCommit().name(), true, 2);
      verifyRebaseForChange(
          r3.getChange().getId(),
          ObjectId.fromString(r2PatchSet1),
          r3.getCommit().name(),
          r2.getChange().getId(),
          true);
      verifyRebaseForChange(
          r4.getChange().getId(),
          ObjectId.fromString(r3PatchSet1),
          r4.getCommit().name(),
          r3.getChange().getId(),
          false);

      verifyChangeIsUpToDate(r2);
      verifyChangeIsUpToDate(r3);
      verifyChangeIsUpToDate(r4);

      // r5 wasn't rebased.
      assertThat(
              gApi.changes()
                  .id(r5.getChangeId())
                  .get(CURRENT_REVISION)
                  .getCurrentRevision()
                  ._number)
          .isEqualTo(1);

      // Rebasing r5
      verifyRebaseChainResponse(
          gApi.changes().id(r5.getChangeId()).rebaseChain(), false, r2, r3, r4, r5);

      verifyRebaseForChange(
          r5.getChange().getId(),
          ObjectId.fromString(r4PatchSet1),
          r5.getCommit().name(),
          r4.getChange().getId(),
          false);
    }

    @Test
    public void rebaseChainWithMerges() throws Exception {
      String file1 = "foo/a.txt";
      String file2 = "bar/b.txt";

      // Create an initial change that adds file1, so that we can modify it later.
      Change.Id initialChange =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file1)
              .content("base content")
              .createV1();
      approveAndSubmit(initialChange);

      // Create another branch
      String branchName = "foo";
      BranchInput branchInput = new BranchInput();
      branchInput.ref = branchName;
      branchInput.revision = projectOperations.project(project).getHead("master").name();
      gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

      // Create a change in master that touches file1.
      Change.Id baseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file1)
              .content("master content")
              .createV1();
      approveAndSubmit(baseChangeInMaster);

      // Create a change in the other branch and that also touches file1.
      Change.Id changeInOtherBranch =
          changeOperations
              .newChange()
              .project(project)
              .branch(branchName)
              .file(file1)
              .content("other content")
              .createV1();
      approveAndSubmit(changeInOtherBranch);

      RevCommit head = projectOperations.project(project).getHead("master");

      // Create a merge change with a conflict resolution.
      Change.Id mergeChangeId =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .mergeOfButBaseOnFirst()
              .tipOfBranch("master")
              .and()
              .tipOfBranch(branchName)
              .file(file1)
              .content("merged content")
              .createV1();
      String mergeCommitThatIsBeingRebased = getCurrentRevision(mergeChangeId);

      // Create a follow up change.
      Change.Id followUpChangeId =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .childOf()
              .change(mergeChangeId)
              .file(file1)
              .content("modified content")
              .createV1();
      String followUpCommitThatIsBeingRebased = getCurrentRevision(followUpChangeId);

      // Create another change in the other branch so that we can create another merge
      Change.Id anotherChangeInOtherBranch =
          changeOperations
              .newChange()
              .project(project)
              .branch(branchName)
              .file(file1)
              .content("yet another content")
              .createV1();
      approveAndSubmit(anotherChangeInOtherBranch);

      // Create a second merge change with a conflict resolution.
      Change.Id followUpMergeChangeId =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .childOf()
              .change(followUpChangeId)
              .mergeOfButBaseOnFirst()
              .change(followUpChangeId)
              .and()
              .tipOfBranch(branchName)
              .file(file1)
              .content("another merged content")
              .createV1();
      String followUpMergeCommitThatIsBeingRebased = getCurrentRevision(followUpMergeChangeId);

      // Create a change in master onto which the chain can be rebased. This change touches an
      // unrelated file (file2) so that there is no conflict on rebase.
      Change.Id newBaseChangeInMaster =
          changeOperations
              .newChange()
              .project(project)
              .branch("master")
              .file(file2)
              .content("other content")
              .createV1();
      approveAndSubmit(newBaseChangeInMaster);

      // Rebase the chain
      RebaseChainInfo rebaseChainInfo =
          gApi.changes().id(followUpMergeChangeId.get()).rebaseChain().value();
      assertThat(rebaseChainInfo.rebasedChanges).hasSize(3);
      assertThat(rebaseChainInfo.containsGitConflicts).isNull();

      verifyRebaseForChange(
          mergeChangeId,
          head,
          mergeCommitThatIsBeingRebased,
          ImmutableList.of(
              getCurrentRevision(newBaseChangeInMaster), getCurrentRevision(changeInOtherBranch)),
          /* shouldHaveApproval= */ false,
          /* shouldHaveConflicts,= */ false,
          /* expectedNumRevisions= */ 2);
      verifyRebaseForChange(
          followUpChangeId,
          ObjectId.fromString(mergeCommitThatIsBeingRebased),
          followUpCommitThatIsBeingRebased,
          ImmutableList.of(getCurrentRevision(mergeChangeId)),
          /* shouldHaveApproval= */ false,
          /* shouldHaveConflicts,= */ false,
          /* expectedNumRevisions= */ 2);
      verifyRebaseForChange(
          followUpMergeChangeId,
          ObjectId.fromString(followUpCommitThatIsBeingRebased),
          followUpMergeCommitThatIsBeingRebased,
          ImmutableList.of(
              getCurrentRevision(followUpChangeId), getCurrentRevision(anotherChangeInOtherBranch)),
          /* shouldHaveApproval= */ false,
          /* shouldHaveConflicts,= */ false,
          /* expectedNumRevisions= */ 2);

      // Verify the file contents.
      assertThat(getFileContent(mergeChangeId, file1)).isEqualTo("merged content");
      assertThat(getFileContent(mergeChangeId, file2)).isEqualTo("other content");
      assertThat(getFileContent(followUpChangeId, file1)).isEqualTo("modified content");
      assertThat(getFileContent(followUpChangeId, file2)).isEqualTo("other content");
      assertThat(getFileContent(followUpMergeChangeId, file1)).isEqualTo("another merged content");
      assertThat(getFileContent(followUpMergeChangeId, file2)).isEqualTo("other content");

      // Rebasing the chain again should fail
      verifyChangeIsUpToDate(followUpChangeId.toString());
    }

    @Test
    public void rebasePartlyOutdatedChain() throws Exception {
      final String file = "modified_file.txt";
      final String oldContent = "old content";
      final String newContent = "new content";
      // Create changes with the following revision hierarchy:
      // * HEAD
      //   * r (merged)
      //   * r2
      //     * r3/1    r3/2
      //       * r4
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      RevCommit head = projectOperations.project(project).getHead("master");
      PushOneCommit.Result r2 = createChange();
      String r2PatchSet1 = getCurrentRevision(r2.getChange().getId());
      PushOneCommit.Result r3 = createChange("original patch-set", file, oldContent);
      String r3PatchSet1 = getCurrentRevision(r3.getChange().getId());
      PushOneCommit.Result r4 = createChange();
      gApi.changes()
          .id(r3.getChangeId())
          .edit()
          .modifyFile(file, RawInputUtil.create(newContent.getBytes(UTF_8)));
      gApi.changes().id(r3.getChangeId()).edit().publish();
      String r3PatchSet2 = getCurrentRevision(r3.getChange().getId());

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase the chain through r4.
      rebaseCall.call(r4.getChangeId());

      verifyRebaseForChange(
          r2.getChange().getId(), head, r2.getCommit().name(), r.getCommit().name(), false, 2);
      verifyRebaseForChange(
          r3.getChange().getId(),
          ObjectId.fromString(r2PatchSet1),
          r3PatchSet2,
          r2.getChange().getId(),
          false,
          3);
      verifyRebaseForChange(
          r4.getChange().getId(),
          ObjectId.fromString(r3PatchSet1),
          r4.getCommit().name(),
          r3.getChange().getId(),
          false);

      assertThat(gApi.changes().id(r3.getChangeId()).current().file(file).content().asString())
          .isEqualTo(newContent);

      verifyChangeIsUpToDate(r2);
      verifyChangeIsUpToDate(r3);
      verifyChangeIsUpToDate(r4);
    }

    @Test
    public void rebaseChainWithMergedAncestor() throws Exception {
      final String file = "modified_file.txt";
      final String newContent = "new content";

      // Create changes with the following hierarchy:
      // * HEAD
      //   * r (merged)
      //   * r2.1         r2.2 (merged)
      //     * r3
      //       * r4
      //         *r5
      PushOneCommit.Result r = createChange();
      PushOneCommit.Result r2 = createChange();
      String r2PatchSet1 = getCurrentRevision(r2.getChange().getId());
      PushOneCommit.Result r3 = createChange();
      String r3PatchSet1 = getCurrentRevision(r3.getChange().getId());
      PushOneCommit.Result r4 = createChange();
      String r4PatchSet1 = getCurrentRevision(r4.getChange().getId());
      PushOneCommit.Result r5 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();
      testRepo.reset("HEAD~1");

      // Create r2.2
      gApi.changes()
          .id(r2.getChangeId())
          .edit()
          .modifyFile(file, RawInputUtil.create(newContent.getBytes(UTF_8)));
      gApi.changes().id(r2.getChangeId()).edit().publish();
      // Approve and submit r2.2
      revision = gApi.changes().id(r2.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Add an approval whose score should be copied on trivial rebase
      gApi.changes().id(r3.getChangeId()).current().review(ReviewInput.recommend());

      // Rebase the chain through r4.
      verifyRebaseChainResponse(gApi.changes().id(r4.getChangeId()).rebaseChain(), false, r3, r4);

      // Only r3 and r4 are rebased.
      verifyRebaseForChange(
          r3.getChange().getId(),
          ObjectId.fromString(r2PatchSet1),
          r3.getCommit().name(),
          r2.getChange().getId(),
          true);
      verifyRebaseForChange(
          r4.getChange().getId(),
          ObjectId.fromString(r3PatchSet1),
          r4.getCommit().name(),
          r3.getChange().getId(),
          false);

      verifyChangeIsUpToDate(r2);
      verifyChangeIsUpToDate(r3);
      verifyChangeIsUpToDate(r4);

      // r5 wasn't rebased.
      assertThat(
              gApi.changes()
                  .id(r5.getChangeId())
                  .get(CURRENT_REVISION)
                  .getCurrentRevision()
                  ._number)
          .isEqualTo(1);

      // Rebasing r5
      verifyRebaseChainResponse(
          gApi.changes().id(r5.getChangeId()).rebaseChain(), false, r3, r4, r5);

      verifyRebaseForChange(
          r5.getChange().getId(),
          ObjectId.fromString(r4PatchSet1),
          r5.getCommit().name(),
          r4.getChange().getId(),
          false);
    }

    @Test
    public void rebaseChainWithConflicts_conflictsForbidden() throws Exception {
      PushOneCommit.Result r1 = createChange();
      gApi.changes()
          .id(r1.getChangeId())
          .revision(r1.getCommit().name())
          .review(ReviewInput.approve());
      gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).submit();

      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(),
              testRepo,
              PushOneCommit.SUBJECT,
              PushOneCommit.FILE_NAME,
              "other content",
              "I0020020020020020020020020020020020020002");
      PushOneCommit.Result r2 = push.to("refs/for/master");
      r2.assertOkStatus();
      PushOneCommit.Result r3 = createChange("refs/for/master");
      r3.assertOkStatus();
      ResourceConflictException exception =
          assertThrows(
              ResourceConflictException.class,
              () -> gApi.changes().id(r3.getChangeId()).rebaseChain());
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Change %s could not be rebased due to a conflict during merge.\n\n"
                      + "merge conflict(s):\n%s",
                  r2.getChange().getId(), PushOneCommit.FILE_NAME));
    }

    @Test
    public void rebaseChainWithConflictsConflictsAllowed() throws Exception {
      testRebaseChainWithConflictsConflictsAllowed(/* useDiff3= */ false);
    }

    @Test
    @GerritConfig(name = "change.diff3ConflictView", value = "true")
    public void rebaseChainWithConflictsConflictsAllowedUsingDiff3() throws Exception {
      testRebaseChainWithConflictsConflictsAllowed(/* useDiff3= */ true);
    }

    private void testRebaseChainWithConflictsConflictsAllowed(boolean useDiff3) throws Exception {
      String patchSetSubject = "patch set change";
      String patchSetContent = "patch set content";
      String baseSubject = "base change";
      String baseContent = "base content";

      RevCommit initialHead = projectOperations.project(project).getHead("master");

      PushOneCommit.Result r1 = createChange(baseSubject, PushOneCommit.FILE_NAME, baseContent);
      gApi.changes()
          .id(r1.getChangeId())
          .revision(r1.getCommit().name())
          .review(ReviewInput.approve());
      gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).submit();

      testRepo.reset("HEAD~1");
      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(),
              testRepo,
              patchSetSubject,
              PushOneCommit.FILE_NAME,
              patchSetContent);
      PushOneCommit.Result r2 = push.to("refs/for/master");
      r2.assertOkStatus();

      String changeWithConflictId = r2.getChangeId();
      RevCommit parentPatchSet = r2.getCommit();
      RevCommit base = r1.getCommit();
      PushOneCommit.Result r3 = createChange("refs/for/master");
      r3.assertOkStatus();
      RevCommit childPatchSet = r3.getCommit();

      TestWorkInProgressStateChangedListener wipStateChangedListener =
          new TestWorkInProgressStateChangedListener();
      try (ExtensionRegistry.Registration registration =
          extensionRegistry.newRegistration().add(wipStateChangedListener)) {
        RebaseInput rebaseInput = new RebaseInput();
        rebaseInput.allowConflicts = true;
        Response<RebaseChainInfo> res =
            gApi.changes().id(r3.getChangeId()).rebaseChain(rebaseInput);
        verifyRebaseChainResponse(res, true, r2, r3);
        RebaseChainInfo rebaseChainInfo = res.value();

        ChangeInfo parentChangeInfo = rebaseChainInfo.rebasedChanges.get(0);
        assertThat(parentChangeInfo.changeId).isEqualTo(r2.getChangeId());
        assertThat(parentChangeInfo.containsGitConflicts).isTrue();
        assertThat(parentChangeInfo.workInProgress).isTrue();

        RevisionInfo parentChangeCurrentRevision = parentChangeInfo.getCurrentRevision();
        assertThat(parentChangeCurrentRevision.commit.parents.get(0).commit).isEqualTo(base.name());
        assertThat(parentChangeCurrentRevision.conflicts).isNotNull();
        assertThat(parentChangeCurrentRevision.conflicts.base).isEqualTo(initialHead.name());
        assertThat(parentChangeCurrentRevision.conflicts.ours).isEqualTo(parentPatchSet.name());
        assertThat(parentChangeCurrentRevision.conflicts.theirs).isEqualTo(base.name());
        assertThat(parentChangeCurrentRevision.conflicts.containsConflicts).isTrue();

        ChangeInfo childChangeInfo = rebaseChainInfo.rebasedChanges.get(1);
        assertThat(childChangeInfo.changeId).isEqualTo(r3.getChangeId());
        assertThat(childChangeInfo.containsGitConflicts).isTrue();
        assertThat(childChangeInfo.workInProgress).isTrue();

        RevisionInfo childChangeCurrentRevision = childChangeInfo.getCurrentRevision();
        assertThat(childChangeCurrentRevision.commit.parents.get(0).commit)
            .isEqualTo(parentChangeCurrentRevision.commit.commit);
        assertThat(childChangeCurrentRevision.conflicts).isNotNull();
        assertThat(parentChangeCurrentRevision.conflicts.base).isEqualTo(initialHead.name());
        assertThat(childChangeCurrentRevision.conflicts.ours).isEqualTo(childPatchSet.name());
        assertThat(childChangeCurrentRevision.conflicts.theirs)
            .isEqualTo(parentChangeCurrentRevision.commit.commit);
        assertThat(childChangeCurrentRevision.conflicts.containsConflicts).isTrue();
      }
      assertThat(wipStateChangedListener.invoked).isTrue();
      assertThat(wipStateChangedListener.wip).isTrue();

      // To get the revisions, we must retrieve the change with more change options.
      ChangeInfo changeInfo =
          gApi.changes()
              .id(changeWithConflictId)
              .get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
      assertThat(changeInfo.revisions).hasSize(2);
      assertThat(changeInfo.getCurrentRevision().commit.parents.get(0).commit)
          .isEqualTo(base.name());

      // Verify that the file content in the created patch set is correct.
      // We expect that it has conflict markers to indicate the conflict.
      BinaryResult bin =
          gApi.changes().id(changeWithConflictId).current().file(PushOneCommit.FILE_NAME).content();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      bin.writeTo(os);
      String fileContent = new String(os.toByteArray(), UTF_8);
      assertThat(fileContent)
          .isEqualTo(
              "<<<<<<< PATCH SET ("
                  + parentPatchSet.getName()
                  + " "
                  + patchSetSubject
                  + ")\n"
                  + patchSetContent
                  + "\n"
                  + (useDiff3
                      ? String.format(
                          "||||||| BASE      (%s %s)\n",
                          initialHead.getName(), initialHead.getShortMessage())
                      : "")
                  + "=======\n"
                  + baseContent
                  + "\n"
                  + ">>>>>>> BASE      ("
                  + base.getName()
                  + " "
                  + baseSubject
                  + ")\n");

      // Verify the message that has been posted on the change.
      List<ChangeMessageInfo> messages = gApi.changes().id(changeWithConflictId).messages();
      assertThat(messages).hasSize(2);
      assertThat(Iterables.getLast(messages).message)
          .isEqualTo(
              "Patch Set 2: Patch Set 1 was rebased\n\n"
                  + "The following files contain Git conflicts:\n"
                  + "* "
                  + PushOneCommit.FILE_NAME
                  + "\n");
    }

    @Test
    public void rebaseOntoMidChain() throws Exception {
      // Create changes with the following hierarchy:
      // * HEAD
      //   * r1
      //   * r2
      //     * r3
      //       * r4
      PushOneCommit.Result r = createChange();
      r.assertOkStatus();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();
      r2.assertOkStatus();
      PushOneCommit.Result r3 = createChange();
      r3.assertOkStatus();
      PushOneCommit.Result r4 = createChange();

      RebaseInput ri = new RebaseInput();
      ri.base = r3.getCommit().name();
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> rebaseCallWithInput.call(r4.getChangeId(), ri));
      assertThat(thrown).hasMessageThat().contains("recursion not allowed");
    }

    @Test
    public void rebaseChainActionEnabled() throws Exception {
      Change.Id changeToBeTheNewBase = changeOperations.newChange().project(project).createV1();

      Change.Id changeToBeRebased1 = changeOperations.newChange().project(project).createV1();
      Change.Id changeToBeRebased2 =
          changeOperations
              .newChange()
              .project(project)
              .childOf()
              .change(changeToBeRebased1)
              .createV1();

      // Approve and submit the change that will be the new base for the chain so that the chain is
      // rebasable.
      gApi.changes().id(changeToBeTheNewBase.get()).current().review(ReviewInput.approve());
      gApi.changes().id(changeToBeTheNewBase.get()).current().submit();

      ChangeInfo changeInfo = gApi.changes().id(changeToBeRebased2.get()).get();
      assertThat(changeInfo.actions).containsKey("rebase:chain");
      ActionInfo rebaseActionInfo = changeInfo.actions.get("rebase:chain");
      assertThat(rebaseActionInfo.enabled).isTrue();
      assertThat(rebaseActionInfo.enabledOptions)
          .containsExactly("rebase", "rebase_on_behalf_of_uploader");
    }

    @Test
    public void rebaseChainWhenChecksRefExists() throws Exception {
      // Create changes with the following hierarchy:
      // * HEAD
      //   * r1
      //   * r2
      //     * r3
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();
      PushOneCommit.Result r3 = createChange();

      // Create checks ref
      try (TestRepository<Repository> testRepo =
          new TestRepository<>(repoManager.openRepository(project))) {
        testRepo.update(
            RefNames.changeRefPrefix(r2.getChange().getId()) + "checks",
            testRepo.commit().message("Empty commit"));
      }

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Add an approval whose score should be copied on trivial rebase
      gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.recommend());
      gApi.changes().id(r3.getChangeId()).current().review(ReviewInput.recommend());

      // Rebase the chain through r3.
      verifyRebaseChainResponse(gApi.changes().id(r3.getChangeId()).rebaseChain(), false, r2, r3);
    }

    @Test
    public void testCountRebasesMetric() throws Exception {
      // Create changes with the following hierarchy:
      // * HEAD
      //   * r1
      //   * r2
      //     * r3
      //       * r4
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();
      PushOneCommit.Result r3 = createChange();
      PushOneCommit.Result r4 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase the chain.
      testMetricMaker.reset();
      verifyRebaseChainResponse(
          gApi.changes().id(r4.getChangeId()).rebaseChain(), false, r2, r3, r4);
      // field1 is on_behalf_of_uploader, field2 is rebase_chain, field3 is allow_conflicts
      assertThat(testMetricMaker.getCount("change/count_rebases", false, true, false)).isEqualTo(1);
      assertThat(testMetricMaker.getCount("change/count_rebases", false, false, false))
          .isEqualTo(0);
      assertThat(testMetricMaker.getCount("change/count_rebases", true, true, false)).isEqualTo(0);
      assertThat(testMetricMaker.getCount("change/count_rebases", true, false, false)).isEqualTo(0);
    }

    private void verifyRebaseChainResponse(
        Response<RebaseChainInfo> res,
        boolean shouldHaveConflicts,
        PushOneCommit.Result... changes) {
      assertThat(res.statusCode()).isEqualTo(200);
      RebaseChainInfo info = res.value();
      assertThat(info.rebasedChanges.stream().map(c -> c._number).collect(Collectors.toList()))
          .containsExactlyElementsIn(
              Arrays.stream(changes)
                  .map(c -> c.getChange().getId().get())
                  .collect(Collectors.toList()))
          .inOrder();
      assertThat(info.containsGitConflicts).isEqualTo(shouldHaveConflicts ? true : null);
    }
  }
}

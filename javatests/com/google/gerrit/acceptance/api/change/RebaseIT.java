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
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
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
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.RelatedChangeAndCommitInfo;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RebaseChainInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
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
    @Inject protected RequestScopeOperations requestScopeOperations;
    @Inject protected ProjectOperations projectOperations;
    @Inject protected ExtensionRegistry extensionRegistry;

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
      // Create two changes both with the same parent
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Add an approval whose score should be copied on trivial rebase
      gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.recommend());

      // Rebase the second change
      rebaseCall.call(r2.getChangeId());

      verifyRebaseForChange(r2.getChange().getId(), r.getCommit().name(), true, 2);

      // Rebasing the second change again should fail
      verifyChangeIsUpToDate(r2);
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

      ChangeInfo ci2 = get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT);
      RevisionInfo ri2 = ci2.revisions.get(ci2.currentRevision);
      assertThat(ri2.commit.parents.get(0).commit).isEqualTo(branchTip);

      Change.Id id1 = r1.getChange().getId();
      RebaseInput in = new RebaseInput();
      in.base = id1.toString();
      rebaseCallWithInput.call(r2.getChangeId(), in);

      Change.Id id2 = r2.getChange().getId();
      ci2 = get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT);
      ri2 = ci2.revisions.get(ci2.currentRevision);
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

      ChangeInfo ci2 = get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT);
      RevisionInfo ri2 = ci2.revisions.get(ci2.currentRevision);
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
      ci2 = get(r2.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT);
      ri2 = ci2.revisions.get(ci2.currentRevision);
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

    protected void verifyRebaseForChange(
        Change.Id changeId, Change.Id baseChangeId, boolean shouldHaveApproval)
        throws RestApiException {
      verifyRebaseForChange(changeId, baseChangeId, shouldHaveApproval, 2);
    }

    protected void verifyRebaseForChange(
        Change.Id changeId,
        Change.Id baseChangeId,
        boolean shouldHaveApproval,
        int expectedNumRevisions)
        throws RestApiException {
      ChangeInfo baseInfo = gApi.changes().id(baseChangeId.get()).get(CURRENT_REVISION);
      verifyRebaseForChange(
          changeId, baseInfo.currentRevision, shouldHaveApproval, expectedNumRevisions);
    }

    protected void verifyRebaseForChange(
        Change.Id changeId, String baseCommit, boolean shouldHaveApproval, int expectedNumRevisions)
        throws RestApiException {
      ChangeInfo info =
          gApi.changes().id(changeId.get()).get(CURRENT_REVISION, CURRENT_COMMIT, DETAILED_LABELS);

      RevisionInfo r = info.revisions.get(info.currentRevision);
      assertThat(r._number).isEqualTo(expectedNumRevisions);
      assertThat(r.realUploader._accountId).isEqualTo(r.uploader._accountId);

      // ...and the base should be correct
      assertThat(r.commit.parents).hasSize(1);
      assertWithMessage("base commit for change " + changeId)
          .that(r.commit.parents.get(0).commit)
          .isEqualTo(baseCommit);

      // ...and the committer and description should be correct
      GitPerson committer = info.revisions.get(info.currentRevision).commit.committer;
      assertThat(committer.name).isEqualTo(admin.fullName());
      assertThat(committer.email).isEqualTo(admin.email());
      String description = info.revisions.get(info.currentRevision).description;
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
      ResourceConflictException thrown =
          assertThrows(ResourceConflictException.class, () -> rebaseCall.call(r.getChangeId()));
      assertThat(thrown).hasMessageThat().contains("Change is already up to date");
    }

    protected static class TestCommitValidationListener implements CommitValidationListener {
      public CommitReceivedEvent receiveEvent;

      @Override
      public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
          throws CommitValidationException {
        this.receiveEvent = receiveEvent;
        return ImmutableList.of();
      }
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

    @Test
    public void rebaseWithConflict_conflictsAllowed() throws Exception {
      String patchSetSubject = "patch set change";
      String patchSetContent = "patch set content";
      String baseSubject = "base change";
      String baseContent = "base content";

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
        ChangeInfo changeInfo =
            gApi.changes().id(changeId).revision(patchSet.name()).rebaseAsInfo(rebaseInput);
        assertThat(changeInfo.containsGitConflicts).isTrue();
        assertThat(changeInfo.workInProgress).isTrue();
      }
      assertThat(wipStateChangedListener.invoked).isTrue();
      assertThat(wipStateChangedListener.wip).isTrue();

      // To get the revisions, we must retrieve the change with more change options.
      ChangeInfo changeInfo =
          gApi.changes().id(changeId).get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
      assertThat(changeInfo.revisions).hasSize(2);
      assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
          .isEqualTo(base.name());

      // Verify that the file content in the created patch set is correct.
      // We expect that it has conflict markers to indicate the conflict.
      BinaryResult bin =
          gApi.changes().id(changeId).current().file(PushOneCommit.FILE_NAME).content();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      bin.writeTo(os);
      String fileContent = new String(os.toByteArray(), UTF_8);
      String patchSetSha1 = abbreviateName(patchSet, 6);
      String baseSha1 = abbreviateName(base, 6);
      assertThat(fileContent)
          .isEqualTo(
              "<<<<<<< PATCH SET ("
                  + patchSetSha1
                  + " "
                  + patchSetSubject
                  + ")\n"
                  + patchSetContent
                  + "\n"
                  + "=======\n"
                  + baseContent
                  + "\n"
                  + ">>>>>>> BASE      ("
                  + baseSha1
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
      ChangeInfo ci3 = get(r3.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT);
      RevisionInfo ri3 = ci3.revisions.get(ci3.currentRevision);
      assertThat(ri3.commit.parents.get(0).commit).isEqualTo(r1.getCommit().name());

      assertThat(gApi.changes().id(id3.get()).revision(ri3._number).related().changes).isEmpty();
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
      ChangeInfo changeInfo2 = gApi.changes().id(changeId2).get();
      assertThat(changeInfo2.revisions.get(changeInfo2.currentRevision)._number).isEqualTo(2);

      // Rebase the first patch set of the second change
      gApi.changes().id(changeId2).revision(1).rebase();

      // Second change should have 3 patch sets
      changeInfo2 = gApi.changes().id(changeId2).get();
      assertThat(changeInfo2.revisions.get(changeInfo2.currentRevision)._number).isEqualTo(3);

      // ... and the committer and description should be correct
      ChangeInfo info = gApi.changes().id(changeId2).get(CURRENT_REVISION, CURRENT_COMMIT);
      GitPerson committer = info.revisions.get(info.currentRevision).commit.committer;
      assertThat(committer.name).isEqualTo(admin.fullName());
      assertThat(committer.email).isEqualTo(admin.email());
      String description = info.revisions.get(info.currentRevision).description;
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
    protected void verifyChangeIsUpToDate(PushOneCommit.Result r) {
      ResourceConflictException thrown =
          assertThrows(ResourceConflictException.class, () -> rebaseCall.call(r.getChangeId()));
      assertThat(thrown).hasMessageThat().contains("The whole chain is already up to date.");
    }

    @Test
    public void rebaseChain() throws Exception {
      // Create changes with the following hierarchy:
      // * HEAD
      //   * r1
      //   * r2
      //     * r3
      //       * r4
      //         *r5
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();
      PushOneCommit.Result r3 = createChange();
      PushOneCommit.Result r4 = createChange();
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
      verifyRebaseForChange(r2.getChange().getId(), r.getCommit().name(), true, 2);
      verifyRebaseForChange(r3.getChange().getId(), r2.getChange().getId(), true);
      verifyRebaseForChange(r4.getChange().getId(), r3.getChange().getId(), false);

      verifyChangeIsUpToDate(r2);
      verifyChangeIsUpToDate(r3);
      verifyChangeIsUpToDate(r4);

      // r5 wasn't rebased.
      ChangeInfo r5info = gApi.changes().id(r5.getChangeId()).get(CURRENT_REVISION);
      assertThat(r5info.revisions.get(r5info.currentRevision)._number).isEqualTo(1);

      // Rebasing r5
      verifyRebaseChainResponse(
          gApi.changes().id(r5.getChangeId()).rebaseChain(), false, r2, r3, r4, r5);

      verifyRebaseForChange(r5.getChange().getId(), r4.getChange().getId(), false);
    }

    @Test
    public void rebasePartlyOutdatedChain() throws Exception {
      final String file = "modified_file.txt";
      final String oldContent = "old content";
      final String newContent = "new content";
      // Create changes with the following revision hierarchy:
      // * HEAD
      //   * r1
      //   * r2
      //     * r3/1    r3/2
      //       * r4
      PushOneCommit.Result r = createChange();
      testRepo.reset("HEAD~1");
      PushOneCommit.Result r2 = createChange();
      PushOneCommit.Result r3 = createChange("original patch-set", file, oldContent);
      PushOneCommit.Result r4 = createChange();
      gApi.changes()
          .id(r3.getChangeId())
          .edit()
          .modifyFile(file, RawInputUtil.create(newContent.getBytes(UTF_8)));
      gApi.changes().id(r3.getChangeId()).edit().publish();

      // Approve and submit the first change
      RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
      revision.review(ReviewInput.approve());
      revision.submit();

      // Rebase the chain through r4.
      rebaseCall.call(r4.getChangeId());

      verifyRebaseForChange(r2.getChange().getId(), r.getCommit().name(), false, 2);
      verifyRebaseForChange(r3.getChange().getId(), r2.getChange().getId(), false, 3);
      verifyRebaseForChange(r4.getChange().getId(), r3.getChange().getId(), false);

      assertThat(gApi.changes().id(r3.getChangeId()).current().file(file).content().asString())
          .isEqualTo(newContent);

      verifyChangeIsUpToDate(r2);
      verifyChangeIsUpToDate(r3);
      verifyChangeIsUpToDate(r4);
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
    public void rebaseChainWithConflicts_conflictsAllowed() throws Exception {
      String patchSetSubject = "patch set change";
      String patchSetContent = "patch set content";
      String baseSubject = "base change";
      String baseContent = "base content";

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
      RevCommit patchSet = r2.getCommit();
      RevCommit base = r1.getCommit();
      PushOneCommit.Result r3 = createChange("refs/for/master");
      r3.assertOkStatus();

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
        ChangeInfo changeWithConflictInfo = rebaseChainInfo.rebasedChanges.get(0);
        assertThat(changeWithConflictInfo.changeId).isEqualTo(r2.getChangeId());
        assertThat(changeWithConflictInfo.containsGitConflicts).isTrue();
        assertThat(changeWithConflictInfo.workInProgress).isTrue();
        ChangeInfo childChangeInfo = rebaseChainInfo.rebasedChanges.get(1);
        assertThat(childChangeInfo.changeId).isEqualTo(r3.getChangeId());
        assertThat(childChangeInfo.containsGitConflicts).isTrue();
        assertThat(childChangeInfo.workInProgress).isTrue();
      }
      assertThat(wipStateChangedListener.invoked).isTrue();
      assertThat(wipStateChangedListener.wip).isTrue();

      // To get the revisions, we must retrieve the change with more change options.
      ChangeInfo changeInfo =
          gApi.changes()
              .id(changeWithConflictId)
              .get(ALL_REVISIONS, CURRENT_COMMIT, CURRENT_REVISION);
      assertThat(changeInfo.revisions).hasSize(2);
      assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
          .isEqualTo(base.name());

      // Verify that the file content in the created patch set is correct.
      // We expect that it has conflict markers to indicate the conflict.
      BinaryResult bin =
          gApi.changes().id(changeWithConflictId).current().file(PushOneCommit.FILE_NAME).content();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      bin.writeTo(os);
      String fileContent = new String(os.toByteArray(), UTF_8);
      String patchSetSha1 = abbreviateName(patchSet, 6);
      String baseSha1 = abbreviateName(base, 6);
      assertThat(fileContent)
          .isEqualTo(
              "<<<<<<< PATCH SET ("
                  + patchSetSha1
                  + " "
                  + patchSetSubject
                  + ")\n"
                  + patchSetContent
                  + "\n"
                  + "=======\n"
                  + baseContent
                  + "\n"
                  + ">>>>>>> BASE      ("
                  + baseSha1
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

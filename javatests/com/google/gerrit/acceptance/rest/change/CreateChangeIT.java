// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.entities.Permission.CREATE;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.entities.RefNames.HEAD;
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.common.testing.GitPersonSubject.assertThat;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.SIGNED_OFF_BY_TAG;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.UseSystemTime;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.submit.ChangeAlreadyMergedException;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.Base64;
import org.junit.Before;
import org.junit.Test;

@UseClockStep
public class CreateChangeIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Before
  public void addNonCommitHead() throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId answer = ins.insert(Constants.OBJ_BLOB, new byte[] {42});
      ins.flush();
      ins.close();

      RefUpdate update = repo.getRefDatabase().newUpdate("refs/heads/answer", false);
      update.setNewObjectId(answer);
      assertThat(update.forceUpdate()).isEqualTo(RefUpdate.Result.NEW);
    }
  }

  @Test
  public void createEmptyChange_MissingBranch() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    assertCreateFails(ci, BadRequestException.class, "branch must be non-empty");
  }

  @Test
  public void createEmptyChange_NonExistingBranch() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.branch = "non-existing";
    assertCreateFails(ci, BadRequestException.class, "Destination branch does not exist");
  }

  @Test
  public void createEmptyChange_MissingMessage() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    ci.branch = "master";
    assertCreateFails(ci, BadRequestException.class, "commit message must be non-empty");
  }

  @Test
  public void createEmptyChange_InvalidStatus() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.MERGED);
    assertCreateFails(ci, BadRequestException.class, "unsupported change status");
  }

  @Test
  public void createEmptyChange_InvalidChangeId() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject\n\nChange-Id: I0000000000000000000000000000000000000000";
    assertCreateFails(
        ci, ResourceConflictException.class, "invalid Change-Id line format in message footer");
  }

  @Test
  public void createEmptyChange_InvalidSubject() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Change-Id: I1234000000000000000000000000000000000000";
    assertCreateFails(
        ci,
        ResourceConflictException.class,
        "missing subject; Change-Id must be in message footer");
  }

  @Test
  public void createNewChange_InvalidCommentInCommitMessage() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "#12345 Test";
    assertCreateFails(ci, BadRequestException.class, "commit message must be non-empty");
  }

  @Test
  public void createNewChange_RequiresAuthentication() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    assertCreateFails(
        newChangeInput(ChangeStatus.NEW), AuthException.class, "Authentication required");
  }

  @Test
  public void createNewChange() throws Exception {
    ChangeInfo info = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .contains("Change-Id: " + info.changeId);

    // Verify the message that has been posted on the change.
    List<ChangeMessageInfo> messages = gApi.changes().id(info._number).messages();
    assertThat(messages).hasSize(1);
    assertThat(Iterables.getOnlyElement(messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void createNewChangeWithCommentsInCommitMessage() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject += "\n# Comment line";
    ChangeInfo info = gApi.changes().create(ci).get();
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .doesNotContain("# Comment line");
  }

  @Test
  public void createNewChangeWithChangeId() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    String changeId = "I1234000000000000000000000000000000000000";
    String changeIdLine = "Change-Id: " + changeId;
    ci.subject = "Subject\n\n" + changeIdLine;
    ChangeInfo info = assertCreateSucceeds(ci);
    assertThat(info.changeId).isEqualTo(changeId);
    assertThat(info.revisions.get(info.currentRevision).commit.message).contains(changeIdLine);
  }

  @Test
  public void cannotCreateChangeOnGerritInternalRefs() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(CREATE).ref("refs/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject";
    ci.branch = "refs/changes/00/1000"; // disallowedRef

    Throwable thrown = assertThrows(RestApiException.class, () -> gApi.changes().create(ci));
    assertThat(thrown).hasMessageThat().contains("Cannot create a change on ref " + ci.branch);
  }

  @Test
  public void cannotCreateChangeOnTagRefs() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(CREATE).ref("refs/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject";
    ci.branch = "refs/tags/v1.0"; // disallowed ref

    Throwable thrown = assertThrows(RestApiException.class, () -> gApi.changes().create(ci));
    assertThat(thrown).hasMessageThat().contains("Cannot create a change on ref " + ci.branch);
  }

  @Test
  public void canCreateChangeOnRefsMetaConfig() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(CREATE).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject";
    ci.branch = RefNames.REFS_CONFIG;
    assertThat(gApi.changes().create(ci).info().branch).isEqualTo(RefNames.REFS_CONFIG);
  }

  @Test
  public void canCreateChangeOnRefsMetaDashboards() throws Exception {
    String branchName = "refs/meta/dashboards/project_1";
    requestScopeOperations.setApiUser(admin.id());
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(CREATE).ref(branchName).group(REGISTERED_USERS))
        .add(allow(READ).ref(branchName).group(REGISTERED_USERS))
        .update();
    BranchNameKey branchNameKey = BranchNameKey.create(project, branchName);
    createBranch(branchNameKey);
    requestScopeOperations.setApiUser(user.id());
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject";
    ci.branch = branchName;
    assertThat(gApi.changes().create(ci).info().branch).isEqualTo(branchName);
  }

  @Test
  public void cannotCreateChangeWithChangeIfOfExistingChangeOnSameBranch() throws Exception {
    String changeId = createChange().getChangeId();

    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject\n\nChange-Id: " + changeId;
    assertCreateFails(
        ci,
        ResourceConflictException.class,
        "A change with Change-Id " + changeId + " already exists for this branch.");
  }

  @Test
  public void canCreateChangeWithChangeIfOfExistingChangeOnOtherBranch() throws Exception {
    String changeId = createChange().getChangeId();

    createBranch(BranchNameKey.create(project, "other"));

    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject\n\nChange-Id: " + changeId;
    ci.branch = "other";
    ChangeInfo info = assertCreateSucceeds(ci);
    assertThat(info.changeId).isEqualTo(changeId);
  }

  @Test
  public void notificationsOnChangeCreation() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    watch(project.get());

    // check that watcher is notified
    requestScopeOperations.setApiUser(admin.id());
    assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getNameEmail());
    assertThat(m.body()).contains(admin.fullName() + " has uploaded this change for review.");

    // check that watcher is not notified if notify=NONE
    sender.clear();
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.notify = NotifyHandling.NONE;
    assertCreateSucceeds(input);
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void createNewChangeSignedOffByFooter() throws Exception {
    setSignedOffByFooter(true);
    try {
      ChangeInfo info = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
      String message = info.revisions.get(info.currentRevision).commit.message;
      assertThat(message)
          .contains(
              String.format(
                  "%sAdministrator <%s>", SIGNED_OFF_BY_TAG, admin.newIdent().getEmailAddress()));
    } finally {
      setSignedOffByFooter(false);
    }
  }

  @Test
  public void createNewChangeSignedOffByFooterWithChangeId() throws Exception {
    setSignedOffByFooter(true);
    try {
      ChangeInput ci = newChangeInput(ChangeStatus.NEW);
      String changeId = "I1234000000000000000000000000000000000000";
      String changeIdLine = "Change-Id: " + changeId;
      ci.subject = "Subject\n\n" + changeIdLine;
      ChangeInfo info = assertCreateSucceeds(ci);
      assertThat(info.changeId).isEqualTo(changeId);
      String message = info.revisions.get(info.currentRevision).commit.message;
      assertThat(message).contains(changeIdLine);
      assertThat(message)
          .contains(
              String.format(
                  "%sAdministrator <%s>", SIGNED_OFF_BY_TAG, admin.newIdent().getEmailAddress()));
    } finally {
      setSignedOffByFooter(false);
    }
  }

  @Test
  public void createNewPrivateChange() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.isPrivate = true;
    assertCreateSucceeds(input);
  }

  @Test
  public void createDefaultAuthor() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    ChangeInfo info = assertCreateSucceeds(input);
    GitPerson person = gApi.changes().id(info.id).current().commit(false).author;
    assertThat(person).email().isEqualTo(admin.email());
  }

  @Test
  public void createAuthorOverrideBadRequest() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.author = new AccountInput();
    input.author.name = "name";
    assertCreateFails(input, BadRequestException.class, "email");
    input.author.name = null;
    input.author.email = "gerritlessjane@invalid";
    assertCreateFails(input, BadRequestException.class, "email");
  }

  @Test
  public void createAuthorOverride() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.author = new AccountInput();
    input.author.email = "gerritlessjane@invalid";
    // This is an email address that doesn't exist as account on the Gerrit server.
    input.author.name = "Gerritless Jane";
    ChangeInfo info = assertCreateSucceeds(input);

    RevisionApi rApi = gApi.changes().id(info.id).current();
    GitPerson author = rApi.commit(false).author;
    assertThat(author).email().isEqualTo(input.author.email);
    assertThat(author).name().isEqualTo(input.author.name);
    GitPerson committer = rApi.commit(false).committer;
    assertThat(committer).email().isEqualTo(admin.getNameEmail().email());
  }

  @Test
  public void createAuthorPermission() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.author = new AccountInput();
    input.author.name = "Jane";
    input.author.email = "jane@invalid";
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .update();
    assertCreateFails(input, AuthException.class, "forge author");
  }

  @Test
  public void createAuthorAddedAsCcAndNotified() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.author = new AccountInput();
    input.author.email = user.email();
    input.author.name = user.fullName();

    ChangeInfo info = assertCreateSucceeds(input);
    assertThat(info.reviewers.get(ReviewerState.CC)).hasSize(1);
    assertThat(Iterables.getOnlyElement(info.reviewers.get(ReviewerState.CC)).email)
        .isEqualTo(user.email());
    assertThat(
            Iterables.getOnlyElement(Iterables.getOnlyElement(sender.getMessages()).rcpt()).email())
        .isEqualTo(user.email());
  }

  @Test
  public void createAuthorAddedAsCcNotNotifiedWithNotifyNone() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.author = new AccountInput();
    input.author.email = user.email();
    input.author.name = user.fullName();
    input.notify = NotifyHandling.NONE;

    ChangeInfo info = assertCreateSucceeds(input);
    assertThat(info.reviewers.get(ReviewerState.CC)).hasSize(1);
    assertThat(Iterables.getOnlyElement(info.reviewers.get(ReviewerState.CC)).email)
        .isEqualTo(user.email());
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void createWithMergeConflictAuthorAddedAsCcNotNotifiedWithNotifyNone() throws Exception {
    String fileName = "shared.txt";
    String sourceBranch = "sourceBranch";
    String sourceSubject = "source change";
    String sourceContent = "source content";
    String targetBranch = "targetBranch";
    String targetSubject = "target change";
    String targetContent = "target content";
    changeInTwoBranches(
        sourceBranch,
        sourceSubject,
        fileName,
        sourceContent,
        targetBranch,
        targetSubject,
        fileName,
        targetContent);
    ChangeInput input = newMergeChangeInput(targetBranch, sourceBranch, "", true);
    input.workInProgress = true;
    input.author = new AccountInput();
    input.author.email = user.email();
    input.author.name = user.fullName();
    input.notify = NotifyHandling.NONE;
    ChangeInfo info = assertCreateSucceeds(input);

    assertThat(info.reviewers.get(ReviewerState.CC)).hasSize(1);
    assertThat(Iterables.getOnlyElement(info.reviewers.get(ReviewerState.CC)).email)
        .isEqualTo(user.email());
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void createAuthorNotAddedAsCcWithAvoidAddingOriginalAuthorAsReviewer() throws Exception {
    ConfigInput config = new ConfigInput();
    config.avoidAddingOriginalAuthorAsReviewer = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(config);
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.author = new AccountInput();
    input.author.email = user.email();
    input.author.name = user.fullName();

    ChangeInfo info = assertCreateSucceeds(input);
    assertThat(info.reviewers).isEmpty();
  }

  @Test
  public void createNewWorkInProgressChange() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.workInProgress = true;
    assertCreateSucceeds(input);
  }

  @Test
  public void createChangeWithParentCommit() throws Exception {
    Map<String, PushOneCommit.Result> setup =
        changeInTwoBranches("foo", "foo.txt", "bar", "bar.txt");
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.baseCommit = setup.get("master").getCommit().getId().name();
    ChangeInfo result = assertCreateSucceeds(input);
    assertThat(gApi.changes().id(result.id).current().commit(false).parents.get(0).commit)
        .isEqualTo(input.baseCommit);
  }

  @Test
  public void createChangeWithParentChange() throws Exception {
    Result change = createChange();
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.baseChange = change.getChangeId();
    ChangeInfo result = assertCreateSucceeds(input);
    assertThat(gApi.changes().id(result.id).current().commit(false).parents.get(0).commit)
        .isEqualTo(change.getCommit().getId().name());
  }

  @Test
  public void createChangeWithBadParentCommitFails() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.baseCommit = "notasha1";
    assertCreateFails(
        input, UnprocessableEntityException.class, "Base notasha1 doesn't represent a valid SHA-1");
  }

  @Test
  public void createChangeWithNonExistingParentCommitFails() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.baseCommit = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    assertCreateFails(
        input,
        UnprocessableEntityException.class,
        String.format("Base %s doesn't exist", input.baseCommit));
  }

  @Test
  public void createChangeWithParentCommitOnWrongBranchFails() throws Exception {
    Map<String, PushOneCommit.Result> setup =
        changeInTwoBranches("foo", "foo.txt", "bar", "bar.txt");
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "foo";
    input.baseCommit = setup.get("bar").getCommit().getId().name();
    assertCreateFails(
        input,
        BadRequestException.class,
        String.format("Commit %s doesn't exist on ref refs/heads/foo", input.baseCommit));
  }

  @Test
  public void createChangeWithParentCommitWithNonExistingTargetBranch() throws Exception {
    Result initialCommit =
        pushFactory
            .create(user.newIdent(), testRepo, "initial commit", "readme.txt", "initial commit")
            .to("refs/heads/master");
    initialCommit.assertOkStatus();

    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "non-existing";
    input.baseCommit = initialCommit.getCommit().getName();
    assertCreateFails(input, BadRequestException.class, "Destination branch does not exist");
  }

  @Test
  public void createChangeOnNonExistingBaseChangeFails() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.baseChange = "999999";
    assertCreateFails(
        input, UnprocessableEntityException.class, "Base change not found: " + input.baseChange);
  }

  @Test
  public void createChangeWithoutAccessToParentCommitFails() throws Exception {
    Map<String, PushOneCommit.Result> results =
        changeInTwoBranches("invisible-branch", "a.txt", "visible-branch", "b.txt");
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(READ).ref("refs/heads/invisible-branch").group(REGISTERED_USERS))
        .update();

    ChangeInput in = newChangeInput(ChangeStatus.NEW);
    in.branch = "visible-branch";
    in.baseChange = results.get("invisible-branch").getChangeId();
    assertCreateFails(
        in, UnprocessableEntityException.class, "Base change not found: " + in.baseChange);
  }

  @Test
  public void noteDbCommit() throws Exception {
    ChangeInfo c = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit =
          rw.parseCommit(repo.exactRef(changeMetaRef(Change.id(c._number))).getObjectId());

      assertThat(commit.getShortMessage()).isEqualTo("Create change");

      PersonIdent expectedAuthor =
          changeNoteUtil.newAccountIdIdent(
              getAccount(admin.id()).id(), c.created.toInstant(), serverIdent.get());
      assertThat(commit.getAuthorIdent()).isEqualTo(expectedAuthor);

      assertThat(commit.getCommitterIdent())
          .isEqualTo(new PersonIdent(serverIdent.get(), c.created));
      assertThat(commit.getParentCount()).isEqualTo(0);
    }
  }

  @Test
  public void createMergeChange() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "");
    ChangeInfo change = assertCreateSucceeds(in);

    // Verify the message that has been posted on the change.
    List<ChangeMessageInfo> messages = gApi.changes().id(change._number).messages();
    assertThat(messages).hasSize(1);
    assertThat(Iterables.getOnlyElement(messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void createMergeChangeAuthor() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "");
    in.author = new AccountInput();
    in.author.name = "Gerritless Jane";
    in.author.email = "gerritlessjane@invalid";
    ChangeInfo change = assertCreateSucceeds(in);

    RevisionApi rApi = gApi.changes().id(change.id).current();
    GitPerson author = rApi.commit(false).author;
    assertThat(author).email().isEqualTo(in.author.email);
    GitPerson committer = rApi.commit(false).committer;
    assertThat(committer).email().isEqualTo(admin.getNameEmail().email());
  }

  @Test
  public void createMergeChange_Conflicts() throws Exception {
    changeInTwoBranches("branchA", "shared.txt", "branchB", "shared.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "");
    assertCreateFails(in, RestApiException.class, "merge conflict");
  }

  @Test
  public void createMergeChange_Conflicts_Ours() throws Exception {
    changeInTwoBranches("branchA", "shared.txt", "branchB", "shared.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "ours");
    assertCreateSucceeds(in);
  }

  @Test
  public void createMergeChange_ConflictsAllowed() throws Exception {
    String fileName = "shared.txt";
    String sourceBranch = "sourceBranch";
    String sourceSubject = "source change";
    String sourceContent = "source content";
    String targetBranch = "targetBranch";
    String targetSubject = "target change";
    String targetContent = "target content";
    changeInTwoBranches(
        sourceBranch,
        sourceSubject,
        fileName,
        sourceContent,
        targetBranch,
        targetSubject,
        fileName,
        targetContent);
    ChangeInput in = newMergeChangeInput(targetBranch, sourceBranch, "", true);
    ChangeInfo change = assertCreateSucceedsWithConflicts(in);

    // Verify that the file content in the created change is correct.
    // We expect that it has conflict markers to indicate the conflict.
    BinaryResult bin = gApi.changes().id(change._number).current().file(fileName).content();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String fileContent = new String(os.toByteArray(), UTF_8);
    String sourceSha1 = abbreviateName(projectOperations.project(project).getHead(sourceBranch), 6);
    String targetSha1 = abbreviateName(projectOperations.project(project).getHead(targetBranch), 6);
    assertThat(fileContent)
        .isEqualTo(
            "<<<<<<< TARGET BRANCH ("
                + targetSha1
                + " "
                + targetSubject
                + ")\n"
                + targetContent
                + "\n"
                + "=======\n"
                + sourceContent
                + "\n"
                + ">>>>>>> SOURCE BRANCH ("
                + sourceSha1
                + " "
                + sourceSubject
                + ")\n");

    // Verify the message that has been posted on the change.
    List<ChangeMessageInfo> messages = gApi.changes().id(change._number).messages();
    assertThat(messages).hasSize(1);
    assertThat(Iterables.getOnlyElement(messages).message)
        .isEqualTo(
            "Uploaded patch set 1.\n\n"
                + "The following files contain Git conflicts:\n"
                + "* "
                + fileName
                + "\n");
  }

  @Test
  public void createMergeChange_ConflictAllowedNotSupportedByMergeStrategy() throws Exception {
    String fileName = "shared.txt";
    String sourceBranch = "sourceBranch";
    String targetBranch = "targetBranch";
    changeInTwoBranches(
        sourceBranch,
        "source change",
        fileName,
        "source content",
        targetBranch,
        "target change",
        fileName,
        "target content");
    String mergeStrategy = "simple-two-way-in-core";
    ChangeInput in = newMergeChangeInput(targetBranch, sourceBranch, mergeStrategy, true);
    assertCreateFails(
        in,
        BadRequestException.class,
        "merge with conflicts is not supported with merge strategy: " + mergeStrategy);
  }

  @Test
  public void createMergeChangeFailsWithConflictIfThereAreTooManyCommonPredecessors()
      throws Exception {
    // Create an initial commit in master.
    Result initialCommit =
        pushFactory
            .create(user.newIdent(), testRepo, "initial commit", "readme.txt", "initial commit")
            .to("refs/heads/master");
    initialCommit.assertOkStatus();

    String file = "shared.txt";
    List<RevCommit> parents = new ArrayList<>();
    // RecursiveMerger#MAX_BASES = 200, cannot use RecursiveMerger#MAX_BASES as it is not static.
    int maxBases = 200;

    // Create more than RecursiveMerger#MAX_BASES base commits.
    for (int i = 1; i <= maxBases + 1; i++) {
      parents.add(
          testRepo
              .commit()
              .message("Base " + i)
              .add(file, "content " + i)
              .parent(initialCommit.getCommit())
              .create());
    }

    // Create 2 branches.
    String branchA = "branchA";
    String branchB = "branchB";
    createBranch(BranchNameKey.create(project, branchA));
    createBranch(BranchNameKey.create(project, branchB));

    // Push an octopus merge to both of the branches.
    Result octopusA =
        pushFactory
            .create(user.newIdent(), testRepo)
            .setParents(parents)
            .to("refs/heads/" + branchA);
    octopusA.assertOkStatus();

    Result octopusB =
        pushFactory
            .create(user.newIdent(), testRepo)
            .setParents(parents)
            .to("refs/heads/" + branchB);
    octopusB.assertOkStatus();

    // Creating a merge commit for the 2 octopus commits fails, because they have more than
    // RecursiveMerger#MAX_BASES common predecessors.
    assertCreateFails(
        newMergeChangeInput("branchA", "branchB", ""),
        ResourceConflictException.class,
        "Cannot create merge commit: No merge base could be determined."
            + " Reason=TOO_MANY_MERGE_BASES.");
  }

  @Test
  public void invalidSource() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "invalid", "");
    assertCreateFails(in, BadRequestException.class, "Cannot resolve 'invalid' to a commit");
  }

  @Test
  public void invalidStrategy() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "octopus");
    assertCreateFails(in, BadRequestException.class, "invalid merge strategy: octopus");
  }

  @Test
  public void alreadyMerged() throws Exception {
    ObjectId c0 =
        testRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("first commit")
            .add("a.txt", "a contents ")
            .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("second commit")
        .add("b.txt", "b contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    ChangeInput in = newMergeChangeInput("master", c0.getName(), "");
    assertCreateFails(
        in, ChangeAlreadyMergedException.class, "'" + c0.getName() + "' has already been merged");
  }

  @Test
  public void onlyContentMerged() throws Exception {
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("first commit")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    // create a change, and cherrypick into master
    PushOneCommit.Result cId = createChange();
    RevCommit commitId = cId.getCommit();
    CherryPickInput cpi = new CherryPickInput();
    cpi.destination = "master";
    cpi.message = "cherry pick the commit";
    ChangeApi orig = gApi.changes().id(cId.getChangeId());
    ChangeApi cherry = orig.current().cherryPick(cpi);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    ObjectId remoteId = projectOperations.project(project).getHead("master");
    assertThat(remoteId).isNotEqualTo(commitId);

    ChangeInput in = newMergeChangeInput("master", commitId.getName(), "");
    assertCreateSucceeds(in);
  }

  @Test
  public void createChangeOnExistingBranchNotPermitted() throws Exception {
    createBranch(BranchNameKey.create(project, "foo"));
    projectOperations
        .project(project)
        .forUpdate()
        // Allow reading for refs/meta/config so that the project is visible to the user. Otherwise
        // the request will fail with an UnprocessableEntityException "Project not found:".
        .add(allow(READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "foo";

    assertCreateFails(input, ResourceNotFoundException.class, "ref refs/heads/foo not found");
  }

  @Test
  public void createChangeOnNonExistingBranch() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "foo";
    input.newBranch = true;
    assertCreateSucceeds(input);
  }

  @Test
  public void createChangeOnNonExistingBranchNotPermitted() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        // Allow reading for refs/meta/config so that the project is visible to the user. Otherwise
        // the request will fail with an UnprocessableEntityException "Project not found:".
        .add(allow(READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "foo";
    // sets this option to be true to make sure permission check happened before this option could
    // be considered.
    input.newBranch = true;

    assertCreateFails(input, ResourceNotFoundException.class, "ref refs/heads/foo not found");
  }

  @Test
  public void createMergeChangeOnNonExistingBranchNotPossible() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ChangeInput input = newMergeChangeInput("foo", "master", "");
    input.newBranch = true;
    assertCreateFails(
        input, BadRequestException.class, "Cannot create merge: destination branch does not exist");
  }

  @Test
  public void createChangeWithBothMergeAndPatch_fails() throws Exception {
    ChangeInput input = newMergeChangeInput("foo", "master", "");
    input.patch = new ApplyPatchInput();
    assertCreateFails(
        input, BadRequestException.class, "Only one of `merge` and `patch` arguments can be set");
  }

  private static final String PATCH_FILE_NAME = "a_file.txt";
  private static final String PATCH_NEW_FILE_CONTENT = "First added line\nSecond added line\n";
  private static final String PATCH_INPUT =
      "diff --git a/a_file.txt b/a_file.txt\n"
          + "new file mode 100644\n"
          + "index 0000000..f0eec86\n"
          + "--- /dev/null\n"
          + "+++ b/a_file.txt\n"
          + "@@ -0,0 +1,2 @@\n"
          + "+First added line\n"
          + "+Second added line\n";
  private static final String MODIFICATION_PATCH_INPUT =
      "diff --git a/a_file.txt b/a_file.txt\n"
          + "new file mode 100644\n"
          + "--- a/a_file.txt\n"
          + "+++ b/a_file.txt.txt\n"
          + "@@ -1,2 +1 @@\n"
          + "-First original line\n"
          + "-Second original line\n"
          + "+Modified line\n";

  @Test
  public void createPatchApplyingChange_success() throws Exception {
    createBranch(BranchNameKey.create(project, "other"));
    ChangeInput input = newPatchApplyingChangeInput("other", PATCH_INPUT);

    ChangeInfo info = assertCreateSucceeds(input);

    DiffInfo diff = gApi.changes().id(info.id).current().file(PATCH_FILE_NAME).diff();
    assertDiffForNewFile(diff, info.currentRevision, PATCH_FILE_NAME, PATCH_NEW_FILE_CONTENT);
  }

  @Test
  public void createPatchApplyingChange_fromGerritPatch_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit =
        createChange("Add file", PATCH_FILE_NAME, PATCH_NEW_FILE_CONTENT);
    baseCommit.assertOkStatus();
    BinaryResult originalPatch = gApi.changes().id(baseCommit.getChangeId()).current().patch();
    createBranchWithRevision(BranchNameKey.create(project, "other"), head);
    ChangeInput input = newPatchApplyingChangeInput("other", originalPatch.asString());

    ChangeInfo info = assertCreateSucceeds(input);

    DiffInfo diff = gApi.changes().id(info.id).current().file(PATCH_FILE_NAME).diff();
    assertDiffForNewFile(diff, info.currentRevision, PATCH_FILE_NAME, PATCH_NEW_FILE_CONTENT);
  }

  @Test
  public void createPatchApplyingChange_fromGerritPatchUsingRest_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit =
        createChange("Add file", PATCH_FILE_NAME, PATCH_NEW_FILE_CONTENT);
    baseCommit.assertOkStatus();
    createBranchWithRevision(BranchNameKey.create(project, "other"), head);
    RestResponse patchResp =
        userRestSession.get("/changes/" + baseCommit.getChangeId() + "/revisions/current/patch");
    patchResp.assertOK();
    String originalPatch = new String(Base64.decode(patchResp.getEntityContent()), UTF_8);
    ChangeInput input = newPatchApplyingChangeInput("other", originalPatch);

    ChangeInfo info = assertCreateSucceedsUsingRest(input);

    DiffInfo diff = gApi.changes().id(info.id).current().file(PATCH_FILE_NAME).diff();
    assertDiffForNewFile(diff, info.currentRevision, PATCH_FILE_NAME, PATCH_NEW_FILE_CONTENT);
  }

  @Test
  public void createPatchApplyingChange_withParentChange_success() throws Exception {
    Result change = createChange();
    ChangeInput input = newPatchApplyingChangeInput("other", PATCH_INPUT);
    input.baseChange = change.getChangeId();

    ChangeInfo info = assertCreateSucceeds(input);

    assertThat(gApi.changes().id(info.id).current().commit(false).parents.get(0).commit)
        .isEqualTo(change.getCommit().getId().name());
    DiffInfo diff = gApi.changes().id(info.id).current().file(PATCH_FILE_NAME).diff();
    assertDiffForNewFile(diff, info.currentRevision, PATCH_FILE_NAME, PATCH_NEW_FILE_CONTENT);
  }

  @Test
  public void createPatchApplyingChange_withParentCommit_success() throws Exception {
    createBranch(BranchNameKey.create(project, "other"));
    Result baseChange = createChange("refs/heads/other");
    PushOneCommit.Result ignoredCommit = createChange();
    ignoredCommit.assertOkStatus();
    ChangeInput input = newPatchApplyingChangeInput("other", PATCH_INPUT);
    input.baseCommit = baseChange.getCommit().getId().name();

    ChangeInfo info = assertCreateSucceeds(input);

    assertThat(gApi.changes().id(info.id).current().commit(false).parents.get(0).commit)
        .isEqualTo(input.baseCommit);
    DiffInfo diff = gApi.changes().id(info.id).current().file(PATCH_FILE_NAME).diff();
    assertDiffForNewFile(diff, info.currentRevision, PATCH_FILE_NAME, PATCH_NEW_FILE_CONTENT);
  }

  @Test
  public void createPatchApplyingChange_withEmptyTip_fails() throws Exception {
    ChangeInput input = newPatchApplyingChangeInput("foo", "patch");
    input.newBranch = true;
    assertCreateFails(
        input, BadRequestException.class, "Cannot apply patch on top of an empty tree");
  }

  @Test
  public void createPatchApplyingChange_fromBadPatch_fails() throws Exception {
    final String invalidPatch = "@@ -2,2 +2,3 @@ a\n" + " b\n" + "+c\n" + " d";
    createBranch(BranchNameKey.create(project, "other"));
    ChangeInput input = newPatchApplyingChangeInput("other", invalidPatch);
    assertCreateFailsWithCause(
        input, BadRequestException.class, PatchFormatException.class, "Format error");
  }

  @Test
  public void createPatchApplyingChange_withAuthorOverride_success() throws Exception {
    createBranch(BranchNameKey.create(project, "other"));
    ChangeInput input = newPatchApplyingChangeInput("other", PATCH_INPUT);
    input.author = new AccountInput();
    input.author.email = "gerritlessjane@invalid";
    // This is an email address that doesn't exist as account on the Gerrit server.
    input.author.name = "Gerritless Jane";
    ChangeInfo info = assertCreateSucceeds(input);

    RevisionApi rApi = gApi.changes().id(info.id).current();
    GitPerson author = rApi.commit(false).author;
    assertThat(author).email().isEqualTo(input.author.email);
    assertThat(author).name().isEqualTo(input.author.name);
    GitPerson committer = rApi.commit(false).committer;
    assertThat(committer).email().isEqualTo(admin.getNameEmail().email());
  }

  @Test
  public void createPatchApplyingChange_withInfeasiblePatch_fails() throws Exception {
    createBranch(BranchNameKey.create(project, "other"));
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Adding unexpected base content, which will cause the patch to fail",
            PATCH_FILE_NAME,
            "unexpected base content");
    Result conflictingChange = push.to("refs/heads/other");
    conflictingChange.assertOkStatus();
    ChangeInput input = newPatchApplyingChangeInput("other", MODIFICATION_PATCH_INPUT);

    assertCreateFailsWithCause(
        input, RestApiException.class, PatchApplyException.class, "Cannot apply: HunkHeader");
  }

  @Test
  @UseSystemTime
  public void sha1sOfTwoNewChangesDiffer() throws Exception {
    ChangeInput changeInput = newChangeInput(ChangeStatus.NEW);
    ChangeInfo info1 = assertCreateSucceeds(changeInput);
    ChangeInfo info2 = assertCreateSucceeds(changeInput);
    assertThat(info1.currentRevision).isNotEqualTo(info2.currentRevision);
  }

  @Test
  @UseSystemTime
  public void sha1sOfTwoNewChangesDifferIfCreatedConcurrently() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      for (int i = 0; i < 10; i++) {
        ChangeInput changeInput = newChangeInput(ChangeStatus.NEW);

        CyclicBarrier sync = new CyclicBarrier(2);
        Callable<ChangeInfo> createChange =
            () -> {
              requestScopeOperations.setApiUser(admin.id());
              sync.await();
              return assertCreateSucceeds(changeInput);
            };

        Future<ChangeInfo> changeInfo1 = executor.submit(createChange);
        Future<ChangeInfo> changeInfo2 = executor.submit(createChange);
        assertThat(changeInfo1.get().currentRevision)
            .isNotEqualTo(changeInfo2.get().currentRevision);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createChangeWithSubmittedMergeSource() throws Exception {
    // Provide coverage for a performance optimization in CommitsCollection#canRead.
    BranchInput branchInput = new BranchInput();
    String mergeTarget = "refs/heads/new-branch";
    RevCommit startCommit = projectOperations.project(project).getHead("master");

    branchInput.revision = startCommit.name();
    branchInput.ref = mergeTarget;

    gApi.projects().name(project.get()).branch(mergeTarget).create(branchInput);

    // To create a merge commit, create two changes from the same parent,
    // and submit them one after the other.
    PushOneCommit.Result result1 =
        pushFactory
            .create(
                admin.newIdent(), testRepo, "subject1", ImmutableMap.of("file1.txt", "content 1"))
            .to("refs/for/master");
    result1.assertOkStatus();

    testRepo.branch("HEAD").update(startCommit);
    PushOneCommit.Result result2 =
        pushFactory
            .create(
                admin.newIdent(), testRepo, "subject2", ImmutableMap.of("file2.txt", "content 2"))
            .to("refs/for/master");
    result2.assertOkStatus();

    ReviewInput reviewInput = ReviewInput.approve().label("Code-Review", 2);

    gApi.changes().id(result1.getChangeId()).revision("current").review(reviewInput);
    gApi.changes().id(result1.getChangeId()).revision("current").submit();

    gApi.changes().id(result2.getChangeId()).revision("current").review(reviewInput);
    gApi.changes().id(result2.getChangeId()).revision("current").submit();

    String mergeRev = gApi.projects().name(project.get()).branch("master").get().revision;
    RevCommit mergeCommit = projectOperations.project(project).getHead("master");
    assertThat(mergeCommit.getParents().length).isEqualTo(2);

    testRepo.git().fetch().call();
    testRepo.branch("HEAD").update(mergeCommit);
    PushOneCommit.Result result3 =
        pushFactory
            .create(
                admin.newIdent(), testRepo, "subject3", ImmutableMap.of("file1.txt", "content 3"))
            .to("refs/for/master");
    result2.assertOkStatus();
    gApi.changes().id(result3.getChangeId()).revision("current").review(reviewInput);
    gApi.changes().id(result3.getChangeId()).revision("current").submit();

    // Now master doesn't point directly to mergeRev
    ChangeInput in = new ChangeInput();
    in.branch = mergeTarget;
    in.merge = new MergeInput();
    in.project = project.get();
    in.merge.source = mergeRev;
    in.subject = "propagate merge";

    gApi.changes().create(in);
  }

  @Test
  public void createChangeWithSourceBranch() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");

    // create a merge change from branchA to master in gerrit
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = "branchA";
    in.subject = "message";
    in.status = ChangeStatus.NEW;
    MergeInput mergeInput = new MergeInput();

    String mergeRev = gApi.projects().name(project.get()).branch("branchB").get().revision;
    mergeInput.source = mergeRev;
    in.merge = mergeInput;

    assertCreateSucceeds(in);

    // Succeeds with a visible branch
    in.merge.sourceBranch = "refs/heads/branchB";
    gApi.changes().create(in);

    // Make it invisible
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(READ).ref(in.merge.sourceBranch).group(REGISTERED_USERS))
        .update();

    // Now it fails.
    assertThrows(BadRequestException.class, () -> gApi.changes().create(in));
  }

  @Test
  public void createChangeWithValidationOptions() throws Exception {
    ChangeInput changeInput = new ChangeInput();
    changeInput.project = project.get();
    changeInput.branch = "master";
    changeInput.subject = "A change";
    changeInput.status = ChangeStatus.NEW;
    changeInput.validationOptions = ImmutableMap.of("key", "value");

    TestCommitValidationListener testCommitValidationListener = new TestCommitValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testCommitValidationListener)) {
      assertCreateSucceeds(changeInput);
      assertThat(testCommitValidationListener.receiveEvent.pushOptions)
          .containsExactly("key", "value");
    }
  }

  private ChangeInput newChangeInput(ChangeStatus status) {
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = "master";
    in.subject = "Empty change";
    in.topic = "support-gerrit-workflow-in-browser";
    in.status = status;
    return in;
  }

  private ChangeInfo assertCreateSucceeds(ChangeInput in) throws Exception {
    ChangeInfo out = gApi.changes().create(in).get();
    validateCreateSucceeds(in, out);
    return out;
  }

  private ChangeInfo assertCreateSucceedsUsingRest(ChangeInput in) throws Exception {
    RestResponse resp = adminRestSession.post("/changes/", in);
    resp.assertCreated();
    ChangeInfo res = readContentFromJson(resp, ChangeInfo.class);
    // The original result doesn't contain any revision data.
    ChangeInfo out = gApi.changes().id(res.changeId).get(ALL_REVISIONS, CURRENT_COMMIT);
    validateCreateSucceeds(in, out);
    return out;
  }

  private static <T> T readContentFromJson(RestResponse r, Class<T> clazz) throws Exception {
    try (JsonReader jsonReader = new JsonReader(r.getReader())) {
      return newGson().fromJson(jsonReader, clazz);
    }
  }

  private void validateCreateSucceeds(ChangeInput in, ChangeInfo out) throws Exception {
    assertThat(out.project).isEqualTo(in.project);
    assertThat(RefNames.fullName(out.branch)).isEqualTo(RefNames.fullName(in.branch));
    assertThat(out.subject).isEqualTo(Splitter.on("\n").splitToList(in.subject).get(0));
    assertThat(out.topic).isEqualTo(in.topic);
    assertThat(out.status).isEqualTo(in.status);
    if (Boolean.TRUE.equals(in.isPrivate)) {
      assertThat(out.isPrivate).isTrue();
    } else {
      assertThat(out.isPrivate).isNull();
    }
    if (Boolean.TRUE.equals(in.workInProgress)) {
      assertThat(out.workInProgress).isTrue();
    } else {
      assertThat(out.workInProgress).isNull();
    }
    assertThat(out.revisions).hasSize(1);
    assertThat(out.submitted).isNull();
    assertThat(out.containsGitConflicts).isNull();
    assertThat(in.status).isEqualTo(ChangeStatus.NEW);
  }

  private ChangeInfo assertCreateSucceedsWithConflicts(ChangeInput in) throws Exception {
    ChangeInfo out = gApi.changes().createAsInfo(in);
    assertThat(out.project).isEqualTo(in.project);
    assertThat(RefNames.fullName(out.branch)).isEqualTo(RefNames.fullName(in.branch));
    assertThat(out.subject).isEqualTo(Splitter.on("\n").splitToList(in.subject).get(0));
    assertThat(out.topic).isEqualTo(in.topic);
    assertThat(out.status).isEqualTo(in.status);
    if (in.isPrivate) {
      assertThat(out.isPrivate).isTrue();
    } else {
      assertThat(out.isPrivate).isNull();
    }
    assertThat(out.submitted).isNull();
    assertThat(out.containsGitConflicts).isTrue();
    assertThat(out.workInProgress).isTrue();
    assertThat(in.status).isEqualTo(ChangeStatus.NEW);
    return out;
  }

  private void assertCreateFails(
      ChangeInput in, Class<? extends RestApiException> errType, String errSubstring)
      throws Exception {
    Throwable thrown = assertThrows(errType, () -> gApi.changes().create(in));
    assertThat(thrown).hasMessageThat().contains(errSubstring);
  }

  private void assertCreateFailsWithCause(
      ChangeInput in,
      Class<? extends RestApiException> errType,
      Class<? extends Exception> causeType,
      String causeSubstring)
      throws Exception {
    Throwable thrown = assertThrows(errType, () -> gApi.changes().create(in));
    assertThat(thrown).hasCauseThat().isInstanceOf(causeType);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains(causeSubstring);
  }

  // TODO(davido): Expose setting of account preferences in the API
  private void setSignedOffByFooter(boolean value) throws Exception {
    RestResponse r = adminRestSession.get("/accounts/" + admin.email() + "/preferences");
    r.assertOK();
    GeneralPreferencesInfo i = newGson().fromJson(r.getReader(), GeneralPreferencesInfo.class);
    i.signedOffBy = value;

    r = adminRestSession.put("/accounts/" + admin.email() + "/preferences", i);
    r.assertOK();
    GeneralPreferencesInfo o = newGson().fromJson(r.getReader(), GeneralPreferencesInfo.class);

    if (value) {
      assertThat(o.signedOffBy).isTrue();
    } else {
      assertThat(o.signedOffBy).isNull();
    }

    requestScopeOperations.resetCurrentApiUser();
  }

  private ChangeInput newMergeChangeInput(String targetBranch, String sourceRef, String strategy) {
    return newMergeChangeInput(targetBranch, sourceRef, strategy, false);
  }

  private ChangeInput newMergeChangeInput(
      String targetBranch, String sourceRef, String strategy, boolean allowConflicts) {
    // create a merge change from branchA to master in gerrit
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = targetBranch;
    in.subject = "merge " + sourceRef + " to " + targetBranch;
    in.status = ChangeStatus.NEW;
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = sourceRef;
    in.merge = mergeInput;
    if (!Strings.isNullOrEmpty(strategy)) {
      in.merge.strategy = strategy;
    }
    in.merge.allowConflicts = allowConflicts;
    return in;
  }

  private ChangeInput newPatchApplyingChangeInput(String targetBranch, String patch) {
    // create a change applying the given patch on the target branch in gerrit
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = targetBranch;
    in.subject = "apply patch to " + targetBranch;
    in.status = ChangeStatus.NEW;
    ApplyPatchInput patchInput = new ApplyPatchInput();
    patchInput.patch = patch;
    in.patch = patchInput;
    return in;
  }

  /**
   * Create an empty commit in master, two new branches with one commit each.
   *
   * @param branchA name of first branch to create
   * @param fileA name of file to commit to branchA
   * @param branchB name of second branch to create
   * @param fileB name of file to commit to branchB
   * @return A {@code Map} of branchName => commit result.
   */
  private Map<String, Result> changeInTwoBranches(
      String branchA, String fileA, String branchB, String fileB) throws Exception {
    return changeInTwoBranches(
        branchA, "change A", fileA, "A content", branchB, "change B", fileB, "B content");
  }

  /**
   * Create an empty commit in master, two new branches with one commit each.
   *
   * @param branchA name of first branch to create
   * @param subjectA commit message subject for the change on branchA
   * @param fileA name of file to commit to branchA
   * @param contentA file content to commit to branchA
   * @param branchB name of second branch to create
   * @param subjectB commit message subject for the change on branchB
   * @param fileB name of file to commit to branchB
   * @param contentB file content to commit to branchB
   * @return A {@code Map} of branchName => commit result.
   */
  private Map<String, Result> changeInTwoBranches(
      String branchA,
      String subjectA,
      String fileA,
      String contentA,
      String branchB,
      String subjectB,
      String fileB,
      String contentB)
      throws Exception {
    // create a initial commit in master
    Result initialCommit =
        pushFactory
            .create(user.newIdent(), testRepo, "initial commit", "readme.txt", "initial commit")
            .to("refs/heads/master");
    initialCommit.assertOkStatus();

    // create two new branches
    createBranch(BranchNameKey.create(project, branchA));
    createBranch(BranchNameKey.create(project, branchB));

    // create a commit in branchA
    Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, subjectA, fileA, contentA)
            .to("refs/heads/" + branchA);
    changeA.assertOkStatus();

    // create a commit in branchB
    PushOneCommit commitB =
        pushFactory.create(user.newIdent(), testRepo, subjectB, fileB, contentB);
    commitB.setParent(initialCommit.getCommit());
    Result changeB = commitB.to("refs/heads/" + branchB);
    changeB.assertOkStatus();

    return ImmutableMap.of("master", initialCommit, branchA, changeA, branchB, changeB);
  }

  private static class TestCommitValidationListener implements CommitValidationListener {
    public CommitReceivedEvent receiveEvent;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      this.receiveEvent = receiveEvent;
      return ImmutableList.of();
    }
  }
}

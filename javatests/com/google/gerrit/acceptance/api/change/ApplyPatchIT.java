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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_FILES;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.patch.DiffUtil.normalizePatchForComparison;
import static com.google.gerrit.server.patch.DiffUtil.removePatchHeader;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchPatchSetInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.testing.GitPersonSubject;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.Base64;
import org.junit.Test;

public class ApplyPatchIT extends AbstractDaemonTest {

  private static final String DESTINATION_BRANCH = "destBranch";

  private static final String ADDED_FILE_NAME = "a_new_file.txt";
  private static final String ADDED_FILE_CONTENT = "First added line\nSecond added line\n";
  private static final String ADDED_FILE_DIFF =
      "diff --git a/a_new_file.txt b/a_new_file.txt\n"
          + "new file mode 100644\n"
          + "--- /dev/null\n"
          + "+++ b/a_new_file.txt\n"
          + "@@ -0,0 +1,2 @@\n"
          + "+First added line\n"
          + "+Second added line\n";

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void applyAddedFilePatch_success() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    DiffInfo diff = fetchDiffForFile(result, ADDED_FILE_NAME);
    assertDiffForNewFile(diff, result.currentRevision, ADDED_FILE_NAME, ADDED_FILE_CONTENT);
  }

  private static final String MODIFIED_FILE_NAME = "modified_file.txt";
  private static final String MODIFIED_FILE_ORIGINAL_CONTENT =
      "First original line\nSecond original line";
  private static final String MODIFIED_FILE_NEW_CONTENT = "Modified line\n";
  private static final String MODIFIED_FILE_DIFF =
      "diff --git a/modified_file.txt b/modified_file.txt\n"
          + "--- a/modified_file.txt\n"
          + "+++ b/modified_file.txt\n"
          + "@@ -1,2 +1 @@\n"
          + "-First original line\n"
          + "-Second original line\n"
          + "+Modified line\n";

  @Test
  public void applyPatchWithoutProvidingPatch_badRequest() throws Exception {
    initBaseWithFile(MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    Throwable error = assertThrows(BadRequestException.class, () -> applyPatch(buildInput(null)));
    assertThat(error).hasMessageThat().isEqualTo("patch required");
  }

  @Test
  public void applyModifiedFilePatch_success() throws Exception {
    initBaseWithFile(MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    ApplyPatchPatchSetInput in = buildInput(MODIFIED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    DiffInfo diff = fetchDiffForFile(result, MODIFIED_FILE_NAME);
    assertDiffForFullyModifiedFile(
        diff,
        result.currentRevision,
        MODIFIED_FILE_NAME,
        MODIFIED_FILE_ORIGINAL_CONTENT,
        MODIFIED_FILE_NEW_CONTENT);
  }

  @Test
  public void applyDeletedFilePatch_success() throws Exception {
    final String deletedFileName = "deleted_file.txt";
    final String deletedFileOriginalContent = "content to be deleted.\n";
    final String deletedFileDiff =
        "diff --git a/deleted_file.txt b/deleted_file.txt\n"
            + "--- a/deleted_file.txt\n"
            + "+++ /dev/null\n";
    initBaseWithFile(deletedFileName, deletedFileOriginalContent);
    ApplyPatchPatchSetInput in = buildInput(deletedFileDiff);

    ChangeInfo result = applyPatch(in);

    DiffInfo diff = fetchDiffForFile(result, deletedFileName);
    assertDiffForDeletedFile(diff, deletedFileName, deletedFileOriginalContent);
  }

  @Test
  public void applyRenamedFilePatch_success() throws Exception {
    final String renamedFileOriginalName = "renamed_file_origin.txt";
    final String renamedFileNewName = "renamed_file_new.txt";
    final String renamedFileDiff =
        "diff --git a/renamed_file_origin.txt b/renamed_file_new.txt\n"
            + "rename from renamed_file_origin.txt\n"
            + "rename to renamed_file_new.txt\n"
            + "--- a/renamed_file_origin.txt\n"
            + "+++ b/renamed_file_new.txt\n"
            + "@@ -1,2 +1 @@\n"
            + "-First original line\n"
            + "-Second original line\n"
            + "+Modified line\n";
    initBaseWithFile(renamedFileOriginalName, MODIFIED_FILE_ORIGINAL_CONTENT);
    ApplyPatchPatchSetInput in = buildInput(renamedFileDiff);

    ChangeInfo result = applyPatch(in);

    DiffInfo originalFileDiff = fetchDiffForFile(result, renamedFileOriginalName);
    assertDiffForDeletedFile(
        originalFileDiff, renamedFileOriginalName, MODIFIED_FILE_ORIGINAL_CONTENT);
    DiffInfo newFileDiff = fetchDiffForFile(result, renamedFileNewName);
    assertDiffForNewFile(
        newFileDiff, result.currentRevision, renamedFileNewName, MODIFIED_FILE_NEW_CONTENT);
  }

  @Test
  public void applyValidTraditionalPatch_success() throws Exception {
    final String fileName = "file_name.txt";
    final String originalContent = "original line";
    final String newContent = "new line\n";
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-original line\n"
            + "+new line\n";
    initBaseWithFile(fileName, originalContent);
    ApplyPatchPatchSetInput in = buildInput(diff);

    ChangeInfo result = applyPatch(in);

    DiffInfo fileDiff = fetchDiffForFile(result, fileName);
    assertDiffForFullyModifiedFile(
        fileDiff, result.currentRevision, fileName, originalContent, newContent);
  }

  @Test
  public void applyGerritBasedPatchWithSingleFile_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit = createChange("Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT);
    baseCommit.assertOkStatus();
    BinaryResult originalPatch = gApi.changes().id(baseCommit.getChangeId()).current().patch();
    ApplyPatchPatchSetInput in = buildInput(originalPatch.asString());
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);

    ChangeInfo result = applyPatch(in);

    BinaryResult resultPatch = gApi.changes().id(result.id).current().patch();
    assertThat(normalizePatchForComparison(resultPatch))
        .isEqualTo(normalizePatchForComparison(originalPatch));
  }

  @Test
  public void applyGerritBasedPatchWithMultipleFiles_success() throws Exception {
    PushOneCommit.Result commonBaseCommit =
        createChange("File for modification", MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    commonBaseCommit.assertOkStatus();
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result commitToPatch =
        createChange("Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT);
    amendChange(
        commitToPatch.getChangeId(), "Modify file", MODIFIED_FILE_NAME, MODIFIED_FILE_NEW_CONTENT);
    commitToPatch.assertOkStatus();
    BinaryResult originalPatch = gApi.changes().id(commitToPatch.getChangeId()).current().patch();
    ApplyPatchPatchSetInput in = buildInput(originalPatch.asString());
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);

    ChangeInfo result = applyPatch(in);

    BinaryResult resultPatch = gApi.changes().id(result.id).current().patch();
    assertThat(normalizePatchForComparison(resultPatch))
        .isEqualTo(normalizePatchForComparison(originalPatch));
  }

  @Test
  public void applyGerritBasedPatchUsingRest_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);
    PushOneCommit.Result destChange = createChange("refs/for/" + DESTINATION_BRANCH);
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit =
        createChange(testRepo, "branch", "Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT, "");
    baseCommit.assertOkStatus();
    RestResponse patchResp =
        userRestSession.get("/changes/" + baseCommit.getChangeId() + "/revisions/current/patch");
    patchResp.assertOK();
    String originalPatch = new String(Base64.decode(patchResp.getEntityContent()), UTF_8);
    ApplyPatchPatchSetInput in = buildInput(originalPatch);

    RestResponse resp =
        adminRestSession.post("/changes/" + destChange.getChangeId() + "/patch:apply", in);

    resp.assertOK();
    BinaryResult resultPatch = gApi.changes().id(destChange.getChangeId()).current().patch();
    assertThat(normalizePatchForComparison(resultPatch))
        .isEqualTo(normalizePatchForComparison(originalPatch));
  }

  @Test
  public void applyGerritBasedPatchUsingRestWithEncodedPatch_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);
    PushOneCommit.Result destChange = createChange("refs/for/" + DESTINATION_BRANCH);
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit =
        createChange(testRepo, "branch", "Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT, "");
    baseCommit.assertOkStatus();
    RestResponse patchResp =
        userRestSession.get("/changes/" + baseCommit.getChangeId() + "/revisions/current/patch");
    patchResp.assertOK();
    String originalEncodedPatch = patchResp.getEntityContent();
    String originalDecodedPatch = new String(Base64.decode(patchResp.getEntityContent()), UTF_8);
    ApplyPatchPatchSetInput in = buildInput(originalEncodedPatch);

    RestResponse resp =
        adminRestSession.post("/changes/" + destChange.getChangeId() + "/patch:apply", in);

    resp.assertOK();
    BinaryResult resultPatch = gApi.changes().id(destChange.getChangeId()).current().patch();
    assertThat(normalizePatchForComparison(resultPatch))
        .isEqualTo(normalizePatchForComparison(originalDecodedPatch));
  }

  @Test
  public void applyPatchWithConflict_appendErrorsToCommitMessage() throws Exception {
    initBaseWithFile(MODIFIED_FILE_NAME, "Unexpected base content");
    String patch = ADDED_FILE_DIFF + MODIFIED_FILE_DIFF;
    ApplyPatchPatchSetInput in = buildInput(patch);
    in.commitMessage = "subject";

    ChangeInfo result = applyPatch(in);

    assertThat(gApi.changes().id(result.id).current().commit(false).message)
        .isEqualTo(
            in.commitMessage
                + "\n\nNOTE FOR REVIEWERS - errors occurred while applying the patch."
                + "\nPLEASE REVIEW CAREFULLY.\nErrors:\nError applying patch in "
                + MODIFIED_FILE_NAME
                + ", hunk HunkHeader[1,2->1,1]: Hunk cannot be applied\n\nOriginal patch:\n "
                + removePatchHeader(patch)
                + "\n\nChange-Id: "
                + result.changeId
                + "\n");
    // Error in MODIFIED_FILE should not affect ADDED_FILE results.
    DiffInfo diff = fetchDiffForFile(result, ADDED_FILE_NAME);
    assertDiffForNewFile(diff, result.currentRevision, ADDED_FILE_NAME, ADDED_FILE_CONTENT);
  }

  @Test
  public void applyPatchWithoutAddPatchSetPermissions_fails() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    projectOperations
        .project(allProjects)
        .forUpdate()
        .remove(
            permissionKey(Permission.ADD_PATCH_SET)
                .ref("refs/for/*")
                .group(REGISTERED_USERS)
                .build())
        .update();
    PushOneCommit.Result destChange = createChange("dest change", "a file", "with content");
    // Add-patch is always allowed for the change owner, so we need to use another account.
    requestScopeOperations.setApiUser(accountCreator.user2().id());

    Throwable error =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(destChange.getChangeId()).applyPatch(in));

    assertThat(error).hasMessageThat().contains("patch set");
  }

  @Test
  public void applyPatchWithCustomMessage_success() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.commitMessage = "custom commit message";

    ChangeInfo result = applyPatch(in);

    assertThat(gApi.changes().id(result.id).current().commit(false).message)
        .contains(in.commitMessage);
  }

  @Test
  public void applyPatchWithBaseCommit_success() throws Exception {
    PushOneCommit.Result baseCommit =
        createChange("base commit", MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    baseCommit.assertOkStatus();
    PushOneCommit.Result ignoredCommit =
        createChange("Ignored file modification", MODIFIED_FILE_NAME, "Ignored file modification");
    ignoredCommit.assertOkStatus();
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(MODIFIED_FILE_DIFF);
    in.base = baseCommit.getCommit().getName();

    ChangeInfo result = applyPatch(in);

    assertThat(gApi.changes().id(result.id).current().commit(false).parents.get(0).commit)
        .isEqualTo(in.base);
  }

  @Test
  public void applyPatchWithDefaultAuthor_success() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    GitPerson person = gApi.changes().id(result.id).current().commit(false).author;
    GitPersonSubject.assertThat(person).email().isEqualTo(admin.email());
  }

  @Test
  public void applyPatchWithAuthorOverrideMissingEmail_throwsIllegalArgument() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.author = new AccountInput();
    in.author.name = "name";

    Throwable error = assertThrows(IllegalArgumentException.class, () -> applyPatch(in));

    assertThat(error).hasMessageThat().contains("E-mail");
  }

  @Test
  public void applyPatchWithAuthorOverrideMissingName_throwsIllegalArgument() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.author = new AccountInput();
    in.author.name = null;
    in.author.email = "gerritlessjane@invalid";

    Throwable error = assertThrows(IllegalArgumentException.class, () -> applyPatch(in));

    assertThat(error).hasMessageThat().contains("Name");
  }

  @Test
  public void applyPatchWithAuthorOverride_success() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.author = new AccountInput();
    in.author.email = "gerritlessjane@invalid";
    // This is an email address that doesn't exist as account on the Gerrit server.
    in.author.name = "Gerritless Jane";

    ChangeInfo result = applyPatch(in);

    RevisionApi rApi = gApi.changes().id(result.id).current();
    GitPerson author = rApi.commit(false).author;
    GitPersonSubject.assertThat(author).email().isEqualTo(in.author.email);
    GitPersonSubject.assertThat(author).name().isEqualTo(in.author.name);
    GitPerson committer = rApi.commit(false).committer;
    GitPersonSubject.assertThat(committer).email().isEqualTo(admin.getNameEmail().email());
  }

  @Test
  public void applyPatchWithAuthorWithoutPermissions_fails() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.author = new AccountInput();
    in.author.name = "Jane";
    in.author.email = "jane@invalid";
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .update();

    Throwable error = assertThrows(ResourceConflictException.class, () -> applyPatch(in));

    assertThat(error).hasMessageThat().contains("forge author");
  }

  @Test
  public void applyPatchWithSelfAsForgedAuthor_success() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.author = new AccountInput();
    in.author.name = admin.fullName();
    in.author.email = admin.email();
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .update();

    ChangeInfo result = applyPatch(in);

    GitPerson person = gApi.changes().id(result.id).current().commit(false).author;
    GitPersonSubject.assertThat(person).email().isEqualTo(admin.email());
  }

  @Test
  public void applyPatchWithExplicitBase_overrideParentId() throws Exception {
    PushOneCommit.Result inputParent = createChange("Input parent", "file1", "content");
    PushOneCommit.Result parent = createChange("Parent Change", "file2", "content");
    parent.assertOkStatus();
    PushOneCommit.Result dest = createChange("Destination Change", "file3", "content");
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.base = inputParent.getCommit().name();

    gApi.changes().id(dest.getChangeId()).applyPatch(in);

    ChangeInfo result = get(dest.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(result.revisions.get(result.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(inputParent.getCommit().name());

    BinaryResult resultPatch = gApi.changes().id(dest.getChangeId()).current().patch();
    assertThat(normalizePatchForComparison(resultPatch))
        .isEqualTo(normalizePatchForComparison(ADDED_FILE_DIFF));
  }

  @Test
  public void applyPatchWithNoExplicitBase_overwritesLatestPatch() throws Exception {
    PushOneCommit.Result dest = createChange("Destination Change", "ps1.txt", "ps1 content");
    RevCommit originalParentCommit = dest.getCommit().getParent(0);
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);

    gApi.changes().id(dest.getChangeId()).applyPatch(in);

    ChangeInfo result = get(dest.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT, CURRENT_FILES);
    assertThat(result.revisions.get(result.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(originalParentCommit.name());
    assertThat(result.revisions.get(result.currentRevision).files.keySet())
        .containsExactly(ADDED_FILE_NAME);
    assertDiffForNewFile(
        fetchDiffForFile(result, ADDED_FILE_NAME),
        result.currentRevision,
        ADDED_FILE_NAME,
        ADDED_FILE_CONTENT);
  }

  @Test
  public void commitMessage_providedMessage() throws Exception {
    final String msg = "custom message";
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.commitMessage = msg;

    ChangeInfo result = applyPatch(in);

    ChangeInfo info = get(result.changeId, CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .isEqualTo(msg + "\n\nChange-Id: " + result.changeId + "\n");
  }

  @Test
  public void commitMessage_providedMessageWithChangeId() throws Exception {
    initDestBranch();
    String changeId =
        gApi.changes()
            .create(new ChangeInput(project.get(), DESTINATION_BRANCH, "Default commit message"))
            .info()
            .changeId;
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.commitMessage = "custom commit message\n\nChange-Id: " + changeId + "\n";

    ChangeInfo result = gApi.changes().id(changeId).applyPatch(in);

    assertThat(gApi.changes().id(result.id).current().commit(false).message)
        .isEqualTo(in.commitMessage);
  }

  @Test
  public void commitMessage_defaultMessageAndPatchHeader() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput("Patch header\n" + ADDED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    ChangeInfo info = get(result.changeId, CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .isEqualTo("Default commit message\n\nChange-Id: " + result.changeId + "\n");
  }

  @Test
  public void commitMessage_defaultMessageAndNoPatchHeader() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    ChangeInfo info = get(result.changeId, CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .isEqualTo("Default commit message\n\nChange-Id: " + result.changeId + "\n");
  }

  @Test
  public void amendCommitWithValidTraditionalPatch_success() throws Exception {
    final String fileName = "file_name.txt";
    final String originalContent = "original line";
    final String newContent = "new line\n";
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-original line\n"
            + "+new line\n";

    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, "Test", fileName, "foo");
    PushOneCommit.Result base = push.to("refs/heads/foo");
    base.assertOkStatus();

    PushOneCommit.Result firstPatchSet =
        createChange(
            testRepo, "foo", "Add original file: " + fileName, fileName, originalContent, null);
    firstPatchSet.assertOkStatus();

    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;
    in.responseFormatOptions =
        ImmutableList.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

    ChangeInfo result = gApi.changes().id(firstPatchSet.getChangeId()).applyPatch(in);

    // Parent of patch set 2 = parent of patch set 1, so we actually amended
    assertThat(result.revisions.get(result.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(base.getCommit().getId().getName());
    DiffInfo fileDiff = gApi.changes().id(result.id).current().file(fileName).diff();
    assertDiffForFullyModifiedFile(fileDiff, result.currentRevision, fileName, "foo", newContent);
    assertThat(gApi.changes().id(firstPatchSet.getChangeId()).current().commit(false).message)
        .isEqualTo(firstPatchSet.getCommit().getFullMessage());
  }

  @Test
  public void amendCantBeUsedWithBase() throws Exception {
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-original line\n"
            + "+new line\n";
    PushOneCommit.Result change = createChange();
    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;
    in.base = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(change.getChangeId()).applyPatch(in));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("amend only works with existing revisions. omit base.");
  }

  @Test
  public void amendCommitWithConflict_appendErrorsToCommitMessage() throws Exception {
    final String fileName = "file_name.txt";
    final String originalContent = "original line";
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-xxx line\n"
            + "+new line\n";

    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, "Test", fileName, "foo");
    PushOneCommit.Result base = push.to("refs/heads/foo");
    base.assertOkStatus();

    PushOneCommit.Result firstPatchSet =
        createChange(
            testRepo, "foo", "Add original file: " + fileName, fileName, originalContent, null);
    firstPatchSet.assertOkStatus();

    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;
    in.responseFormatOptions =
        ImmutableList.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

    ChangeInfo result = gApi.changes().id(firstPatchSet.getChangeId()).applyPatch(in);
    assertThat(gApi.changes().id(result.id).current().commit(false).message)
        .startsWith(
            "Add original file: file_name.txt\n"
                + "\n"
                + "NOTE FOR REVIEWERS - errors occurred while applying the patch.\n"
                + "PLEASE REVIEW CAREFULLY.\n"
                + "Errors:\n"
                + "Error applying patch in file_name.txt, hunk HunkHeader[1,1->1,1]: Hunk cannot be applied\n"
                + "\n"
                + "Original patch:\n"
                + " diff file_name.txt file_name.txt\n"
                + "--- file_name.txt\n"
                + "+++ file_name.txt\n"
                + "@@ -1 +1 @@\n"
                + "-xxx line\n"
                + "+new line");
  }

  @Test
  public void amendCommitWithValidTraditionalPatchEmptyRepo_resourceNotFound() throws Exception {
    final String fileName = "file_name.txt";
    final String originalContent = "original line";
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-original line\n"
            + "+new line\n";

    Project.NameKey emptyProject = projectOperations.newProject().noEmptyCommit().create();
    TestRepository<InMemoryRepository> emptyClone = cloneProject(emptyProject);
    PushOneCommit.Result firstPatchSet =
        createChange(
            emptyClone,
            "master",
            "Add original file: " + fileName,
            fileName,
            originalContent,
            null);
    firstPatchSet.assertOkStatus();

    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;

    Throwable error =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.changes().id(firstPatchSet.getChangeId()).applyPatch(in));
    assertThat(error).hasMessageThat().contains("Branch refs/heads/master does not exist");
  }

  private void initDestBranch() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, ApplyPatchIT.DESTINATION_BRANCH), head);
  }

  private void initBaseWithFile(String fileName, String fileContent) throws Exception {
    PushOneCommit.Result baseCommit =
        createChange("Add original file: " + fileName, fileName, fileContent);
    baseCommit.assertOkStatus();
    initDestBranch();
  }

  private ApplyPatchPatchSetInput buildInput(String patch) {
    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = patch;
    return in;
  }

  private ChangeInfo applyPatch(ApplyPatchPatchSetInput input) throws RestApiException {
    input.responseFormatOptions = ImmutableList.of(ListChangesOption.CURRENT_REVISION);
    return gApi.changes()
        .create(new ChangeInput(project.get(), DESTINATION_BRANCH, "Default commit message"))
        .applyPatch(input);
  }

  private DiffInfo fetchDiffForFile(ChangeInfo result, String fileName) throws RestApiException {
    return gApi.changes().id(result.id).current().file(fileName).diff();
  }
}

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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchPatchSetInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.testing.GitPersonSubject;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.util.Base64;
import org.junit.Test;

public class ApplyPatchIT extends AbstractDaemonTest {

  private static final String COMMIT_MESSAGE = "Applying patch";
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
          + "new file mode 100644\n"
          + "--- a/modified_file.txt\n"
          + "+++ b/modified_file.txt\n"
          + "@@ -1,2 +1 @@\n"
          + "-First original line\n"
          + "-Second original line\n"
          + "+Modified line\n";

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
    assertThat(removeHeader(resultPatch)).isEqualTo(removeHeader(originalPatch));
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
    assertThat(removeHeader(resultPatch)).isEqualTo(removeHeader(originalPatch));
  }

  @Test
  public void applyGerritBasedPatchUsingRest_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit = createChange("Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT);
    baseCommit.assertOkStatus();
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);
    RestResponse patchResp =
        userRestSession.get("/changes/" + baseCommit.getChangeId() + "/revisions/current/patch");
    patchResp.assertOK();
    String originalPatch = new String(Base64.decode(patchResp.getEntityContent()), UTF_8);
    ApplyPatchPatchSetInput in = buildInput(originalPatch);
    PushOneCommit.Result destChange = createChange();

    RestResponse resp =
        adminRestSession.post("/changes/" + destChange.getChangeId() + "/patch:apply", in);

    resp.assertOK();
    BinaryResult resultPatch = gApi.changes().id(destChange.getChangeId()).current().patch();
    assertThat(removeHeader(resultPatch)).isEqualTo(removeHeader(originalPatch));
  }

  @Test
  public void applyGerritBasedPatchUsingRestWithEncodedPatch_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit = createChange("Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT);
    baseCommit.assertOkStatus();
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);
    RestResponse patchResp =
        userRestSession.get("/changes/" + baseCommit.getChangeId() + "/revisions/current/patch");
    patchResp.assertOK();
    String originalEncodedPatch = patchResp.getEntityContent();
    String originalDecodedPatch = new String(Base64.decode(patchResp.getEntityContent()), UTF_8);
    ApplyPatchPatchSetInput in = buildInput(originalEncodedPatch);
    PushOneCommit.Result destChange = createChange();

    RestResponse resp =
        adminRestSession.post("/changes/" + destChange.getChangeId() + "/patch:apply", in);

    resp.assertOK();
    BinaryResult resultPatch = gApi.changes().id(destChange.getChangeId()).current().patch();
    assertThat(removeHeader(resultPatch)).isEqualTo(removeHeader(originalDecodedPatch));
  }

  @Test
  public void applyPatchWithConflict_fails() throws Exception {
    initBaseWithFile(MODIFIED_FILE_NAME, "Unexpected base content");
    ApplyPatchPatchSetInput in = buildInput(MODIFIED_FILE_DIFF);

    Throwable error = assertThrows(RestApiException.class, () -> applyPatch(in));

    assertThat(error).hasMessageThat().contains("Cannot apply patch");
    assertThat(error).hasCauseThat().isInstanceOf(PatchApplyException.class);
    assertThat(error).hasCauseThat().hasMessageThat().contains("Cannot apply: HunkHeader");
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
    return gApi.changes()
        .create(new ChangeInput(project.get(), DESTINATION_BRANCH, COMMIT_MESSAGE))
        .applyPatch(input);
  }

  private DiffInfo fetchDiffForFile(ChangeInfo result, String fileName) throws RestApiException {
    return gApi.changes().id(result.id).current().file(fileName).diff();
  }

  private String removeHeader(BinaryResult bin) throws IOException {
    return removeHeader(bin.asString());
  }

  private String removeHeader(String s) {
    return s.substring(s.lastIndexOf("\ndiff --git"), s.length() - 1);
  }
}

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

import static org.eclipse.jgit.lib.Constants.HEAD;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchAsPatchSetInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.io.IOException;
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

  @Test
  public void applyAddedFilePatch() throws Exception {
    initDestBranch(DESTINATION_BRANCH, HEAD);
    ApplyPatchAsPatchSetInput in = buildInput(ADDED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    DiffInfo diff = fetchDiffForFile(result, ADDED_FILE_NAME);
    assertDiffForNewFile(diff, null, ADDED_FILE_NAME, ADDED_FILE_CONTENT);
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
  public void applyModifiedFilePatch() throws Exception {
    initBaseWithFile(MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    ApplyPatchAsPatchSetInput in = buildInput(MODIFIED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    DiffInfo diff = fetchDiffForFile(result, MODIFIED_FILE_NAME);
    assertDiffForFullyModifiedFile(
        diff, null, MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT, MODIFIED_FILE_NEW_CONTENT);
  }

  private static final String DELETED_FILE_NAME = "deleted_file.txt";
  private static final String DELETED_FILE_ORIGINAL_CONTENT = "content to be deleted.\n";
  private static final String DELETED_FILE_DIFF =
      "diff --git a/deleted_file.txt b/deleted_file.txt\n"
          + "--- a/deleted_file.txt\n"
          + "+++ /dev/null\n";

  @Test
  public void applyDeletedFilePatch() throws Exception {
    initBaseWithFile(DELETED_FILE_NAME, DELETED_FILE_ORIGINAL_CONTENT);
    ApplyPatchAsPatchSetInput in = buildInput(DELETED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    DiffInfo diff = fetchDiffForFile(result, DELETED_FILE_NAME);
    assertDiffForDeletedFile(diff, null, DELETED_FILE_NAME, DELETED_FILE_ORIGINAL_CONTENT);
  }

  private static final String RENAMED_FILE_ORIGINAL_NAME = "renamed_file_origin.txt";
  private static final String RENAMED_FILE_NEW_NAME = "renamed_file_new.txt";
  private static final String RENAMED_FILE_DIFF =
      "diff --git a/renamed_file_origin.txt b/renamed_file_new.txt\n"
          + "rename from renamed_file_origin.txt\n"
          + "rename to renamed_file_new.txt\n"
          + "--- a/renamed_file_origin.txt\n"
          + "+++ b/renamed_file_new.txt\n"
          + "@@ -1,2 +1 @@\n"
          + "-First original line\n"
          + "-Second original line\n"
          + "+Modified line\n";

  @Test
  public void applyRenamedFilePatch() throws Exception {
    initBaseWithFile(RENAMED_FILE_ORIGINAL_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    ApplyPatchAsPatchSetInput in = buildInput(RENAMED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    DiffInfo originalFileDiff = fetchDiffForFile(result, RENAMED_FILE_ORIGINAL_NAME);
    assertDiffForDeletedFile(
        originalFileDiff, null, RENAMED_FILE_ORIGINAL_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    DiffInfo newFileDiff = fetchDiffForFile(result, RENAMED_FILE_NEW_NAME);
    assertDiffForNewFile(newFileDiff, null, RENAMED_FILE_NEW_NAME, MODIFIED_FILE_NEW_CONTENT);
  }

  @Test
  public void applyGerritBasedPatch() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit = createChange("Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT);
    baseCommit.assertOkStatus();
    BinaryResult originalPatch = gApi.changes().id(baseCommit.getChangeId()).current().patch();
    ApplyPatchAsPatchSetInput in = buildInput(originalPatch.asString());
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);

    ChangeInfo result = applyPatch(in);

    BinaryResult resultPatch = gApi.changes().id(result.id).current().patch();
    assertThat(removeHeader(resultPatch)).isEqualTo(removeHeader(originalPatch));
  }

  private void initDestBranch(String branchName, String parent) throws Exception {
    String head = getHead(repo(), parent).name();
    createBranchWithRevision(BranchNameKey.create(project, branchName), head);
  }

  private void initBaseWithFile(String fileName, String fileContent) throws Exception {
    PushOneCommit.Result baseCommit = createChange("Add original file: " + fileName, fileName,
        fileContent);
    baseCommit.assertOkStatus();
    initDestBranch(DESTINATION_BRANCH, HEAD);
  }

  private ApplyPatchAsPatchSetInput buildInput(String patch) {
    ApplyPatchAsPatchSetInput in = new ApplyPatchAsPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = patch;
    return in;
  }

  private ChangeInfo applyPatch(ApplyPatchAsPatchSetInput input) throws RestApiException {
    return gApi.changes()
        .create(new ChangeInput(project.get(), DESTINATION_BRANCH, COMMIT_MESSAGE))
        .applyPatch(input);
  }

  private DiffInfo fetchDiffForFile(ChangeInfo result, String fileName) throws RestApiException {
    return gApi.changes().id(result.id).current().file(fileName).diff();
  }

  private String removeHeader(BinaryResult bin) throws IOException {
    String full = bin.asString();
    return full.substring(full.indexOf("diff --git"), full.length() - 1);
  }
}

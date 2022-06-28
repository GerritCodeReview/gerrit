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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.DiffInfo;
import org.junit.Before;
import org.junit.Test;

public class ApplyPatchIT extends AbstractDaemonTest {

  private static final String COMMIT_MESSAGE = "Applying patch";
  private static final String DESTINATION_BRANCH = "destBranch";
  private static final String ADDED_FILE_NAME = "a_new_file.txt";
  private static final String ADDED_FILE_CONTENT = "First added line\nSecond added line\n";
  private static final String MODIFIED_FILE_NAME = "modified_file.txt";
  private static final String MODIFIED_FILE_ORIGINAL_CONTENT =
      "First original line\nSecond original line";
  private static final String MODIFIED_FILE_NEW_CONTENT = "Modified line\n";
  private static final String DELETED_FILE_NAME = "deleted_file.txt";
  private static final String DELETED_FILE_ORIGINAL_CONTENT = "content to be deleted.\n";

  private static final String ADDED_FILE_DIFF =
      "diff --git a/a_new_file.txt b/a_new_file.txt\n"
          + "new file mode 100644\n"
          + "index 0000000..f0eec86\n"
          + "--- /dev/null\n"
          + "+++ b/a_new_file.txt\n"
          + "@@ -0,0 +1,2 @@\n"
          + "+First added line\n"
          + "+Second added line\n";
  private static final String MODIFIED_FILE_DIFF =
      "diff --git a/modified_file.txt b/modified_file.txt\n"
          + "index 009cbc4ed..0063edbe7 100644\n"
          + "--- a/modified_file.txt\n"
          + "+++ b/modified_file.txt\n"
          + "@@ -1,2 +1 @@\n"
          + "-First original line\n"
          + "-Second original line\n"
          + "+Modified line\n";
  private static final String DELETED_FILE_DIFF =
      "diff --git a/deleted_file.txt b/deleted_file.txt\n"
          + "index fe6a849..0000000\n"
          + "--- a/deleted_file.txt\n"
          + "+++ /dev/null\n";

  @Before
  public void setup() throws Exception {
    PushOneCommit.Result baseCommit =
        createChange("empty change", "unaffected_file.txt", "nothing will change here.");
    baseCommit =
        amendChange(
            baseCommit.getChangeId(),
            "Add file for deletion",
            DELETED_FILE_NAME,
            DELETED_FILE_ORIGINAL_CONTENT);
    baseCommit =
        amendChange(
            baseCommit.getChangeId(),
            "Add file for modification",
            MODIFIED_FILE_NAME,
            MODIFIED_FILE_ORIGINAL_CONTENT);
    baseCommit.assertOkStatus();

    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);
  }

  @Test
  public void applyAddedFilePatch() throws Exception {
    ApplyPatchInput in = new ApplyPatchInput();
    in.patch = ADDED_FILE_DIFF;

    ChangeInfo result =
        gApi.changes()
            .create(new ChangeInput(project.get(), DESTINATION_BRANCH, COMMIT_MESSAGE))
            .applyPatch(in);

    DiffInfo diff = gApi.changes().id(result.id).current().file(ADDED_FILE_NAME).diff();
    assertDiffForNewFile(diff, null, ADDED_FILE_NAME, ADDED_FILE_CONTENT);
  }

  @Test
  public void applyModifiedFilePatch() throws Exception {
    ApplyPatchInput in = new ApplyPatchInput();
    in.patch = MODIFIED_FILE_DIFF;

    ChangeInfo result =
        gApi.changes()
            .create(new ChangeInput(project.get(), DESTINATION_BRANCH, COMMIT_MESSAGE))
            .applyPatch(in);

    DiffInfo diff = gApi.changes().id(result.id).current().file(MODIFIED_FILE_NAME).diff();
    assertDiffForFullyModifiedFile(
        diff, null, MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT, MODIFIED_FILE_NEW_CONTENT);
  }

  @Test
  public void applyDeletedFilePatch() throws Exception {
    ApplyPatchInput in = new ApplyPatchInput();
    in.patch = DELETED_FILE_DIFF;

    ChangeInfo result =
        gApi.changes()
            .create(new ChangeInput(project.get(), DESTINATION_BRANCH, COMMIT_MESSAGE))
            .applyPatch(in);

    DiffInfo diff = gApi.changes().id(result.id).current().file(DELETED_FILE_NAME).diff();
    assertDiffForDeletedFile(diff, null, DELETED_FILE_NAME, DELETED_FILE_ORIGINAL_CONTENT);
  }

  //  @Test
  //  public void applyMultipleFilesPatch() throws Exception {
  //  }
  //
  //
  //  @Test
  //  public void appliedPatchChangeAttributes() throws Exception {
  //  }
  //
  //
  //  @Test
  //  public void appliedPatchOverrideChangeMessage() throws Exception {
  //  }
}

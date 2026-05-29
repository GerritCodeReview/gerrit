// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.restapi.project.GetDiffFile;
import com.google.gerrit.server.restapi.project.ListDiffFiles;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Test class for project-level diff endpoints: {@link ListDiffFiles} and {@link GetDiffFile}. */
public class DiffCommitsIT extends AbstractDaemonTest {
  private static final String R_HEADS_MASTER = "refs/heads/master";

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private ObjectId initialCommit;

  @Before
  public void setUp() throws Exception {
    initialCommit = testRepo.getRepository().resolve("HEAD");
  }

  @Test
  public void listDiffFiles_success() throws Exception {
    // Create a commit with file changes
    PushOneCommit.Result result =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Test commit",
                ImmutableMap.of("file1.txt", "content1", "file2.txt", "content2"))
            .to("refs/heads/master");
    result.assertOkStatus();

    ObjectId newCommit = result.getCommit();

    Map<String, FileInfo> files =
        gApi.projects().name(project.get()).diffFiles(initialCommit.name(), newCommit.name(), true);

    assertThat(files).isNotEmpty();
    assertThat(files.keySet()).contains("file1.txt");
    assertThat(files.keySet()).contains("file2.txt");
  }

  @Test
  public void listDiffFiles_missingOldParam() throws Exception {
    PushOneCommit.Result result = createChange();
    ObjectId newCommit = result.getCommit();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).diffFiles(null, newCommit.name(), true));
    assertThat(thrown).hasMessageThat().contains("base");
  }

  @Test
  public void listDiffFiles_missingNewParam() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).diffFiles(initialCommit.name(), null, true));
    assertThat(thrown).hasMessageThat().contains("new commit SHA1");
  }

  @Test
  public void listDiffFiles_invalidSha1Format() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .diffFiles("invalid", initialCommit.name(), true));
    assertThat(thrown).hasMessageThat().contains("40-character SHA1");
  }

  @Test
  public void listDiffFiles_commitNotFound() throws Exception {
    String nonExistentSha = "0000000000000000000000000000000000000000";

    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .diffFiles(initialCommit.name(), nonExistentSha, true));
    assertThat(thrown).hasMessageThat().contains("Not found");
  }

  @Test
  public void listDiffFiles_commitsNotInAncestorRelationship() throws Exception {
    // Create branch1 at the initial commit first
    createBranch(BranchNameKey.create(project, "branch1"));

    // Create a commit on master
    PushOneCommit.Result result1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit 1", "file1.txt", "content1")
            .to("refs/heads/master");
    result1.assertOkStatus();

    // Reset to initial commit and push to branch1
    testRepo.reset(initialCommit);

    PushOneCommit.Result result2 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit 2", "file2.txt", "content2")
            .to("refs/heads/branch1");
    result2.assertOkStatus();

    // These two commits are siblings (share common ancestor) but neither is ancestor of the other
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .diffFiles(result1.getCommit().name(), result2.getCommit().name(), true));
    assertThat(thrown).hasMessageThat().contains("ancestor/descendant");
  }

  @Test
  public void listDiffFiles_verifyPathVisibility() throws Exception {
    // Create branch 'visible' at initial commit
    createBranch(BranchNameKey.create(project, "visible"));

    // Create a commit on master
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Test commit", "file.txt", "content")
            .to("refs/heads/master");
    result.assertOkStatus();

    ObjectId newCommit = result.getCommit();

    // Block read permission on master, but keep it on 'visible'
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(R_HEADS_MASTER).group(REGISTERED_USERS))
        .update();

    // Use non-admin user
    requestScopeOperations.setApiUser(user.id());

    // Fails because target commit (newCommit) is on a hidden ref
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .diffFiles(initialCommit.name(), newCommit.name(), true));
    // This fails in CommitsCollection.parse which returns "Not found: <sha1>"
    assertThat(thrown).hasMessageThat().contains("Not found");
  }

  @Test
  public void listDiffFiles_reverseDiff_verifyPathVisibility() throws Exception {
    // Create branch 'visible' at initial commit
    createBranch(BranchNameKey.create(project, "visible"));

    // Create a commit on master
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Test commit", "file.txt", "content")
            .to("refs/heads/master");
    result.assertOkStatus();

    ObjectId newCommit = result.getCommit();

    // Block read permission on master, but keep it on 'visible'
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(R_HEADS_MASTER).group(REGISTERED_USERS))
        .update();

    // Use non-admin user
    requestScopeOperations.setApiUser(user.id());

    // In a reverse diff (comparing descendant to ancestor):
    // target = initialCommit (visible via 'visible' branch)
    // base = newCommit (hidden because it's only on 'master')
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .diffFiles(newCommit.name(), initialCommit.name(), true));
    // This should be caught by verifyPathVisibility which throws "Commit not visible"
    assertThat(thrown).hasMessageThat().contains("not visible");
  }

  @Test
  public void getDiffFile_success() throws Exception {
    String fileContent = "Line 1\nLine 2\nLine 3\n";
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Test commit", "test-file.txt", fileContent)
            .to("refs/heads/master");
    result.assertOkStatus();

    ObjectId newCommit = result.getCommit();

    DiffInfo diffInfo =
        gApi.projects()
            .name(project.get())
            .diffFile(initialCommit.name(), newCommit.name(), "test-file.txt");

    assertThat(diffInfo).isNotNull();
    assertThat(diffInfo.metaB).isNotNull();
    assertThat(diffInfo.metaB.name).isEqualTo("test-file.txt");
  }

  @Test
  public void getDiffFile_unchangedFile() throws Exception {
    // When requesting a file that exists but hasn't changed between commits, we should get an
    // empty diff (no changes)
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Test commit", "existing-file.txt", "content")
            .to("refs/heads/master");
    result.assertOkStatus();

    ObjectId commit1 = result.getCommit();

    // Create another commit that doesn't change existing-file.txt
    PushOneCommit.Result result2 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Second commit", "other-file.txt", "other content")
            .to("refs/heads/master");
    result2.assertOkStatus();
    ObjectId commit2 = result2.getCommit();

    // Request diff for existing-file.txt which hasn't changed
    DiffInfo diffInfo =
        gApi.projects()
            .name(project.get())
            .diffFile(commit1.name(), commit2.name(), "existing-file.txt");

    // Should return a diff result (possibly empty)
    assertThat(diffInfo).isNotNull();
  }

  @Test
  public void getDiffFile_missingOldParam() throws Exception {
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Test commit", "file.txt", "content")
            .to("refs/heads/master");
    result.assertOkStatus();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .diffFile(null, result.getCommit().name(), "file.txt"));
    assertThat(thrown).hasMessageThat().contains("base");
  }

  @Test
  public void getDiffFile_verifyPathVisibility() throws Exception {
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Test commit", "file.txt", "content")
            .to("refs/heads/master");
    result.assertOkStatus();

    ObjectId newCommit = result.getCommit();

    // Block read permission on master
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(R_HEADS_MASTER).group(REGISTERED_USERS))
        .update();

    // Use non-admin user
    requestScopeOperations.setApiUser(user.id());

    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                gApi.projects()
                    .name(project.get())
                    .diffFile(initialCommit.name(), newCommit.name(), "file.txt"));
    assertThat(thrown).hasMessageThat().contains("not visible");
  }

  @Test
  public void listDiffFiles_modifiedFile() throws Exception {
    // First create a file
    PushOneCommit.Result result1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Initial commit", "file.txt", "initial content")
            .to("refs/heads/master");
    result1.assertOkStatus();
    ObjectId commit1 = result1.getCommit();

    // Then modify the file
    PushOneCommit.Result result2 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Modify file", "file.txt", "modified content")
            .to("refs/heads/master");
    result2.assertOkStatus();
    ObjectId commit2 = result2.getCommit();

    Map<String, FileInfo> files =
        gApi.projects().name(project.get()).diffFiles(commit1.name(), commit2.name(), true);

    assertThat(files).containsKey("file.txt");
    FileInfo fileInfo = files.get("file.txt");
    // Modified files should not have a status (status is null for MODIFIED)
    assertThat(fileInfo.status).isNull();
  }

  @Test
  public void listDiffFiles_deletedFile() throws Exception {
    // Create a file
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Add file", "file-to-check.txt", "content")
            .to("refs/heads/master");
    result.assertOkStatus();
    ObjectId commitWithFile = result.getCommit();

    // When comparing from the commit with the file to initialCommit (which doesn't have it),
    // the file should appear as deleted
    Map<String, FileInfo> files =
        gApi.projects()
            .name(project.get())
            .diffFiles(commitWithFile.name(), initialCommit.name(), true);

    assertThat(files).containsKey("file-to-check.txt");
    FileInfo fileInfo = files.get("file-to-check.txt");
    assertThat(fileInfo.status).isEqualTo('D');
  }

  @Test
  public void listDiffFiles_addedFile() throws Exception {
    PushOneCommit.Result result =
        pushFactory
            .create(admin.newIdent(), testRepo, "Add file", "new-file.txt", "new content")
            .to("refs/heads/master");
    result.assertOkStatus();
    ObjectId newCommit = result.getCommit();

    Map<String, FileInfo> files =
        gApi.projects().name(project.get()).diffFiles(initialCommit.name(), newCommit.name(), true);

    assertThat(files).containsKey("new-file.txt");
    FileInfo fileInfo = files.get("new-file.txt");
    assertThat(fileInfo.status).isEqualTo('A');
  }

  @Test
  public void listDiffFiles_includesFilesFromIntermediateCommits() throws Exception {
    // Create a chain of commits: initial -> commit1 (adds file1) -> commit2 (adds file2)
    // When comparing initial to commit2, we should see both file1 and file2

    // Commit 1: Add file1.txt
    PushOneCommit.Result result1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Add file1", "file1.txt", "content of file1")
            .to("refs/heads/master");
    result1.assertOkStatus();

    // Commit 2: Add file2.txt (doesn't touch file1.txt)
    PushOneCommit.Result result2 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Add file2", "file2.txt", "content of file2")
            .to("refs/heads/master");
    result2.assertOkStatus();
    ObjectId commit2 = result2.getCommit();

    // Compare initial (grandparent) to commit2 (grandchild)
    // Should include file1.txt from commit1 and file2.txt from commit2
    Map<String, FileInfo> files =
        gApi.projects().name(project.get()).diffFiles(initialCommit.name(), commit2.name(), true);

    // Both files should be present
    assertThat(files.keySet()).contains("file1.txt");
    assertThat(files.keySet()).contains("file2.txt");

    // Both should be marked as added
    assertThat(files.get("file1.txt").status).isEqualTo('A');
    assertThat(files.get("file2.txt").status).isEqualTo('A');
  }

  @Test
  public void listDiffFiles_includesFilesFromMultipleIntermediateCommits() throws Exception {
    // Create a longer chain: initial -> c1 -> c2 -> c3 -> c4
    // When comparing initial to c4, all files from intermediate commits should be visible

    PushOneCommit.Result r1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit 1", "a.txt", "a")
            .to("refs/heads/master");
    r1.assertOkStatus();

    PushOneCommit.Result r2 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit 2", "b.txt", "b")
            .to("refs/heads/master");
    r2.assertOkStatus();

    PushOneCommit.Result r3 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit 3", "c.txt", "c")
            .to("refs/heads/master");
    r3.assertOkStatus();

    PushOneCommit.Result r4 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Commit 4", "d.txt", "d")
            .to("refs/heads/master");
    r4.assertOkStatus();
    ObjectId commit4 = r4.getCommit();

    // Compare initial to commit4 - should see all 4 files
    Map<String, FileInfo> files =
        gApi.projects().name(project.get()).diffFiles(initialCommit.name(), commit4.name(), true);

    assertThat(files.keySet()).contains("a.txt");
    assertThat(files.keySet()).contains("b.txt");
    assertThat(files.keySet()).contains("c.txt");
    assertThat(files.keySet()).contains("d.txt");
  }

  @Test
  public void listDiffFiles_includesFilesAfterRebase() throws Exception {
    // Scenario: Create parent change, child change, amend parent, rebase child.
    // The project-level diff should show ALL files, not filter out "rebase-only" changes.
    //
    // Setup:
    // 1. Create change1 with fileA.txt
    // 2. Create change2 (child of change1) with fileB.txt
    // 3. Amend change1 to add fileC.txt
    // 4. Rebase change2 on the amended change1
    // 5. Compare initialCommit to rebased change2 - should see fileA.txt, fileB.txt, fileC.txt

    // Create change1 with fileA.txt
    PushOneCommit.Result change1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Change 1", "fileA.txt", "content A")
            .to("refs/for/master");
    change1.assertOkStatus();
    String change1Id = change1.getChangeId();

    // Create change2 (child of change1) with fileB.txt
    PushOneCommit.Result change2 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Change 2", "fileB.txt", "content B")
            .to("refs/for/master");
    change2.assertOkStatus();
    String change2Id = change2.getChangeId();

    // Amend change1 to add fileC.txt
    testRepo.reset(change1.getCommit());
    PushOneCommit.Result amendResult =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Change 1 amended",
                ImmutableMap.of("fileA.txt", "content A", "fileC.txt", "content C"),
                change1Id)
            .to("refs/for/master");
    amendResult.assertOkStatus();

    // Approve and submit change1
    gApi.changes()
        .id(change1Id)
        .current()
        .review(com.google.gerrit.extensions.api.changes.ReviewInput.approve());
    gApi.changes().id(change1Id).current().submit();

    // Rebase change2 on top of the submitted change1
    gApi.changes().id(change2Id).rebase();

    // Get the rebased commit SHA
    String rebasedCommitSha = gApi.changes().id(change2Id).get().getCurrentRevision().commit.commit;

    // Compare initialCommit to rebased change2
    // Should see all files: fileA.txt, fileB.txt, fileC.txt
    Map<String, FileInfo> files =
        gApi.projects().name(project.get()).diffFiles(initialCommit.name(), rebasedCommitSha, true);

    // All three files should be present, including fileC.txt which was added
    // in the amended parent change and might be flagged as "due to rebase"
    assertThat(files.keySet()).contains("fileA.txt");
    assertThat(files.keySet()).contains("fileB.txt");
    assertThat(files.keySet()).contains("fileC.txt");
  }

  @Test
  public void getDiffFile_showsContentForRebasedFile() throws Exception {
    // Similar to above, but verify that the file diff endpoint also shows content
    // for files that might be flagged as "due to rebase"

    // Create change1 with fileA.txt
    PushOneCommit.Result change1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Change 1", "fileA.txt", "content A")
            .to("refs/for/master");
    change1.assertOkStatus();
    String change1Id = change1.getChangeId();

    // Create change2 (child of change1) with fileB.txt
    PushOneCommit.Result change2 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Change 2", "fileB.txt", "content B")
            .to("refs/for/master");
    change2.assertOkStatus();
    String change2Id = change2.getChangeId();

    // Amend change1 to add fileC.txt
    testRepo.reset(change1.getCommit());
    PushOneCommit.Result amendResult =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Change 1 amended",
                ImmutableMap.of("fileA.txt", "content A", "fileC.txt", "rebase content"),
                change1Id)
            .to("refs/for/master");
    amendResult.assertOkStatus();

    // Approve and submit change1
    gApi.changes()
        .id(change1Id)
        .current()
        .review(com.google.gerrit.extensions.api.changes.ReviewInput.approve());
    gApi.changes().id(change1Id).current().submit();

    // Rebase change2 on top of the submitted change1
    gApi.changes().id(change2Id).rebase();

    // Get the rebased commit SHA
    String rebasedCommitSha = gApi.changes().id(change2Id).get().getCurrentRevision().commit.commit;

    // Get diff for fileC.txt - this file was added in the amended parent
    // and might be flagged as "due to rebase", but should still have content
    DiffInfo diffInfo =
        gApi.projects()
            .name(project.get())
            .diffFile(initialCommit.name(), rebasedCommitSha, "fileC.txt");

    assertThat(diffInfo).isNotNull();
    assertThat(diffInfo.changeType).isEqualTo(ChangeType.ADDED);
    // Verify the content is present (not filtered out)
    assertThat(diffInfo.content).isNotEmpty();
  }
}

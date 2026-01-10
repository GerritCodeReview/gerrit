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
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.restapi.project.DiffCollection;
import com.google.gerrit.server.restapi.project.GetDiffFile;
import com.google.gerrit.server.restapi.project.ListDiffFiles;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for project-level diff endpoints: {@link ListDiffFiles}, {@link GetDiffFile}, and
 * {@link DiffCollection}.
 */
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
        gApi.projects().name(project.get()).diffFiles(initialCommit.name(), newCommit.name());

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
            () -> gApi.projects().name(project.get()).diffFiles(null, newCommit.name()));
    assertThat(thrown).hasMessageThat().contains("old");
  }

  @Test
  public void listDiffFiles_missingNewParam() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).diffFiles(initialCommit.name(), null));
    assertThat(thrown).hasMessageThat().contains("new");
  }

  @Test
  public void listDiffFiles_invalidSha1Format() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).diffFiles("invalid", initialCommit.name()));
    assertThat(thrown).hasMessageThat().contains("40-character SHA1");
  }

  @Test
  public void listDiffFiles_commitNotFound() throws Exception {
    String nonExistentSha = "0000000000000000000000000000000000000000";

    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).diffFiles(initialCommit.name(), nonExistentSha));
    assertThat(thrown).hasMessageThat().contains("not found");
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
                    .diffFiles(result1.getCommit().name(), result2.getCommit().name()));
    assertThat(thrown).hasMessageThat().contains("ancestor/descendant");
  }

  @Test
  public void listDiffFiles_verifyPathVisibility() throws Exception {
    // Create a commit and then block read permission
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
                    .diffFiles(initialCommit.name(), newCommit.name()));
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
    assertThat(thrown).hasMessageThat().contains("old");
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
        gApi.projects().name(project.get()).diffFiles(commit1.name(), commit2.name());

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
        gApi.projects().name(project.get()).diffFiles(commitWithFile.name(), initialCommit.name());

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
        gApi.projects().name(project.get()).diffFiles(initialCommit.name(), newCommit.name());

    assertThat(files).containsKey("new-file.txt");
    FileInfo fileInfo = files.get("new-file.txt");
    assertThat(fileInfo.status).isEqualTo('A');
  }
}

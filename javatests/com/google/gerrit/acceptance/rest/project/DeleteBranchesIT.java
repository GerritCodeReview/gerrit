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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REFS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class DeleteBranchesIT extends AbstractDaemonTest {
  private static final ImmutableList<String> BRANCHES =
      ImmutableList.of("refs/heads/test-1", "refs/heads/test-2", "test-3", "refs/meta/foo");

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/*").group(REGISTERED_USERS))
        .update();
    for (String name : BRANCHES) {
      project().branch(name).create(new BranchInput());
    }
    assertBranches(BRANCHES);
  }

  @Test
  public void deleteBranches() throws Exception {
    HashMap<String, RevCommit> initialRevisions = initialRevisions(BRANCHES);
    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = BRANCHES;
    project().deleteBranches(input);
    assertBranchesDeleted(BRANCHES);
    assertRefUpdatedEvents(initialRevisions);
  }

  @Test
  public void deleteOneBranchWithoutPermissionForbidden() throws Exception {
    ImmutableList<String> branchToDelete = ImmutableList.of("refs/heads/test-1");

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = branchToDelete;
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown = assertThrows(AuthException.class, () -> project().deleteBranches(input));
    assertThat(thrown).hasMessageThat().isEqualTo("not permitted: delete on refs/heads/test-1");
    requestScopeOperations.setApiUser(admin.id());
    assertBranches(BRANCHES);
  }

  @Test
  public void deleteMultiBranchesWithoutPermissionForbidden() throws Exception {
    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = BRANCHES;
    requestScopeOperations.setApiUser(user.id());
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> project().deleteBranches(input));
    assertThat(thrown).hasMessageThat().isEqualTo(errorMessageForBranches(BRANCHES));
    requestScopeOperations.setApiUser(admin.id());
    assertBranches(BRANCHES);
  }

  @Test
  public void deleteBranchesNotFound() throws Exception {
    DeleteBranchesInput input = new DeleteBranchesInput();
    List<String> branches = Lists.newArrayList(BRANCHES);
    branches.add("refs/heads/does-not-exist");
    input.branches = branches;
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> project().deleteBranches(input));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(errorMessageForBranches(ImmutableList.of("refs/heads/does-not-exist")));
    assertBranchesDeleted(BRANCHES);
  }

  @Test
  public void deleteBranchesNotFoundContinue() throws Exception {
    // If it fails on the first branch in the input, it should still
    // continue to process the remaining branches.
    DeleteBranchesInput input = new DeleteBranchesInput();
    List<String> branches = Lists.newArrayList("refs/heads/does-not-exist");
    branches.addAll(BRANCHES);
    input.branches = branches;
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> project().deleteBranches(input));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(errorMessageForBranches(ImmutableList.of("refs/heads/does-not-exist")));
    assertBranchesDeleted(BRANCHES);
  }

  @Test
  public void missingInput() throws Exception {
    DeleteBranchesInput input = null;
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> project().deleteBranches(input));
    assertThat(thrown).hasMessageThat().contains("branches must be specified");
  }

  @Test
  public void missingBranchList() throws Exception {
    DeleteBranchesInput input = new DeleteBranchesInput();
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> project().deleteBranches(input));
    assertThat(thrown).hasMessageThat().contains("branches must be specified");
  }

  @Test
  public void emptyBranchList() throws Exception {
    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = Lists.newArrayList();
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> project().deleteBranches(input));
    assertThat(thrown).hasMessageThat().contains("branches must be specified");
  }

  @Test
  public void cannotDeleteRefsMetaConfig() throws Exception {
    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = Lists.newArrayList();
    input.branches.add(RefNames.REFS_CONFIG);
    MethodNotAllowedException thrown =
        assertThrows(MethodNotAllowedException.class, () -> project().deleteBranches(input));
    assertThat(thrown).hasMessageThat().contains("not allowed to delete branch refs/meta/config");
  }

  @Test
  public void cannotDeleteHead() throws Exception {
    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = Lists.newArrayList();
    input.branches.add(RefNames.HEAD);
    MethodNotAllowedException thrown =
        assertThrows(MethodNotAllowedException.class, () -> project().deleteBranches(input));
    assertThat(thrown).hasMessageThat().contains("not allowed to delete HEAD");
  }

  private String errorMessageForBranches(List<String> branches) {
    StringBuilder message = new StringBuilder();
    for (String branch : branches) {
      message
          .append("Cannot delete ")
          .append(prefixRef(branch))
          .append(": it doesn't exist or you do not have permission ")
          .append("to delete it\n");
    }
    return message.toString();
  }

  private HashMap<String, RevCommit> initialRevisions(List<String> branches) throws Exception {
    HashMap<String, RevCommit> result = new HashMap<>();
    for (String branch : branches) {
      result.put(branch, projectOperations.project(project).getHead(branch));
    }
    return result;
  }

  private void assertRefUpdatedEvents(HashMap<String, RevCommit> revisions) throws Exception {
    for (String branch : revisions.keySet()) {
      RevCommit revision = revisions.get(branch);
      eventRecorder.assertRefUpdatedEvents(
          project.get(), prefixRef(branch), null, revision, revision, null);
    }
  }

  private String prefixRef(String ref) {
    return ref.startsWith(R_REFS) ? ref : R_HEADS + ref;
  }

  private ProjectApi project() throws Exception {
    return gApi.projects().name(project.get());
  }

  private void assertBranches(List<String> branches) throws Exception {
    List<String> expected = Lists.newArrayList("HEAD", RefNames.REFS_CONFIG, "refs/heads/master");
    expected.addAll(branches.stream().map(this::prefixRef).collect(toList()));
    try (Repository repo = repoManager.openRepository(project)) {
      for (String branch : expected) {
        assertThat(repo.exactRef(branch)).isNotNull();
      }
    }
  }

  private void assertBranchesDeleted(List<String> branches) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      for (String branch : branches) {
        assertThat(repo.exactRef(branch)).isNull();
      }
    }
  }
}

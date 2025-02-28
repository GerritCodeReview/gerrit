// Copyright (C) 2013 The Android Open Source Project
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
import static com.google.gerrit.acceptance.rest.project.RefAssert.assertRefs;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.restapi.project.ListBranches;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.junit.Test;

@NoHttpd
public class ListBranchesIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Provider<ListBranches> listBranchesProvider;
  @Inject private ProjectsCollection projects;

  @Test
  public void listBranchesOfNonExistingProject_NotFound() throws Exception {
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name("non-existing").branches().get());
  }

  @Test
  public void listBranchesOfNonVisibleProject_NotFound() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(project.get()).branches().get());
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void listBranchesOfEmptyProject() throws Exception {
    assertRefs(
        ImmutableList.of(branch("HEAD", null, false), branch(RefNames.REFS_CONFIG, null, false)),
        list().get());
  }

  @Test
  public void listBranches() throws Exception {
    String master = pushTo("refs/heads/master").getCommit().name();
    String dev = pushTo("refs/heads/dev").getCommit().name();
    String refsConfig = projectOperations.project(project).getHead(RefNames.REFS_CONFIG).name();
    assertRefs(
        ImmutableList.of(
            branch("HEAD", "master", false),
            branch(RefNames.REFS_CONFIG, refsConfig, false),
            branch("refs/heads/dev", dev, true),
            branch("refs/heads/master", master, false)),
        list().get());
  }

  @Test
  public void listBranchesSomeHidden() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/dev").group(REGISTERED_USERS))
        .update();
    String master = pushTo("refs/heads/master").getCommit().name();
    pushTo("refs/heads/dev");
    requestScopeOperations.setApiUser(user.id());
    // refs/meta/config is hidden since user is no project owner
    assertRefs(
        ImmutableList.of(
            branch("HEAD", "master", false), branch("refs/heads/master", master, false)),
        list().get());
  }

  @Test
  public void listBranchesHeadHidden() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();
    pushTo("refs/heads/master");
    String dev = pushTo("refs/heads/dev").getCommit().name();
    requestScopeOperations.setApiUser(user.id());
    // refs/meta/config is hidden since user is no project owner
    assertRefs(ImmutableList.of(branch("refs/heads/dev", dev, false)), list().get());
  }

  @Test
  public void listBranchesUsingPagination() throws Exception {
    BranchInfo head = branch("HEAD", "master", false);
    BranchInfo refsConfig =
        branch(
            RefNames.REFS_CONFIG,
            projectOperations.project(project).getHead(RefNames.REFS_CONFIG).name(),
            false);
    BranchInfo master =
        branch("refs/heads/master", pushTo("refs/heads/master").getCommit().getName(), false);
    BranchInfo branch1 =
        branch(
            "refs/heads/someBranch1", pushTo("refs/heads/someBranch1").getCommit().getName(), true);
    BranchInfo branch2 =
        branch(
            "refs/heads/someBranch2", pushTo("refs/heads/someBranch2").getCommit().getName(), true);
    BranchInfo branch3 =
        branch(
            "refs/heads/someBranch3", pushTo("refs/heads/someBranch3").getCommit().getName(), true);

    // Using only limit.
    assertRefs(ImmutableList.of(head, refsConfig, master, branch1), list().withLimit(4).get());

    // Limit higher than total number of branches.
    assertRefs(
        ImmutableList.of(head, refsConfig, master, branch1, branch2, branch3),
        list().withLimit(25).get());

    // Using start only.
    assertRefs(ImmutableList.of(master, branch1, branch2, branch3), list().withStart(2).get());

    // Skip more branches than the number of available branches.
    assertRefs(ImmutableList.of(), list().withStart(7).get());

    // Ssing start and limit.
    assertRefs(ImmutableList.of(master, branch1), list().withStart(2).withLimit(2).get());
  }

  @Test
  public void listBranches_withNextPageToken() throws Exception {
    BranchInfo headBranch = branch("HEAD", "master", false);
    BranchInfo refsConfig =
        branch(
            RefNames.REFS_CONFIG,
            projectOperations.project(project).getHead(RefNames.REFS_CONFIG).name(),
            false);
    BranchInfo masterBranch = createBranch("refs/heads/master", false);
    BranchInfo branch1 = createBranch("refs/heads/someBranch1", true);
    BranchInfo branch2 = createBranch("refs/heads/someBranch2", true);
    BranchInfo branch3 = createBranch("refs/heads/someBranch3", true);

    // Listing all branches returns all 6 branches.
    assertRefs(
        ImmutableList.of(headBranch, refsConfig, masterBranch, branch1, branch2, branch3),
        list().get());

    // No continuation token and limit = 2 returns first two branches.
    ListBranches listBranches = listBranchesProvider.get();
    listBranches.setLimit(2);
    Response<ImmutableList<BranchInfo>> response =
        listBranches.apply(projects.parse(project.get()));
    String continuationToken =
        response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList().get(0);
    assertRefs(ImmutableList.of(headBranch, refsConfig), response.value());
    assertThat(continuationToken).isEqualTo(ListBranches.encodeToken("refs/meta/config"));

    // Using the previous continuation token returns the 3rd and 4th branches.
    listBranches.setNextPageToken(continuationToken);
    response = listBranches.apply(projects.parse(project.get()));
    continuationToken = response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList().get(0);
    assertRefs(ImmutableList.of(masterBranch, branch1), response.value());
    assertThat(continuationToken).isEqualTo(ListBranches.encodeToken("refs/heads/someBranch1"));

    // Using the previous continuation token returns the 5th and 6th branches. No more continuation
    // token.
    listBranches.setNextPageToken(continuationToken);
    response = listBranches.apply(projects.parse(project.get()));
    assertRefs(ImmutableList.of(branch2, branch3), response.value());
    assertThat(response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER)).isEmpty();
  }

  @Test
  public void listBranches_withNextPageToken_someBranchesHidden() throws Exception {
    BranchInfo headBranch = branch("HEAD", "master", false);
    branch(
        RefNames.REFS_CONFIG,
        projectOperations.project(project).getHead(RefNames.REFS_CONFIG).name(),
        false);
    BranchInfo masterBranch = createBranch("refs/heads/master", false);
    BranchInfo branch1 = createBranch("refs/heads/someBranch1", false);

    // Hide refs/meta/config branch.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();

    // refs/meta/config is not visible.
    // Use a non-admin user, since admins can always see all refs.
    requestScopeOperations.setApiUser(user.id());
    assertRefs(ImmutableList.of(headBranch, masterBranch, branch1), list().get());

    // Try listing branches using the next-page-token
    ListBranches listBranches = listBranchesProvider.get();
    listBranches.setLimit(2);
    Response<ImmutableList<BranchInfo>> response =
        listBranches.apply(projects.parse(project.get()));
    String continuationToken =
        response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList().get(0);
    assertRefs(ImmutableList.of(headBranch, masterBranch), response.value());
    assertThat(continuationToken).isEqualTo(ListBranches.encodeToken("refs/heads/master"));

    // Using the previous continuation token returns branch1. The refs/meta/config branch is
    // skipped.
    listBranches.setNextPageToken(continuationToken);
    response = listBranches.apply(projects.parse(project.get()));
    assertRefs(ImmutableList.of(branch1), response.value());
    assertThat(response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER)).isEmpty();
  }

  @Test
  public void listBranches_withNextPageToken_branchesCreatedAfterFirstPage() throws Exception {
    BranchInfo headBranch = branch("HEAD", "master", false);
    BranchInfo refsConfig =
        branch(
            RefNames.REFS_CONFIG,
            projectOperations.project(project).getHead(RefNames.REFS_CONFIG).name(),
            false);
    BranchInfo masterBranch = createBranch("refs/heads/master", false);
    BranchInfo branch2 = createBranch("refs/heads/someBranch2", true);
    BranchInfo branch3 = createBranch("refs/heads/someBranch3", true);
    BranchInfo branch4 = createBranch("refs/heads/someBranch4", true);

    // Listing all branches returns all 6 branches. Order is important.
    assertRefs(
        ImmutableList.of(headBranch, refsConfig, masterBranch, branch2, branch3, branch4),
        list().get());
    ListBranches listBranches = listBranchesProvider.get();
    listBranches.setLimit(2);
    Response<ImmutableList<BranchInfo>> response =
        listBranches.apply(projects.parse(project.get()));
    String continuationToken =
        response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList().get(0);
    assertRefs(ImmutableList.of(headBranch, refsConfig), response.value());

    listBranches.setNextPageToken(continuationToken);
    response = listBranches.apply(projects.parse(project.get()));
    continuationToken = response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList().get(0);
    assertRefs(ImmutableList.of(masterBranch, branch2), response.value());

    // Create Branch1
    createBranch("refs/heads/someBranch1", false);

    // Using the previous continuation token, branch1 is not returned because the continuation token
    // points at branch2 (last result of previous response) and will look for results past it.
    listBranches.setNextPageToken(continuationToken);
    response = listBranches.apply(projects.parse(project.get()));
    assertRefs(ImmutableList.of(branch3, branch4), response.value());
    assertThat(response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList()).isEmpty();
  }

  @Test
  public void listBranches_withNonExistentNextPageTokenBranch_startsFromNextGreaterBranch()
      throws Exception {
    BranchInfo branch2 = createBranch("refs/heads/someBranch2", true);

    ListBranches listBranches = listBranchesProvider.get();
    listBranches.setLimit(3);
    // Set continuation token to a non-existent branch
    listBranches.setNextPageToken(ListBranches.encodeToken("refs/heads/someBranch1"));
    Response<ImmutableList<BranchInfo>> response =
        listBranches.apply(projects.parse(project.get()));
    ImmutableList<String> continuationToken =
        response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList();
    // Since branch1 does not exist, the server continues from branch2.
    assertRefs(ImmutableList.of(branch2), response.value());
    assertThat(continuationToken).isEmpty();
  }

  @Test
  public void listBranches_withNextPageTokenGreaterThanAllBranches_returnsEmpty() throws Exception {
    createBranch("refs/heads/someBranch2", true);

    ListBranches listBranches = listBranchesProvider.get();
    listBranches.setLimit(3);
    // Set continuation token to a non-existent branch
    listBranches.setNextPageToken(ListBranches.encodeToken("refs/heads/someBranch4"));
    Response<ImmutableList<BranchInfo>> response =
        listBranches.apply(projects.parse(project.get()));
    ImmutableList<String> continuationToken =
        response.headers().get(ListBranches.NEXT_PAGE_TOKEN_HEADER).asList();
    // Since branch1 does not exist, the server continues from branch2.
    assertRefs(ImmutableList.of(), response.value());
    assertThat(continuationToken).isEmpty();
  }

  @Test
  public void listBranches_withBothStartAndNextPageTokenSet_isDisallowed() {
    Exception exception =
        assertThrows(
            BadRequestException.class,
            () -> list().withStart(2).withNextPageToken("refs/meta/config").get());

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("'start' and 'next-page-token' parameters are mutually exclusive.");
  }

  @Test
  public void listBranches_withInvalidNextPageToken_isDisallowed() {
    Exception exception =
        assertThrows(
            BadRequestException.class, () -> list().withNextPageToken("invalidToken").get());

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Invalid 'next-page-token'. This token was not created by the Gerrit server.");
  }

  @Test
  public void listBranchesUsingFilter() throws Exception {
    BranchInfo master =
        branch("refs/heads/master", pushTo("refs/heads/master").getCommit().getName(), false);
    BranchInfo branch1 =
        branch(
            "refs/heads/someBranch1", pushTo("refs/heads/someBranch1").getCommit().getName(), true);
    BranchInfo branch2 =
        branch(
            "refs/heads/someBranch2", pushTo("refs/heads/someBranch2").getCommit().getName(), true);
    BranchInfo branch3 =
        branch(
            "refs/heads/someBranch3", pushTo("refs/heads/someBranch3").getCommit().getName(), true);

    // Using substring.
    assertRefs(ImmutableList.of(branch1, branch2, branch3), list().withSubstring("some").get());

    assertRefs(ImmutableList.of(branch1, branch2, branch3), list().withSubstring("Branch").get());

    assertRefs(
        ImmutableList.of(branch1, branch2, branch3), list().withSubstring("somebranch").get());

    // Using regex.
    assertRefs(ImmutableList.of(master), list().withRegex(".*ast.*r").get());
    assertRefs(ImmutableList.of(), list().withRegex(".*AST.*R").get());

    // Conflicting options
    assertBadRequest(list().withSubstring("somebranch").withRegex(".*ast.*r"));
  }

  private ListRefsRequest<BranchInfo> list() throws Exception {
    return gApi.projects().name(project.get()).branches();
  }

  private BranchInfo createBranch(String name, boolean canDelete) throws Exception {
    return branch(name, pushTo(name).getCommit().getName(), canDelete);
  }

  private static BranchInfo branch(String ref, String revision, boolean canDelete) {
    BranchInfo info = new BranchInfo();
    info.ref = ref;
    info.revision = revision;
    info.canDelete = canDelete ? true : null;
    return info;
  }

  private void assertBadRequest(ListRefsRequest<BranchInfo> req) throws Exception {
    assertThrows(BadRequestException.class, () -> req.get());
  }
}

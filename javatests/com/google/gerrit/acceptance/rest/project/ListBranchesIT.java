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
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class ListBranchesIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

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

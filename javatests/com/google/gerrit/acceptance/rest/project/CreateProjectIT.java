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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectInfo;
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectOwners;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.capabilityKey;
import static com.google.gerrit.server.project.ProjectConfig.PROJECT_CONFIG;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class CreateProjectIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void createProjectHttp() throws Exception {
    String newProjectName = name("newProject");
    RestResponse r = adminRestSession.put("/projects/" + newProjectName);
    r.assertCreated();
    ProjectInfo p = newGson().fromJson(r.getReader(), ProjectInfo.class);
    assertThat(p.name).isEqualTo(newProjectName);

    // Check that we populate the label data in the HTTP path. See GetProjectIT#getProject
    // for more extensive coverage of the LabelTypeInfo.
    assertThat(p.labels).hasSize(1);

    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertProjectInfo(projectState.get().getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void createProjectHttpWhenProjectAlreadyExists_conflict() throws Exception {
    adminRestSession.put("/projects/" + allProjects.get()).assertConflict();
  }

  @Test
  public void createProjectHttpWhenProjectAlreadyExists_preconditionFailed() throws Exception {
    adminRestSession
        .putWithHeaders(
            "/projects/" + allProjects.get(), new BasicHeader(HttpHeaders.IF_NONE_MATCH, "*"))
        .assertPreconditionFailed();
  }

  @Test
  public void createSameProjectFromTwoConcurrentRequests() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(2);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        assertThat(ImmutableList.of(r1.get().getStatusCode(), r2.get().getStatusCode()))
            .containsAtLeast(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @UseLocalDisk
  public void createProjectHttpWithUnreasonableName_badRequest() throws Exception {
    ImmutableList<String> forbiddenStrings =
        ImmutableList.of(
            "/../", "/./", "//", ".git/", "?", "%", "*", ":", "<", ">", "|", "$", "/+", "~");
    for (String s : forbiddenStrings) {
      String projectName = name("invalid" + s + "name");
      assertWithMessage("Expected status code for " + projectName + " to be 400.")
          .that(adminRestSession.put("/projects/" + Url.encode(projectName)).getStatusCode())
          .isEqualTo(HttpStatus.SC_BAD_REQUEST);
    }
  }

  @Test
  public void createProjectHttpWithNameMismatch_badRequest() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("otherName");
    adminRestSession.put("/projects/" + name("someName"), in).assertBadRequest();
  }

  @Test
  public void createProjectHttpWithInvalidRefName_badRequest() throws Exception {
    ProjectInput in = new ProjectInput();
    in.branches = Collections.singletonList(name("invalid ref name"));
    adminRestSession.put("/projects/" + name("newProject"), in).assertBadRequest();
  }

  @Test
  public void createProject() throws Exception {
    String newProjectName = name("newProject");
    ProjectInfo p = gApi.projects().create(newProjectName).get();
    assertThat(p.name).isEqualTo(newProjectName);
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertProjectInfo(projectState.get().getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
    assertThat(readProjectConfig(newProjectName))
        .hasValue("[access]\n\tinheritFrom = All-Projects\n[submit]\n\taction = inherit\n");
  }

  @Test
  public void createProjectWithGitSuffix() throws Exception {
    String newProjectName = name("newProject");
    ProjectInfo p = gApi.projects().create(newProjectName + ".git").get();
    assertThat(p.name).isEqualTo(newProjectName);
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertProjectInfo(projectState.get().getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void createProjectThatEndsWithSlash() throws Exception {
    String newProjectName = name("newProject");
    ProjectInfo p = gApi.projects().create(newProjectName + "/").get();
    assertThat(p.name).isEqualTo(newProjectName);
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertProjectInfo(projectState.get().getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void createProjectThatContainsSlash() throws Exception {
    String newProjectName = name("newProject/newProject");
    ProjectInfo p = gApi.projects().create(newProjectName).get();
    assertThat(p.name).isEqualTo(newProjectName);
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertProjectInfo(projectState.get().getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void createProjectWithProperties() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.description = "Test description";
    in.submitType = SubmitType.CHERRY_PICK;
    in.useContributorAgreements = InheritableBoolean.TRUE;
    in.useSignedOffBy = InheritableBoolean.TRUE;
    in.useContentMerge = InheritableBoolean.TRUE;
    in.requireChangeId = InheritableBoolean.TRUE;
    ProjectInfo p = gApi.projects().create(in).get();
    assertThat(p.name).isEqualTo(newProjectName);
    Project project = projectCache.get(Project.nameKey(newProjectName)).get().getProject();
    assertProjectInfo(project, p);
    assertThat(project.getDescription()).isEqualTo(in.description);
    assertThat(project.getSubmitType()).isEqualTo(in.submitType);
    assertThat(project.getBooleanConfig(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS))
        .isEqualTo(in.useContributorAgreements);
    assertThat(project.getBooleanConfig(BooleanProjectConfig.USE_SIGNED_OFF_BY))
        .isEqualTo(in.useSignedOffBy);
    assertThat(project.getBooleanConfig(BooleanProjectConfig.USE_CONTENT_MERGE))
        .isEqualTo(in.useContentMerge);
    assertThat(project.getBooleanConfig(BooleanProjectConfig.REQUIRE_CHANGE_ID))
        .isEqualTo(in.requireChangeId);
  }

  @Test
  public void createChildProject() throws Exception {
    String parentName = name("parent");
    ProjectInput in = new ProjectInput();
    in.name = parentName;
    gApi.projects().create(in);

    String childName = name("child");
    in = new ProjectInput();
    in.name = childName;
    in.parent = parentName;
    gApi.projects().create(in);
    Project project = projectCache.get(Project.nameKey(childName)).get().getProject();
    assertThat(project.getParentName()).isEqualTo(in.parent);
  }

  @Test
  public void createChildProjectUnderNonExistingParent_UnprocessableEntity() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("newProjectName");
    in.parent = "non-existing-project";
    assertCreateFails(in, UnprocessableEntityException.class);
  }

  @Test
  public void createProjectWithOwner() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.owners = Lists.newArrayListWithCapacity(3);
    in.owners.add("Anonymous Users"); // by name
    in.owners.add(SystemGroupBackend.REGISTERED_USERS.get()); // by UUID
    Optional<InternalGroup> group = groupCache.get(AccountGroup.nameKey("Administrators"));
    if (group.isPresent()) {
      in.owners.add(Integer.toString(group.get().getId().get())); // by ID
    }

    gApi.projects().create(in);
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    Set<AccountGroup.UUID> expectedOwnerIds = Sets.newHashSetWithExpectedSize(3);
    expectedOwnerIds.add(SystemGroupBackend.ANONYMOUS_USERS);
    expectedOwnerIds.add(SystemGroupBackend.REGISTERED_USERS);
    expectedOwnerIds.add(groupUuid("Administrators"));
    assertThat(projectState).isPresent();
    assertProjectOwners(expectedOwnerIds, projectState.get());
  }

  @Test
  public void createProjectWithNonExistingOwner_UnprocessableEntity() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("newProjectName");
    in.owners = Collections.singletonList("non-existing-group");
    assertCreateFails(in, UnprocessableEntityException.class);
  }

  @Test
  public void createPermissionOnlyProject() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.permissionsOnly = true;
    gApi.projects().create(in);
    assertHead(newProjectName, RefNames.REFS_CONFIG);
  }

  @Test
  @GerritConfig(name = "gerrit.defaultBranch", value = "main")
  public void createPermissionOnlyProject_WhenDefaultBranchIsSet() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.permissionsOnly = true;
    gApi.projects().create(in);
    // For permissionOnly, don't use host-level default branch.
    assertHead(newProjectName, RefNames.REFS_CONFIG);
  }

  @Test
  public void createProjectWithBranches() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.createEmptyCommit = true;
    in.branches = Lists.newArrayListWithCapacity(3);
    in.branches.add("refs/heads/test");
    in.branches.add("refs/heads/master");
    in.branches.add("release"); // without 'refs/heads' prefix
    gApi.projects().create(in);
    assertHead(newProjectName, "refs/heads/test");
    assertEmptyCommit(newProjectName, "refs/heads/test", "refs/heads/master", "refs/heads/release");
  }

  @Test
  public void createProjectWithInvalidBranch() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.createEmptyCommit = true;
    in.branches = ImmutableList.of("refs/heads/test", "refs/changes/34/1234");
    Throwable thrown =
        assertThrows(ResourceConflictException.class, () -> gApi.projects().create(in));
    assertThat(thrown).hasCauseThat().isInstanceOf(ValidationException.class);
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot create a project with branch refs/changes/34/1234");
  }

  @Test
  @GerritConfig(name = "gerrit.defaultBranch", value = "main")
  public void createProject_WhenDefaultBranchIsSet() throws Exception {
    String newProjectName = name("newProject");
    gApi.projects().create(newProjectName).get();
    ImmutableMap<String, BranchInfo> branches = getProjectBranches(newProjectName);
    // HEAD symbolic ref is set to the default, but the actual ref is not created.
    assertThat(branches.keySet()).containsExactly("HEAD", "refs/meta/config");
    assertHead(newProjectName, "refs/heads/main");
  }

  @Test
  @GerritConfig(name = "gerrit.defaultBranch", value = "main")
  public void createProjectWithEmptyCommit_WhenDefaultBranchIsSet() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.createEmptyCommit = true;
    gApi.projects().create(in);
    ImmutableMap<String, BranchInfo> branches = getProjectBranches(newProjectName);
    // HEAD symbolic ref is set to the default, and the actual ref is created.
    assertThat(branches.keySet()).containsExactly("HEAD", "refs/meta/config", "refs/heads/main");
    assertHead(newProjectName, "refs/heads/main");
    assertEmptyCommit(newProjectName, "HEAD", "refs/heads/main");
  }

  @Test
  @GerritConfig(name = "gerrit.defaultBranch", value = "refs/heads/main")
  public void createProject_WhenDefaultBranchIsSet_WithBranches() throws Exception {
    // Host-level default only applies if no branches were passed in the input
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.createEmptyCommit = true;
    in.branches = ImmutableList.of("refs/heads/test", "release");
    gApi.projects().create(in);
    ImmutableMap<String, BranchInfo> branches = getProjectBranches(newProjectName);
    assertThat(branches.keySet())
        .containsExactly("HEAD", "refs/meta/config", "refs/heads/test", "refs/heads/release");
    assertHead(newProjectName, "refs/heads/test");
    assertEmptyCommit(newProjectName, "refs/heads/test", "refs/heads/release");
  }

  @Test
  @GerritConfig(name = "gerrit.defaultBranch", value = "refs/users/self")
  public void createProject_WhenDefaultBranchIsSet_ToGerritRef() throws Exception {
    String newProjectName = name("newProject");
    Throwable thrown =
        assertThrows(ResourceConflictException.class, () -> gApi.projects().create(newProjectName));
    assertThat(thrown).hasCauseThat().isInstanceOf(ValidationException.class);
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot create a project with branch refs/users/self");
  }

  @Test
  @GerritConfig(name = "gerrit.defaultBranch", value = "refs~main")
  public void createProject_WhenDefaultBranchIsSet_ToInvalidBranch() throws Exception {
    String newProjectName = name("newProject");
    Throwable thrown =
        assertThrows(BadRequestException.class, () -> gApi.projects().create(newProjectName));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Branch \"refs/heads/refs~main\" is not a valid name.");
  }

  @Test
  public void createProjectWithCapability() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(
            allowCapability(GlobalCapability.CREATE_PROJECT)
                .group(SystemGroupBackend.REGISTERED_USERS))
        .update();
    try {
      requestScopeOperations.setApiUser(user.id());
      ProjectInput in = new ProjectInput();
      in.name = name("newProject");
      ProjectInfo p = gApi.projects().create(in).get();
      assertThat(p.name).isEqualTo(in.name);
    } finally {
      projectOperations
          .allProjectsForUpdate()
          .remove(
              capabilityKey(GlobalCapability.CREATE_PROJECT)
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();
    }
  }

  @Test
  public void createProjectWithoutCapability_Forbidden() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ProjectInput in = new ProjectInput();
    in.name = name("newProject");
    assertCreateFails(in, AuthException.class);
  }

  @Test
  public void createProjectWhenProjectAlreadyExists_Conflict() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = allProjects.get();
    assertCreateFails(in, ResourceConflictException.class);
  }

  @Test
  public void createProjectWithCreateProjectCapabilityAndParentNotVisible() throws Exception {
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig()
          .updateProject(p -> p.setState(com.google.gerrit.extensions.client.ProjectState.HIDDEN));
      u.save();
    }
    projectOperations
        .allProjectsForUpdate()
        .add(
            allowCapability(GlobalCapability.CREATE_PROJECT)
                .group(SystemGroupBackend.REGISTERED_USERS))
        .update();
    try {
      requestScopeOperations.setApiUser(user.id());
      ProjectInput in = new ProjectInput();
      in.name = name("newProject");
      ProjectInfo p = gApi.projects().create(in).get();
      assertThat(p.name).isEqualTo(in.name);
    } finally {
      try (ProjectConfigUpdate u = updateProject(allProjects)) {
        u.getConfig()
            .updateProject(
                p -> p.setState(com.google.gerrit.extensions.client.ProjectState.ACTIVE));
        u.save();
      }
      projectOperations
          .allProjectsForUpdate()
          .remove(
              capabilityKey(GlobalCapability.CREATE_PROJECT)
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();
    }
  }

  @SuppressWarnings("deprecation")
  @Test
  public void createProjectWithDefaultInheritedSubmitType() throws Exception {
    String parent = name("parent");
    ProjectInput pin = new ProjectInput();
    pin.name = parent;
    ConfigInfo cfg = gApi.projects().create(pin).config();
    assertThat(cfg.submitType).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(cfg.defaultSubmitType.value).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(cfg.defaultSubmitType.configuredValue).isEqualTo(SubmitType.INHERIT);
    assertThat(cfg.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.MERGE_IF_NECESSARY);

    ConfigInput cin = new ConfigInput();
    cin.submitType = SubmitType.CHERRY_PICK;
    gApi.projects().name(parent).config(cin);
    cfg = gApi.projects().name(parent).config();
    assertThat(cfg.submitType).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.value).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.configuredValue).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.MERGE_IF_NECESSARY);

    String child = name("child");
    pin = new ProjectInput();
    pin.submitType = SubmitType.INHERIT;
    pin.parent = parent;
    pin.name = child;
    cfg = gApi.projects().create(pin).config();
    assertThat(cfg.submitType).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.value).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.configuredValue).isEqualTo(SubmitType.INHERIT);
    assertThat(cfg.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.CHERRY_PICK);

    cin = new ConfigInput();
    cin.submitType = SubmitType.REBASE_IF_NECESSARY;
    gApi.projects().name(parent).config(cin);
    cfg = gApi.projects().name(parent).config();
    assertThat(cfg.submitType).isEqualTo(SubmitType.REBASE_IF_NECESSARY);
    assertThat(cfg.defaultSubmitType.value).isEqualTo(SubmitType.REBASE_IF_NECESSARY);
    assertThat(cfg.defaultSubmitType.configuredValue).isEqualTo(SubmitType.REBASE_IF_NECESSARY);
    assertThat(cfg.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.MERGE_IF_NECESSARY);

    cfg = gApi.projects().name(child).config();
    assertThat(cfg.submitType).isEqualTo(SubmitType.REBASE_IF_NECESSARY);
    assertThat(cfg.defaultSubmitType.value).isEqualTo(SubmitType.REBASE_IF_NECESSARY);
    assertThat(cfg.defaultSubmitType.configuredValue).isEqualTo(SubmitType.INHERIT);
    assertThat(cfg.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.REBASE_IF_NECESSARY);
  }

  @SuppressWarnings("deprecation")
  @Test
  @GerritConfig(
      name = "repository.testinheritedsubmittype/*.defaultSubmitType",
      value = "CHERRY_PICK")
  public void repositoryConfigTakesPrecedenceOverInheritedSubmitType() throws Exception {
    // Can't use name() since we need to specify this project name in gerrit.config prior to
    // startup. Pick something reasonably unique instead.
    String parent = "testinheritedsubmittype";
    ProjectInput pin = new ProjectInput();
    pin.name = parent;
    pin.submitType = SubmitType.MERGE_ALWAYS;
    ConfigInfo cfg = gApi.projects().create(pin).config();
    assertThat(cfg.submitType).isEqualTo(SubmitType.MERGE_ALWAYS);
    assertThat(cfg.defaultSubmitType.value).isEqualTo(SubmitType.MERGE_ALWAYS);
    assertThat(cfg.defaultSubmitType.configuredValue).isEqualTo(SubmitType.MERGE_ALWAYS);
    assertThat(cfg.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.MERGE_IF_NECESSARY);

    String child = parent + "/child";
    pin = new ProjectInput();
    pin.parent = parent;
    pin.name = child;
    cfg = gApi.projects().create(pin).config();
    assertThat(cfg.submitType).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.value).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.configuredValue).isEqualTo(SubmitType.CHERRY_PICK);
    assertThat(cfg.defaultSubmitType.inheritedValue).isEqualTo(SubmitType.MERGE_ALWAYS);
  }

  private void assertEmptyCommit(String projectName, String... refs) throws Exception {
    Project.NameKey projectKey = Project.nameKey(projectName);
    try (Repository repo = repoManager.openRepository(projectKey);
        RevWalk rw = new RevWalk(repo);
        TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
      for (String ref : refs) {
        RevCommit commit = rw.lookupCommit(repo.exactRef(ref).getObjectId());
        rw.parseBody(commit);
        tw.addTree(commit.getTree());
        assertThat(tw.next()).isFalse();
        tw.reset();
      }
    }
  }

  private void assertCreateFails(ProjectInput in, Class<? extends RestApiException> errType)
      throws Exception {
    assertThrows(errType, () -> gApi.projects().create(in));
  }

  private Optional<String> readProjectConfig(String projectName) throws Exception {
    try (Repository repo = repoManager.openRepository(Project.nameKey(projectName));
        TestRepository<Repository> tr = new TestRepository<>(repo)) {
      RevWalk rw = tr.getRevWalk();
      Ref ref = repo.exactRef(RefNames.REFS_CONFIG);
      if (ref == null) {
        return Optional.empty();
      }
      ObjectLoader obj =
          rw.getObjectReader()
              .open(tr.get(rw.parseTree(ref.getObjectId()), PROJECT_CONFIG), Constants.OBJ_BLOB);
      return Optional.of(new String(obj.getCachedBytes(Integer.MAX_VALUE), UTF_8));
    }
  }
}

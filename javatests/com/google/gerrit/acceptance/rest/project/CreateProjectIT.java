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
import static com.google.gerrit.server.project.ProjectConfig.PROJECT_CONFIG;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectState;
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
  @Inject private ProjectAccessor.Factory projectAccessorFactory;

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

    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void createProjectHttpWhenProjectAlreadyExists_Conflict() throws Exception {
    adminRestSession.put("/projects/" + allProjects.get()).assertConflict();
  }

  @Test
  public void createProjectHttpWhenProjectAlreadyExists_PreconditionFailed() throws Exception {
    adminRestSession
        .putWithHeader(
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
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @UseLocalDisk
  public void createProjectHttpWithUnreasonableName_BadRequest() throws Exception {
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
  public void createProjectHttpWithNameMismatch_BadRequest() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("otherName");
    adminRestSession.put("/projects/" + name("someName"), in).assertBadRequest();
  }

  @Test
  public void createProjectHttpWithInvalidRefName_BadRequest() throws Exception {
    ProjectInput in = new ProjectInput();
    in.branches = Collections.singletonList(name("invalid ref name"));
    adminRestSession.put("/projects/" + name("newProject"), in).assertBadRequest();
  }

  @Test
  public void createProject() throws Exception {
    String newProjectName = name("newProject");
    ProjectInfo p = gApi.projects().create(newProjectName).get();
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
    assertHead(newProjectName, "refs/heads/master");
    assertThat(readProjectConfig(newProjectName))
        .hasValue("[access]\n\tinheritFrom = All-Projects\n[submit]\n\taction = inherit\n");
  }

  @Test
  public void createProjectWithGitSuffix() throws Exception {
    String newProjectName = name("newProject");
    ProjectInfo p = gApi.projects().create(newProjectName + ".git").get();
    assertThat(p.name).isEqualTo(newProjectName);
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
    assertProjectInfo(projectState.getProject(), p);
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
    Project project = projectCache.get(new Project.NameKey(newProjectName)).getProject();
    assertProjectInfo(project, p);
    assertThat(project.getDescription()).isEqualTo(in.description);
    assertThat(project.getConfiguredSubmitType()).isEqualTo(in.submitType);
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
    Project project = projectCache.get(new Project.NameKey(childName)).getProject();
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
    in.owners.add(
        Integer.toString(
            groupCache
                .get(new AccountGroup.NameKey("Administrators"))
                .orElse(null)
                .getId()
                .get())); // by ID
    gApi.projects().create(in);
    ProjectAccessor projectAccessor =
        projectAccessorFactory.create(new Project.NameKey(newProjectName));
    Set<AccountGroup.UUID> expectedOwnerIds = Sets.newHashSetWithExpectedSize(3);
    expectedOwnerIds.add(SystemGroupBackend.ANONYMOUS_USERS);
    expectedOwnerIds.add(SystemGroupBackend.REGISTERED_USERS);
    expectedOwnerIds.add(groupUuid("Administrators"));
    assertProjectOwners(expectedOwnerIds, projectAccessor);
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
  public void createProjectWithEmptyCommit() throws Exception {
    String newProjectName = name("newProject");
    ProjectInput in = new ProjectInput();
    in.name = newProjectName;
    in.createEmptyCommit = true;
    gApi.projects().create(in);
    assertEmptyCommit(newProjectName, "refs/heads/master");
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
  public void createProjectWithCapability() throws Exception {
    allowGlobalCapabilities(SystemGroupBackend.REGISTERED_USERS, GlobalCapability.CREATE_PROJECT);
    try {
      setApiUser(user);
      ProjectInput in = new ProjectInput();
      in.name = name("newProject");
      ProjectInfo p = gApi.projects().create(in).get();
      assertThat(p.name).isEqualTo(in.name);
    } finally {
      removeGlobalCapabilities(
          SystemGroupBackend.REGISTERED_USERS, GlobalCapability.CREATE_PROJECT);
    }
  }

  @Test
  public void createProjectWithoutCapability_Forbidden() throws Exception {
    setApiUser(user);
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
    Project parent = projectCache.get(allProjects).getProject();
    parent.setState(com.google.gerrit.extensions.client.ProjectState.HIDDEN);
    allowGlobalCapabilities(SystemGroupBackend.REGISTERED_USERS, GlobalCapability.CREATE_PROJECT);
    try {
      setApiUser(user);
      ProjectInput in = new ProjectInput();
      in.name = name("newProject");
      ProjectInfo p = gApi.projects().create(in).get();
      assertThat(p.name).isEqualTo(in.name);
    } finally {
      parent.setState(com.google.gerrit.extensions.client.ProjectState.ACTIVE);
      removeGlobalCapabilities(
          SystemGroupBackend.REGISTERED_USERS, GlobalCapability.CREATE_PROJECT);
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
    value = "CHERRY_PICK"
  )
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

  private void assertHead(String projectName, String expectedRef) throws Exception {
    try (Repository repo = repoManager.openRepository(new Project.NameKey(projectName))) {
      assertThat(repo.exactRef(Constants.HEAD).getTarget().getName()).isEqualTo(expectedRef);
    }
  }

  private void assertEmptyCommit(String projectName, String... refs) throws Exception {
    Project.NameKey projectKey = new Project.NameKey(projectName);
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
    exception.expect(errType);
    gApi.projects().create(in);
  }

  private Optional<String> readProjectConfig(String projectName) throws Exception {
    try (Repository repo = repoManager.openRepository(new Project.NameKey(projectName))) {
      TestRepository<?> tr = new TestRepository<>(repo);
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

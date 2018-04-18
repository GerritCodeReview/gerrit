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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.project.testing.Util;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Before;
import org.junit.Test;

public class ConfigChangeIT extends AbstractDaemonTest {
  @Before
  public void setUp() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.allow(u.getConfig(), Permission.OWNER, REGISTERED_USERS, "refs/*");
      Util.allow(u.getConfig(), Permission.PUSH, REGISTERED_USERS, "refs/for/refs/meta/config");
      Util.allow(u.getConfig(), Permission.SUBMIT, REGISTERED_USERS, RefNames.REFS_CONFIG);
      u.save();
    }

    setApiUser(user);
    fetchRefsMetaConfig();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void updateProjectConfig() throws Exception {
    String id = testUpdateProjectConfig();
    assertThat(gApi.changes().id(id).get().revisions).hasSize(1);
  }

  @Test
  @TestProjectInput(cloneAs = "user", submitType = SubmitType.CHERRY_PICK)
  public void updateProjectConfigWithCherryPick() throws Exception {
    String id = testUpdateProjectConfig();
    assertThat(gApi.changes().id(id).get().revisions).hasSize(2);
  }

  private String testUpdateProjectConfig() throws Exception {
    Config cfg = readProjectConfig();
    assertThat(cfg.getString("project", null, "description")).isNull();
    String desc = "new project description";
    cfg.setString("project", null, "description", desc);

    PushOneCommit.Result r = createConfigChange(cfg);
    String id = r.getChangeId();

    gApi.changes().id(id).current().review(ReviewInput.approve());
    gApi.changes().id(id).current().submit();

    assertThat(gApi.changes().id(id).info().status).isEqualTo(ChangeStatus.MERGED);
    assertThat(gApi.projects().name(project.get()).get().description).isEqualTo(desc);
    fetchRefsMetaConfig();
    assertThat(readProjectConfig().getString("project", null, "description")).isEqualTo(desc);
    String changeRev = gApi.changes().id(id).get().currentRevision;
    String branchRev =
        gApi.projects().name(project.get()).branch(RefNames.REFS_CONFIG).get().revision;
    assertThat(changeRev).isEqualTo(branchRev);
    return id;
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void onlyAdminMayUpdateProjectParent() throws Exception {
    setApiUser(admin);
    ProjectInput parent = new ProjectInput();
    parent.name = name("parent");
    parent.permissionsOnly = true;
    gApi.projects().create(parent);

    setApiUser(user);
    Config cfg = readProjectConfig();
    assertThat(cfg.getString("access", null, "inheritFrom")).isAnyOf(null, allProjects.get());
    cfg.setString("access", null, "inheritFrom", parent.name);

    PushOneCommit.Result r = createConfigChange(cfg);
    String id = r.getChangeId();

    gApi.changes().id(id).current().review(ReviewInput.approve());
    try {
      gApi.changes().id(id).current().submit();
      fail("expected submit to fail");
    } catch (ResourceConflictException e) {
      int n = gApi.changes().id(id).info()._number;
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Failed to submit 1 change due to the following problems:\n"
                  + "Change "
                  + n
                  + ": Change contains a project configuration that"
                  + " changes the parent project.\n"
                  + "The change must be submitted by a Gerrit administrator.");
    }

    assertThat(gApi.projects().name(project.get()).get().parent).isEqualTo(allProjects.get());
    fetchRefsMetaConfig();
    assertThat(readProjectConfig().getString("access", null, "inheritFrom"))
        .isAnyOf(null, allProjects.get());

    setApiUser(admin);
    gApi.changes().id(id).current().submit();
    assertThat(gApi.changes().id(id).info().status).isEqualTo(ChangeStatus.MERGED);
    assertThat(gApi.projects().name(project.get()).get().parent).isEqualTo(parent.name);
    fetchRefsMetaConfig();
    assertThat(readProjectConfig().getString("access", null, "inheritFrom")).isEqualTo(parent.name);
  }

  @Test
  public void rejectDoubleInheritance() throws Exception {
    setApiUser(admin);
    // Create separate projects to test the config
    Project.NameKey parent = createProject("projectToInheritFrom");
    Project.NameKey child = createProject("projectWithMalformedConfig");

    String config =
        gApi.projects()
            .name(child.get())
            .branch(RefNames.REFS_CONFIG)
            .file("project.config")
            .asString();

    // Append and push malformed project config
    String pattern = "[access]\n\tinheritFrom = " + allProjects.get() + "\n";
    String doubleInherit = pattern + "\tinheritFrom = " + parent.get() + "\n";
    config = config.replace(pattern, doubleInherit);

    TestRepository<InMemoryRepository> childRepo = cloneProject(child, admin);
    // Fetch meta ref
    GitUtil.fetch(childRepo, RefNames.REFS_CONFIG + ":cfg");
    childRepo.reset("cfg");
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), childRepo, "Subject", "project.config", config);
    PushOneCommit.Result res = push.to(RefNames.REFS_CONFIG);
    res.assertErrorStatus();
    res.assertMessage("cannot inherit from multiple projects");
  }

  private void fetchRefsMetaConfig() throws Exception {
    git().fetch().setRefSpecs(new RefSpec("refs/meta/config:refs/meta/config")).call();
    testRepo.reset(RefNames.REFS_CONFIG);
  }

  private Config readProjectConfig() throws Exception {
    RevWalk rw = testRepo.getRevWalk();
    RevTree tree = rw.parseTree(testRepo.getRepository().resolve("HEAD"));
    RevObject obj = rw.parseAny(testRepo.get(tree, "project.config"));
    ObjectLoader loader = rw.getObjectReader().open(obj);
    String text = new String(loader.getCachedBytes(), UTF_8);
    Config cfg = new Config();
    cfg.fromText(text);
    return cfg;
  }

  private PushOneCommit.Result createConfigChange(Config cfg) throws Exception {
    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                user.getIdent(),
                testRepo,
                "Update project config",
                "project.config",
                cfg.toText())
            .to("refs/for/refs/meta/config");
    r.assertOkStatus();
    return r;
  }
}

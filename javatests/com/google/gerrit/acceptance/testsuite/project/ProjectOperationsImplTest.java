// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.blockLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.capabilityKey;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.labelPermissionKey;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.common.data.GlobalCapability.ADMINISTRATE_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.DEFAULT_MAX_QUERY_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.QUERY_LIMIT;
import static com.google.gerrit.entities.RefNames.REFS_CONFIG;
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.ConfigSubject.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestPermission;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Test;

public class ProjectOperationsImplTest extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;

  @Test
  public void defaultName() throws Exception {
    Project.NameKey name = projectOperations.newProject().create();
    // check that the project was created (throws exception if not found.)
    gApi.projects().name(name.get());
    Project.NameKey name2 = projectOperations.newProject().create();
    assertThat(name2).isNotEqualTo(name);
  }

  @Test
  public void specifiedName() throws Exception {
    String name = "somename";
    Project.NameKey key = projectOperations.newProject().name(name).create();
    assertThat(key.get()).isEqualTo(name);
  }

  @Test
  public void emptyCommit() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();

    List<BranchInfo> branches = gApi.projects().name(key.get()).branches().get();
    assertThat(branches).isNotEmpty();
    assertThat(branches.stream().map(x -> x.ref).collect(toList()))
        .isEqualTo(ImmutableList.of("HEAD", "refs/meta/config", "refs/heads/master"));
  }

  @Test
  public void getProjectConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    assertThat(projectOperations.project(key).getProjectConfig().getProject().getDescription())
        .isEmpty();

    ConfigInput input = new ConfigInput();
    input.description = "my fancy project";
    gApi.projects().name(key.get()).config(input);

    assertThat(projectOperations.project(key).getProjectConfig().getProject().getDescription())
        .isEqualTo("my fancy project");
  }

  @Test
  public void getProjectConfigNoRefsMetaConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    deleteRefsMetaConfig(key);

    ProjectConfig projectConfig = projectOperations.project(key).getProjectConfig();
    assertThat(projectConfig.getName()).isEqualTo(key);
    assertThat(projectConfig.getRevision()).isNull();
  }

  @Test
  public void getConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    Config config = projectOperations.project(key).getConfig();
    assertThat(config).isNotInstanceOf(StoredConfig.class);
    assertThat(config).text().isEmpty();

    ConfigInput input = new ConfigInput();
    input.description = "my fancy project";
    gApi.projects().name(key.get()).config(input);

    config = projectOperations.project(key).getConfig();
    assertThat(config).isNotInstanceOf(StoredConfig.class);
    assertThat(config).sections().containsExactly("project");
    assertThat(config).subsections("project").isEmpty();
    assertThat(config).sectionValues("project").containsExactly("description", "my fancy project");
  }

  @Test
  public void getConfigNoRefsMetaConfig() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    deleteRefsMetaConfig(key);

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).isNotInstanceOf(StoredConfig.class);
    assertThat(config).isEmpty();
  }

  @Test
  public void addAllowPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "group global:Registered-Users");
  }

  @Test
  public void addDenyPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(deny(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "deny group global:Registered-Users");
  }

  @Test
  public void addBlockPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(block(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "block group global:Registered-Users");
  }

  @Test
  public void addAllowForcePermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS).force(true))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "+force group global:Registered-Users");
  }

  @Test
  public void updateExclusivePermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .setExclusiveGroup(permissionKey(Permission.ABANDON).ref("refs/foo"), true)
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Registered-Users",
            "exclusiveGroupPermissions", "abandon");

    projectOperations
        .project(key)
        .forUpdate()
        .setExclusiveGroup(permissionKey(Permission.ABANDON).ref("refs/foo"), false)
        .update();

    config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "group global:Registered-Users");
  }

  @Test
  public void addMultipleExclusivePermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .setExclusiveGroup(permissionKey(Permission.ABANDON).ref("refs/foo"), true)
        .setExclusiveGroup(permissionKey(Permission.CREATE).ref("refs/foo"), true)
        .update();
    assertThat(projectOperations.project(key).getConfig())
        .subsectionValues("access", "refs/foo")
        .containsEntry("exclusiveGroupPermissions", "abandon create");

    projectOperations
        .project(key)
        .forUpdate()
        .setExclusiveGroup(permissionKey(Permission.ABANDON).ref("refs/foo"), false)
        .update();
    assertThat(projectOperations.project(key).getConfig())
        .subsectionValues("access", "refs/foo")
        .containsEntry("exclusiveGroupPermissions", "create");
  }

  @Test
  public void addMultiplePermissions() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allow(Permission.ABANDON).ref("refs/foo").group(PROJECT_OWNERS))
        .add(allow(Permission.CREATE).ref("refs/foo").group(REGISTERED_USERS))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Project-Owners",
            "create", "group global:Registered-Users");
  }

  @Test
  public void addDuplicatePermissions() throws Exception {
    TestPermission permission =
        TestProjectUpdate.allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS).build();
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations.project(key).forUpdate().add(permission).add(permission).update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Registered-Users",
            "abandon", "group global:Registered-Users");

    projectOperations.project(key).forUpdate().add(permission).update();
    config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Registered-Users",
            "abandon", "group global:Registered-Users",
            "abandon", "group global:Registered-Users");
  }

  @Test
  public void addAllowLabelPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/foo").group(REGISTERED_USERS).range(-1, 2))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("label-Code-Review", "-1..+2 group global:Registered-Users");
  }

  @Test
  public void addBlockLabelPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(blockLabel("Code-Review").ref("refs/foo").group(REGISTERED_USERS).range(-1, 2))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("label-Code-Review", "block -1..+2 group global:Registered-Users");
  }

  @Test
  public void addAllowExclusiveLabelPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/foo").group(REGISTERED_USERS).range(-1, 2))
        .setExclusiveGroup(labelPermissionKey("Code-Review").ref("refs/foo"), true)
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "label-Code-Review", "-1..+2 group global:Registered-Users",
            "exclusiveGroupPermissions", "label-Code-Review");

    projectOperations
        .project(key)
        .forUpdate()
        .setExclusiveGroup(labelPermissionKey("Code-Review").ref("refs/foo"), false)
        .update();

    config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("label-Code-Review", "-1..+2 group global:Registered-Users");
  }

  @Test
  public void addAllowLabelAsPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(
            allowLabel("Code-Review")
                .ref("refs/foo")
                .group(REGISTERED_USERS)
                .range(-1, 2)
                .impersonation(true))
        .update();

    Config config = projectOperations.project(key).getConfig();
    assertThat(config).sections().containsExactly("access");
    assertThat(config).subsections("access").containsExactly("refs/foo");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsExactly("labelAs-Code-Review", "-1..+2 group global:Registered-Users");
  }

  @Test
  public void addAllowCapability() throws Exception {
    Config config = projectOperations.project(allProjects).getConfig();
    assertThat(config)
        .sectionValues("capability")
        .doesNotContainEntry("administrateServer", "group Registered Users");

    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS))
        .update();

    assertThat(projectOperations.project(allProjects).getConfig())
        .sectionValues("capability")
        .containsEntry("administrateServer", "group Registered Users");
  }

  @Test
  public void addAllowCapabilityWithRange() throws Exception {
    Config config = projectOperations.project(allProjects).getConfig();
    assertThat(config).sectionValues("capability").doesNotContainKey("queryLimit");

    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, 5000))
        .update();

    assertThat(projectOperations.project(allProjects).getConfig())
        .sectionValues("capability")
        .containsEntry("queryLimit", "+0..+5000 group Registered Users");
  }

  @Test
  public void addAllowCapabilityWithDefaultRange() throws Exception {
    Config config = projectOperations.project(allProjects).getConfig();
    assertThat(config).sectionValues("capability").doesNotContainKey("queryLimit");

    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(QUERY_LIMIT).group(REGISTERED_USERS))
        .update();

    assertThat(projectOperations.project(allProjects).getConfig())
        .sectionValues("capability")
        .containsEntry("queryLimit", "+0..+" + DEFAULT_MAX_QUERY_LIMIT + " group Registered Users");
  }

  @Test
  public void removePermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .add(allow(Permission.ABANDON).ref("refs/foo").group(PROJECT_OWNERS))
        .update();
    assertThat(projectOperations.project(key).getConfig())
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "abandon", "group global:Registered-Users",
            "abandon", "group global:Project-Owners");

    projectOperations
        .project(key)
        .forUpdate()
        .remove(permissionKey(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .update();
    assertThat(projectOperations.project(key).getConfig())
        .subsectionValues("access", "refs/foo")
        .containsExactly("abandon", "group global:Project-Owners");
  }

  @Test
  public void removeLabelPermission() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/foo").group(REGISTERED_USERS).range(-1, 2))
        .add(allowLabel("Code-Review").ref("refs/foo").group(PROJECT_OWNERS).range(-2, 1))
        .update();
    assertThat(projectOperations.project(key).getConfig())
        .subsectionValues("access", "refs/foo")
        .containsExactly(
            "label-Code-Review", "-1..+2 group global:Registered-Users",
            "label-Code-Review", "-2..+1 group global:Project-Owners");

    projectOperations
        .project(key)
        .forUpdate()
        .remove(labelPermissionKey("Code-Review").ref("refs/foo").group(REGISTERED_USERS))
        .update();
    assertThat(projectOperations.project(key).getConfig())
        .subsectionValues("access", "refs/foo")
        .containsExactly("label-Code-Review", "-2..+1 group global:Project-Owners");
  }

  @Test
  public void removeCapability() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS))
        .add(allowCapability(ADMINISTRATE_SERVER).group(PROJECT_OWNERS))
        .update();
    assertThat(projectOperations.project(allProjects).getConfig())
        .sectionValues("capability")
        .containsAtLeastEntriesIn(
            ImmutableListMultimap.of(
                "administrateServer", "group Registered Users",
                "administrateServer", "group Project Owners"));

    projectOperations
        .allProjectsForUpdate()
        .remove(capabilityKey(ADMINISTRATE_SERVER).group(REGISTERED_USERS))
        .update();
    assertThat(projectOperations.project(allProjects).getConfig())
        .sectionValues("capability")
        .doesNotContainEntry("administrateServer", "group Registered Users");
  }

  @Test
  public void removeOnePermissionForAllGroupsFromOneAccessSection() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    projectOperations
        .project(key)
        .forUpdate()
        .add(allow(Permission.ABANDON).ref("refs/foo").group(PROJECT_OWNERS))
        .add(allow(Permission.ABANDON).ref("refs/foo").group(REGISTERED_USERS))
        .add(allow(Permission.CREATE).ref("refs/foo").group(REGISTERED_USERS))
        .update();
    assertThat(projectOperations.project(key).getConfig())
        .subsectionValues("access", "refs/foo")
        .containsAtLeastEntriesIn(
            ImmutableListMultimap.of(
                "abandon", "group global:Project-Owners",
                "abandon", "group global:Registered-Users",
                "create", "group global:Registered-Users"));

    projectOperations
        .project(key)
        .forUpdate()
        .remove(permissionKey(Permission.ABANDON).ref("refs/foo"))
        .update();
    Config config = projectOperations.project(key).getConfig();
    assertThat(config).subsectionValues("access", "refs/foo").doesNotContainKey("abandon");
    assertThat(config)
        .subsectionValues("access", "refs/foo")
        .containsEntry("create", "group global:Registered-Users");
  }

  @Test
  public void updatingCapabilitiesNotAllowedForNonAllProjects() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    assertThrows(
        RuntimeException.class,
        () ->
            projectOperations
                .project(key)
                .forUpdate()
                .add(allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS))
                .update());
    assertThrows(
        RuntimeException.class,
        () ->
            projectOperations
                .project(key)
                .forUpdate()
                .remove(capabilityKey(ADMINISTRATE_SERVER))
                .update());
  }

  private void deleteRefsMetaConfig(Project.NameKey key) throws Exception {
    try (Repository repo = repoManager.openRepository(key);
        TestRepository<Repository> tr = new TestRepository<>(repo)) {
      tr.delete(REFS_CONFIG);
    }
  }
}

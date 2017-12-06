// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.testing.SchemaUpgradeTestEnvironment;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class Schema_165_to_166_Test {
  @Rule public SchemaUpgradeTestEnvironment testEnv = new SchemaUpgradeTestEnvironment();

  @Inject private ProjectCache projectCache;
  @Inject private AllProjectsName allProjectsName;
  @Inject private AllUsersName allUsersName;
  @Inject private Schema_166 schema166;
  @Inject @GerritPersonIdent private PersonIdent serverUser;
  @Inject private MetaDataUpdate.Server metaDataUpdateFactory;

  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    testEnv.getInjector().injectMembers(this);
    db = testEnv.getDb();
  }

  @Test
  public void migrateGlobalCapability() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjectsName).getConfig();
    AccessSection section = cfg.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
    Permission permission = new Permission(GlobalCapability.CREATE_GROUP);
    permission.add(Util.newRule(cfg, SystemGroupBackend.REGISTERED_USERS));
    section.addPermission(permission);
    saveProjectConfig(allProjectsName, cfg);

    schema166.migrateData(db, new TestUpdateUI());

    // Check that the new permission was migrated correctly
    ProjectConfig migratedCfg = projectCache.checkedGet(allUsersName).getConfig();
    AccessSection refsGroups = migratedCfg.getAccessSection(RefNames.REFS_GROUPS + "*", true);

    assertThat(refsGroups.getPermissions()).hasSize(2); // READ (default), CREATE
    Permission migratedCreatePermission = refsGroups.getPermission(Permission.CREATE);
    assertThat(migratedCreatePermission).isNotNull();
    assertThat(migratedCreatePermission.getRules())
        .containsExactly(Util.newRule(migratedCfg, SystemGroupBackend.REGISTERED_USERS));
  }

  @Test
  public void migrateGlobalCapabilityWhenPermissionAlreadyExists() throws Exception {
    ProjectConfig allProjectsCfg = projectCache.checkedGet(allProjectsName).getConfig();
    AccessSection section =
        allProjectsCfg.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
    Permission permission = new Permission(GlobalCapability.CREATE_GROUP);
    permission.add(Util.newRule(allProjectsCfg, SystemGroupBackend.REGISTERED_USERS));
    section.addPermission(permission);
    saveProjectConfig(allProjectsName, allProjectsCfg);

    ProjectConfig allUsersCfg = projectCache.checkedGet(allUsersName).getConfig();
    AccessSection allUsersRefsGroups =
        allUsersCfg.getAccessSection(RefNames.REFS_GROUPS + "*", true);
    Permission allUsersCreate = allUsersRefsGroups.getPermission(Permission.CREATE, true);
    allUsersCreate.add(Util.newRule(allUsersCfg, SystemGroupBackend.REGISTERED_USERS));
    saveProjectConfig(allUsersName, allUsersCfg);

    schema166.migrateData(db, new TestUpdateUI());

    // Check that the migration did not make any changes
    ProjectConfig migratedCfg = projectCache.checkedGet(allUsersName).getConfig();
    assertThat(allUsersCfg.getAccessSection(RefNames.REFS_GROUPS + "*"))
        .isEqualTo(migratedCfg.getAccessSection(RefNames.REFS_GROUPS + "*"));
  }

  @Test
  public void doNothing() throws Exception {
    // No group is granted CREATE_GROUP
    schema166.migrateData(db, new TestUpdateUI());
    // Check that the new permission was migrated correctly
    ProjectConfig migratedCfg = projectCache.checkedGet(allUsersName).getConfig();
    AccessSection refsGroups = migratedCfg.getAccessSection(RefNames.REFS_GROUPS + "*", true);
    assertThat(refsGroups.getPermissions()).hasSize(1); // READ (default)
    Permission migratedCreatePermission = refsGroups.getPermission(Permission.CREATE);
    assertThat(migratedCreatePermission).isNull();
  }

  protected void saveProjectConfig(Project.NameKey p, ProjectConfig cfg) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(p)) {
      md.getCommitBuilder().setCommitter(serverUser);
      md.getCommitBuilder().setAuthor(serverUser);
      cfg.commit(md);
    }
    projectCache.evict(cfg.getProject());
  }
}

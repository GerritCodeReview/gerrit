// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;

import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.PermissionRule.Action;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class GrantDirectPushPermissionsOnStartup implements LifecycleListener {
  public static class GrantDirectPushPermissionsOnStartupModule extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(GrantDirectPushPermissionsOnStartup.class).in(Scopes.SINGLETON);
    }
  }

  private final AllProjectsName allProjects;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final Groups groups;

  @Inject
  GrantDirectPushPermissionsOnStartup(
      AllProjectsName allProjects,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory,
      Groups groups) {
    this.allProjects = allProjects;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.groups = groups;
  }

  @Override
  public void start() {
    try (RefUpdateContext ctx = openTestRefUpdateContext();
        MetaDataUpdate metaDataUpdate = metaDataUpdateFactory.create(allProjects)) {
      ProjectConfig projectConfig = projectConfigFactory.read(metaDataUpdate);
      GroupReference adminGroupRef = findAdminGroup().orElseThrow();
      adminGroupRef = projectConfig.resolve(adminGroupRef);
      PermissionRule.Builder rule = PermissionRule.builder(adminGroupRef).setAction(Action.ALLOW);
      projectConfig.upsertAccessSection(
          RefNames.REFS_HEADS + "*", as -> as.upsertPermission(Permission.PUSH).add(rule));
      projectConfig.upsertAccessSection(
          RefNames.REFS_CONFIG, as -> as.upsertPermission(Permission.PUSH).add(rule));
      projectConfig.commit(metaDataUpdate);
    } catch (IOException | ConfigInvalidException e) {
      throw new IllegalStateException(
          "Unable to assign direct push permissions, tests may fail", e);
    }
  }

  @Override
  public void stop() {}

  private Optional<GroupReference> findAdminGroup() throws IOException, ConfigInvalidException {
    for (GroupReference groupRef : groups.getAllGroupReferences().collect(toImmutableList())) {
      InternalGroup group = groups.getGroup(groupRef.getUUID()).orElseThrow();
      if (group.getName().equals("Administrators")) {
        return Optional.of(groupRef);
      }
    }
    return Optional.empty();
  }
}

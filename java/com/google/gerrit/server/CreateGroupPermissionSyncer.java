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

package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With groups in NoteDb, the capability of creating a group is expressed as a {@code CREATE}
 * permission on {@code refs/groups/*} rather than a global capability in {@code All-Projects}.
 *
 * <p>During the transition phase, we have to keep these permissions in sync with the global
 * capabilities that serve as the source of truth.
 *
 * <p><This class implements a one-way synchronization from the the global {@code CREATE_GROUP}
 * capability in {@code All-Projects} to a {@code CREATE} permission on {@code refs/groups/*} in
 * {@code All-Users}.
 */
@Singleton
public class CreateGroupPermissionSyncer implements ChangeMergedListener {
  private static final Logger log = LoggerFactory.getLogger(CreateGroupPermissionSyncer.class);

  private final AllProjectsName allProjects;
  private final AllUsersName allUsers;
  private final ProjectCache projectCache;
  private final Provider<MetaDataUpdate.Server> metaDataUpdateFactory;

  @Inject
  CreateGroupPermissionSyncer(
      AllProjectsName allProjects,
      AllUsersName allUsers,
      ProjectCache projectCache,
      Provider<MetaDataUpdate.Server> metaDataUpdateFactory) {
    this.allProjects = allProjects;
    this.allUsers = allUsers;
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
  }

  /**
   * Checks if {@code GlobalCapability.CREATE_GROUP} and {@code CREATE} permission on {@code
   * refs/groups/*} have diverged and syncs them by applying the {@code CREATE} permission to {@code
   * refs/groups/*}.
   */
  public void syncIfNeeded() throws IOException, ConfigInvalidException {
    ProjectState allProjectsState = projectCache.checkedGet(allProjects);
    checkNotNull(allProjectsState, "Can't obtain project state for " + allProjects);
    ProjectState allUsersState = projectCache.checkedGet(allUsers);
    checkNotNull(allUsersState, "Can't obtain project state for " + allUsers);

    Set<PermissionRule> createGroupsGlobal =
        new HashSet<>(allProjectsState.getCapabilityCollection().createGroup);
    Set<PermissionRule> createGroupsRef = new HashSet<>();

    AccessSection allUsersCreateGroupAccessSection =
        allUsersState.getConfig().getAccessSection(RefNames.REFS_GROUPS + "*");
    if (allUsersCreateGroupAccessSection != null) {
      Permission create = allUsersCreateGroupAccessSection.getPermission(Permission.CREATE);
      if (create != null && create.getRules() != null) {
        createGroupsRef.addAll(create.getRules());
      }
    }

    if (Sets.symmetricDifference(createGroupsGlobal, createGroupsRef).isEmpty()) {
      // Nothing to sync
      return;
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsers)) {
      ProjectConfig config = ProjectConfig.read(md);
      AccessSection createGroupAccessSection =
          config.getAccessSection(RefNames.REFS_GROUPS + "*", true);
      if (createGroupsGlobal.isEmpty()) {
        createGroupAccessSection.setPermissions(
            createGroupAccessSection
                .getPermissions()
                .stream()
                .filter(p -> !Permission.CREATE.equals(p.getName()))
                .collect(toList()));
        config.replace(createGroupAccessSection);
      } else {
        Permission createGroupPermission = new Permission(Permission.CREATE);
        createGroupAccessSection.addPermission(createGroupPermission);
        createGroupsGlobal.forEach(pr -> createGroupPermission.add(pr));
        // The create permission is managed by Gerrit at this point only so there is no concern of
        // overwriting user-defined permissions here.
        config.replace(createGroupAccessSection);
      }

      config.commit(md);
      projectCache.evict(config.getProject());
    }
  }

  @Override
  public void onChangeMerged(Event event) {
    if (!allProjects.get().equals(event.getChange().project)
        || !isConfigRef(event.getChange().branch)) {
      return;
    }
    try {
      syncIfNeeded();
    } catch (IOException | ConfigInvalidException e) {
      log.error("Can't sync create group permissions", e);
    }
  }
}

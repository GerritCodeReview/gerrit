// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import static com.google.gerrit.server.permissions.GlobalPermission.ADMINISTRATE_SERVER;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static com.google.gerrit.server.permissions.RefPermission.READ;
import static com.google.gerrit.server.permissions.RefPermission.WRITE_CONFIG;

import com.google.common.collect.Maps;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupInfo;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.data.WebLinkInfoCommon;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

class ProjectAccessFactory extends Handler<ProjectAccess> {
  interface Factory {
    ProjectAccessFactory create(@Assisted Project.NameKey name);
  }

  private final GroupBackend groupBackend;
  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final GroupControl.Factory groupControlFactory;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final AllProjectsName allProjectsName;

  private final Project.NameKey projectName;
  private WebLinks webLinks;

  @Inject
  ProjectAccessFactory(
      GroupBackend groupBackend,
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      GroupControl.Factory groupControlFactory,
      MetaDataUpdate.Server metaDataUpdateFactory,
      AllProjectsName allProjectsName,
      WebLinks webLinks,
      @Assisted final Project.NameKey name) {
    this.groupBackend = groupBackend;
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.groupControlFactory = groupControlFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allProjectsName = allProjectsName;
    this.webLinks = webLinks;

    this.projectName = name;
  }

  @Override
  public ProjectAccess call()
      throws NoSuchProjectException, IOException, ConfigInvalidException,
          PermissionBackendException {
    ProjectState projectState = checkProjectState();

    // Load the current configuration from the repository, ensuring its the most
    // recent version available. If it differs from what was in the project
    // state, force a cache flush now.
    //
    ProjectConfig config;
    try (MetaDataUpdate md = metaDataUpdateFactory.create(projectName)) {
      config = ProjectConfig.read(md);
      if (config.updateGroupNames(groupBackend)) {
        md.setMessage("Update group names\n");
        config.commit(md);
        projectCache.evict(config.getProject());
        projectState = checkProjectState();
      } else if (config.getRevision() != null
          && !config.getRevision().equals(projectState.getConfig().getRevision())) {
        projectCache.evict(config.getProject());
        projectState = checkProjectState();
      }
    }

    // The following implementation must match the GetAccess REST API endpoint.

    List<AccessSection> local = new ArrayList<>();
    Set<String> ownerOf = new HashSet<>();
    Map<AccountGroup.UUID, Boolean> visibleGroups = new HashMap<>();
    PermissionBackend.ForProject perm = permissionBackend.user(user).project(projectName);
    boolean checkReadConfig = check(perm, RefNames.REFS_CONFIG, READ);
    boolean canWriteProjectConfig = true;
    try {
      perm.check(ProjectPermission.WRITE_CONFIG);
    } catch (AuthException e) {
      canWriteProjectConfig = false;
    }

    for (AccessSection section : config.getAccessSections()) {
      String name = section.getName();
      if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
        if (canWriteProjectConfig) {
          local.add(section);
          ownerOf.add(name);

        } else if (checkReadConfig) {
          local.add(section);
        }

      } else if (RefConfigSection.isValid(name)) {
        if (check(perm, name, WRITE_CONFIG)) {
          local.add(section);
          ownerOf.add(name);

        } else if (checkReadConfig) {
          local.add(section);

        } else if (check(perm, name, READ)) {
          // Filter the section to only add rules describing groups that
          // are visible to the current-user. This includes any group the
          // user is a member of, as well as groups they own or that
          // are visible to all users.

          AccessSection dst = null;
          for (Permission srcPerm : section.getPermissions()) {
            Permission dstPerm = null;

            for (PermissionRule srcRule : srcPerm.getRules()) {
              AccountGroup.UUID group = srcRule.getGroup().getUUID();
              if (group == null) {
                continue;
              }

              Boolean canSeeGroup = visibleGroups.get(group);
              if (canSeeGroup == null) {
                try {
                  canSeeGroup = groupControlFactory.controlFor(group).isVisible();
                } catch (NoSuchGroupException e) {
                  canSeeGroup = Boolean.FALSE;
                }
                visibleGroups.put(group, canSeeGroup);
              }

              if (canSeeGroup) {
                if (dstPerm == null) {
                  if (dst == null) {
                    dst = new AccessSection(name);
                    local.add(dst);
                  }
                  dstPerm = dst.getPermission(srcPerm.getName(), true);
                }
                dstPerm.add(srcRule);
              }
            }
          }
        }
      }
    }

    if (ownerOf.isEmpty() && isAdmin()) {
      // Special case: If the section list is empty, this project has no current
      // access control information. Fall back to site administrators.
      ownerOf.add(AccessSection.ALL);
    }

    final ProjectAccess detail = new ProjectAccess();
    detail.setProjectName(projectName);

    if (config.getRevision() != null) {
      detail.setRevision(config.getRevision().name());
    }

    detail.setInheritsFrom(config.getProject().getParent(allProjectsName));

    if (projectName.equals(allProjectsName)
        && permissionBackend.user(user).testOrFalse(ADMINISTRATE_SERVER)) {
      ownerOf.add(AccessSection.GLOBAL_CAPABILITIES);
    }

    detail.setLocal(local);
    detail.setOwnerOf(ownerOf);
    detail.setCanUpload(
        canWriteProjectConfig
            || (checkReadConfig && perm.ref(RefNames.REFS_CONFIG).testOrFalse(CREATE_CHANGE)));
    detail.setConfigVisible(canWriteProjectConfig || checkReadConfig);
    detail.setGroupInfo(buildGroupInfo(local));
    detail.setLabelTypes(projectState.getLabelTypes());
    detail.setRoleNames(projectState.getAllRoleSections().keySet());
    detail.setFileHistoryLinks(getConfigFileLogLinks(projectName.get()));
    return detail;
  }

  private List<WebLinkInfoCommon> getConfigFileLogLinks(String projectName) {
    List<WebLinkInfoCommon> links =
        webLinks.getFileHistoryLinks(
            projectName, RefNames.REFS_CONFIG, ProjectConfig.PROJECT_CONFIG);
    return links.isEmpty() ? null : links;
  }

  private Map<AccountGroup.UUID, GroupInfo> buildGroupInfo(List<AccessSection> local) {
    Map<AccountGroup.UUID, GroupInfo> infos = new HashMap<>();
    for (AccessSection section : local) {
      for (Permission permission : section.getPermissions()) {
        for (PermissionRule rule : permission.getRules()) {
          if (rule.getGroup() != null) {
            AccountGroup.UUID uuid = rule.getGroup().getUUID();
            if (uuid != null && !infos.containsKey(uuid)) {
              GroupDescription.Basic group = groupBackend.get(uuid);
              infos.put(uuid, group != null ? new GroupInfo(group) : null);
            }
          }
        }
      }
    }
    return Maps.filterEntries(infos, in -> in.getValue() != null);
  }

  private ProjectState checkProjectState()
      throws NoSuchProjectException, IOException, PermissionBackendException {
    ProjectState state = projectCache.checkedGet(projectName);
    try {
      permissionBackend.user(user).project(projectName).check(ProjectPermission.ACCESS);
    } catch (AuthException e) {
      throw new NoSuchProjectException(projectName);
    }
    return state;
  }

  private static boolean check(PermissionBackend.ForProject ctx, String ref, RefPermission perm)
      throws PermissionBackendException {
    try {
      ctx.ref(ref).check(perm);
      return true;
    } catch (AuthException denied) {
      return false;
    }
  }

  private boolean isAdmin() throws PermissionBackendException {
    try {
      permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }
}

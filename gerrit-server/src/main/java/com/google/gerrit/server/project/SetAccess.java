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

package com.google.gerrit.server.project;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SetAccess implements RestModifyView<ProjectResource, ProjectAccessInput> {
  protected final GroupBackend groupBackend;
  private final PermissionBackend permissionBackend;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final GetAccess getAccess;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> identifiedUser;
  private final SetAccessUtil accessUtil;

  @Inject
  private SetAccess(
      GroupBackend groupBackend,
      PermissionBackend permissionBackend,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllProjectsName allProjects,
      Provider<SetParent> setParent,
      GroupsCollection groupsCollection,
      ProjectCache projectCache,
      GetAccess getAccess,
      Provider<IdentifiedUser> identifiedUser,
      SetAccessUtil accessUtil) {
    this.groupBackend = groupBackend;
    this.permissionBackend = permissionBackend;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.getAccess = getAccess;
    this.projectCache = projectCache;
    this.identifiedUser = identifiedUser;
    this.accessUtil = accessUtil;
  }

  @Override
  public ProjectAccessInfo apply(ProjectResource rsrc, ProjectAccessInput input)
      throws ResourceNotFoundException, ResourceConflictException, IOException, AuthException,
          BadRequestException, UnprocessableEntityException, PermissionBackendException {
    List<AccessSection> removals = accessUtil.getAccessSections(input.remove);
    List<AccessSection> additions = accessUtil.getAccessSections(input.add);
    MetaDataUpdate.User metaDataUpdateUser = metaDataUpdateFactory.get();

    ProjectControl projectControl = rsrc.getControl();
    ProjectConfig config;

    Project.NameKey newParentProjectName =
        input.parent == null ? null : new Project.NameKey(input.parent);

    try (MetaDataUpdate md = metaDataUpdateUser.create(rsrc.getNameKey())) {
      config = ProjectConfig.read(md);

      // Perform permission checks.
      for (AccessSection section : Iterables.concat(additions, removals)) {
        boolean isGlobalCapabilities = AccessSection.GLOBAL_CAPABILITIES.equals(section.getName());
        if (isGlobalCapabilities) {
          permissionBackend.user(identifiedUser).check(GlobalPermission.ADMINISTRATE_SERVER);
        } else if (!projectControl.controlForRef(section.getName()).isOwner()) {
          throw new AuthException(
              "You are not allowed to edit permissions for ref: " + section.getName());
        }
      }

      accessUtil.validityChecks(projectControl, config, newParentProjectName, additions, removals);
      accessUtil.cleanupPermissions(config, removals, additions);
      accessUtil.updateParent(projectControl, config, newParentProjectName, true);

      if (!Strings.isNullOrEmpty(input.message)) {
        if (!input.message.endsWith("\n")) {
          input.message += "\n";
        }
        md.setMessage(input.message);
      } else {
        md.setMessage("Modify access rules\n");
      }

      config.commit(md);
      projectCache.evict(config.getProject());
    } catch (InvalidNameException e) {
      throw new BadRequestException(e.toString());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(rsrc.getName());
    }

    return getAccess.apply(rsrc.getNameKey());
  }
}

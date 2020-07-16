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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CreateGroupPermissionSyncer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
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
  private final CreateGroupPermissionSyncer createGroupPermissionSyncer;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  private SetAccess(
      GroupBackend groupBackend,
      PermissionBackend permissionBackend,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      ProjectCache projectCache,
      GetAccess getAccess,
      Provider<IdentifiedUser> identifiedUser,
      SetAccessUtil accessUtil,
      CreateGroupPermissionSyncer createGroupPermissionSyncer,
      ProjectConfig.Factory projectConfigFactory) {
    this.groupBackend = groupBackend;
    this.permissionBackend = permissionBackend;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.getAccess = getAccess;
    this.projectCache = projectCache;
    this.identifiedUser = identifiedUser;
    this.accessUtil = accessUtil;
    this.createGroupPermissionSyncer = createGroupPermissionSyncer;
    this.projectConfigFactory = projectConfigFactory;
  }

  @Override
  public Response<ProjectAccessInfo> apply(ProjectResource rsrc, ProjectAccessInput input)
      throws Exception {
    MetaDataUpdate.User metaDataUpdateUser = metaDataUpdateFactory.get();

    ProjectConfig config;

    List<AccessSection> removals = accessUtil.getAccessSections(input.remove);
    List<AccessSection> additions = accessUtil.getAccessSections(input.add);
    try (MetaDataUpdate md = metaDataUpdateUser.create(rsrc.getNameKey())) {
      config = projectConfigFactory.read(md);

      // Check that the user has the right permissions.
      boolean checkedAdmin = false;
      for (AccessSection section : Iterables.concat(additions, removals)) {
        boolean isGlobalCapabilities = AccessSection.GLOBAL_CAPABILITIES.equals(section.getName());
        if (isGlobalCapabilities) {
          if (!checkedAdmin) {
            permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
            checkedAdmin = true;
          }
        } else {
          permissionBackend
              .currentUser()
              .project(rsrc.getNameKey())
              .ref(section.getName())
              .check(RefPermission.WRITE_CONFIG);
        }
      }

      accessUtil.validateChanges(config, removals, additions);
      accessUtil.applyChanges(config, removals, additions);

      accessUtil.setParentName(
          identifiedUser.get(),
          config,
          rsrc.getNameKey(),
          input.parent == null ? null : Project.nameKey(input.parent),
          !checkedAdmin);

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
      createGroupPermissionSyncer.syncIfNeeded();
    } catch (InvalidNameException e) {
      throw new BadRequestException(e.toString());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(rsrc.getName(), e);
    }

    return Response.ok(getAccess.apply(rsrc.getNameKey()));
  }
}

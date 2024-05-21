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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CreateGroupPermissionSyncer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigUpdater;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SetAccess implements RestModifyView<ProjectResource, ProjectAccessInput> {
  protected final GroupBackend groupBackend;
  private final PermissionBackend permissionBackend;
  private final GetAccess getAccess;
  private final Provider<IdentifiedUser> identifiedUser;
  private final SetAccessUtil accessUtil;
  private final RepoMetaDataUpdater repoMetaDataUpdater;
  private final CreateGroupPermissionSyncer createGroupPermissionSyncer;

  @Inject
  private SetAccess(
      GroupBackend groupBackend,
      PermissionBackend permissionBackend,
      GetAccess getAccess,
      Provider<IdentifiedUser> identifiedUser,
      SetAccessUtil accessUtil,
      CreateGroupPermissionSyncer createGroupPermissionSyncer,
      RepoMetaDataUpdater repoMetaDataUpdater) {
    this.groupBackend = groupBackend;
    this.permissionBackend = permissionBackend;
    this.getAccess = getAccess;
    this.identifiedUser = identifiedUser;
    this.accessUtil = accessUtil;
    this.repoMetaDataUpdater = repoMetaDataUpdater;
    this.createGroupPermissionSyncer = createGroupPermissionSyncer;
  }

  @Override
  public Response<ProjectAccessInfo> apply(ProjectResource rsrc, ProjectAccessInput input)
      throws Exception {
    validateInput(input);

    ImmutableList<AccessSection> removals =
        accessUtil.getAccessSections(input.remove, /* rejectNonResolvableGroups= */ false);
    ImmutableList<AccessSection> additions =
        accessUtil.getAccessSections(input.add, /* rejectNonResolvableGroups= */ true);

    try (ConfigUpdater updater =
        repoMetaDataUpdater.configUpdaterWithoutPermissionsCheck(
            rsrc.getNameKey(), input.message, "Modify access rules")) {
      ProjectConfig config = updater.getConfig();
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

      updater.commitConfigUpdate();
      createGroupPermissionSyncer.syncIfNeeded();
    } catch (InvalidNameException e) {
      throw new BadRequestException(e.toString());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(rsrc.getName(), e);
    }

    return Response.ok(getAccess.apply(rsrc.getNameKey()));
  }

  private static void validateInput(ProjectAccessInput input) throws BadRequestException {
    if (input.add != null) {
      for (Map.Entry<String, AccessSectionInfo> accessSectionEntry : input.add.entrySet()) {
        validateAccessSection(accessSectionEntry.getKey(), accessSectionEntry.getValue());
      }
    }
  }

  private static void validateAccessSection(String ref, AccessSectionInfo accessSectionInfo)
      throws BadRequestException {
    if (accessSectionInfo != null) {
      for (Map.Entry<String, PermissionInfo> permissionEntry :
          accessSectionInfo.permissions.entrySet()) {
        validatePermission(ref, permissionEntry.getKey(), permissionEntry.getValue());
      }
    }
  }

  private static void validatePermission(
      String ref, String permission, PermissionInfo permissionInfo) throws BadRequestException {
    if (permissionInfo != null) {
      for (Map.Entry<String, PermissionRuleInfo> permissionRuleEntry :
          permissionInfo.rules.entrySet()) {
        validatePermissionRule(
            ref, permission, permissionRuleEntry.getKey(), permissionRuleEntry.getValue());
      }
    }
  }

  private static void validatePermissionRule(
      String ref, String permission, String groupId, PermissionRuleInfo permissionRuleInfo)
      throws BadRequestException {
    if (permissionRuleInfo != null) {
      if (permissionRuleInfo.min != null || permissionRuleInfo.max != null) {
        if (permissionRuleInfo.min == null) {
          throw new BadRequestException(
              String.format(
                  "Invalid range for permission rule that assigns %s to group %s on ref %s:"
                      + " ..%d (min is required if max is set)",
                  permission, groupId, ref, permissionRuleInfo.max));
        }

        if (permissionRuleInfo.max == null) {
          throw new BadRequestException(
              String.format(
                  "Invalid range for permission rule that assigns %s to group %s on ref %s:"
                      + " %d.. (max is required if min is set)",
                  permission, groupId, ref, permissionRuleInfo.min));
        }

        if (permissionRuleInfo.min > permissionRuleInfo.max) {
          throw new BadRequestException(
              String.format(
                  "Invalid range for permission rule that assigns %s to group %s on ref %s:"
                      + " %d..%d (min must be <= max)",
                  permission, groupId, ref, permissionRuleInfo.min, permissionRuleInfo.max));
        }
      }
    }
  }
}

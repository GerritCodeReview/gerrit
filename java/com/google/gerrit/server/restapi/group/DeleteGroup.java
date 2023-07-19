// Copyright (C) 2023 The Android Open Source Project
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
package com.google.gerrit.server.restapi.group;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.NameInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.CREATE_GROUP)
@Singleton
public class DeleteGroup implements RestModifyView<GroupResource, NameInput> {
  private final Provider<ListGroups> listGroupProvider;
  private final GroupCache groupCache;
  private final ProjectCache projectCache;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final GroupJson json;
  private final Groups groups;

  @Inject
  DeleteGroup(
      Provider<ListGroups> listGroupProvider,
      GroupCache groupCache,
      ProjectCache projectCache,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      GroupJson json,
      Groups groups) {
    this.listGroupProvider = listGroupProvider;
    this.groupCache = groupCache;
    this.projectCache = projectCache;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.json = json;
    this.groups = groups;
  }

  public DeleteGroup addOption(ListGroupsOption o) {
    json.addOption(o);
    return this;
  }

  @Override
  public Response<String> apply(GroupResource resource, NameInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
          ResourceConflictException, IOException, ConfigInvalidException, ResourceNotFoundException,
          PermissionBackendException, NotInternalGroupException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    groupDeletionPrecondition(internalGroup);
    if (input != null && !Strings.isNullOrEmpty(input.name) && internalGroup.getName().equals(input.name)) {
      deleteGroup(internalGroup);
      return Response.ok();
    }
    return Response.ok("dry run");
  }

  private List<String> getSubgroupsInGroups(AccountGroup.UUID uuid, String groupName)
      throws ConfigInvalidException, IOException {
    List<String> allGroupsWithSubGroups = new ArrayList<>();
    groups
        .getAllGroupReferences()
        .forEach(
            entry -> {
              try {
                if (groups.getGroup(entry.getUUID()).isEmpty()) {
                  throw new ResourceNotFoundException(
                      String.format(
                          "Could not check if group %s is subgroup of %s",
                          groupName, entry.getName()));
                }
                if (groups.getGroup(entry.getUUID()).get().getSubgroups().contains(uuid)) {
                  allGroupsWithSubGroups.add(entry.getName());
                  throw new ResourceNotFoundException(
                      String.format(
                          "Could not check if group %s is subgroup of %s",
                          groupName, entry.getName()));
                }
              } catch (IOException | ConfigInvalidException | ResourceNotFoundException e) {
                throw new RuntimeException(e);
              }
            });
    return allGroupsWithSubGroups;
  }

  private List<String> getProjectsWithGroupRefs(AccountGroup.UUID uuid) {
    List<String> projects = new ArrayList<>();
    Iterable<Project.NameKey> names = projectCache.all();

    for (Project.NameKey projectName : names) {
      Optional<ProjectState> projectState = projectCache.get(projectName);
      if (projectState.isPresent()) {
        CachedProjectConfig config = projectState.get().getConfig();
        if (config.getGroup(uuid).isPresent()) {
          projects.add(projectName.toString());
        }
      }
    }
    return projects;
  }

  private List<InternalGroup> getOwnedGroup(AccountGroup.UUID uuid, String groupOwnerName)
      throws IOException {
    try {
      ListGroups listOwner = listGroupProvider.get();
      listOwner.setOwnedBy(uuid.get());
      List<GroupInfo> groups = listOwner.get();
      return groups.stream()
          .filter(group -> !group.name.equals(groupOwnerName))
          .map(group -> groupCache.get(AccountGroup.UUID.parse(group.id)))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());
    } catch (Exception e) {
      throw new IOException("Failed to check owned groups for group " + uuid.get(), e);
    }
  }

  private void groupDeletionPrecondition(GroupDescription.Internal internalGroup)
      throws ResourceConflictException, ConfigInvalidException, IOException {
    AccountGroup.UUID uuid = internalGroup.getGroupUUID();
    if (groupCache.get(internalGroup.getGroupUUID()).isEmpty()) {
      throw new ResourceConflictException(
          String.format("group %s does not exist", internalGroup.getGroupUUID()));
    }
    List<InternalGroup> ownedGroup = getOwnedGroup(uuid, internalGroup.getName());
    if (!ownedGroup.isEmpty()) {
      String msg =
          "Cannot delete group that is owner of other groups: \n"
              + ownedGroup.stream()
                  .map(InternalGroup::getName)
                  .collect(Collectors.joining(", ", "[", "]"));
      throw new ResourceConflictException(msg);
    }
    List<String> inProjects = getProjectsWithGroupRefs(uuid);
    if (!inProjects.isEmpty()) {
      String msg =
          "Cannot delete group that is referenced in access permissions for project: \n"
              + inProjects;
      throw new ResourceConflictException(msg);
    }
    List<String> subgroupsInGroups = getSubgroupsInGroups(uuid, internalGroup.getName());
    if (!subgroupsInGroups.isEmpty()) {
      String msg = "Cannot delete group that is subgroup of another group: \n" + subgroupsInGroups;
      throw new ResourceConflictException(msg);
    }
  }

  private void deleteGroup(GroupDescription.Internal internalGroup)
      throws IOException, ConfigInvalidException {
    AccountGroup.UUID uuid = internalGroup.getGroupUUID();
    groupsUpdateProvider.get().deleteGroup(uuid);
  }
}

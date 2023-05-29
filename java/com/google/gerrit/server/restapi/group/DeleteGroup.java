package com.google.gerrit.server.restapi.group;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.DeleteRef;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeleteGroup implements RestModifyView<GroupResource, GroupInput> {
  private final Provider<ListGroups> listProvider;
  private final GroupCache groupCache;
  private final ProjectCache projectCache;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final GroupJson json;
  private final Groups groups;

  @Inject
  DeleteGroup(
      Provider<ListGroups> listProvider,
      GroupCache groupCache,
      ProjectCache projectCache,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider, GroupJson json,
      Groups groups) {
    this.listProvider = listProvider;
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
  public Response<String> apply(GroupResource resource, GroupInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
      ResourceConflictException, IOException, ConfigInvalidException, ResourceNotFoundException,
      PermissionBackendException, NotInternalGroupException {
    if (input == null) {
      //TODO implement own input
      input = new GroupInput();
    }
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    groupDeletionPrecondition(internalGroup);
    deleteGroup(internalGroup);
    return Response.ok();
  }

  private List<String> getSubgroupsInGroups(AccountGroup.UUID uuid, String groupName)
      throws ConfigInvalidException, IOException {
    List<String> allGrsWithSubGr = new ArrayList<>();
      groups.getAllGroupReferences().forEach(entry -> {
        try {
          if (groups.getGroup(entry.getUUID()).isEmpty()){
            throw new ResourceNotFoundException(
                String.format(
                    "Could not check if group %s is subgroup of %s", groupName, entry.getName()));
          }
          if(groups.getGroup(entry.getUUID()).get().getSubgroups().contains(uuid)){
            allGrsWithSubGr.add(entry.getName());
            throw new ResourceNotFoundException(
                String.format(
                    "Could not check if group %s is subgroup of %s", groupName, entry.getName()));
          }
        } catch (IOException | ConfigInvalidException |
                 ResourceNotFoundException e) {
          throw new RuntimeException(e);
        }
      });
    return allGrsWithSubGr;
  }

  private List<String> getProjectsWithGroupRefs(
      AccountGroup.UUID uuid) {
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

  private List<InternalGroup> getOwnedGroup(AccountGroup.UUID uuid, String groupOwnerName){
    try {
      ListGroups listOwner = listProvider.get();
      listOwner.setOwnedBy(uuid.get());
      List<GroupInfo> groups = listOwner.get();
      return groups.stream()
          .filter(group -> !group.name.equals(groupOwnerName))
          .map(group -> groupCache.get(AccountGroup.UUID.parse(group.id)))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void groupDeletionPrecondition(GroupDescription.Internal internalGroup) throws ResourceConflictException, ConfigInvalidException, IOException {
    AccountGroup.UUID uuid = internalGroup.getGroupUUID();
    if (groupCache.get(internalGroup.getGroupUUID()).isEmpty()) {
      throw new ResourceConflictException(
          String.format("group %s does not exist", internalGroup.getGroupUUID()));
    }
    List<InternalGroup> ownedGroup = getOwnedGroup(uuid, internalGroup.getName());
    if (!ownedGroup.isEmpty()) {
      String msg = "Cannot delete group that is owner of other groups: \n" +
          ownedGroup.stream().map(InternalGroup::getName)
              .collect(Collectors.joining(", ", "[", "]"));;
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
      String msg =
          "Cannot delete group that is subgroup of another group: \n" + subgroupsInGroups;
      throw new ResourceConflictException(msg);
    }
  }

  private void deleteGroup(GroupDescription.Internal internalGroup) throws ResourceConflictException, AuthException, PermissionBackendException, IOException {
    AccountGroup.UUID uuid = internalGroup.getGroupUUID();
    try {
      groupsUpdateProvider.get().deleteGroup(uuid, internalGroup.getId(), internalGroup.getName(), internalGroup.getMembers(), internalGroup.getSubgroups());
    } catch (ConfigInvalidException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}

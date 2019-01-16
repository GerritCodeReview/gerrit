// Copyright (C) 2013 The Android Open Source Project
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.SubgroupResource;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.group.AddSubgroups.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AddSubgroups implements RestModifyView<GroupResource, Input> {
  public static class Input {
    @DefaultInput String _oneGroup;

    public List<String> groups;

    public static Input fromGroups(List<String> groups) {
      Input in = new Input();
      in.groups = groups;
      return in;
    }

    static Input init(Input in) {
      if (in == null) {
        in = new Input();
      }
      if (in.groups == null) {
        in.groups = Lists.newArrayListWithCapacity(1);
      }
      if (!Strings.isNullOrEmpty(in._oneGroup)) {
        in.groups.add(in._oneGroup);
      }
      return in;
    }
  }

  private final GroupResolver groupResolver;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final GroupJson json;

  @Inject
  public AddSubgroups(
      GroupResolver groupResolver,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      GroupJson json) {
    this.groupResolver = groupResolver;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.json = json;
  }

  @Override
  public List<GroupInfo> apply(GroupResource resource, Input input)
      throws NotInternalGroupException, AuthException, UnprocessableEntityException,
          ResourceNotFoundException, IOException, ConfigInvalidException,
          PermissionBackendException {
    GroupDescription.Internal group =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    input = Input.init(input);

    GroupControl control = resource.getControl();
    if (!control.canAddGroup()) {
      throw new AuthException(String.format("Cannot add groups to group %s", group.getName()));
    }

    List<GroupInfo> result = new ArrayList<>();
    Set<AccountGroup.UUID> subgroupUuids = new LinkedHashSet<>();
    for (String subgroupIdentifier : input.groups) {
      GroupDescription.Basic subgroup = groupResolver.parse(subgroupIdentifier);
      subgroupUuids.add(subgroup.getGroupUUID());
      result.add(json.format(subgroup));
    }

    AccountGroup.UUID groupUuid = group.getGroupUUID();
    try {
      addSubgroups(groupUuid, subgroupUuids);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    }
    return result;
  }

  private void addSubgroups(
      AccountGroup.UUID parentGroupUuid, Set<AccountGroup.UUID> newSubgroupUuids)
      throws NoSuchGroupException, IOException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(subgroupUuids -> Sets.union(subgroupUuids, newSubgroupUuids))
            .build();
    groupsUpdateProvider.get().updateGroup(parentGroupUuid, groupUpdate);
  }

  @Singleton
  public static class CreateSubgroup
      implements RestCollectionCreateView<GroupResource, SubgroupResource, Input> {
    private final AddSubgroups addSubgroups;

    @Inject
    public CreateSubgroup(AddSubgroups addSubgroups) {
      this.addSubgroups = addSubgroups;
    }

    @Override
    public GroupInfo apply(GroupResource resource, IdString id, Input input)
        throws AuthException, MethodNotAllowedException, ResourceNotFoundException, IOException,
            ConfigInvalidException, PermissionBackendException {
      AddSubgroups.Input in = new AddSubgroups.Input();
      in.groups = ImmutableList.of(id.get());
      try {
        List<GroupInfo> list = addSubgroups.apply(resource, in);
        if (list.size() == 1) {
          return list.get(0);
        }
        throw new IllegalStateException();
      } catch (UnprocessableEntityException e) {
        throw new ResourceNotFoundException(id);
      }
    }
  }

  @Singleton
  public static class UpdateSubgroup implements RestModifyView<SubgroupResource, Input> {
    private final Provider<GetSubgroup> get;

    @Inject
    public UpdateSubgroup(Provider<GetSubgroup> get) {
      this.get = get;
    }

    @Override
    public GroupInfo apply(SubgroupResource resource, Input input)
        throws PermissionBackendException {
      // Do nothing, the group is already included.
      return get.get().apply(resource);
    }
  }
}

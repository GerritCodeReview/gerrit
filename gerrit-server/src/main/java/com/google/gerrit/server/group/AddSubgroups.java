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

package com.google.gerrit.server.group;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.AddSubgroups.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  private final GroupsCollection groupsCollection;
  private final Provider<ReviewDb> db;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final GroupJson json;

  @Inject
  public AddSubgroups(
      GroupsCollection groupsCollection,
      Provider<ReviewDb> db,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      GroupJson json) {
    this.groupsCollection = groupsCollection;
    this.db = db;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.json = json;
  }

  @Override
  public List<GroupInfo> apply(GroupResource resource, Input input)
      throws MethodNotAllowedException, AuthException, UnprocessableEntityException, OrmException,
          ResourceNotFoundException {
    GroupDescription.Internal group =
        resource.asInternalGroup().orElseThrow(MethodNotAllowedException::new);
    input = Input.init(input);

    GroupControl control = resource.getControl();
    if (!control.canAddGroup()) {
      throw new AuthException(String.format("Cannot add groups to group %s", group.getName()));
    }

    List<GroupInfo> result = new ArrayList<>();
    Set<AccountGroup.UUID> subgroupUuids = new HashSet<>();
    for (String subgroupIdentifier : input.groups) {
      GroupDescription.Basic subgroup = groupsCollection.parse(subgroupIdentifier);
      subgroupUuids.add(subgroup.getGroupUUID());
      result.add(json.format(subgroup));
    }

    AccountGroup.UUID groupUuid = group.getGroupUUID();
    try {
      groupsUpdateProvider.get().addSubgroups(db.get(), groupUuid, subgroupUuids);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    }
    return result;
  }

  static class PutSubgroup implements RestModifyView<GroupResource, PutSubgroup.Input> {
    static class Input {}

    private final AddSubgroups addSubgroups;
    private final String id;

    PutSubgroup(AddSubgroups addSubgroups, String id) {
      this.addSubgroups = addSubgroups;
      this.id = id;
    }

    @Override
    public GroupInfo apply(GroupResource resource, Input input)
        throws AuthException, MethodNotAllowedException, ResourceNotFoundException, OrmException {
      AddSubgroups.Input in = new AddSubgroups.Input();
      in.groups = ImmutableList.of(id);
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
  static class UpdateSubgroup implements RestModifyView<SubgroupResource, PutSubgroup.Input> {
    private final Provider<GetSubgroup> get;

    @Inject
    UpdateSubgroup(Provider<GetSubgroup> get) {
      this.get = get;
    }

    @Override
    public GroupInfo apply(SubgroupResource resource, PutSubgroup.Input input) throws OrmException {
      // Do nothing, the group is already included.
      return get.get().apply(resource);
    }
  }
}

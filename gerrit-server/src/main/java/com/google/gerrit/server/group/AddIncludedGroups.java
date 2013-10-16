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
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.group.AddIncludedGroups.Input;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;
import java.util.Map;

public class AddIncludedGroups implements RestModifyView<GroupResource, Input> {
  public static class Input {
    @DefaultInput
    String _oneGroup;

    List<String> groups;

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

  private final Provider<GroupsCollection> groupsCollection;
  private final GroupIncludeCache groupIncludeCache;
  private final ReviewDb db;
  private final GroupJson json;

  @Inject
  public AddIncludedGroups(Provider<GroupsCollection> groupsCollection,
      GroupIncludeCache groupIncludeCache,
      ReviewDb db, GroupJson json) {
    this.groupsCollection = groupsCollection;
    this.groupIncludeCache = groupIncludeCache;
    this.db = db;
    this.json = json;
  }

  @Override
  public List<GroupInfo> apply(GroupResource resource, Input input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
      UnprocessableEntityException, OrmException {
    AccountGroup group = resource.toAccountGroup();
    if (group == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    GroupControl control = resource.getControl();
    Map<AccountGroup.UUID, AccountGroupById> newIncludedGroups = Maps.newHashMap();
    List<AccountGroupByIdAud> newIncludedGroupsAudits = Lists.newLinkedList();
    List<GroupInfo> result = Lists.newLinkedList();
    Account.Id me = ((IdentifiedUser) control.getCurrentUser()).getAccountId();

    for (String includedGroup : input.groups) {
      GroupDescription.Basic d = groupsCollection.get().parse(includedGroup);
      if (!control.canAddGroup(d.getGroupUUID())) {
        throw new AuthException(String.format("Cannot add group: %s",
            d.getName()));
      }

      if (!newIncludedGroups.containsKey(d.getGroupUUID())) {
        AccountGroupById.Key agiKey =
            new AccountGroupById.Key(group.getId(),
                d.getGroupUUID());
        AccountGroupById agi = db.accountGroupById().get(agiKey);
        if (agi == null) {
          agi = new AccountGroupById(agiKey);
          newIncludedGroups.put(d.getGroupUUID(), agi);
          newIncludedGroupsAudits.add(
              new AccountGroupByIdAud(agi, me, TimeUtil.nowTs()));
        }
      }
      result.add(json.format(d));
    }

    if (!newIncludedGroups.isEmpty()) {
      db.accountGroupByIdAud().insert(newIncludedGroupsAudits);
      db.accountGroupById().insert(newIncludedGroups.values());
      for (AccountGroupById agi : newIncludedGroups.values()) {
        groupIncludeCache.evictMemberIn(agi.getIncludeUUID());
      }
      groupIncludeCache.evictMembersOf(group.getGroupUUID());
    }

    return result;
  }

  static class PutIncludedGroup implements RestModifyView<GroupResource, PutIncludedGroup.Input> {
    static class Input {
    }

    private final Provider<AddIncludedGroups> put;
    private final String id;

    PutIncludedGroup(Provider<AddIncludedGroups> put, String id) {
      this.put = put;
      this.id = id;
    }

    @Override
    public GroupInfo apply(GroupResource resource, Input input)
        throws MethodNotAllowedException, AuthException, BadRequestException,
        UnprocessableEntityException, OrmException {
      AddIncludedGroups.Input in = new AddIncludedGroups.Input();
      in.groups = ImmutableList.of(id);
      List<GroupInfo> list = put.get().apply(resource, in);
      if (list.size() == 1) {
        return list.get(0);
      }
      throw new IllegalStateException();
    }
  }

  static class UpdateIncludedGroup implements RestModifyView<IncludedGroupResource, PutIncludedGroup.Input> {
    static class Input {
    }

    private final Provider<GetIncludedGroup> get;

    @Inject
    UpdateIncludedGroup(Provider<GetIncludedGroup> get) {
      this.get = get;
    }

    @Override
    public Object apply(IncludedGroupResource resource,
        PutIncludedGroup.Input input) throws MethodNotAllowedException, OrmException {
      // Do nothing, the group is already included.
      return get.get().apply(resource);
    }
  }
}

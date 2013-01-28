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
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuidAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.BadRequestHandler;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.group.AddIncludedGroups.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;
import java.util.Map;

public class AddIncludedGroups implements RestModifyView<GroupResource, Input> {
  static class Input {
    @DefaultInput
    String _oneGroup;

    List<String> groups;

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

  private final GroupControl.Factory groupControlFactory;
  private final Provider<GroupsCollection> groupsCollection;
  private final GroupIncludeCache groupIncludeCache;
  private final ReviewDb db;
  private final Provider<CurrentUser> self;

  @Inject
  public AddIncludedGroups(final GroupControl.Factory groupControlFactory,
      final Provider<GroupsCollection> groupsCollection,
      final GroupIncludeCache groupIncludeCache, final ReviewDb db,
      final Provider<CurrentUser> self) {
    this.groupControlFactory = groupControlFactory;
    this.groupsCollection = groupsCollection;
    this.groupIncludeCache = groupIncludeCache;
    this.db = db;
    this.self = self;
  }

  @Override
  public List<GroupInfo> apply(GroupResource resource, Input input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
      OrmException {
    final GroupDescription.Basic group = resource.getGroup();
    if (!(group instanceof GroupDescription.Internal)) {
      throw new MethodNotAllowedException();
    }

    input = Input.init(input);

    final AccountGroup internalGroup = ((GroupDescription.Internal) group).getAccountGroup();
    final GroupControl control = groupControlFactory.controlFor(internalGroup);
    final Map<AccountGroup.UUID, AccountGroupIncludeByUuid> newIncludedGroups = Maps.newHashMap();
    final List<AccountGroupIncludeByUuidAudit> newIncludedGroupsAudits = Lists.newLinkedList();
    final BadRequestHandler badRequest = new BadRequestHandler("adding groups");
    final List<GroupInfo> result = Lists.newLinkedList();
    final Account.Id me = ((IdentifiedUser) self.get()).getAccountId();

    for (final String includedGroup : input.groups) {
      try {
        final GroupResource includedGroupResource = groupsCollection.get().parse(includedGroup);
        if (!control.canAddGroup(includedGroupResource.getGroupUUID())) {
          throw new AuthException(String.format("Cannot add group: %s",
              includedGroupResource.getName()));
        }

        if (!newIncludedGroups.containsKey(includedGroupResource.getGroupUUID())) {
          final AccountGroupIncludeByUuid.Key agiKey =
              new AccountGroupIncludeByUuid.Key(internalGroup.getId(),
                  includedGroupResource.getGroupUUID());
          AccountGroupIncludeByUuid agi = db.accountGroupIncludesByUuid().get(agiKey);
          if (agi == null) {
            agi = new AccountGroupIncludeByUuid(agiKey);
            newIncludedGroups.put(includedGroupResource.getGroupUUID(), agi);
            newIncludedGroupsAudits.add(new AccountGroupIncludeByUuidAudit(agi, me));
          }
        }
        result.add(new GroupInfo(includedGroupResource.getGroup()));
      } catch (ResourceNotFoundException e) {
        badRequest.addError(new NoSuchGroupException(includedGroup));
      }
    }

    badRequest.failOnError();

    if (!newIncludedGroups.isEmpty()) {
      db.accountGroupIncludesByUuidAudit().insert(newIncludedGroupsAudits);
      db.accountGroupIncludesByUuid().insert(newIncludedGroups.values());
      for (final AccountGroupIncludeByUuid agi : newIncludedGroups.values()) {
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

    PutIncludedGroup(final Provider<AddIncludedGroups> put, String id) {
      this.put = put;
      this.id = id;
    }

    @Override
    public GroupInfo apply(GroupResource resource, Input input)
        throws MethodNotAllowedException, AuthException, BadRequestException,
        OrmException {
      AddIncludedGroups.Input in = new AddIncludedGroups.Input();
      in.groups = ImmutableList.of(id);
      List<GroupInfo> list = put.get().apply(resource, in);
      if (list.size() == 1) {
        return list.get(0);
      } else {
        throw new IllegalStateException();
      }
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
    public Object apply(IncludedGroupResource resource, PutIncludedGroup.Input input) {
      // Do nothing, the group is already included.
      return get.get().apply(resource);
    }
  }
}

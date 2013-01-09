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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.util.Url;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class MembersCollection implements
    ChildCollection<GroupResource, MemberResource> {
  public final static String GROUP_PREFIX = "group-";
  public final static String ACCOUNT_PREFIX = "account-";

  private final DynamicMap<RestView<MemberResource>> views;
  private final Provider<ListMembers> list;
  private final GroupControl.Factory groupControlFactory;
  private final IdentifiedUser.GenericFactory userGenericFactory;

  @Inject
  MembersCollection(final DynamicMap<RestView<MemberResource>> views,
      final Provider<ListMembers> list,
      final GroupControl.Factory groupControlFactory,
      final IdentifiedUser.GenericFactory userGenericFactory) {
    this.views = views;
    this.list = list;
    this.groupControlFactory = groupControlFactory;
    this.userGenericFactory = userGenericFactory;
  }

  @Override
  public RestView<GroupResource> list() throws ResourceNotFoundException,
      AuthException {
    return list.get();
  }

  @Override
  public MemberResource parse(final GroupResource parent, final String id)
      throws ResourceNotFoundException, Exception {
    final String decodedId = Url.decode(id);
    if (decodedId.startsWith(GROUP_PREFIX)) {
      final String groupId = decodedId.substring(GROUP_PREFIX.length());
      final GroupControl ctl;
      try {
        if (groupId.startsWith(GroupsCollection.UUID_PREFIX)) {
          final String uuid = groupId.substring(GroupsCollection.UUID_PREFIX.length());
          ctl = groupControlFactory.controlFor(new AccountGroup.UUID(uuid));
        } else {
          try {
            ctl = groupControlFactory.controlFor(
                new AccountGroup.Id(Integer.parseInt(groupId)));
          } catch (NumberFormatException e) {
            throw new ResourceNotFoundException(id);
          }
        }
      } catch (NoSuchGroupException e) {
        throw new ResourceNotFoundException(id);
      }
      if (!ctl.isVisible() && !ctl.isOwner()) {
        throw new ResourceNotFoundException(id);
      }
      return new MemberResource(ctl);
    } else if (decodedId.startsWith(ACCOUNT_PREFIX)) {
      final String accountId = decodedId.substring(ACCOUNT_PREFIX.length());
      try {
        final IdentifiedUser u =
            userGenericFactory.create(new Account.Id(Integer
                .parseInt(accountId)));
        return new MemberResource(u);
      } catch (NumberFormatException e) {
        throw new ResourceNotFoundException(id);
      }
    } else {
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public DynamicMap<RestView<MemberResource>> views() {
    return views;
  }

  static class MemberInfo {
  }

  static class GroupInfo extends MemberInfo {
    final String kind = "gerritcodereview#group";

    String name;
    String id;
    String uuid;
    int groupId;
    String description;
    boolean isVisibleToAll;
    String ownerUuid;

    void setUuid(AccountGroup.UUID u) {
      uuid = u.get();
      id = Url.encode(GROUP_PREFIX + GroupsCollection.UUID_PREFIX + uuid);
    }
  }

  static class AccountInfo extends MemberInfo {
    final String kind = "gerritcodereview#account";

    String fullName;
    String id;
    int accountId;
    String email;
    String userName;

    void setId(Account.Id i) {
      accountId = i.get();
      id = Url.encode(ACCOUNT_PREFIX + accountId);
    }
  }
}

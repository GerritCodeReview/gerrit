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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.MemberResource;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.group.AddMembers.Input;
import com.google.gwtorm.server.OrmException;
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
public class AddMembers implements RestModifyView<GroupResource, Input> {
  public static class Input {
    @DefaultInput String _oneMember;

    List<String> members;

    public static Input fromMembers(List<String> members) {
      Input in = new Input();
      in.members = members;
      return in;
    }

    static Input init(Input in) {
      if (in == null) {
        in = new Input();
      }
      if (in.members == null) {
        in.members = Lists.newArrayListWithCapacity(1);
      }
      if (!Strings.isNullOrEmpty(in._oneMember)) {
        in.members.add(in._oneMember);
      }
      return in;
    }
  }

  private final AccountResolver accountResolver;
  private final AccountLoader.Factory infoFactory;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  AddMembers(
      AccountResolver accountResolver,
      AccountLoader.Factory infoFactory,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.accountResolver = accountResolver;
    this.infoFactory = infoFactory;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public List<AccountInfo> apply(GroupResource resource, Input input)
      throws AuthException, NotInternalGroupException, UnprocessableEntityException, OrmException,
          IOException, ConfigInvalidException, ResourceNotFoundException,
          PermissionBackendException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    input = Input.init(input);

    GroupControl control = resource.getControl();
    if (!control.canAddMember()) {
      throw new AuthException("Cannot add members to group " + internalGroup.getName());
    }

    Set<Account.Id> newMemberIds = new LinkedHashSet<>();
    for (String nameOrEmailOrId : input.members) {
      Account a = findAccount(nameOrEmailOrId);
      if (!a.isActive()) {
        throw new UnprocessableEntityException(
            String.format("Account Inactive: %s", nameOrEmailOrId));
      }
      newMemberIds.add(a.getId());
    }

    AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
    try {
      addMembers(groupUuid, newMemberIds);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    }
    return toAccountInfoList(newMemberIds);
  }

  Account findAccount(String nameOrEmailOrId)
      throws AuthException, UnprocessableEntityException, OrmException, IOException,
          ConfigInvalidException {
    return accountResolver.parse(nameOrEmailOrId).getAccount();
  }

  public void addMembers(AccountGroup.UUID groupUuid, Set<Account.Id> newMemberIds)
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, newMemberIds))
            .build();
    groupsUpdateProvider.get().updateGroup(groupUuid, groupUpdate);
  }

  private List<AccountInfo> toAccountInfoList(Set<Account.Id> accountIds)
      throws PermissionBackendException {
    List<AccountInfo> result = new ArrayList<>();
    AccountLoader loader = infoFactory.create(true);
    for (Account.Id accId : accountIds) {
      result.add(loader.get(accId));
    }
    loader.fill();
    return result;
  }

  @Singleton
  public static class CreateMember
      implements RestCollectionCreateView<GroupResource, MemberResource, Input> {
    private final AddMembers put;

    @Inject
    public CreateMember(AddMembers put) {
      this.put = put;
    }

    @Override
    public AccountInfo apply(GroupResource resource, IdString id, Input input)
        throws AuthException, MethodNotAllowedException, ResourceNotFoundException, OrmException,
            IOException, ConfigInvalidException, PermissionBackendException {
      AddMembers.Input in = new AddMembers.Input();
      in._oneMember = id.get();
      try {
        List<AccountInfo> list = put.apply(resource, in);
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
  public static class UpdateMember implements RestModifyView<MemberResource, Input> {
    private final GetMember get;

    @Inject
    public UpdateMember(GetMember get) {
      this.get = get;
    }

    @Override
    public AccountInfo apply(MemberResource resource, Input input)
        throws OrmException, PermissionBackendException {
      // Do nothing, the user is already a member.
      return get.apply(resource);
    }
  }
}

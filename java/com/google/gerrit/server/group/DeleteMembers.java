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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.AddMembers.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteMembers implements RestModifyView<GroupResource, Input> {
  private final AccountsCollection accounts;
  private final Provider<ReviewDb> db;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  DeleteMembers(
      AccountsCollection accounts,
      Provider<ReviewDb> db,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.accounts = accounts;
    this.db = db;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public Response<?> apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException,
          IOException, ConfigInvalidException, ResourceNotFoundException {
    AccountGroup internalGroup = resource.toAccountGroup();
    if (internalGroup == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    if (!control.canRemoveMember()) {
      throw new AuthException("Cannot delete members from group " + internalGroup.getName());
    }

    Set<Account.Id> membersToRemove = new HashSet<>();
    for (String nameOrEmail : input.members) {
      Account a = accounts.parse(nameOrEmail).getAccount();
      membersToRemove.add(a.getId());
    }
    AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
    try {
      groupsUpdateProvider.get().removeGroupMembers(db.get(), groupUuid, membersToRemove);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    }

    return Response.none();
  }

  @Singleton
  static class DeleteMember implements RestModifyView<MemberResource, DeleteMember.Input> {
    static class Input {}

    private final Provider<DeleteMembers> delete;

    @Inject
    DeleteMember(Provider<DeleteMembers> delete) {
      this.delete = delete;
    }

    @Override
    public Response<?> apply(MemberResource resource, Input input)
        throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException,
            IOException, ConfigInvalidException, ResourceNotFoundException {
      AddMembers.Input in = new AddMembers.Input();
      in._oneMember = resource.getMember().getAccountId().toString();
      return delete.get().apply(resource, in);
    }
  }
}

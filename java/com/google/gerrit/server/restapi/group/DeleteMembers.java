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

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.MemberResource;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.restapi.group.AddMembers.Input;
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
  private final AccountResolver accountResolver;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  DeleteMembers(
      AccountResolver accountResolver, @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.accountResolver = accountResolver;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public Response<?> apply(GroupResource resource, Input input)
      throws AuthException, NotInternalGroupException, UnprocessableEntityException, OrmException,
          IOException, ConfigInvalidException, ResourceNotFoundException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    if (!control.canRemoveMember()) {
      throw new AuthException("Cannot delete members from group " + internalGroup.getName());
    }

    Set<Account.Id> membersToRemove = new HashSet<>();
    for (String nameOrEmail : input.members) {
      membersToRemove.add(accountResolver.resolve(nameOrEmail).asUnique().getAccount().getId());
    }
    AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
    try {
      removeGroupMembers(groupUuid, membersToRemove);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    }

    return Response.none();
  }

  private void removeGroupMembers(AccountGroup.UUID groupUuid, Set<Account.Id> accountIds)
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.difference(memberIds, accountIds))
            .build();
    groupsUpdateProvider.get().updateGroup(groupUuid, groupUpdate);
  }

  @Singleton
  public static class DeleteMember implements RestModifyView<MemberResource, Input> {

    private final Provider<DeleteMembers> delete;

    @Inject
    public DeleteMember(Provider<DeleteMembers> delete) {
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

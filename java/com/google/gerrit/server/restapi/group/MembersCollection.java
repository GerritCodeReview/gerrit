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

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.MemberResource;
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class MembersCollection implements ChildCollection<GroupResource, MemberResource> {
  private final DynamicMap<RestView<MemberResource>> views;
  private final Provider<ListMembers> list;
  private final AccountsCollection accounts;

  @Inject
  MembersCollection(
      DynamicMap<RestView<MemberResource>> views,
      Provider<ListMembers> list,
      AccountsCollection accounts) {
    this.views = views;
    this.list = list;
    this.accounts = accounts;
  }

  @Override
  public RestView<GroupResource> list() throws ResourceNotFoundException, AuthException {
    return list.get();
  }

  @Override
  public MemberResource parse(GroupResource parent, IdString id)
      throws NotInternalGroupException, AuthException, ResourceNotFoundException, StorageException,
          IOException, ConfigInvalidException {
    GroupDescription.Internal group =
        parent.asInternalGroup().orElseThrow(NotInternalGroupException::new);

    IdentifiedUser user = accounts.parse(TopLevelResource.INSTANCE, id).getUser();
    if (parent.getControl().canSeeMember(user.getAccountId()) && isMember(group, user)) {
      return new MemberResource(parent, user);
    }
    throw new ResourceNotFoundException(id);
  }

  private static boolean isMember(GroupDescription.Internal group, IdentifiedUser user) {
    return group.getMembers().contains(user.getAccountId());
  }

  @Override
  public DynamicMap<RestView<MemberResource>> views() {
    return views;
  }
}

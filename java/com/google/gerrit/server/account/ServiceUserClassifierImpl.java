// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * An implementation of {@link ServiceUserClassifier} that will consider a user to be a robot if
 * they are a member in the {@code Service Users} group.
 */
@Singleton
public class ServiceUserClassifierImpl implements ServiceUserClassifier {
  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(ServiceUserClassifier.class).to(ServiceUserClassifierImpl.class).in(Scopes.SINGLETON);
      }
    };
  }

  private final GroupCache groupCache;
  private final InternalGroupBackend internalGroupBackend;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  ServiceUserClassifierImpl(
      GroupCache groupCache,
      InternalGroupBackend internalGroupBackend,
      IdentifiedUser.GenericFactory identifiedUserFactory) {
    this.groupCache = groupCache;
    this.internalGroupBackend = internalGroupBackend;
    this.identifiedUserFactory = identifiedUserFactory;
  }

  @Override
  public boolean isServiceUser(Account.Id user) {
    Optional<InternalGroup> maybeGroup = groupCache.get(AccountGroup.nameKey("Service Users"));
    if (!maybeGroup.isPresent()) {
      return false;
    }
    List<AccountGroup.UUID> toTraverse = new ArrayList<>();
    toTraverse.add(maybeGroup.get().getGroupUUID());
    while (!toTraverse.isEmpty()) {
      InternalGroup next =
          groupCache
              .get(toTraverse.remove(0))
              .orElseThrow(() -> new IllegalStateException("invalid subgroup"));
      boolean hasExternalSubgroup =
          next.getSubgroups().stream().anyMatch(g -> !internalGroupBackend.handles(g));
      if (next.getMembers().contains(user)) {
        // The user is a member of the 'Service Users' group or a subgroup.
        return true;
      }
      if (hasExternalSubgroup) {
        // 'Service Users' contains an external subgroup, so we have to default to the more
        // expensive evaluation of
        // getting all of the user's group memberships.
        return identifiedUserFactory
            .create(user)
            .getEffectiveGroups()
            .contains(maybeGroup.get().getGroupUUID());
      }
      toTraverse.addAll(next.getSubgroups());
    }
    return false;
  }
}

// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.RegexAllowedGroups;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Set;

/**
 * Decides who may provide a regular expression as a query operand.
 *
 * <p>TODO: This should become a global capability on master, where we have the freedom to include
 * new permissions without having this interface at all.
 */
public interface RegexPermissionPolicy {
  String NOT_PERMITTED_MESSAGE = "regular expressions are not permitted for your account";

  RegexPermissionPolicy ALLOW_ALL = () -> true;

  default void check() throws RegexNotAllowedException {
    if (!isAllowed()) {
      throw new RegexNotAllowedException();
    }
  }

  boolean isAllowed();

  @Singleton
  class Factory implements Provider<RegexPermissionPolicy> {
    private final Supplier<RegexPermissionPolicy> policy;

    @Inject
    Factory(
        @RegexAllowedGroups Provider<Set<AccountGroup.UUID>> allowGroupsProvider,
        Provider<CurrentUser> currentUser) {
      policy =
          Suppliers.memoize(
              () -> {
                Set<AccountGroup.UUID> allowGroups = allowGroupsProvider.get();
                return allowGroups.isEmpty()
                    ? ALLOW_ALL
                    : new GroupRestricted(allowGroups, currentUser);
              });
    }

    @Override
    public RegexPermissionPolicy get() {
      return policy.get();
    }
  }

  class GroupRestricted implements RegexPermissionPolicy {
    private final ImmutableSet<AccountGroup.UUID> allowGroups;
    private final Provider<CurrentUser> currentUser;

    GroupRestricted(Set<AccountGroup.UUID> allowGroups, Provider<CurrentUser> currentUser) {
      this.allowGroups = ImmutableSet.copyOf(allowGroups);
      this.currentUser = currentUser;
    }

    @Override
    public boolean isAllowed() {
      CurrentUser user = currentUser.get();
      return user.isInternalUser() || user.getEffectiveGroups().containsAnyOf(allowGroups);
    }
  }

  class RegexNotAllowedException extends QueryParseException {
    public RegexNotAllowedException() {
      super(NOT_PERMITTED_MESSAGE);
    }
  }
}

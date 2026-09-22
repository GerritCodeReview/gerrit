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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import org.junit.Test;

public class RegexPermissionPolicyTest {
  private static final AccountGroup.UUID ALLOWED = AccountGroup.uuid("allowed-group");
  private static final AccountGroup.UUID OTHER = AccountGroup.uuid("other-group");

  private static RegexPermissionPolicy restricted(CurrentUser user) {
    return new RegexPermissionPolicy.Factory(
            Providers.of(ImmutableSet.of(ALLOWED)), Providers.of(user))
        .get();
  }

  private static class FakeIdentifiedUser extends CurrentUser {
    private final GroupMembership groups;

    FakeIdentifiedUser(AccountGroup.UUID... groups) {
      this.groups = new ListGroupMembership(ImmutableSet.copyOf(groups));
    }

    @Override
    public GroupMembership getEffectiveGroups() {
      return groups;
    }

    @Override
    public Object getCacheKey() {
      return "fake";
    }
  }

  @Test
  public void noConfiguredGroupUsesNoOpPolicy() {
    assertThat(
            new RegexPermissionPolicy.Factory(Providers.of(ImmutableSet.of()), noUserInScope())
                .get())
        .isSameInstanceAs(RegexPermissionPolicy.ALLOW_ALL);
  }

  @Test
  public void noOpPolicyDoesNotLookAtUser() throws Exception {
    RegexPermissionPolicy.ALLOW_ALL.check();
  }

  @Test
  public void restrictedPolicyAllowsMemberAndInternalUser() throws Exception {
    restricted(new FakeIdentifiedUser(ALLOWED)).check();
    restricted(new InternalUser()).check();
    assertThat(restricted(new FakeIdentifiedUser(ALLOWED)).isAllowed()).isTrue();
    assertThat(restricted(new FakeIdentifiedUser(OTHER)).isAllowed()).isFalse();
  }

  @Test
  public void restrictedPolicyRefusesNonMemberAndAnonymousUser() {
    QueryParseException nonMemberException =
        assertThrows(
            QueryParseException.class, () -> restricted(new FakeIdentifiedUser(OTHER)).check());
    assertThat(nonMemberException)
        .hasMessageThat()
        .isEqualTo(RegexPermissionPolicy.NOT_PERMITTED_MESSAGE);

    QueryParseException anonymousException =
        assertThrows(QueryParseException.class, () -> restricted(new AnonymousUser()).check());
    assertThat(anonymousException)
        .hasMessageThat()
        .isEqualTo(RegexPermissionPolicy.NOT_PERMITTED_MESSAGE);
  }

  private static Provider<CurrentUser> noUserInScope() {
    return () -> {
      throw new IllegalStateException("no user in scope");
    };
  }
}

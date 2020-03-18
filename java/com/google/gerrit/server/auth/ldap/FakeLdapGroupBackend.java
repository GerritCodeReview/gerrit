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

package com.google.gerrit.server.auth.ldap;

import static com.google.gerrit.server.auth.ldap.Helper.LDAP_UUID;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.project.ProjectState;
import java.util.Collection;
import java.util.Collections;

/** Fake Implementation of an LDAP group backend used for testing */
public class FakeLdapGroupBackend implements GroupBackend {

  FakeLdapGroupBackend() {}

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    /** Returns true if the provided parameter is an LDAP UUID */
    return uuid.get().startsWith(LDAP_UUID);
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    if (!handles(uuid)) {
      return null;
    }

    return new GroupDescription.Basic() {
      @Override
      public AccountGroup.UUID getGroupUUID() {
        return uuid;
      }

      @Override
      public String getName() {
        return "fake_group";
      }

      @Override
      @Nullable
      public String getEmailAddress() {
        return null;
      }

      @Override
      @Nullable
      public String getUrl() {
        return null;
      }
    };
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectState project) {
    return Collections.emptySet();
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return new ListGroupMembership(Collections.emptyList());
  }

  @Override
  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
    return true;
  }
}

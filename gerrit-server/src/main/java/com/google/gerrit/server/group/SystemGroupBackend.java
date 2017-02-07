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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AbstractGroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.project.ProjectControl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class SystemGroupBackend extends AbstractGroupBackend {
  public static final String SYSTEM_GROUP_SCHEME = "global:";

  /** Common UUID assigned to the "Anonymous Users" group. */
  public static final AccountGroup.UUID ANONYMOUS_USERS =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Anonymous-Users");

  /** Common UUID assigned to the "Registered Users" group. */
  public static final AccountGroup.UUID REGISTERED_USERS =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Registered-Users");

  /** Common UUID assigned to the "Project Owners" placeholder group. */
  public static final AccountGroup.UUID PROJECT_OWNERS =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Project-Owners");

  /** Common UUID assigned to the "Change Owner" placeholder group. */
  public static final AccountGroup.UUID CHANGE_OWNER =
      new AccountGroup.UUID(SYSTEM_GROUP_SCHEME + "Change-Owner");

  private static final SortedMap<String, GroupReference> names;
  private static final ImmutableMap<AccountGroup.UUID, GroupReference> uuids;
  private static final AccountGroup.UUID[] all = {
    ANONYMOUS_USERS, REGISTERED_USERS, PROJECT_OWNERS, CHANGE_OWNER,
  };

  static {
    SortedMap<String, GroupReference> n = new TreeMap<>();
    ImmutableMap.Builder<AccountGroup.UUID, GroupReference> u = ImmutableMap.builder();

    for (AccountGroup.UUID uuid : all) {
      int c = uuid.get().indexOf(':');
      String name = uuid.get().substring(c + 1).replace('-', ' ');
      GroupReference ref = new GroupReference(uuid, name);
      n.put(ref.getName().toLowerCase(Locale.US), ref);
      u.put(ref.getUUID(), ref);
    }
    names = Collections.unmodifiableSortedMap(n);
    uuids = u.build();
  }

  public static boolean isSystemGroup(AccountGroup.UUID uuid) {
    return uuid.get().startsWith(SYSTEM_GROUP_SCHEME);
  }

  public static boolean isAnonymousOrRegistered(GroupReference ref) {
    return isAnonymousOrRegistered(ref.getUUID());
  }

  public static boolean isAnonymousOrRegistered(AccountGroup.UUID uuid) {
    return ANONYMOUS_USERS.equals(uuid) || REGISTERED_USERS.equals(uuid);
  }

  public static GroupReference getGroup(AccountGroup.UUID uuid) {
    return checkNotNull(uuids.get(uuid), "group %s not found", uuid.get());
  }

  public static List<String> getNames() {
    List<String> names = new ArrayList<>();
    for (AccountGroup.UUID uuid : all) {
      int c = uuid.get().indexOf(':');
      names.add(uuid.get().substring(c + 1).replace('-', ' '));
    }
    return names;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return isSystemGroup(uuid);
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    final GroupReference ref = getGroup(uuid);
    return new GroupDescription.Basic() {
      @Override
      public String getName() {
        return ref.getName();
      }

      @Override
      public AccountGroup.UUID getGroupUUID() {
        return ref.getUUID();
      }

      @Override
      public String getUrl() {
        return null;
      }

      @Override
      public String getEmailAddress() {
        return null;
      }
    };
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectControl project) {
    String nameLC = name.toLowerCase(Locale.US);
    SortedMap<String, GroupReference> matches = names.tailMap(nameLC);
    if (matches.isEmpty()) {
      return Collections.emptyList();
    }

    List<GroupReference> r = new ArrayList<>(matches.size());
    for (Map.Entry<String, GroupReference> e : matches.entrySet()) {
      if (e.getKey().startsWith(nameLC)) {
        r.add(e.getValue());
      } else {
        break;
      }
    }
    return r;
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return new ListGroupMembership(ImmutableSet.of(ANONYMOUS_USERS, REGISTERED_USERS));
  }
}

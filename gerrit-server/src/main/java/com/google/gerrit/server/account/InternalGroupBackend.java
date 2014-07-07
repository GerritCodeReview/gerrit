// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Implementation of GroupBackend for the internal group system. */
@Singleton
public class InternalGroupBackend implements GroupBackend {
  private static final Logger log = LoggerFactory.getLogger(InternalGroupBackend.class);
  private static final Function<AccountGroup, GroupReference> ACT_GROUP_TO_GROUP_REF =
      new Function<AccountGroup, GroupReference>() {
        @Override
        public GroupReference apply(AccountGroup group) {
          return GroupReference.forGroup(group);
        }
      };

  private final GroupControl.Factory groupControlFactory;
  private final GroupCache groupCache;
  private final IncludingGroupMembership.Factory groupMembershipFactory;
  private final GroupIncludeCache groupIncludes;
  private final AccountInfoCacheFactory aic;
  private final GroupInfoCacheFactory gic;

  @Inject
  InternalGroupBackend(GroupControl.Factory groupControlFactory,
      GroupCache groupCache,
      GroupIncludeCache groupIncludes,
      AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      GroupInfoCacheFactory.Factory groupInfoCacheFactory,
      IncludingGroupMembership.Factory groupMembershipFactory) {
    this.groupControlFactory = groupControlFactory;
    this.groupCache = groupCache;
    this.groupMembershipFactory = groupMembershipFactory;
    this.groupIncludes = groupIncludes;
    this.aic = accountInfoCacheFactory.create();
    this.gic = groupInfoCacheFactory.create();
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return AccountGroup.isInternalGroup(uuid);
  }

  @Override
  public GroupDescription.Internal get(AccountGroup.UUID uuid) {
    if (!handles(uuid)) {
      return null;
    }

    AccountGroup g = groupCache.get(uuid);
    if (g == null) {
      return null;
    }
    return GroupDescriptions.forAccountGroup(g);
  }

  @Override
  public Collection<GroupReference> suggest(final String name,
      final ProjectControl project) {
    Iterable<AccountGroup> filtered = Iterables.filter(groupCache.all(),
        new Predicate<AccountGroup>() {
          @Override
          public boolean apply(AccountGroup group) {
            // startsWithIgnoreCase && isVisible
            return group.getName().regionMatches(true, 0, name, 0, name.length())
                && groupControlFactory.controlFor(group).isVisible();
          }
        });
    return Lists.newArrayList(Iterables.transform(filtered, ACT_GROUP_TO_GROUP_REF));
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return groupMembershipFactory.create(user);
  }

  @Override
  public List<Account.Id> loadMembers(final AccountGroup.UUID groupUUID) {
    List<Account.Id> members = new ArrayList<>();
    final AccountGroup group = groupCache.get(groupUUID);
    if (group == null) {
      return Collections.emptyList();
    }
    try {
      GroupControl control = groupControlFactory.validateFor(groupUUID);
      for (final Account.Id m : groupIncludes.accountsOf(groupUUID)) {
        if (control.canSeeMember(m)) {
          aic.want(m);
          members.add(m);
        }
      }
    } catch (NoSuchGroupException e) {
      log.warn(String.format("Cannot lookup members of %s", group.getName()), e);
      return Collections.emptyList();
    }

    Collections.sort(members, new Comparator<Account.Id>() {
      public int compare(final Account.Id o1, final Account.Id o2) {
        final Account a = aic.get(o1);
        final Account b = aic.get(o2);
        return n(a).compareTo(n(b));
      }

      private String n(final Account a) {
        String n = a.getFullName();
        if (n != null && n.length() > 0) {
          return n;
        }

        n = a.getPreferredEmail();
        if (n != null && n.length() > 0) {
          return n;
        }

        return a.getId().toString();
      }
    });
    return members;
  }

  @Override
  public List<AccountGroup.UUID> loadIncludes(
      final AccountGroup.UUID groupUUID, final Project.NameKey nameKey) {
    final AccountGroup group = groupCache.get(groupUUID);
    if (group == null) {
      return Collections.emptyList();
    }

    List<AccountGroup.UUID> groups = new ArrayList<>();
    try {
      GroupControl control = groupControlFactory.validateFor(groupUUID);
      for (final AccountGroup.UUID m : groupIncludes.membersOf(groupUUID)) {
        if (control.canSeeGroup(m)) {
          gic.want(m);
          groups.add(m);
        }
      }
    } catch (NoSuchGroupException e) {
      log.warn(String.format("Cannot lookup members of %s", group.getName()), e);
      return Collections.emptyList();
    }

    Collections.sort(groups, new Comparator<AccountGroup.UUID>() {
      public int compare(final AccountGroup.UUID o1, final AccountGroup.UUID o2) {
        GroupDescription.Basic a = gic.get(o1);
        GroupDescription.Basic b = gic.get(o2);
        return n(a).compareTo(n(b));
      }

      private String n(GroupDescription.Basic a) {
        if (a == null) {
          return "";
        }

        String n = a.getName();
        if (n != null && n.length() > 0) {
          return n;
        }
        return a.getGroupUUID().get();
      }
    });
    return groups;
  }
}

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

import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.project.ProjectControl;

import java.util.Collection;
import java.util.Comparator;

/**
 * Utility class for dealing with a GroupBackend.
 */
public class GroupBackends {

  public static final Comparator<GroupReference> GROUP_REF_NAME_COMPARATOR =
      new Comparator<GroupReference>() {
    @Override
    public int compare(GroupReference a, GroupReference b) {
      return a.getName().compareTo(b.getName());
    }
  };

  public static final Comparator<AccountGroup.UUID> getComparator(
      final GroupInfoCacheFactory gic) {
    return new Comparator<AccountGroup.UUID>() {
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
    };
  }

  public static final Comparator<Account.Id> getComparator(
      final AccountInfoCacheFactory aic) {
    return new Comparator<Account.Id>() {
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
    };
  }

  /**
   * Runs {@link GroupBackend#suggest(String, ProjectControl)} and filters the
   * result to return the best suggestion, or null if one does not exist.
   *
   * @param groupBackend the group backend
   * @param name the name for which to suggest groups
   * @return the best single GroupReference suggestion
   */
  @Nullable
  public static GroupReference findBestSuggestion(GroupBackend groupBackend,
      String name) {
    return findBestSuggestion(groupBackend, name, null);
  }

  /**
   * Runs {@link GroupBackend#suggest(String, ProjectControl)} and filters the
   * result to return the best suggestion, or null if one does not exist.
   *
   * @param groupBackend the group backend
   * @param name the name for which to suggest groups
   * @param project the project for which to suggest groups
   * @return the best single GroupReference suggestion
   */
  @Nullable
  public static GroupReference findBestSuggestion(GroupBackend groupBackend,
      String name, @Nullable ProjectControl project) {
    Collection<GroupReference> refs = groupBackend.suggest(name, project);
    if (refs.size() == 1) {
      return Iterables.getOnlyElement(refs);
    }

    for (GroupReference ref : refs) {
      if (isExactSuggestion(ref, name)) {
        return ref;
      }
    }
    return null;
  }

  /**
   * Runs {@link GroupBackend#suggest(String, ProjectControl)} and filters the
   * result to return the exact suggestion, or null if one does not exist.
   *
   * @param groupBackend the group backend
   * @param name the name for which to suggest groups
   * @return the exact single GroupReference suggestion
   */
  @Nullable
  public static GroupReference findExactSuggestion(
      GroupBackend groupBackend, String name) {
    return findExactSuggestion(groupBackend, name, null);
  }

  /**
   * Runs {@link GroupBackend#suggest(String, ProjectControl)} and filters the
   * result to return the exact suggestion, or null if one does not exist.
   *
   * @param groupBackend the group backend
   * @param name the name for which to suggest groups
   * @param project the project for which to suggest groups
   * @return the exact single GroupReference suggestion
   */
  @Nullable
  public static GroupReference findExactSuggestion(
      GroupBackend groupBackend, String name, ProjectControl project) {
    Collection<GroupReference> refs = groupBackend.suggest(name, project);
    for (GroupReference ref : refs) {
      if (isExactSuggestion(ref, name)) {
        return ref;
      }
    }
    return null;
  }

  /** Returns whether the GroupReference is an exact suggestion for the name. */
  public static boolean isExactSuggestion(GroupReference ref, String name) {
    return ref.getName().equalsIgnoreCase(name) || ref.getUUID().get().equals(name);
  }

  private GroupBackends() {
  }
}

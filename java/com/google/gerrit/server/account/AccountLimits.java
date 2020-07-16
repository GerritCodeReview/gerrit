// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.index.query.QueryProcessor;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/** Limits which QoS a user runs as, and how many search results it can request. */
public class AccountLimits {
  @Singleton
  public static class Factory {
    private final ProjectCache projectCache;

    @Inject
    Factory(ProjectCache projectCache) {
      this.projectCache = projectCache;
    }

    public AccountLimits create(CurrentUser user) {
      return new AccountLimits(projectCache, user);
    }
  }

  private final CapabilityCollection capabilities;
  private final CurrentUser user;

  private AccountLimits(ProjectCache projectCache, CurrentUser currentUser) {
    capabilities = projectCache.getAllProjects().getCapabilityCollection();
    user = currentUser;
  }

  /** @return which priority queue the user's tasks should be submitted to. */
  public QueueProvider.QueueType getQueueType() {
    // If a non-generic group (that is not Anonymous Users or Registered Users)
    // grants us INTERACTIVE permission, use the INTERACTIVE queue even if
    // BATCH was otherwise granted. This allows site administrators to grant
    // INTERACTIVE to Registered Users, and BATCH to 'CI Servers' and have
    // the 'CI Servers' actually use the BATCH queue while everyone else gets
    // to use the INTERACTIVE queue without additional grants.
    //
    GroupMembership groups = user.getEffectiveGroups();
    boolean batch = false;
    for (PermissionRule r : capabilities.priority) {
      if (match(groups, r)) {
        switch (r.getAction()) {
          case INTERACTIVE:
            if (!SystemGroupBackend.isAnonymousOrRegistered(r.getGroup())) {
              return QueueProvider.QueueType.INTERACTIVE;
            }
            break;

          case BATCH:
            batch = true;
            break;

          case ALLOW:
          case BLOCK:
          case DENY:
            break;
        }
      }
    }

    if (batch) {
      // If any of our groups matched to the BATCH queue, use it.
      return QueueProvider.QueueType.BATCH;
    }
    return QueueProvider.QueueType.INTERACTIVE;
  }

  /**
   * Get the limit on a {@link QueryProcessor} for a given user.
   *
   * @return limit according to {@link GlobalCapability#QUERY_LIMIT}.
   */
  public int getQueryLimit() {
    return getRange(GlobalCapability.QUERY_LIMIT).getMax();
  }

  /** @return true if the user has a permission rule specifying the range. */
  public boolean hasExplicitRange(String permission) {
    return GlobalCapability.hasRange(permission) && !getRules(permission).isEmpty();
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    if (GlobalCapability.hasRange(permission)) {
      return toRange(permission, getRules(permission));
    }
    return null;
  }

  private static PermissionRange toRange(String permissionName, List<PermissionRule> ruleList) {
    int min = 0;
    int max = 0;
    if (ruleList.isEmpty()) {
      PermissionRange.WithDefaults defaultRange = GlobalCapability.getRange(permissionName);
      if (defaultRange != null) {
        min = defaultRange.getDefaultMin();
        max = defaultRange.getDefaultMax();
      }
    } else {
      for (PermissionRule rule : ruleList) {
        min = Math.min(min, rule.getMin());
        max = Math.max(max, rule.getMax());
      }
    }
    return new PermissionRange(permissionName, min, max);
  }

  private List<PermissionRule> getRules(String permissionName) {
    List<PermissionRule> rules = capabilities.getPermission(permissionName);
    GroupMembership groups = user.getEffectiveGroups();

    List<PermissionRule> mine = new ArrayList<>(rules.size());
    for (PermissionRule rule : rules) {
      if (match(groups, rule)) {
        mine.add(rule);
      }
    }
    return mine;
  }

  private static boolean match(GroupMembership groups, PermissionRule rule) {
    return groups.contains(rule.getGroup().getUUID());
  }
}

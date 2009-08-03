// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.inject.servlet.RequestScoped;

import java.util.Set;

/**
 * Information about the currently logged in user.
 * <p>
 * This is a {@link RequestScoped} property managed by Guice.
 *
 * @see AnonymousUser
 * @see IdentifiedUser
 */
public abstract class CurrentUser {
  protected final SystemConfig systemConfig;

  protected CurrentUser(final SystemConfig cfg) {
    systemConfig = cfg;
  }

  /**
   * Get the set of groups the user is currently a member of.
   * <p>
   * The returned set may be a subset of the user's actual groups; if the user's
   * account is currently deemed to be untrusted then the effective group set is
   * only the anonymous and registered user groups. To enable additional groups
   * (and gain their granted permissions) the user must update their account to
   * use only trusted authentication providers.
   *
   * @return active groups for this user.
   */
  public abstract Set<AccountGroup.Id> getEffectiveGroups();

  @Deprecated
  public final boolean isAdministrator() {
    return getEffectiveGroups().contains(systemConfig.adminGroupId);
  }

  @Deprecated
  public boolean canPerform(final ProjectCache.Entry e,
      final ApprovalCategory.Id actionId, final short requireValue) {
    if (e == null) {
      return false;
    }

    int val = Integer.MIN_VALUE;
    for (final ProjectRight pr : e.getRights()) {
      if (actionId.equals(pr.getApprovalCategoryId())
          && getEffectiveGroups().contains(pr.getAccountGroupId())) {
        if (val < 0 && pr.getMaxValue() > 0) {
          // If one of the user's groups had denied them access, but
          // this group grants them access, prefer the grant over
          // the denial. We have to break the tie somehow and we
          // prefer being "more open" to being "more closed".
          //
          val = pr.getMaxValue();
        } else {
          // Otherwise we use the largest value we can get.
          //
          val = Math.max(pr.getMaxValue(), val);
        }
      }
    }
    if (val == Integer.MIN_VALUE && actionId.canInheritFromWildProject()) {
      for (final ProjectRight pr : Common.getProjectCache().getWildcardRights()) {
        if (actionId.equals(pr.getApprovalCategoryId())
            && getEffectiveGroups().contains(pr.getAccountGroupId())) {
          val = Math.max(pr.getMaxValue(), val);
        }
      }
    }

    return val >= requireValue;
  }
}

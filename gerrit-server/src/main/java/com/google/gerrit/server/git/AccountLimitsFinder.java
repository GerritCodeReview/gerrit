// Copyright (C) 2017 The Android Open Source Project
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
package com.google.gerrit.server.git;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.git.AccountLimitsConfig.RateLimit;
import com.google.gerrit.server.git.AccountLimitsConfig.Type;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountLimitsFinder {
  private static final Logger log = LoggerFactory.getLogger(AccountLimitsFinder.class);

  private final ProjectCache projectCache;
  private final GroupsCollection groupsCollection;

  @Inject
  AccountLimitsFinder(ProjectCache projectCache, GroupsCollection groupsCollection) {
    this.projectCache = projectCache;
    this.groupsCollection = groupsCollection;
  }

  /**
   * @param type type of rate limit
   * @param user identified user
   * @return the rate limit matching the first configured group limit the given user is a member of
   */
  public Optional<RateLimit> firstMatching(AccountLimitsConfig.Type type, IdentifiedUser user) {
    Optional<Map<String, AccountLimitsConfig.RateLimit>> limits = getRatelimits(type);
    if (limits.isPresent()) {
      GroupMembership memberShip = user.getEffectiveGroups();
      for (String groupName : limits.get().keySet()) {
        GroupDescription.Basic d = groupsCollection.parseId(groupName);
        if (d == null) {
          log.error("Ignoring limits for unknown group ''{}'' in account-limits.config", groupName);
        } else if (memberShip.contains(d.getGroupUUID())) {
          return Optional.ofNullable(limits.get().get(groupName));
        }
      }
    }
    return Optional.empty();
  }

  /**
   * @param type type of rate limit
   * @param groupName name of group to lookup up rate limit for
   * @return rate limit
   */
  public Optional<RateLimit> getRateLimit(Type type, String groupName) {
    if (getRatelimits(type).isPresent()) {
      return Optional.ofNullable(getRatelimits(type).get().get(groupName));
    }
    return Optional.empty();
  }

  /**
   * @param type type of rate limit
   * @return map of rate limits per group name
   */
  private Optional<Map<String, RateLimit>> getRatelimits(Type type) {
    Config cfg = projectCache.getAllProjects().getConfig("account-limits.config").get();
    AccountLimitsConfig limitsCfg = cfg.get(AccountLimitsConfig.KEY);
    Optional<Map<String, AccountLimitsConfig.RateLimit>> limits = limitsCfg.getRatelimits(type);
    return limits;
  }
}

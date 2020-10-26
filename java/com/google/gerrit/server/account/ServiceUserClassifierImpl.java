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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.jgit.lib.Config;

/**
 * An implementation of {@link ServiceUserClassifier} that will consider a user to be a robot if
 * they are a member in the {@code Service Users} group.
 */
@Singleton
public class ServiceUserClassifierImpl implements ServiceUserClassifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(ServiceUserClassifier.class).to(ServiceUserClassifierImpl.class).in(Scopes.SINGLETON);
      }
    };
  }

  private final Config config;
  private final GroupCache groupCache;
  private final InternalGroupBackend internalGroupBackend;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  ServiceUserClassifierImpl(
      @GerritServerConfig Config config,
      GroupCache groupCache,
      InternalGroupBackend internalGroupBackend,
      IdentifiedUser.GenericFactory identifiedUserFactory) {
    this.config = config;
    this.groupCache = groupCache;
    this.internalGroupBackend = internalGroupBackend;
    this.identifiedUserFactory = identifiedUserFactory;
  }

  @Override
  public boolean isServiceUser(Account.Id user) {
    if (isUserWithServiceUserSuffix(user)) {
      return true;
    }
    Optional<InternalGroup> maybeGroup = groupCache.get(AccountGroup.nameKey("Service Users"));
    if (!maybeGroup.isPresent()) {
      return false;
    }
    List<AccountGroup.UUID> toTraverse = new ArrayList<>();
    toTraverse.add(maybeGroup.get().getGroupUUID());
    Set<AccountGroup.UUID> seen = new HashSet<>();
    while (!toTraverse.isEmpty()) {
      InternalGroup currentGroup =
          groupCache
              .get(toTraverse.remove(0))
              .orElseThrow(() -> new IllegalStateException("invalid subgroup"));
      if (seen.contains(currentGroup.getGroupUUID())) {
        logger.atWarning().log(
            "Skipping %s because it's a cyclic subgroup", currentGroup.getGroupUUID());
        continue;
      }
      seen.add(currentGroup.getGroupUUID());
      if (currentGroup.getMembers().contains(user)) {
        // The user is a member of the 'Service Users' group or a subgroup.
        return true;
      }
      boolean hasExternalSubgroup =
          currentGroup.getSubgroups().stream().anyMatch(g -> !internalGroupBackend.handles(g));
      if (hasExternalSubgroup) {
        // 'Service Users or a subgroup of Service User' contains an external subgroup, so we have
        // to default to the more expensive evaluation of getting all of the user's group
        // memberships.
        return identifiedUserFactory
            .create(user)
            .getEffectiveGroups()
            .contains(maybeGroup.get().getGroupUUID());
      }
      toTraverse.addAll(currentGroup.getSubgroups());
    }
    return false;
  }

  /**
   * checks whether the user has an email address which has a suffix that is considered to be a
   * suffix for service users in accounts.serviceUserEmailSuffix.
   *
   * @param user the user we want to check whether they are a service user based on their email
   *     suffix.
   * @return true if one of the email addressses have a suffix that is configured to be a suffix for
   *     service users.
   */
  private boolean isUserWithServiceUserSuffix(Account.Id user) {
    String[] serviceUserSuffixes = config.getStringList("accounts", null, "serviceUserEmailSuffix");
    Set<String> emails = identifiedUserFactory.create(user).getEmailAddresses();
    if (Arrays.stream(serviceUserSuffixes)
        .anyMatch(suffix -> emails.stream().anyMatch(email -> email.endsWith(suffix)))) {
      return true;
    }
    return false;
  }
}

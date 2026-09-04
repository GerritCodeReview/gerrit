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

package com.google.gerrit.server.config;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.server.StartupCheck;
import com.google.gerrit.server.StartupException;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.Groups;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class RegexAllowedGroupsProvider implements Provider<Set<AccountGroup.UUID>>, StartupCheck {
  private final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String SECTION = "regex";
  public static final String KEY = "allowedGroup";

  private final Supplier<ImmutableSet<AccountGroup.UUID>> groupIds;
  private final ImmutableSet<String> configuredNames;
  private final SystemGroupBackend systemGroupBackend;
  private final Groups groups;

  private static final AccountGroup.UUID[] SYSTEM_GROUPS_UUIDS =
      new AccountGroup.UUID[] {
        SystemGroupBackend.ANONYMOUS_USERS,
        SystemGroupBackend.REGISTERED_USERS,
        SystemGroupBackend.PROJECT_OWNERS
      };

  private static class GroupLoadingException extends RuntimeException {
    GroupLoadingException(Exception cause) {
      super(cause);
    }
  }

  @Inject
  RegexAllowedGroupsProvider(
      Groups groups, SystemGroupBackend systemGroupBackend, @GerritServerConfig Config config) {
    this.systemGroupBackend = systemGroupBackend;
    this.groups = groups;

    configuredNames = ImmutableSet.copyOf(config.getStringList(SECTION, null, KEY));
    groupIds =
        Suppliers.memoize(
            () -> {
              if (configuredNames.isEmpty()) {
                return ImmutableSet.of();
              }

              ImmutableSet.Builder<AccountGroup.UUID> groupIds = ImmutableSet.builder();
              Arrays.stream(SYSTEM_GROUPS_UUIDS)
                  .map(systemGroupBackend::getGroup)
                  .filter(group -> configuredNames.contains(group.getName()))
                  .map(GroupReference::getUUID)
                  .forEach(groupIds::add);
              try {
                groups
                    .getAllGroupReferences()
                    .filter(group -> configuredNames.contains(group.getName()))
                    .map(GroupReference::getUUID)
                    .forEach(groupIds::add);
              } catch (IOException | ConfigInvalidException e) {
                throw new GroupLoadingException(e);
              }
              return groupIds.build();
            });
  }

  @Override
  public Set<AccountGroup.UUID> get() {
    return groupIds.get();
  }

  @Override
  public void check() throws StartupException {
    try {
      if (configuredNames.size() <= get().size()) {
        return;
      }
    } catch (GroupLoadingException e) {
      throw new StartupException(
          "Could not load internal groups while resolving regex.allowedGroup.", e.getCause());
    }

    Set<String> unresolvedGroupNames = new HashSet<>(configuredNames);
    for (AccountGroup.UUID uuid : get()) {
      try {
        Optional<String> systemGroupName =
            Optional.ofNullable(systemGroupBackend.get(uuid)).map(GroupDescription.Basic::getName);
        Optional<String> internalGroupName = groups.getGroup(uuid).map(InternalGroup::getName);
        systemGroupName.or(() -> internalGroupName).ifPresent(unresolvedGroupNames::remove);
      } catch (IOException | ConfigInvalidException e) {
        logger.atWarning().withCause(e).log("Unable to resolve group %s", uuid);
      }
    }

    throw new StartupException(
        String.format(
            "Groups configured in regex.allowedGroup could not be resolved: %s."
                + " Fix or remove these entries from gerrit.config.",
            unresolvedGroupNames));
  }
}

// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.server.StartupCheck;
import com.google.gerrit.server.StartupException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.db.Groups;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class BlockUsers {
  @Singleton
  public static class BlockedUsersEnabled implements Provider<Boolean> {
    private final Boolean enabled;

    @Inject
    BlockedUsersEnabled(@GerritServerConfig Config config) {
      this.enabled = !Strings.isNullOrEmpty(config.getString("groups", null, "blockedUsersGroup"));
    }

    @Override
    public Boolean get() {
      return enabled;
    }
  }

  public static class BlockUsersCheck implements StartupCheck {
    static final String NOT_EXISTING_GROUP_MESSAGE =
        "The configured blocked users group name (%s) doesn't exist. Please create it prior configuring it.";

    private final Config config;
    private final Groups groups;

    @Inject
    BlockUsersCheck(@GerritServerConfig Config config, Groups groups) {
      this.config = config;
      this.groups = groups;
    }

    @Override
    public void check() throws StartupException {
      String configuredGroup = config.getString("groups", null, "blockedUsersGroup");
      if (Strings.isNullOrEmpty(configuredGroup)) {
        return;
      }

      try {
        Optional<GroupReference> groupExists =
            groups
                .getAllGroupReferences()
                .filter(group -> group.getName().equals(configuredGroup))
                .findAny();
        if (groupExists.isEmpty()) {
          throw new StartupException(String.format(NOT_EXISTING_GROUP_MESSAGE, configuredGroup));
        }
      } catch (IOException | ConfigInvalidException ignored) {
        return;
      }
    }
  }
}

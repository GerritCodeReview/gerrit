// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.config;

import com.google.gerrit.common.ServerCommand;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Guice provider for the class ServerCommandConfig
 */
public class ServerCommandConfigProvider implements Provider<ServerCommand> {

  private static final Logger log =
      LoggerFactory.getLogger(ProjectCreatorGroupsProvider.class);

  private final ServerCommand serverCommandConfig;

  @Inject
  ServerCommandConfigProvider(@GerritServerConfig final Config config,
      SchemaFactory<ReviewDb> db, final SystemConfig systemConfig) {
    String[] receiveNames = config.getStringList("receive", null, "allowGroup");
    Set<AccountGroup.Id> receiveGroup =
        ConfigUtil.groupsFor(db, receiveNames, log);

    // if no group was set, "registered users" and "anonymous" groups are the
    // default
    if (receiveGroup.isEmpty()) {
      receiveGroup.add(systemConfig.registeredGroupId);
      receiveGroup.add(systemConfig.anonymousGroupId);
    }

    String[] uploadNames = config.getStringList("upload", null, "allowGroup");
    Set<AccountGroup.Id> uploadGroup =
        ConfigUtil.groupsFor(db, uploadNames, log);

    // if no group was set, "registered users" and "anonymous" groups are the
    // default
    if (uploadGroup.isEmpty()) {
      uploadGroup.add(systemConfig.registeredGroupId);
      uploadGroup.add(systemConfig.anonymousGroupId);
    }

    serverCommandConfig = new ServerCommand(receiveGroup, uploadGroup);
  }

  @Override
  public ServerCommand get() {
    return serverCommandConfig;
  }
}

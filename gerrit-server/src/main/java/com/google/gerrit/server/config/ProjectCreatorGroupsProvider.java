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
// limitations under the License.

package com.google.gerrit.server.config;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Provider of the group(s) which are allowed to create new projects.
 * Currently only supports {@code createGroup} declarations in the
 * {@code "*"} repository, like so:
 * <pre>
 * [repository "*"]
 *     createGroup = Registered Users
 *     createGroup = Administrators
 * </pre>
 */
public class ProjectCreatorGroupsProvider implements Provider<Set<AccountGroup.Id>> {
  static final Logger log = LoggerFactory.getLogger(ProjectCreatorGroupsProvider.class);
  private final Set<AccountGroup.Id> groupIds;

  @Inject
  ProjectCreatorGroupsProvider(@GerritServerConfig final Config config, SchemaFactory<ReviewDb> db, final SystemConfig systemConfig) {

    String[] createGroupNames = config.getStringList("repository", "*", "createGroup");
    Set<AccountGroup.Id> createGroups = ConfigUtil.groupsFor(db, createGroupNames, log);

    if (createGroups.isEmpty()){
      groupIds = Collections.singleton(systemConfig.adminGroupId);
    }else{
      groupIds = createGroups;
    }
  }

  public Set<AccountGroup.Id> get() {
    return groupIds;
  }
}
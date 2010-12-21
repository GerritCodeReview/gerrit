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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.Collections;

public class GitReceivePackGroupsProvider extends GroupSetProvider {
  @Inject
  public GitReceivePackGroupsProvider(@GerritServerConfig Config config,
      SchemaFactory<ReviewDb> db) {
    super(config, db, "receive", null, "allowGroup");

    // If no group was set, default to "registered users"
    //
    if (groupIds.isEmpty()) {
      groupIds = Collections.singleton(AccountGroup.REGISTERED_USERS);
    }
  }
}

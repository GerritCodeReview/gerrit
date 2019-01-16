// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.base.Strings;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

/** Preferences for user accounts during schema migrations. */
class VersionedAccountPreferences extends VersionedMetaData {
  static final String PREFERENCES = "preferences.config";

  static VersionedAccountPreferences forUser(Account.Id id) {
    return new VersionedAccountPreferences(RefNames.refsUsers(id));
  }

  static VersionedAccountPreferences forDefault() {
    return new VersionedAccountPreferences(RefNames.REFS_USERS_DEFAULT);
  }

  private final String ref;
  private Config cfg;

  protected VersionedAccountPreferences(String ref) {
    this.ref = ref;
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  Config getConfig() {
    return cfg;
  }

  @Override
  protected void onLoad() throws ConfigInvalidException {
    cfg = readConfig(PREFERENCES);
  }

  @Override
  protected boolean onSave(CommitBuilder commit) {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated preferences\n");
    }
    saveConfig(PREFERENCES, cfg);
    return true;
  }
}

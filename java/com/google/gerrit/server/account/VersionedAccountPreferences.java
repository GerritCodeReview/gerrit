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

package com.google.gerrit.server.account;

import com.google.common.base.Strings;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.VersionedMetaData;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

/**
 * Preferences for user accounts.
 *
 * <p>Preferences are available through {@link AccountState} which can be retrieved from the {@link
 * AccountCache}.
 *
 * <p>To update preferences use {@link AccountsUpdate}.
 *
 * <p>This class is only kept to support old schema migrations and should be deleted when these
 * schema migrations are no longer needed.
 */
@Deprecated
public class VersionedAccountPreferences extends VersionedMetaData {
  private static final String PREFERENCES = "preferences.config";

  public static VersionedAccountPreferences forUser(Account.Id id) {
    return new VersionedAccountPreferences(RefNames.refsUsers(id));
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

  public Config getConfig() {
    return cfg;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    cfg = readConfig(PREFERENCES);
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated preferences\n");
    }
    saveConfig(PREFERENCES, cfg);
    return true;
  }
}

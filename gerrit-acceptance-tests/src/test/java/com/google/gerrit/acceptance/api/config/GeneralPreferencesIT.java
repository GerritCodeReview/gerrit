// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.AssertUtil.assertPrefs;
import static com.google.gerrit.acceptance.GitUtil.fetch;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;

import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.After;
import org.junit.Test;

@NoHttpd
public class GeneralPreferencesIT extends AbstractDaemonTest {
  @Inject
  private AllUsersName allUsers;

  @After
  public void cleanUp() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    try {
      fetch(allUsersRepo, RefNames.REFS_USERS_DEFAULT + ":defaults");
    } catch (TransportException e) {
      if (e.getMessage().equals("Remote does not have "
          + RefNames.REFS_USERS_DEFAULT + " available for fetch.")) {
        return;
      }
      throw e;
    }
    allUsersRepo.reset("defaults");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), allUsersRepo,
        "Delete default preferences", VersionedAccountPreferences.PREFERENCES,
        "");
    push.rm(RefNames.REFS_USERS_DEFAULT).assertOkStatus();
  }

  @Test
  public void getGeneralPreferences() throws Exception {
    GeneralPreferencesInfo result =
        gApi.config().server().getDefaultPreferences();
    assertPrefs(result, GeneralPreferencesInfo.defaults(), "my");
  }

  @Test
  public void setGeneralPreferences() throws Exception {
    boolean newSignedOffBy = !GeneralPreferencesInfo.defaults().signedOffBy;
    GeneralPreferencesInfo update = new GeneralPreferencesInfo();
    update.signedOffBy = newSignedOffBy;
    GeneralPreferencesInfo result =
        gApi.config().server().setDefaultPreferences(update);
    assertThat(result.signedOffBy).named("signedOffBy").isEqualTo(newSignedOffBy);

    result = gApi.config().server().getDefaultPreferences();
    GeneralPreferencesInfo expected = GeneralPreferencesInfo.defaults();
    expected.signedOffBy = newSignedOffBy;
    assertPrefs(result, expected, "my");
  }
}

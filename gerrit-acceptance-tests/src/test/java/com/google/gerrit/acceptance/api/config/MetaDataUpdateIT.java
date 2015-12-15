// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.io.IOException;

@NoHttpd
public class MetaDataUpdateIT extends AbstractDaemonTest  {

  @Inject
  protected AllUsersName allUsers;

  @Test
  public void withWrongBatchRefUpdateUsage() throws Exception {
    // prepare 2 refs/users/... branches that each have 1 commit
    setTheme(admin.id, Theme.MIDNIGHT);
    setTheme(user.id, Theme.TWILIGHT);

    // update both refs/users/ branches with a batch
    try (Repository repo = repoManager.openRepository(allUsers)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      MetaDataUpdate md = metaDataUpdateFactory.create(allUsers, batchUpdate);
      try {
        md.setMessage("Update with batch\n");
        setTheme(md, admin.id, Theme.ECLIPSE);
        setTheme(md, user.id, Theme.NEAT);
      } finally {
        md.close();
      }
      try (RevWalk rw = new RevWalk(repo)) {
        batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
      }
    }

    // verify the result
    assertThat(getTheme(admin.id)).isEqualTo(Theme.ECLIPSE);
    // Not true that <ECLIPSE> is equal to <NEAT>
    assertThat(getTheme(user.id)).isEqualTo(Theme.NEAT);

    try (Repository repo = repoManager.openRepository(allUsers);
         RevWalk rw = new RevWalk(repo)) {
      Ref adminRef = repo.getRef(RefNames.refsUsers(admin.id));
      RevCommit adminCommit = rw.parseCommit(adminRef.getObjectId());
      assertThat(adminCommit.getParentCount()).isEqualTo(1);

      Ref userRef = repo.getRef(RefNames.refsUsers(user.id));
      RevCommit userCommit = rw.parseCommit(userRef.getObjectId());
      // Not true that <2> is equal to <1>
      assertThat(userCommit.getParentCount()).isEqualTo(1);
    }
  }

  @Test
  public void withCorrectBatchRefUpdateUsage() throws Exception {
    // prepare 2 refs/users/... branches that each have 1 commit
    setTheme(admin.id, Theme.MIDNIGHT);
    setTheme(user.id, Theme.TWILIGHT);

    // update both refs/users/ branches with a batch
    try (Repository repo = repoManager.openRepository(allUsers)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      MetaDataUpdate md = metaDataUpdateFactory.create(allUsers, batchUpdate);
      try {
        md.setMessage("Update with batch\n");
        setTheme(md, admin.id, Theme.ECLIPSE);
      } finally {
        md.close();
      }

      md = metaDataUpdateFactory.create(allUsers, batchUpdate);
      try {
        md.setMessage("Update with batch\n");
        setTheme(md, user.id, Theme.NEAT);
      } finally {
        md.close();
      }
      try (RevWalk rw = new RevWalk(repo)) {
        batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
      }
    }

    // verify the result
    assertThat(getTheme(admin.id)).isEqualTo(Theme.ECLIPSE);
    assertThat(getTheme(user.id)).isEqualTo(Theme.NEAT);

    try (Repository repo = repoManager.openRepository(allUsers);
         RevWalk rw = new RevWalk(repo)) {
      Ref adminRef = repo.getRef(RefNames.refsUsers(admin.id));
      RevCommit adminCommit = rw.parseCommit(adminRef.getObjectId());
      assertThat(adminCommit.getParentCount()).isEqualTo(1);

      Ref userRef = repo.getRef(RefNames.refsUsers(user.id));
      RevCommit userCommit = rw.parseCommit(userRef.getObjectId());
      assertThat(userCommit.getParentCount()).isEqualTo(1);
    }
  }

  private void setTheme(Account.Id id, Theme theme)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allUsers);
    try {
      setTheme(md, id, theme);
    } finally {
      md.close();
    }
  }

  private void setTheme(MetaDataUpdate md, Account.Id id, Theme theme)
      throws IOException, ConfigInvalidException {
    VersionedAccountPreferences vPrefs =
        VersionedAccountPreferences.forUser(id);
    vPrefs.load(md);
    DiffPreferencesInfo prefs =
        loadSection(vPrefs.getConfig(), UserConfigSections.DIFF, null,
            new DiffPreferencesInfo(), DiffPreferencesInfo.defaults(), null);
    prefs.theme = theme;
    storeSection(vPrefs.getConfig(), UserConfigSections.DIFF, null, prefs,
        DiffPreferencesInfo.defaults());
    vPrefs.commit(md);
  }

  private Theme getTheme(Account.Id id)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allUsers);
    try {
      VersionedAccountPreferences vPrefs =
          VersionedAccountPreferences.forUser(id);
      vPrefs.load(md);
      DiffPreferencesInfo prefs =
          loadSection(vPrefs.getConfig(), UserConfigSections.DIFF, null,
              new DiffPreferencesInfo(), DiffPreferencesInfo.defaults(), null);
      return prefs.theme;
    } finally {
      md.close();
    }
  }
}

// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static com.google.gerrit.server.git.UserConfigSections.MY;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;

/**
 * Remove "My Drafts" menu items for all users.
 *
 * <p>Since draft changes no longer exist, these menu items are obsolete.
 *
 * <p>Only matches menu items (with any name) where the URL exactly matches the <a
 * href="https://gerrit.googlesource.com/gerrit/+/v2.14.4/gerrit-server/src/main/java/com/google/gerrit/server/account/GeneralPreferencesLoader.java#144">default
 * version from 2.14 and earlier</a>. Other menus containing {@code is:draft} in other positions are
 * not affected; this is still a valid predicate that matches no changes.
 */
public class Schema_160 extends SchemaVersion {
  @VisibleForTesting static final String DEFAULT_DRAFT_ITEM = "/q/owner:self+is:draft";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final Provider<PersonIdent> serverIdent;

  @Inject
  Schema_160(
      Provider<Schema_159> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent Provider<PersonIdent> serverIdent) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverIdent = serverIdent;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try {
      try (Repository repo = repoManager.openRepository(allUsersName)) {
        ProgressMonitor pm = new TextProgressMonitor();
        pm.beginTask("Removing \"My Drafts\" menu items", ProgressMonitor.UNKNOWN);
        for (Account.Id id : (Iterable<Account.Id>) Accounts.readUserRefs(repo)::iterator) {
          if (removeMyDrafts(repo, id)) {
            pm.update(1);
          }
        }
        pm.endTask();
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Removing \"My Drafts\" menu items failed", e);
    }
  }

  private boolean removeMyDrafts(Repository repo, Account.Id id)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, repo);
    PersonIdent ident = serverIdent.get();
    md.getCommitBuilder().setAuthor(ident);
    md.getCommitBuilder().setCommitter(ident);
    Prefs prefs = new Prefs(id);
    prefs.load(repo);
    prefs.removeMyDrafts();
    prefs.commit(md);
    return prefs.dirty();
  }

  private static class Prefs extends VersionedAccountPreferences {
    private boolean dirty;

    Prefs(Account.Id id) {
      super(RefNames.refsUsers(id));
    }

    @Override
    protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
      if (!dirty) {
        return false;
      }
      commit.setMessage("Remove \"My Drafts\" menu items");
      return super.onSave(commit);
    }

    void removeMyDrafts() {
      Config cfg = getConfig();
      for (String item : cfg.getSubsections(MY)) {
        String value = cfg.getString(MY, item, KEY_URL)
        if (value != null && value.endsWith(DEFAULT_DRAFT_ITEM)) {
          cfg.unsetSection(MY, item);
          dirty = true;
        }
      }
    }

    boolean dirty() {
      return dirty;
    }
  }
}
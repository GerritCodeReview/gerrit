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

package com.google.gerrit.server.schema;

import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gwtorm.server.OrmException;
import com.google.gerrit.server.schema.Schema_119.EmailStrategy;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class Schema_124 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_124(Provider<Schema_123> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {

    try (Repository git = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(git)) {
      BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
      for (Account a : db.accounts().all()) {
        try (MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED,
            allUsersName, git, bru)) {
          md.getCommitBuilder().setAuthor(serverUser);
          md.getCommitBuilder().setCommitter(serverUser);
          VersionedAccountPreferences p =
              VersionedAccountPreferences.forUser(a.getId());
          try {
            p.load(md);
            Config cfg = p.getConfig();

            EmailStrategy emailStrategy =
                cfg.getEnum(UserConfigSections.GENERAL, null, "emailStrategy",
                    EmailStrategy.ENABLED);

            ArrayList<EmailTypes> types;
            if (emailStrategy == EmailStrategy.ENABLED
                || emailStrategy == EmailStrategy.CC_ON_OWN_COMMENTS) {
              types = new ArrayList<>(GeneralPreferencesInfo.getDefaultEmailTypes());
            } else {
              types = new ArrayList<>();
            }

            if (emailStrategy == EmailStrategy.CC_ON_OWN_COMMENTS) {
              types.add(EmailTypes.CC_ON_OWN_COMMENTS);
            }

            cfg.unsetSection(UserConfigSections.EMAIL, null);
            for (String subsection: cfg.getSubsections(UserConfigSections.EMAIL)) {
              cfg.unsetSection(UserConfigSections.EMAIL, subsection);
            }

            cfg.unset(UserConfigSections.GENERAL, null, "emailStrategy");

            ArrayList<String> stringTypes = new ArrayList<>();
            for (EmailTypes type : types) {
              stringTypes.add(type.name());
            }
            cfg.setStringList(UserConfigSections.EMAIL, null,
                UserConfigSections.EMAIL, stringTypes);
            cfg.setBoolean(UserConfigSections.EMAIL, null, "isConfigured",
                true);
          } catch (ConfigInvalidException e) {
            e.printStackTrace();
          }
          p.commit(md);
        }
      }
      bru.execute(rw, new TextProgressMonitor());
    } catch (IOException ex) {
      throw new OrmException(ex);
    }
  }
}

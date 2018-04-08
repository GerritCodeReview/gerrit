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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.InternalAccountUpdate;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class Schema_139 extends SchemaVersion {
  private static final String MSG = "Migrate project watches to git";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_139(
      Provider<Schema_138> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    ListMultimap<Account.Id, ProjectWatch> imports =
        MultimapBuilder.hashKeys().arrayListValues().build();
    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT "
                    + "account_id, "
                    + "project_name, "
                    + "filter, "
                    + "notify_abandoned_changes, "
                    + "notify_all_comments, "
                    + "notify_new_changes, "
                    + "notify_new_patch_sets, "
                    + "notify_submitted_changes "
                    + "FROM account_project_watches")) {
      while (rs.next()) {
        Account.Id accountId = new Account.Id(rs.getInt(1));
        ProjectWatch.Builder b =
            ProjectWatch.builder()
                .project(new Project.NameKey(rs.getString(2)))
                .filter(rs.getString(3))
                .notifyAbandonedChanges(toBoolean(rs.getString(4)))
                .notifyAllComments(toBoolean(rs.getString(5)))
                .notifyNewChanges(toBoolean(rs.getString(6)))
                .notifyNewPatchSets(toBoolean(rs.getString(7)))
                .notifySubmittedChanges(toBoolean(rs.getString(8)));
        imports.put(accountId, b.build());
      }
    }

    if (imports.isEmpty()) {
      return;
    }

    try (Repository git = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(git)) {
      BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
      bru.setRefLogIdent(serverUser);
      bru.setRefLogMessage(MSG, false);

      for (Map.Entry<Account.Id, Collection<ProjectWatch>> e : imports.asMap().entrySet()) {
        Map<ProjectWatchKey, Set<NotifyType>> projectWatches = new HashMap<>();
        for (ProjectWatch projectWatch : e.getValue()) {
          ProjectWatchKey key =
              ProjectWatchKey.create(projectWatch.project(), projectWatch.filter());
          if (projectWatches.containsKey(key)) {
            throw new OrmDuplicateKeyException(
                "Duplicate key for watched project: " + key.toString());
          }
          Set<NotifyType> notifyValues = EnumSet.noneOf(NotifyType.class);
          if (projectWatch.notifyAbandonedChanges()) {
            notifyValues.add(NotifyType.ABANDONED_CHANGES);
          }
          if (projectWatch.notifyAllComments()) {
            notifyValues.add(NotifyType.ALL_COMMENTS);
          }
          if (projectWatch.notifyNewChanges()) {
            notifyValues.add(NotifyType.NEW_CHANGES);
          }
          if (projectWatch.notifyNewPatchSets()) {
            notifyValues.add(NotifyType.NEW_PATCHSETS);
          }
          if (projectWatch.notifySubmittedChanges()) {
            notifyValues.add(NotifyType.SUBMITTED_CHANGES);
          }
          projectWatches.put(key, notifyValues);
        }

        try (MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git, bru)) {
          md.getCommitBuilder().setAuthor(serverUser);
          md.getCommitBuilder().setCommitter(serverUser);
          md.setMessage(MSG);

          AccountConfig accountConfig = new AccountConfig(e.getKey(), git);
          accountConfig.load(md);
          accountConfig.setAccountUpdate(
              InternalAccountUpdate.builder()
                  .deleteProjectWatches(accountConfig.getProjectWatches().keySet())
                  .updateProjectWatches(projectWatches)
                  .build());
          accountConfig.commit(md);
        }
      }
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    } catch (IOException | ConfigInvalidException ex) {
      throw new OrmException(ex);
    }
  }

  @AutoValue
  abstract static class ProjectWatch {
    abstract Project.NameKey project();

    abstract @Nullable String filter();

    abstract boolean notifyAbandonedChanges();

    abstract boolean notifyAllComments();

    abstract boolean notifyNewChanges();

    abstract boolean notifyNewPatchSets();

    abstract boolean notifySubmittedChanges();

    static Builder builder() {
      return new AutoValue_Schema_139_ProjectWatch.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder project(Project.NameKey project);

      abstract Builder filter(@Nullable String filter);

      abstract Builder notifyAbandonedChanges(boolean notifyAbandonedChanges);

      abstract Builder notifyAllComments(boolean notifyAllComments);

      abstract Builder notifyNewChanges(boolean notifyNewChanges);

      abstract Builder notifyNewPatchSets(boolean notifyNewPatchSets);

      abstract Builder notifySubmittedChanges(boolean notifySubmittedChanges);

      abstract ProjectWatch build();
    }
  }

  private static boolean toBoolean(String v) {
    Preconditions.checkState(!Strings.isNullOrEmpty(v));
    return v.equals("Y");
  }
}

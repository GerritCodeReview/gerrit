// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.DISABLE_REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.GerritServerIdProvider;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.AuditLogFormatter;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Migrate groups from ReviewDb to NoteDb. */
public class Schema_167 extends SchemaVersion {
  private static final Logger log = LoggerFactory.getLogger(Schema_167.class);

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final Config gerritConfig;
  private final SitePaths sitePaths;
  private final PersonIdent serverIdent;
  private final SystemGroupBackend systemGroupBackend;

  @Inject
  protected Schema_167(
      Provider<Schema_166> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritServerConfig Config gerritConfig,
      SitePaths sitePaths,
      @GerritPersonIdent PersonIdent serverIdent,
      SystemGroupBackend systemGroupBackend) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.gerritConfig = gerritConfig;
    this.sitePaths = sitePaths;
    this.serverIdent = serverIdent;
    this.systemGroupBackend = systemGroupBackend;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    if (gerritConfig.getBoolean(SECTION_NOTE_DB, GROUPS.key(), DISABLE_REVIEW_DB, false)) {
      // Groups in ReviewDb have already been disabled, nothing to do.
      return;
    }

    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      List<GroupReference> allGroupReferences = readGroupReferencesFromReviewDb(db);

      BatchRefUpdate batchRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();
      writeAllGroupNamesToNoteDb(allUsersRepo, allGroupReferences, batchRefUpdate);

      GroupRebuilder groupRebuilder = createGroupRebuilder(db, allUsersRepo);
      for (GroupReference groupReference : allGroupReferences) {
        migrateOneGroupToNoteDb(
            db, allUsersRepo, groupRebuilder, groupReference.getUUID(), batchRefUpdate);
      }

      RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException(
          String.format("Failed to migrate groups to NoteDb for %s", allUsersName.get()), e);
    }
  }

  private List<GroupReference> readGroupReferencesFromReviewDb(ReviewDb db) throws SQLException {
    try (Statement stmt = ReviewDbWrapper.unwrapJbdcSchema(db).getConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT group_uuid, name FROM account_groups")) {
      List<GroupReference> allGroupReferences = new ArrayList<>();
      while (rs.next()) {
        AccountGroup.UUID groupUuid = new AccountGroup.UUID(rs.getString(1));
        String groupName = rs.getString(2);
        allGroupReferences.add(new GroupReference(groupUuid, groupName));
      }
      return allGroupReferences;
    }
  }

  private void writeAllGroupNamesToNoteDb(
      Repository allUsersRepo,
      List<GroupReference> allGroupReferences,
      BatchRefUpdate batchRefUpdate)
      throws IOException {
    try (ObjectInserter inserter = allUsersRepo.newObjectInserter()) {
      GroupNameNotes.updateAllGroups(
          allUsersRepo, inserter, batchRefUpdate, allGroupReferences, serverIdent);
      inserter.flush();
    }
  }

  private GroupRebuilder createGroupRebuilder(ReviewDb db, Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    AuditLogFormatter auditLogFormatter =
        createAuditLogFormatter(db, allUsersRepo, gerritConfig, sitePaths);
    return new GroupRebuilder(serverIdent, allUsersName, auditLogFormatter);
  }

  private AuditLogFormatter createAuditLogFormatter(
      ReviewDb db, Repository allUsersRepo, Config gerritConfig, SitePaths sitePaths)
      throws IOException, ConfigInvalidException {
    String serverId = new GerritServerIdProvider(gerritConfig, sitePaths).get();
    SimpleInMemoryAccountCache accountCache = new SimpleInMemoryAccountCache(allUsersRepo);
    SimpleInMemoryGroupCache groupCache = new SimpleInMemoryGroupCache(db);
    return AuditLogFormatter.create(
        accountCache::get,
        uuid -> {
          if (systemGroupBackend.handles(uuid)) {
            return Optional.ofNullable(systemGroupBackend.get(uuid));
          }
          return groupCache.get(uuid);
        },
        serverId);
  }

  private static void migrateOneGroupToNoteDb(
      ReviewDb db,
      Repository allUsersRepo,
      GroupRebuilder rebuilder,
      AccountGroup.UUID uuid,
      BatchRefUpdate batchRefUpdate)
      throws ConfigInvalidException, IOException, OrmException {
    GroupBundle reviewDbBundle = GroupBundle.Factory.fromReviewDb(db, uuid);
    RefUpdateUtil.deleteChecked(allUsersRepo, RefNames.refsGroups(uuid));
    rebuilder.rebuild(allUsersRepo, reviewDbBundle, batchRefUpdate);
  }

  // The regular account cache isn't available during init. -> Use a simple replacement which tries
  // to load every account only once from disk.
  private static class SimpleInMemoryAccountCache {
    private final Repository allUsersRepo;
    private Map<Account.Id, Optional<Account>> accounts = new HashMap<>();

    public SimpleInMemoryAccountCache(Repository allUsersRepo) {
      this.allUsersRepo = allUsersRepo;
    }

    public Optional<Account> get(Account.Id accountId) {
      accounts.computeIfAbsent(accountId, this::load);
      return accounts.get(accountId);
    }

    private Optional<Account> load(Account.Id accountId) {
      try {
        AccountConfig accountConfig = new AccountConfig(accountId, allUsersRepo).load();
        return accountConfig.getLoadedAccount();
      } catch (IOException | ConfigInvalidException ignored) {
        log.warn(
            "Failed to load account {}."
                + " Cannot get account name for group audit log commit messages.",
            accountId.get(),
            ignored);
        return Optional.empty();
      }
    }
  }

  // The regular GroupBackends (especially external GroupBackends) and our internal group cache
  // aren't available during init. -> Use a simple replacement which tries to look up only internal
  // groups and which loads every internal group only once from disc. (There's no way we can look up
  // external groups during init. As we need those groups only for cosmetic aspects in
  // AuditLogFormatter, it's safe to exclude them.)
  private static class SimpleInMemoryGroupCache {
    private final ReviewDb db;
    private Map<AccountGroup.UUID, Optional<GroupDescription.Basic>> groups = new HashMap<>();

    public SimpleInMemoryGroupCache(ReviewDb db) {
      this.db = db;
    }

    public Optional<GroupDescription.Basic> get(AccountGroup.UUID groupUuid) {
      groups.computeIfAbsent(groupUuid, this::load);
      return groups.get(groupUuid);
    }

    private Optional<GroupDescription.Basic> load(AccountGroup.UUID groupUuid) {
      if (!AccountGroup.isInternalGroup(groupUuid)) {
        return Optional.empty();
      }

      List<GroupDescription.Basic> groupDescriptions = getGroupDescriptions(groupUuid);
      if (groupDescriptions.size() == 1) {
        return Optional.of(Iterables.getOnlyElement(groupDescriptions));
      }
      return Optional.empty();
    }

    private List<GroupDescription.Basic> getGroupDescriptions(AccountGroup.UUID groupUuid) {
      try (Statement stmt = ReviewDbWrapper.unwrapJbdcSchema(db).getConnection().createStatement();
          ResultSet rs =
              stmt.executeQuery(
                  "SELECT name FROM account_groups where group_uuid = '" + groupUuid + "'")) {
        List<GroupDescription.Basic> groupDescriptions = new ArrayList<>();
        while (rs.next()) {
          String groupName = rs.getString(1);
          groupDescriptions.add(toGroupDescription(groupUuid, groupName));
        }
        return groupDescriptions;
      } catch (SQLException ignored) {
        log.warn(
            "Failed to load group {}."
                + " Cannot get group name for group audit log commit messages.",
            groupUuid.get(),
            ignored);
        return ImmutableList.of();
      }
    }

    private static GroupDescription.Basic toGroupDescription(
        AccountGroup.UUID groupUuid, String groupName) {
      return new GroupDescription.Basic() {
        @Override
        public AccountGroup.UUID getGroupUUID() {
          return groupUuid;
        }

        @Override
        public String getName() {
          return groupName;
        }

        @Nullable
        @Override
        public String getEmailAddress() {
          return null;
        }

        @Nullable
        @Override
        public String getUrl() {
          return null;
        }
      };
    }
  }
}

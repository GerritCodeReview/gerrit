// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.elasticsearch.ElasticIndexModule;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.SetInactiveFlag;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdUpsertPreprocessor;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.audit.AuditModule;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.db.GroupDbModule;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.sshd.SshKeyCacheImpl;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

/**
 * Lists accounts with usernames that are only different in their capitalization, e.g. johndoe and
 * JohnDoe.
 */
public class FindDuplicateUsernames extends SiteProgram {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Option(name = "--delete", usage = "Show option to delete duplicates.")
  private boolean delete;

  private ConsoleUI ui;

  private Injector dbInjector;
  private Injector sysInjector;
  private Injector cfgInjector;

  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private ExternalIdNotes.FactoryNoReindex externalIdNotesFactory;
  @Inject private ExternalIds externalIds;
  @Inject private SetInactiveFlag setInactiveFlag;
  @Inject @ServerInitiated protected Provider<AccountsUpdate> accountsUpdate;
  @Inject private AccountManager accountManager;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector();
    cfgInjector = dbInjector.createChildInjector();

    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.getInstance(PluginGuiceEnvironment.class).setDbCfgInjector(dbInjector, cfgInjector);
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    sysInjector.injectMembers(this);

    try {
      ui = ConsoleUI.getInstance(false);

      try (Repository repo = repoManager.openRepository(allUsersName)) {
        ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
        ImmutableSet<ExternalId> extIds = extIdNotes.all();

        Map<String, List<ExternalId>> duplicateExtIds =
            extIds.stream()
                .collect(Collectors.groupingBy(extId -> getLowerCaseKey(extId)))
                .entrySet()
                .stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (duplicateExtIds.isEmpty()) {
          ui.message("\n\nNo duplicated usernames found.\n");
          return 0;
        }

        if (delete) {
          ui.message(
              "\n\n=====================================================================\n\n"
                  + "For each set of duplicated usernames, please select accounts to"
                  + " delete until only one account is left. An account can be selected"
                  + " by typing its account ID.\nUse `skip` to skip the decision and `cancel`"
                  + " to additionally revert decisions for the current set of accounts.\n"
                  + "`?` can be used to show all options."
                  + "\n\n=====================================================================\n\n");
        }

        ui.message("\n\nDuplicated usernames found:\n\n");
        ArrayList<ExternalId> toDelete = new ArrayList<>();

        for (Map.Entry<String, List<ExternalId>> dupExtid : duplicateExtIds.entrySet()) {
          Map<String, ExternalId> ids =
              dupExtid.getValue().stream()
                  .collect(Collectors.toMap(e -> e.accountId().toString(), e -> e));
          HashSet<String> answers = new HashSet<>();
          for (Map.Entry<String, ExternalId> entry : ids.entrySet()) {
            ui.message("\t* %s (%s)\n", entry.getValue().key().id(), entry.getKey());
            answers.add(entry.getKey());
          }

          if (delete) {
            toDelete.addAll(
                getAccountsToDelete(answers).stream().map(id -> ids.get(id)).collect(toList()));
          }
          ui.message("\n");
        }

        if (delete && !toDelete.isEmpty() && getConfirmationForDeletion(toDelete)) {
          for (ExternalId id : toDelete) {
            deleteAccount(id.accountId());
          }
        }
      }
    } finally {
      sysManager.stop();
      dbManager.stop();
    }

    return 0;
  }

  private Injector createSysInjector() {
    List<Module> modules = new ArrayList<>();
    Module indexModule;
    IndexType indexType = IndexModule.getIndexType(dbInjector);
    if (indexType.isLucene()) {
      indexModule = LuceneIndexModule.latestVersion(false);
    } else if (indexType.isElasticsearch()) {
      indexModule = ElasticIndexModule.latestVersion(false);
    } else {
      throw new IllegalStateException("unsupported index.type = " + indexType);
    }
    modules.add(indexModule);
    modules.add(SshKeyCacheImpl.module());
    modules.add(new AuditModule());
    modules.add(new GroupDbModule());
    modules.add(new BatchProgramModule(dbInjector));
    modules.add(
        new FactoryModule() {
          @Override
          protected void configure() {
            bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
            factory(MetaDataUpdate.InternalFactory.class);
            factory(AccountsUpdate.Factory.class);
            factory(GroupsUpdate.Factory.class);
            factory(ChangeResource.Factory.class);
            factory(VersionedAuthorizedKeys.Factory.class);
            DynamicMap.mapOf(binder(), ExternalIdUpsertPreprocessor.class);
          }

          @Provides
          @ServerInitiated
          AccountsUpdate provideServerInitiatedAccountsUpdate(
              AccountsUpdate.Factory accountsUpdateFactory,
              ExternalIdNotes.Factory extIdNotesFactory) {
            return accountsUpdateFactory.createWithServerIdent(extIdNotesFactory);
          }
        });

    return dbInjector.createChildInjector(modules);
  }

  private List<String> getAccountsToDelete(Set<String> answers) {
    answers.add("skip");
    answers.add("cancel");
    List<String> toDelete = new ArrayList<>();
    while (answers.size() > 3) {
      String answer = ui.readString("skip", answers, "\nWhich of the account should be deleted");
      if (answer.equals("skip")) {
        return toDelete;
      } else if (answer.equals("cancel")) {
        return new ArrayList<>();
      }
      toDelete.add(answer);
      answers.remove(answer);
    }
    return toDelete;
  }

  private boolean getConfirmationForDeletion(ArrayList<ExternalId> toDelete) {
    ui.message("\nThe following accounts are marked for deletion: \n\n");
    for (ExternalId extId : toDelete) {
      ui.message("\t* %s (%s)\n", extId.key().id(), extId.accountId());
    }
    return ui.yesno(false, "\nDo you want to continue with deleting these accounts?");
  }

  private void deleteAccount(Account.Id accountId) {
    try {
      deactivateAccount(accountId);
    } catch (RestApiException | IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log("Failed to deactivate account. Will not delete account.");
    }
    try {
      deleteExternalIds(accountId);
    } catch (IOException | ConfigInvalidException | AccountException e) {
      logger.atSevere().withCause(e).log("Failed to delete the account's external IDs.");
    }
  }

  private void deactivateAccount(Account.Id accountId)
      throws RestApiException, IOException, ConfigInvalidException {
    try {
      setInactiveFlag.deactivate(accountId);
    } catch (ResourceConflictException e) {
      logger.atInfo().log("Account %s already inactive.", accountId);
    }
    logger.atInfo().log("Account %s is now inactive.", accountId);
  }

  private void deleteExternalIds(Account.Id accountId)
      throws IOException, ConfigInvalidException, AccountException {
    Set<ExternalId.Key> ids =
        externalIds.byAccount(accountId).stream().map(e -> e.key()).collect(toSet());
    if (ids.isEmpty()) {
      logger.atInfo().log("Account %s has no external Ids", accountId);
      return;
    }

    accountManager.unlink(accountId, ids);
    logger.atInfo().log("All external IDs of Account %s have been deleted", accountId);
  }

  private String getLowerCaseKey(ExternalId extId) {
    return extId.key().get().toLowerCase(Locale.US);
  }
}

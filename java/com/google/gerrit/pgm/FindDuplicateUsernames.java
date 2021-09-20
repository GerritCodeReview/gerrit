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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.elasticsearch.ElasticIndexModule;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
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
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.restapi.account.DeleteActive;
import com.google.gerrit.server.restapi.account.DeleteExternalIds;
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
 * Changes the case sensitivity of `username:` and `gerrit:` external IDs by recomputing the SHA-1
 * sums used as note names.
 */
public class FindDuplicateUsernames extends SiteProgram {

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
  @Inject private DeleteExternalIds deleteExternalIds;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private DeleteActive deleteActive;
  @Inject @ServerInitiated protected Provider<AccountsUpdate> accountsUpdate;

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

        ui.message("\n\nDuplicated usernames found:\n");

        for (Map.Entry<String, List<ExternalId>> dupExtid : duplicateExtIds.entrySet()) {
          ui.message("\n-----------------------------------------------------\n");
          Map<String, ExternalId> ids =
              dupExtid.getValue().stream().collect(Collectors.toMap(e -> e.key().id(), e -> e));
          HashSet<String> answers = new HashSet<>();
          for (Map.Entry<String, ExternalId> entry : ids.entrySet()) {
            ui.message("\t- %s (%s)\n", entry.getKey(), entry.getValue().accountId());
            answers.add(entry.getKey());
          }

          if (delete) {
            List<String> answer = getAccountsToDelete(answers);
            for (String id : answer) {
              deleteAccount(ids.get(id));
            }
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
    //    modules.add(new com.google.gerrit.server.restapi.account.Module());
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
    while (answers.size() >= 3) {
      String answer = ui.readString("Which of the account should be deleted", answers, null);
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

  private void deleteAccount(ExternalId externalId)
      throws IOException, RestApiException, ConfigInvalidException, PermissionBackendException {
    AccountResource rsrc = new AccountResource(userFactory.create(externalId.accountId()));
    deleteActive.apply(rsrc, null);

    List<String> ids =
        externalIds.byAccount(externalId.accountId()).stream()
            .map(e -> e.key().get())
            .collect(toList());
    if (ids.isEmpty()) {
      throw new ResourceNotFoundException("Account has no external Ids");
    }
    deleteExternalIds.apply(rsrc, ids);
  }

  private String getLowerCaseKey(ExternalId extId) {
    return extId.key().get().toLowerCase(Locale.US);
  }
}

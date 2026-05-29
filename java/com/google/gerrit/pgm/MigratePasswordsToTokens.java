// Copyright (C) 2025 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.init.VersionedAuthTokensOnInit;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InstallAllPlugins;
import com.google.gerrit.pgm.init.api.InstallPlugins;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.DefaultRefLogIdentityProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AuthTokenCache;
import com.google.gerrit.server.account.AuthTokenModule;
import com.google.gerrit.server.account.PasswordMigrator;
import com.google.gerrit.server.account.VersionedAuthTokens;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdCacheImpl;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNoteDbReadStorageModule;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNoteDbWriteStorageModule;
import com.google.gerrit.server.account.storage.notedb.AccountNoteDbReadStorageModule;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.WorkQueue.WorkQueueModule;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.index.project.ProjectIndexerImpl;
import com.google.gerrit.server.notedb.NoteDbDraftCommentsModule;
import com.google.gerrit.server.notedb.NoteDbStarredChangesModule;
import com.google.gerrit.server.project.DefaultLockManager;
import com.google.gerrit.server.project.LockManager;
import com.google.gerrit.server.restapi.account.CreateToken;
import com.google.gerrit.server.schema.NoteDbSchemaVersionCheck;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.kohsuke.args4j.Option;

/** Converts HTTP passwords for all accounts to tokens */
public class MigratePasswordsToTokens extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();

  private Optional<Instant> lifetime = Optional.empty();

  @Inject private AuthConfig authConfig;
  @Inject private PasswordMigrator.Factory passwordMigratorFactory;

  @Option(name = "--lifetime", usage = "The lifetime of migrated tokens.")
  public void setDefaultLifetime(String value) throws BadRequestException {
    lifetime = CreateToken.getExpirationInstant(value, Optional.empty());
  }

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    ConsoleUI ui = ConsoleUI.getInstance();

    Injector dbInjector = createDbInjector();
    manager.add(dbInjector, dbInjector.createChildInjector(NoteDbSchemaVersionCheck.module()));
    manager.start();
    dbInjector
        .createChildInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
                bind(ConsoleUI.class).toInstance(ui);
                bind(Boolean.class)
                    .annotatedWith(InstallAllPlugins.class)
                    .toInstance(Boolean.FALSE);
                bind(new TypeLiteral<List<String>>() {})
                    .annotatedWith(InstallPlugins.class)
                    .toInstance(new ArrayList<>());
                bind(LockManager.class).toInstance(new DefaultLockManager());

                factory(PasswordMigrator.Factory.class);
                factory(MetaDataUpdate.InternalFactory.class);
                factory(VersionedAuthTokens.Factory.class);
                factory(VersionedAuthTokensOnInit.Factory.class);
                factory(Section.Factory.class);
                factory(MultiProgressMonitor.Factory.class);
                factory(ProjectIndexerImpl.Factory.class);
                factory(ChangeResource.Factory.class);
                bind(IdentifiedUser.GenericFactory.class);

                install(new ExternalIdNoteDbReadStorageModule());
                install(new ExternalIdNoteDbWriteStorageModule());
                install(new ExternalIdCacheImpl.ExternalIdCacheModule());
                install(new ExternalIdCacheImpl.ExternalIdCacheBindingModule());
                install(new DefaultRefLogIdentityProvider.Module());
                install(new BatchProgramModule(dbInjector, ImmutableSet.of()));
                install(LuceneIndexModule.latestVersion(false, AutoFlush.ENABLED));
                install(new AccountNoteDbReadStorageModule());
                install(new NoteDbStarredChangesModule());
                install(new NoteDbDraftCommentsModule());
                install(new WorkQueueModule());
                install(AuthTokenCache.module());
                install(new AuthTokenModule());
              }
            })
        .injectMembers(this);

    Optional<Duration> maxTokenLifetime = authConfig.getMaxAuthTokenLifetime();
    if (maxTokenLifetime.isPresent()) {
      if (lifetime.isEmpty()
          || lifetime.get().isAfter(Instant.now().plus(maxTokenLifetime.get()))) {
        throw new BadRequestException(
            "A lifetime shorter than the maximum allowed lifetime has to be provided.");
      }
    }

    passwordMigratorFactory.create(lifetime).run();

    manager.stop();
    return 0;
  }
}

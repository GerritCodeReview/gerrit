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

package com.google.gerrit.pgm;

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.gpg.SubkeyToMasterKeyCache;
import com.google.gerrit.gpg.SubkeyToMasterKeyCacheImpl;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.cache.h2.H2CacheModule;
import com.google.gerrit.server.cache.mem.DefaultMemoryCacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;

/** Buid GPG subkey cache */
public class GpgBuildSubkeyMasterKeyCache extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();

  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private SubkeyToMasterKeyCache gpgSubkeyMasterCache;

  @Override
  public int run() throws Exception {
    Injector dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector, dbInjector.createChildInjector(SchemaVersionCheck.module()));
    manager.start();
    dbInjector
        .createChildInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
                bind(new TypeLiteral<DynamicMap<Cache<?, ?>>>() {})
                    .toInstance(DynamicMap.<Cache<?, ?>>emptyMap());
                install(new DefaultMemoryCacheModule());
                install(new H2CacheModule());
                install(SubkeyToMasterKeyCacheImpl.module());
              }
            })
        .injectMembers(this);

    monitor.beginTask("Populate GPG subkey cache", 0);

    try (Repository repo = repoManager.openRepository(allUsersName);
        PublicKeyStore store = new PublicKeyStore(repo, gpgSubkeyMasterCache)) {
      store.rebuildSubkeyCache(monitor);
    }

    monitor.endTask();
    manager.stop();
    return 0;
  }
}

// Copyright (C) 2013 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.pgm;

import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.lucene.LuceneChangeIndex;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;

import java.util.List;

public class Reindex extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private Injector dbInjector;
  private Injector sysInjector;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(SINGLE_USER);
    sysInjector = createSysInjector();
    manager.add(dbInjector);
    manager.add(sysInjector);
    manager.start();

    SchemaFactory<ReviewDb> schema = dbInjector.getInstance(
        Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
    ReviewDb db = schema.open();
    Provider<ReviewDb> dbProvider = Providers.of(db);
    PatchListCache plc = sysInjector.getInstance(PatchListCache.class);
    LuceneChangeIndex index = sysInjector.getInstance(LuceneChangeIndex.class);

    index.getWriter().deleteAll();
    int i = 0;
    for (Change change : db.changes().all()) {
      ChangeData cd = new ChangeData(change);
      cd.fillIndexFields(dbProvider, plc);
      index.insert(cd);
      i++;
    }
    index.getWriter().commit();
    System.out.println("Reindexed " + i + " changes");

    manager.stop();
    return 0;
  }

  private Injector createSysInjector() {
    List<Module> modules = Lists.newArrayList();
    modules.add(PatchListCacheImpl.module());
    modules.add(LuceneChangeIndex.module());
    modules.add(new LifecycleModule() {
      @SuppressWarnings("rawtypes")
      @Override
      protected void configure() {
        // Plugins are not loaded and we're just running through each change
        // once, so don't worry about cache removal.
        bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {})
            .toInstance(DynamicSet.<CacheRemovalListener> emptySet());
        install(new DefaultCacheFactory.Module());
      }
    });
    return dbInjector.createChildInjector(modules);
  }
}

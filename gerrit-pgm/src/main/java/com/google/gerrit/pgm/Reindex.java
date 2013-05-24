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

import static com.google.gerrit.lucene.IndexVersionCheck.SCHEMA_VERSIONS;
import static com.google.gerrit.lucene.IndexVersionCheck.gerritIndexConfig;
import static com.google.gerrit.lucene.LuceneChangeIndex.LUCENE_VERSION;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Reindex extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private final AtomicReference<ReviewDb> dbRef =
      new AtomicReference<ReviewDb>();
  private Injector dbInjector;
  private Injector sysInjector;
  private SitePaths sitePaths;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(SINGLE_USER);
    if (!LuceneIndexModule.isEnabled(dbInjector)) {
      throw die("Secondary index not enabled");
    }

    sitePaths = dbInjector.getInstance(SitePaths.class);
    deleteAll();

    sysInjector = createSysInjector();
    manager.add(dbInjector);
    manager.add(sysInjector);
    manager.start();

    SchemaFactory<ReviewDb> schema = dbInjector.getInstance(
        Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
    ReviewDb db = schema.open();
    dbRef.set(db);

    ChangeIndexer indexer = sysInjector.getInstance(ChangeIndexer.class);

    Stopwatch sw = new Stopwatch().start();
    int i = 0;
    for (Change change : db.changes().all()) {
      indexer.index(change).get();
      i++;
    }
    double elapsed = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format("Reindexed %d changes in %.02fms", i, elapsed);
    writeVersion();

    manager.stop();
    return 0;
  }

  private Injector createSysInjector() {
    List<Module> modules = Lists.newArrayList();
    modules.add(PatchListCacheImpl.module());
    modules.add(new LuceneIndexModule(false));
    modules.add(new AbstractModule() {
      @SuppressWarnings("rawtypes")
      @Override
      protected void configure() {
        bind(ReviewDb.class).toProvider(new Provider<ReviewDb>() {
          @Override
          public ReviewDb get() {
            return dbRef.get();
          }
        });
        // Plugins are not loaded and we're just running through each change
        // once, so don't worry about cache removal.
        bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {})
            .toInstance(DynamicSet.<CacheRemovalListener> emptySet());
        install(new DefaultCacheFactory.Module());
      }
    });
    return dbInjector.createChildInjector(modules);
  }

  private void deleteAll() throws IOException {
    for (String index : SCHEMA_VERSIONS.keySet()) {
      File file = new File(sitePaths.index_dir, index);
      if (file.exists()) {
        Directory dir = FSDirectory.open(file);
        try {
          for (String name : dir.listAll()) {
            dir.deleteFile(name);
          }
        } finally {
          dir.close();
        }
      }
    }
  }

  private void writeVersion() throws IOException, ConfigInvalidException {
    FileBasedConfig cfg =
        new FileBasedConfig(gerritIndexConfig(sitePaths), FS.detect());
    cfg.load();

    for (Map.Entry<String, Integer> e : SCHEMA_VERSIONS.entrySet()) {
      cfg.setInt("index", e.getKey(), "schemaVersion", e.getValue());
    }
    cfg.setEnum("lucene", null, "version", LUCENE_VERSION);
    cfg.save();
  }
}

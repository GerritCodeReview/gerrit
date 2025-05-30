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
// limitations under the License.

package com.google.gerrit.pgm;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Die;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.index.SiteIndexer;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.LibModuleLoader;
import com.google.gerrit.server.ModuleOverloader;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdCacheImpl;
import com.google.gerrit.server.account.storage.notedb.AccountNoteDbReadStorageModule;
import com.google.gerrit.server.account.storage.notedb.AccountNoteDbWriteStorageModule;
import com.google.gerrit.server.cache.CacheDisplay;
import com.google.gerrit.server.cache.CacheInfoFactory;
import com.google.gerrit.server.cache.h2.CacheOptions;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue.WorkQueueModule;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.index.options.IsFirstInsertForEntry;
import com.google.gerrit.server.notedb.NoteDbDraftCommentsModule;
import com.google.gerrit.server.notedb.NoteDbStarredChangesModule;
import com.google.gerrit.server.notedb.RepoSequence.RepoSequenceModule;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.util.ReplicaUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.kohsuke.args4j.Option;

public class Reindex extends SiteProgram {
  @Option(
      name = "--threads",
      usage = "Number of threads to use for indexing. Default is index.batchThreads from config.")
  private int threads = 0;

  @Option(
      name = "--changes-schema-version",
      usage = "Schema version to reindex, for changes; default is most recent version")
  private Integer changesVersion;

  @Option(name = "--verbose", usage = "Output debug information for each change")
  private boolean verbose;

  @Option(name = "--list", usage = "List supported indices and exit")
  private boolean list;

  @Option(name = "--index", usage = "Only reindex specified indices")
  private List<String> indices = new ArrayList<>();

  @Option(name = "--show-cache-stats", usage = "Show cache statistics at the end.")
  private boolean showCacheStats;

  @Option(name = "--build-bloom-filter", usage = "Build bloom filter for H2 disk caches.")
  private boolean buildBloomFilter;

  @Option(
      name = "--read-only-disk-caches",
      aliases = {"--ro-disk-caches"},
      usage = "Don't update H2 disk caches.")
  private boolean readOnlyDiskCaches;

  private Boolean reuseExistingDocumentsOption;

  private Injector dbInjector;
  private Injector sysInjector;
  private Injector cfgInjector;
  private Config globalConfig;
  private boolean reuseExistingDocuments;

  @Inject private Collection<IndexDefinition<?, ?, ?>> indexDefs;
  @Inject private DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  private @Named("DiskCacheReadOnly") AtomicBoolean isDiskCacheReadOnly;

  @Option(name = "--reuse", usage = "Reindex only when existing index entry is stale")
  public void setReuseExistingDocuments(boolean value) {
    reuseExistingDocumentsOption = value;
  }

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector();
    cfgInjector = dbInjector.createChildInjector();
    globalConfig = dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    overrideConfig();
    reuseExistingDocuments =
        reuseExistingDocumentsOption != null
            ? reuseExistingDocumentsOption
            : globalConfig.getBoolean("index", null, "reuseExistingDocuments", false);
    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.getInstance(PluginGuiceEnvironment.class).setDbCfgInjector(dbInjector, cfgInjector);
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    sysInjector.injectMembers(this);
    checkIndicesOption();
    isDiskCacheReadOnly.set(readOnlyDiskCaches);

    try {
      boolean ok = list ? list() : reindex();
      if (showCacheStats) {
        printCacheStats();
      }
      return ok ? 0 : 1;
    } catch (Exception e) {
      throw die(e.getMessage(), e);
    } finally {
      sysManager.stop();
      dbManager.stop();
    }
  }

  private boolean list() {
    for (IndexDefinition<?, ?, ?> def : indexDefs) {
      System.out.format("%s\n", def.getName());
    }
    return true;
  }

  private boolean reindex() {
    boolean ok = true;
    for (IndexDefinition<?, ?, ?> def : indexDefs) {
      if (indices.isEmpty() || indices.contains(def.getName())) {
        ok &= reindex(def);
      }
    }
    return ok;
  }

  private void checkIndicesOption() throws Die {
    if (indices.isEmpty()) {
      return;
    }

    requireNonNull(indexDefs, "Called this method before injectMembers?");
    Set<String> valid = indexDefs.stream().map(IndexDefinition::getName).sorted().collect(toSet());
    Set<String> invalid = Sets.difference(Sets.newHashSet(indices), valid);
    if (invalid.isEmpty()) {
      return;
    }

    throw die(
        "invalid index name(s): " + new TreeSet<>(invalid) + " available indices are: " + valid);
  }

  private Injector createSysInjector() {
    ImmutableMap<String, Integer> versions =
        changesVersion != null
            ? ImmutableMap.of(ChangeSchemaDefinitions.INSTANCE.getName(), changesVersion)
            : ImmutableMap.of();
    boolean replica = ReplicaUtil.isReplica(globalConfig);
    List<Module> modules = new ArrayList<>();
    modules.add(new WorkQueueModule());

    Module indexModule;
    IndexType indexType = IndexModule.getIndexType(dbInjector);
    if (indexType.isLucene()) {
      indexModule =
          LuceneIndexModule.singleVersionWithExplicitVersions(
              versions, threads, replica, AutoFlush.DISABLED);
    } else if (indexType.isFake()) {
      // Use Reflection so that we can omit the fake index binary in production code. Test code does
      // compile the component in.
      try {
        Class<?> clazz = Class.forName("com.google.gerrit.index.testing.FakeIndexModule");
        Method m =
            clazz.getMethod(
                "singleVersionWithExplicitVersions", ImmutableMap.class, int.class, boolean.class);
        indexModule = (Module) m.invoke(null, versions, threads, replica);
      } catch (NoSuchMethodException
          | ClassNotFoundException
          | IllegalAccessException
          | InvocationTargetException e) {
        throw new IllegalStateException("can't create index", e);
      }
    } else {
      throw new IllegalStateException("unsupported index.type = " + indexType);
    }
    modules.add(indexModule);
    modules.add(
        new AbstractModule() {
          @Override
          protected void configure() {
            super.configure();
            OptionalBinder.newOptionalBinder(binder(), IsFirstInsertForEntry.class)
                .setBinding()
                .toInstance(
                    reuseExistingDocuments ? IsFirstInsertForEntry.NO : IsFirstInsertForEntry.YES);
          }
        });
    ImmutableSet<CacheOptions> options =
        buildBloomFilter ? ImmutableSet.of(CacheOptions.BUILD_BLOOM_FILTER) : ImmutableSet.of();
    modules.add(new BatchProgramModule(dbInjector, options));
    modules.add(
        new FactoryModule() {
          @Override
          protected void configure() {
            factory(ChangeResource.Factory.class);
          }
        });
    modules.add(new AccountNoteDbWriteStorageModule());
    modules.add(new AccountNoteDbReadStorageModule());
    modules.add(new ExternalIdCacheImpl.ExternalIdCacheModule());
    modules.add(new ExternalIdCacheImpl.ExternalIdCacheBindingModule());
    modules.add(new RepoSequenceModule());
    modules.add(new NoteDbDraftCommentsModule());
    modules.add(new NoteDbStarredChangesModule());

    return dbInjector.createChildInjector(
        ModuleOverloader.override(
            modules, LibModuleLoader.loadReindexModules(cfgInjector, versions, threads, replica)));
  }

  private void overrideConfig() {
    // Disable auto-commit for speed; committing will happen at the end of the process.
    if (IndexModule.getIndexType(dbInjector).isLucene()) {
      globalConfig.setLong("index", "changes_open", "commitWithin", -1);
      globalConfig.setLong("index", "changes_closed", "commitWithin", -1);
    }

    // Disable change cache.
    globalConfig.setLong("cache", "changes", "maximumWeight", 0);

    // Disable auto-reindexing if stale, since there are no concurrent writes to race with.
    globalConfig.setBoolean("index", null, "autoReindexIfStale", false);
  }

  private <K, V, I extends Index<K, V>> boolean reindex(IndexDefinition<K, V, I> def) {
    I index = def.getIndexCollection().getSearchIndex();
    requireNonNull(
        index, () -> String.format("no active search index configured for %s", def.getName()));
    index.markReady(false);

    if (!reuseExistingDocuments) {
      index.deleteAll();
    }

    SiteIndexer<K, V, I> siteIndexer = def.getSiteIndexer(reuseExistingDocuments);
    siteIndexer.setProgressOut(System.err);
    siteIndexer.setVerboseOut(verbose ? System.out : NullOutputStream.INSTANCE);
    SiteIndexer.Result result = siteIndexer.indexAll(index);
    int n = result.doneCount() + result.failedCount();
    double t = result.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format(
        "Reindexed %d documents in %s index in %.01fs (%.01f/s)\n", n, def.getName(), t, n / t);
    if (result.success()) {
      index.markReady(true);
    }
    System.out.format(
        "Index %s in version %d is %sready\n",
        def.getName(), index.getSchema().getVersion(), result.success() ? "" : "NOT ");

    return result.success();
  }

  private void printCacheStats() {
    try (Writer sw = new StringWriter()) {
      sw.write("Cache Statistics at the end of reindexing\n");
      new CacheDisplay(
              sw,
              StreamSupport.stream(cacheMap.spliterator(), false)
                  .map(e -> CacheInfoFactory.create(e.getExportName(), e.get()))
                  .collect(Collectors.toList()))
          .displayCaches();
      System.out.print(sw.toString());
    } catch (Exception e) {
      System.out.format("Error displaying the cache statistics\n" + e.getMessage());
    }
  }
}

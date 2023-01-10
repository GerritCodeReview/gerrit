// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.CacheRefreshExecutor;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.cache.serialize.ProtobufSerializer;
import com.google.gerrit.server.cache.serialize.entities.CachedProjectConfigSerializer;
import com.google.gerrit.server.config.AllProjectsConfigProvider;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

/**
 * Cache of project information, including access rights.
 *
 * <p>The data of a project is the project's project.config in refs/meta/config parsed out as an
 * immutable value. It's keyed purely by the refs/meta/config SHA-1. We also cache the same value
 * keyed by name. The latter mapping can become outdated, so data must be evicted explicitly.
 */
@Singleton
public class ProjectCacheImpl implements ProjectCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String CACHE_NAME = "projects";

  public static final String PERSISTED_CACHE_NAME = "persisted_projects";

  public static final String CACHE_LIST = "project_list";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        // We split the project cache into two parts for performance reasons:
        // 1) An in-memory part that has only the project name as key.
        // 2) A persisted part that has the name and revision as key.
        //
        // When loading dashboards or returning change query results we potentially
        // need to access hundreds of projects because each change could originate in
        // a different project and, through inheritance, require us to check even more
        // projects when evaluating permissions. It's not feasible to read the revision
        // of refs/meta/config from each of these repos as that would require opening
        // them all and reading their ref list or table.
        // At the same time, we want the persisted cache to be immutable and we want it
        // to be impossible that a value for a given key is out of date. We therefore
        // require a revision in the key. That is in line with the rest of the caches in
        // Gerrit.
        //
        // Splitting the cache into two chunks internally in this class allows us to retain
        // the existing performance guarantees of not requiring reads for the repo for values
        // cached in-memory but also to persist the cache which leads to a much improved
        // cold-start behavior and in-memory miss latency.
        cache(CACHE_NAME, Project.NameKey.class, CachedProjectConfig.class)
            .loader(InMemoryLoader.class)
            .refreshAfterWrite(Duration.ofMinutes(15))
            .expireAfterWrite(Duration.ofHours(1));

        persist(PERSISTED_CACHE_NAME, Cache.ProjectCacheKeyProto.class, CachedProjectConfig.class)
            .loader(PersistedLoader.class)
            .keySerializer(new ProtobufSerializer<>(Cache.ProjectCacheKeyProto.parser()))
            .valueSerializer(PersistedProjectConfigSerializer.INSTANCE)
            .diskLimit(1 << 30) // 1 GiB
            .version(4)
            .maximumWeight(0);

        cache(CACHE_LIST, ListKey.class, new TypeLiteral<ImmutableSortedSet<Project.NameKey>>() {})
            .maximumWeight(1)
            .loader(Lister.class);

        bind(ProjectCacheImpl.class);
        bind(ProjectCache.class).to(ProjectCacheImpl.class);

        install(
            new LifecycleModule() {
              @Override
              protected void configure() {
                listener().to(ProjectCacheWarmer.class);
              }
            });
        install(
            new LifecycleModule() {
              @Override
              protected void configure() {
                listener().to(PeriodicProjectListCacheWarmer.LifeCycle.class);
              }
            });
      }
    };
  }

  private final Config config;
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final LoadingCache<Project.NameKey, CachedProjectConfig> inMemoryProjectCache;
  private final LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list;
  private final Lock listLock;
  private final Provider<ProjectIndexer> indexer;
  private final Timer0 guessRelevantGroupsLatency;
  private final ProjectState.Factory projectStateFactory;

  @Inject
  ProjectCacheImpl(
      @GerritServerConfig Config config,
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      @Named(CACHE_NAME) LoadingCache<Project.NameKey, CachedProjectConfig> inMemoryProjectCache,
      @Named(CACHE_LIST) LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list,
      Provider<ProjectIndexer> indexer,
      MetricMaker metricMaker,
      ProjectState.Factory projectStateFactory) {
    this.config = config;
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.inMemoryProjectCache = inMemoryProjectCache;
    this.list = list;
    this.listLock = new ReentrantLock(true /* fair */);
    this.indexer = indexer;
    this.projectStateFactory = projectStateFactory;

    this.guessRelevantGroupsLatency =
        metricMaker.newTimer(
            "group/guess_relevant_groups_latency",
            new Description("Latency for guessing relevant groups")
                .setCumulative()
                .setUnit(Units.NANOSECONDS));
  }

  @Override
  public ProjectState getAllProjects() {
    return get(allProjectsName).orElseThrow(illegalState(allProjectsName));
  }

  @Override
  public ProjectState getAllUsers() {
    return get(allUsersName).orElseThrow(illegalState(allUsersName));
  }

  @Override
  public Optional<ProjectState> get(@Nullable Project.NameKey projectName) {
    if (projectName == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(inMemoryProjectCache.get(projectName)).map(projectStateFactory::create);
    } catch (ExecutionException e) {
      if ((e.getCause() instanceof RepositoryNotFoundException)) {
        logger.atFine().log("Cannot find project %s", projectName.get());
        return Optional.empty();
      }
      throw new StorageException(
          String.format("project state of project %s not available", projectName.get()), e);
    }
  }

  @Override
  public void evict(Project.NameKey p) {
    if (p != null) {
      logger.atFine().log("Evict project '%s'", p.get());
      inMemoryProjectCache.invalidate(p);
    }
  }

  @Override
  public void evictAndReindex(Project p) {
    evictAndReindex(p.getNameKey());
  }

  @Override
  public void evictAndReindex(Project.NameKey p) {
    evict(p);
    indexer.get().index(p);
  }

  @Override
  public void remove(Project p) {
    remove(p.getNameKey());
  }

  @Override
  public void remove(Project.NameKey name) {
    listLock.lock();
    try {
      list.put(
          ListKey.ALL,
          ImmutableSortedSet.copyOf(Sets.difference(list.get(ListKey.ALL), ImmutableSet.of(name))));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot list available projects");
    } finally {
      listLock.unlock();
    }
    evictAndReindex(name);
  }

  @Override
  public void onCreateProject(Project.NameKey newProjectName) throws IOException {
    listLock.lock();
    try {
      list.put(
          ListKey.ALL,
          ImmutableSortedSet.copyOf(
              Sets.union(list.get(ListKey.ALL), ImmutableSet.of(newProjectName))));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot list available projects");
    } finally {
      listLock.unlock();
    }
    indexer.get().index(newProjectName);
  }

  @Override
  public ImmutableSortedSet<Project.NameKey> all() {
    try {
      return list.get(ListKey.ALL);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot list available projects");
      return ImmutableSortedSet.of();
    }
  }

  @Override
  public void refreshProjectList() {
    list.refresh(ListKey.ALL);
  }

  @Override
  public Set<AccountGroup.UUID> guessRelevantGroupUUIDs() {
    try (Timer0.Context ignored = guessRelevantGroupsLatency.start()) {
      Stream<AccountGroup.UUID> configuredRelevantGroups =
          Arrays.stream(config.getStringList("groups", /* subsection= */ null, "relevantGroup"))
              .map(AccountGroup::uuid);

      Stream<AccountGroup.UUID> guessedRelevantGroups;
      ImmutableSortedSet<Project.NameKey> all = list.getIfPresent(ListKey.ALL);
      if (all != null) {
        guessedRelevantGroups =
            all.stream()
                .map(n -> inMemoryProjectCache.getIfPresent(n))
                .filter(Objects::nonNull)
                .flatMap(p -> p.getAllGroupUUIDs().stream())
                // getAllGroupUUIDs shouldn't really return null UUIDs, but harden
                // against them just in case there is a bug or corner case.
                .filter(id -> id != null && id.get() != null);
      } else {
        logger.atFine().log("project list not loaded, skip guessing groups");
        guessedRelevantGroups = Stream.empty();
      }

      Set<AccountGroup.UUID> relevantGroupUuids =
          Streams.concat(configuredRelevantGroups, guessedRelevantGroups).collect(toSet());
      logger.atFine().log("relevant group UUIDs: %s", relevantGroupUuids);
      return relevantGroupUuids;
    }
  }

  @Override
  public ImmutableSortedSet<Project.NameKey> byName(String pfx) {
    Project.NameKey start = Project.nameKey(pfx);
    Project.NameKey end = Project.nameKey(pfx + Character.MAX_VALUE);
    try {
      // Right endpoint is exclusive, but U+FFFF is a non-character so no project ends with it.
      return list.get(ListKey.ALL).subSet(start, end);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot look up projects for prefix %s", pfx);
      return ImmutableSortedSet.of();
    }
  }

  /**
   * Returns a {@code MurMur128} hash of the contents of {@code etc/All-Projects-project.config}.
   */
  public static byte[] allProjectsFileProjectConfigHash(Optional<StoredConfig> allProjectsConfig) {
    // Hash the contents of All-Projects-project.config
    // This is a way for administrators to orchestrate project.config changes across many Gerrit
    // instances.
    // When this file changes, we need to make sure we disregard persistently cached project
    // state.
    if (!allProjectsConfig.isPresent()) {
      // If the project.config file is not present, this is equal to an empty config file:
      return Hashing.murmur3_128().hashString("", UTF_8).asBytes();
    }
    try {
      allProjectsConfig.get().load();
    } catch (IOException | ConfigInvalidException e) {
      throw new IllegalStateException(e);
    }
    return Hashing.murmur3_128().hashString(allProjectsConfig.get().toText(), UTF_8).asBytes();
  }

  @Singleton
  static class InMemoryLoader extends CacheLoader<Project.NameKey, CachedProjectConfig> {
    private final LoadingCache<Cache.ProjectCacheKeyProto, CachedProjectConfig> persistedCache;
    private final GitRepositoryManager repoManager;
    private final ListeningExecutorService cacheRefreshExecutor;
    private final Counter2<String, Boolean> refreshCounter;
    private final AllProjectsName allProjectsName;
    private final AllProjectsConfigProvider allProjectsConfigProvider;

    @Inject
    InMemoryLoader(
        @Named(PERSISTED_CACHE_NAME)
            LoadingCache<Cache.ProjectCacheKeyProto, CachedProjectConfig> persistedCache,
        GitRepositoryManager repoManager,
        @CacheRefreshExecutor ListeningExecutorService cacheRefreshExecutor,
        MetricMaker metricMaker,
        AllProjectsName allProjectsName,
        AllProjectsConfigProvider allProjectsConfigProvider) {
      this.persistedCache = persistedCache;
      this.repoManager = repoManager;
      this.cacheRefreshExecutor = cacheRefreshExecutor;
      refreshCounter =
          metricMaker.newCounter(
              "caches/refresh_count",
              new Description(
                      "The number of refreshes per cache with an indicator if a reload was"
                          + " necessary.")
                  .setRate(),
              Field.ofString("cache", Metadata.Builder::className)
                  .description("The name of the cache.")
                  .build(),
              Field.ofBoolean("outdated", Metadata.Builder::outdated)
                  .description("Whether the cache entry was outdated on reload.")
                  .build());
      this.allProjectsName = allProjectsName;
      this.allProjectsConfigProvider = allProjectsConfigProvider;
    }

    @Override
    public CachedProjectConfig load(Project.NameKey key) throws IOException, ExecutionException {
      try (TraceTimer ignored =
              TraceContext.newTimer(
                  "Loading project from serialized cache",
                  Metadata.builder().projectName(key.get()).build());
          Repository git = repoManager.openRepository(key)) {
        Cache.ProjectCacheKeyProto.Builder keyProto =
            Cache.ProjectCacheKeyProto.newBuilder().setProject(key.get());
        Ref configRef = git.exactRef(RefNames.REFS_CONFIG);
        if (key.get().equals(allProjectsName.get())) {
          Optional<StoredConfig> allProjectsConfig = allProjectsConfigProvider.get(allProjectsName);
          byte[] fileHash = allProjectsFileProjectConfigHash(allProjectsConfig);
          keyProto.setGlobalConfigRevision(ByteString.copyFrom(fileHash));
        }
        if (configRef != null) {
          keyProto.setRevision(ObjectIdConverter.create().toByteString(configRef.getObjectId()));
        }
        return persistedCache.get(keyProto.build());
      }
    }

    @Override
    public ListenableFuture<CachedProjectConfig> reload(
        Project.NameKey key, CachedProjectConfig oldState) throws Exception {
      try (TraceTimer ignored =
          TraceContext.newTimer(
              "Reload project", Metadata.builder().projectName(key.get()).build())) {
        try (Repository git = repoManager.openRepository(key)) {
          Ref configRef = git.exactRef(RefNames.REFS_CONFIG);
          if (configRef != null && configRef.getObjectId().equals(oldState.getRevision().get())) {
            refreshCounter.increment(CACHE_NAME, false);
            return Futures.immediateFuture(oldState);
          }
        }

        // Repository is not thread safe, so we have to open it on the thread that does the loading.
        // Just invoke the loader on the other thread.
        refreshCounter.increment(CACHE_NAME, true);
        return cacheRefreshExecutor.submit(() -> load(key));
      }
    }
  }

  @Singleton
  static class PersistedLoader
      extends CacheLoader<Cache.ProjectCacheKeyProto, CachedProjectConfig> {
    private final GitRepositoryManager repoManager;
    private final ProjectConfig.Factory projectConfigFactory;

    @Inject
    PersistedLoader(GitRepositoryManager repoManager, ProjectConfig.Factory projectConfigFactory) {
      this.repoManager = repoManager;
      this.projectConfigFactory = projectConfigFactory;
    }

    @Override
    public CachedProjectConfig load(Cache.ProjectCacheKeyProto key) throws Exception {
      Project.NameKey nameKey = Project.nameKey(key.getProject());
      ObjectId revision =
          key.getRevision().isEmpty()
              ? null
              : ObjectIdConverter.create().fromByteString(key.getRevision());
      try (TraceTimer ignored =
          TraceContext.newTimer(
              "Loading project from repo", Metadata.builder().projectName(nameKey.get()).build())) {
        try (Repository git = repoManager.openRepository(nameKey)) {
          ProjectConfig cfg = projectConfigFactory.create(nameKey);
          cfg.load(git, revision);
          return cfg.getCacheable();
        }
      }
    }
  }

  private enum PersistedProjectConfigSerializer implements CacheSerializer<CachedProjectConfig> {
    INSTANCE;

    @Override
    public byte[] serialize(CachedProjectConfig value) {
      return Protos.toByteArray(CachedProjectConfigSerializer.serialize(value));
    }

    @Override
    public CachedProjectConfig deserialize(byte[] in) {
      return CachedProjectConfigSerializer.deserialize(
          Protos.parseUnchecked(Cache.CachedProjectConfigProto.parser(), in));
    }
  }

  static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {}
  }

  static class Lister extends CacheLoader<ListKey, ImmutableSortedSet<Project.NameKey>> {
    private final GitRepositoryManager mgr;

    @Inject
    Lister(GitRepositoryManager mgr) {
      this.mgr = mgr;
    }

    @Override
    public ImmutableSortedSet<Project.NameKey> load(ListKey key) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading project list")) {
        return ImmutableSortedSet.copyOf(mgr.list());
      }
    }
  }

  @VisibleForTesting
  public void evictAllByName() {
    inMemoryProjectCache.invalidateAll();
  }

  @VisibleForTesting
  public long sizeAllByName() {
    return inMemoryProjectCache.size();
  }
}

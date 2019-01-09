// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.index.RefState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritOptions;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Cache for the minimal information per change that we need to compute visibility. Used for ref
 * filtering.
 *
 * <p>This class is thread safe.
 */
@Singleton
public class ChangeRefCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String ID_CACHE = "change_refs";

  public static class Module extends CacheModule {
    @Override
    protected void configure() {
      cache(ID_CACHE, Key.class, new TypeLiteral<CachedChange>() {})
          .maximumWeight(10000)
          .loader(Loader.class);

      bind(ChangeRefCache.class);
    }
  }

  @AutoValue
  abstract static class Key {
    abstract Project.NameKey project();

    abstract Change.Id changeId();

    abstract ObjectId metaId();
  }

  @AutoValue
  abstract static class CachedChange {
    // Subset of fields in ChangeData, specifically fields needed to serve
    // VisibleRefFilter without touching the database. More can be added as
    // necessary.
    abstract Change change();

    @Nullable
    abstract ReviewerSet reviewers();
  }

  private final LoadingCache<Key, CachedChange> cache;
  private final ChangeData.Factory changeDataFactory;
  private final OneOffRequestContext requestContext;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GerritOptions gerritOptions;
  private final Config gerritConfig;
  private final Set<Project.NameKey> bootstrappedProjects;

  @Inject
  ChangeRefCache(
      @Named(ID_CACHE) LoadingCache<Key, CachedChange> cache,
      ChangeData.Factory changeDataFactory,
      OneOffRequestContext requestContext,
      Provider<InternalChangeQuery> queryProvider,
      GerritOptions gerritOptions,
      @GerritServerConfig Config gerritConfig) {
    this.cache = cache;
    this.changeDataFactory = changeDataFactory;
    this.requestContext = requestContext;
    this.queryProvider = queryProvider;
    this.gerritOptions = gerritOptions;
    this.gerritConfig = gerritConfig;
    this.bootstrappedProjects = new CopyOnWriteArraySet<>();
  }

  /**
   * Read changes from the cache.
   *
   * <p>Returned changes only include the {@code Change} object (with id, branch) and the reviewers.
   * There is no guarantee that additional fields are populated, although they can be.
   *
   * @param project project to read.
   * @param changeId change ID to read
   * @param metaId object ID of the meta branch to read. This is only used to ensure consistency. It
   *     does not allow for reading non-current meta versions.
   * @return change data
   * @throws IllegalArgumentException in case
   */
  public ChangeData getChangeData(Project.NameKey project, Change.Id changeId, ObjectId metaId) {
    Key key = new AutoValue_ChangeRefCache_Key(project, changeId, metaId);
    CachedChange cached = cache.getUnchecked(key);
    if (cached == null) {
      throw new IllegalArgumentException("no change found for key " + key);
    }
    ChangeData cd = changeDataFactory.create(cached.change());
    cd.setReviewers(cached.reviewers());
    return cd;
  }

  public void bootstrapIfNecessary(Project.NameKey project) {
    if (!gerritOptions.enableMasterFeatures()) {
      // Bootstrapping using the ChangeIndex is only supported on master in a master-slave replica.
      return;
    }
    if (gerritConfig.getInt("cache", ID_CACHE, "memoryLimit", -1) == 0) {
      // The cache is disabled, don't bother bootstrapping.
      return;
    }
    if (bootstrappedProjects.contains(project)) {
      // We have bootstrapped for this project before. If the cache is too small, we might have
      // evicted all entries by now. Don't bother about this though as we don't want to add the
      // complexity of checking for existing projects, since that might not be authoritative as well
      // since we could have already evicted the majority of the entries.
      return;
    }

    try (TraceTimer ignored =
            TraceContext.newTimer("bootstrapping ChangeRef cache for project " + project);
        ManualRequestContext ignored2 = requestContext.open()) {
      List<ChangeData> cds =
          queryProvider
              .get()
              .setRequestedFields(ChangeField.CHANGE, ChangeField.REVIEWER, ChangeField.REF_STATE)
              .byProject(project);
      for (ChangeData cd : cds) {
        Set<RefState> refStates = RefState.parseStates(cd.getRefStates()).get(project);
        Optional<RefState> refState =
            refStates
                .stream()
                .filter(r -> r.ref().equals(RefNames.changeMetaRef(cd.getId())))
                .findAny();
        if (!refState.isPresent()) {
          continue;
        }
        cache.put(
            new AutoValue_ChangeRefCache_Key(project, cd.change().getId(), refState.get().id()),
            new AutoValue_ChangeRefCache_CachedChange(cd.change(), cd.getReviewers()));
      }
      // Mark the project as bootstrapped. We could have bootstrapped it multiple times for
      // simultaneous requests. We accept this in favor of less thread synchronization and
      // complexity.
      bootstrappedProjects.add(project);
    } catch (OrmException e) {
      logger.atWarning().withCause(e).log(
          "unable to bootstrap ChangeRef cache for project " + project);
    }
  }

  static class Loader extends CacheLoader<Key, CachedChange> {
    private final ChangeNotes.Factory notesFactory;

    @Inject
    Loader(ChangeNotes.Factory notesFactory) {
      this.notesFactory = notesFactory;
    }

    @Override
    public CachedChange load(Key key) throws Exception {
      ChangeNotes notes = notesFactory.create(key.project(), key.changeId());
      if (notes.getMetaId().equals(key.metaId())) {
        return new AutoValue_ChangeRefCache_CachedChange(notes.getChange(), notes.getReviewers());
      }
      throw new NoSuchElementException("unable to load change");
    }
  }

  @VisibleForTesting
  public void resetBootstrappedProjects() {
    bootstrappedProjects.clear();
  }
}

// Copyright (C) 2009 The Android Open Source Project
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
//

package com.google.gerrit.server.patch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/** Provides a cached list of {@link PatchListEntry}. */
@Singleton
public class PatchListCacheImpl implements PatchListCache {
  static final String FILE_NAME = "diff";
  static final String INTRA_NAME = "diff_intraline";
  static final String DIFF_SUMMARY = "diff_summary";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        factory(PatchListLoader.Factory.class);
        // TODO(davido): Switch off using legacy cache backend, after fixing PatchListLoader
        // to be recursion free.
        persist(FILE_NAME, PatchListKey.class, PatchList.class, CacheBackend.GUAVA)
            .maximumWeight(10 << 20)
            .weigher(PatchListWeigher.class);

        factory(IntraLineLoader.Factory.class);
        persist(INTRA_NAME, IntraLineDiffKey.class, IntraLineDiff.class)
            .maximumWeight(10 << 20)
            .weigher(IntraLineWeigher.class);

        factory(DiffSummaryLoader.Factory.class);
        persist(DIFF_SUMMARY, DiffSummaryKey.class, DiffSummary.class)
            .maximumWeight(10 << 20)
            .weigher(DiffSummaryWeigher.class)
            .diskLimit(1 << 30);

        bind(PatchListCacheImpl.class);
        bind(PatchListCache.class).to(PatchListCacheImpl.class);
      }
    };
  }

  private final Cache<PatchListKey, PatchList> fileCache;
  private final Cache<IntraLineDiffKey, IntraLineDiff> intraCache;
  private final Cache<DiffSummaryKey, DiffSummary> diffSummaryCache;
  private final PatchListLoader.Factory fileLoaderFactory;
  private final IntraLineLoader.Factory intraLoaderFactory;
  private final DiffSummaryLoader.Factory diffSummaryLoaderFactory;
  private final boolean computeIntraline;

  @Inject
  PatchListCacheImpl(
      @Named(FILE_NAME) Cache<PatchListKey, PatchList> fileCache,
      @Named(INTRA_NAME) Cache<IntraLineDiffKey, IntraLineDiff> intraCache,
      @Named(DIFF_SUMMARY) Cache<DiffSummaryKey, DiffSummary> diffSummaryCache,
      PatchListLoader.Factory fileLoaderFactory,
      IntraLineLoader.Factory intraLoaderFactory,
      DiffSummaryLoader.Factory diffSummaryLoaderFactory,
      @GerritServerConfig Config cfg) {
    this.fileCache = fileCache;
    this.intraCache = intraCache;
    this.diffSummaryCache = diffSummaryCache;
    this.fileLoaderFactory = fileLoaderFactory;
    this.intraLoaderFactory = intraLoaderFactory;
    this.diffSummaryLoaderFactory = diffSummaryLoaderFactory;

    this.computeIntraline =
        cfg.getBoolean(
            "cache", INTRA_NAME, "enabled", cfg.getBoolean("cache", "diff", "intraline", true));
  }

  @Override
  public PatchList get(PatchListKey key, Project.NameKey project)
      throws PatchListNotAvailableException {
    try {
      PatchList pl = fileCache.get(key, fileLoaderFactory.create(key, project));
      if (pl instanceof LargeObjectTombstone) {
        throw new PatchListObjectTooLargeException(
            "Error computing " + key + ". Previous attempt failed with LargeObjectException");
      }
      return pl;
    } catch (ExecutionException e) {
      PatchListLoader.logger.atWarning().withCause(e).log("Error computing %s", key);
      throw new PatchListNotAvailableException(e);
    } catch (UncheckedExecutionException e) {
      if (e.getCause() instanceof LargeObjectException) {
        // Cache negative result so we don't need to redo expensive computations that would yield
        // the same result.
        fileCache.put(key, new LargeObjectTombstone());
        PatchListLoader.logger.atWarning().withCause(e).log("Error computing %s", key);
        throw new PatchListNotAvailableException(e);
      }
      throw e;
    }
  }

  @Override
  public PatchList get(Change change, PatchSet patchSet) throws PatchListNotAvailableException {
    return get(change, patchSet, null);
  }

  @Override
  public ObjectId getOldId(Change change, PatchSet patchSet, Integer parentNum)
      throws PatchListNotAvailableException {
    return get(change, patchSet, parentNum).getOldId();
  }

  private PatchList get(Change change, PatchSet patchSet, Integer parentNum)
      throws PatchListNotAvailableException {
    Project.NameKey project = change.getProject();
    ObjectId b = patchSet.commitId();
    if (parentNum != null) {
      return get(PatchListKey.againstParentNum(parentNum, b, Whitespace.IGNORE_NONE), project);
    }
    return get(PatchListKey.againstDefaultBase(b, Whitespace.IGNORE_NONE), project);
  }

  @Override
  public IntraLineDiff getIntraLineDiff(IntraLineDiffKey key, IntraLineDiffArgs args) {
    if (computeIntraline) {
      try {
        return intraCache.get(key, intraLoaderFactory.create(key, args));
      } catch (ExecutionException | LargeObjectException e) {
        IntraLineLoader.logger.atWarning().withCause(e).log("Error computing %s", key);
        return new IntraLineDiff(IntraLineDiff.Status.ERROR);
      }
    }
    return new IntraLineDiff(IntraLineDiff.Status.DISABLED);
  }

  @Override
  public DiffSummary getDiffSummary(DiffSummaryKey key, Project.NameKey project)
      throws PatchListNotAvailableException {
    try {
      return diffSummaryCache.get(key, diffSummaryLoaderFactory.create(key, project));
    } catch (ExecutionException e) {
      PatchListLoader.logger.atWarning().withCause(e).log("Error computing %s", key);
      throw new PatchListNotAvailableException(e);
    } catch (UncheckedExecutionException e) {
      if (e.getCause() instanceof LargeObjectException) {
        PatchListLoader.logger.atWarning().withCause(e).log("Error computing %s", key);
        throw new PatchListNotAvailableException(e);
      }
      throw e;
    }
  }

  /** Used to cache negative results in {@code fileCache}. */
  @VisibleForTesting
  public static class LargeObjectTombstone extends PatchList {
    private static final long serialVersionUID = 1L;

    @VisibleForTesting
    public LargeObjectTombstone() {
      // Initialize super class with valid values. We don't care about the inner state, but need to
      // pass valid values that don't break (de)serialization.
      super(
          null, ObjectId.zeroId(), false, ComparisonType.againstAutoMerge(), new PatchListEntry[0]);
    }
  }
}

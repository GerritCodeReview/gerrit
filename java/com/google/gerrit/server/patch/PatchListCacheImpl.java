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

import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Config;

/** Provides a cached list of {@link PatchListEntry}. */
@Singleton
public class PatchListCacheImpl implements PatchListCache {
  public static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String INTRA_NAME = "diff_intraline";
  static final String DIFF_SUMMARY = "diff_summary";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        factory(IntraLineLoader.Factory.class);
        persist(INTRA_NAME, IntraLineDiffKey.class, IntraLineDiff.class)
            .maximumWeight(10 << 20)
            .weigher(IntraLineWeigher.class)
            .version(1)
            .keySerializer(IntraLineDiffKey.Serializer.INSTANCE)
            .valueSerializer(IntraLineDiff.Serializer.INSTANCE);

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

  private final Cache<IntraLineDiffKey, IntraLineDiff> intraCache;
  private final Cache<DiffSummaryKey, DiffSummary> diffSummaryCache;
  private final IntraLineLoader.Factory intraLoaderFactory;
  private final DiffSummaryLoader.Factory diffSummaryLoaderFactory;
  private final boolean computeIntraline;

  @Inject
  PatchListCacheImpl(
      @Named(INTRA_NAME) Cache<IntraLineDiffKey, IntraLineDiff> intraCache,
      @Named(DIFF_SUMMARY) Cache<DiffSummaryKey, DiffSummary> diffSummaryCache,
      IntraLineLoader.Factory intraLoaderFactory,
      DiffSummaryLoader.Factory diffSummaryLoaderFactory,
      @GerritServerConfig Config cfg) {
    this.intraCache = intraCache;
    this.diffSummaryCache = diffSummaryCache;
    this.intraLoaderFactory = intraLoaderFactory;
    this.diffSummaryLoaderFactory = diffSummaryLoaderFactory;

    this.computeIntraline =
        cfg.getBoolean(
            "cache", INTRA_NAME, "enabled", cfg.getBoolean("cache", "diff", "intraline", true));
  }

  @Override
  public IntraLineDiff getIntraLineDiff(IntraLineDiffKey key, IntraLineDiffArgs args) {
    if (computeIntraline) {
      try {
        return intraCache.get(key, intraLoaderFactory.create(key, args));
      } catch (ExecutionException | LargeObjectException e) {
        IntraLineLoader.logger.atWarning().withCause(e).log("Error computing %s", key);
        return IntraLineDiff.create(IntraLineDiff.Status.ERROR);
      }
    }
    return IntraLineDiff.create(IntraLineDiff.Status.DISABLED);
  }

  @Override
  public DiffSummary getDiffSummary(DiffSummaryKey key, Project.NameKey project)
      throws PatchListNotAvailableException {
    try {
      return diffSummaryCache.get(key, diffSummaryLoaderFactory.create(key, project));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Error computing %s", key);
      throw new PatchListNotAvailableException(e);
    } catch (UncheckedExecutionException e) {
      if (e.getCause() instanceof LargeObjectException) {
        logger.atWarning().withCause(e).log("Error computing %s", key);
        throw new PatchListNotAvailableException(e);
      }
      throw e;
    }
  }
}

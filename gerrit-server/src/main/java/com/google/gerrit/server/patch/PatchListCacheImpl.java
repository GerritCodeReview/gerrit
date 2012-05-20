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

import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

import java.util.concurrent.ExecutionException;

/** Provides a cached list of {@link PatchListEntry}. */
@Singleton
public class PatchListCacheImpl implements PatchListCache {
  private static final String FILE_NAME = "diff";
  static final String INTRA_NAME = "diff_intraline";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(FILE_NAME, PatchListKey.class, PatchList.class)
            .maximumWeight(10 << 20)
            .loader(PatchListLoader.class)
            .weigher(PatchListWeigher.class);

        persist(INTRA_NAME, IntraLineDiffKey.class, IntraLineDiff.class)
            .maximumWeight(10 << 20)
            .loader(IntraLineLoader.class)
            .weigher(IntraLineWeigher.class);

        bind(PatchListCacheImpl.class);
        bind(PatchListCache.class).to(PatchListCacheImpl.class);
      }
    };
  }

  private final LoadingCache<PatchListKey, PatchList> fileCache;
  private final LoadingCache<IntraLineDiffKey, IntraLineDiff> intraCache;
  private final boolean computeIntraline;

  @Inject
  PatchListCacheImpl(
      @Named(FILE_NAME) LoadingCache<PatchListKey, PatchList> fileCache,
      @Named(INTRA_NAME) LoadingCache<IntraLineDiffKey, IntraLineDiff> intraCache,
      @GerritServerConfig Config cfg) {
    this.fileCache = fileCache;
    this.intraCache = intraCache;

    this.computeIntraline =
        cfg.getBoolean("cache", INTRA_NAME, "enabled",
            cfg.getBoolean("cache", "diff", "intraline", true));
  }

  public PatchList get(PatchListKey key) {
    try {
      return fileCache.get(key);
    } catch (ExecutionException e) {
      PatchListLoader.log.warn("Error computing " + key, e);
      return null; // TODO Handle PatchList errors in callers.
    }
  }

  public PatchList get(final Change change, final PatchSet patchSet) {
    final Project.NameKey projectKey = change.getProject();
    final ObjectId a = null;
    final ObjectId b = ObjectId.fromString(patchSet.getRevision().get());
    final Whitespace ws = Whitespace.IGNORE_NONE;
    return get(new PatchListKey(projectKey, a, b, ws));
  }

  @Override
  public IntraLineDiff getIntraLineDiff(IntraLineDiffKey key) {
    if (computeIntraline) {
      try {
        return intraCache.get(key);
      } catch (ExecutionException e) {
        IntraLineLoader.log.warn("Error computing " + key, e);
        return new IntraLineDiff(IntraLineDiff.Status.ERROR);
      }
    } else {
      return new IntraLineDiff(IntraLineDiff.Status.DISABLED);
    }
  }
}

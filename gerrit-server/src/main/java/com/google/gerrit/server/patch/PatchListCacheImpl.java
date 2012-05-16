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


import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/** Provides a cached list of {@link PatchListEntry}. */
@Singleton
public class PatchListCacheImpl implements PatchListCache {
  private static final String FILE_NAME = "diff";
  static final String INTRA_NAME = "diff_intraline";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<PatchListKey, PatchList>> fileType =
            new TypeLiteral<Cache<PatchListKey, PatchList>>() {};
        disk(fileType, FILE_NAME) //
            .memoryLimit(128) // very large items, cache only a few
            .populateWith(PatchListLoader.class) //
        ;

        final TypeLiteral<Cache<IntraLineDiffKey, IntraLineDiff>> intraType =
            new TypeLiteral<Cache<IntraLineDiffKey, IntraLineDiff>>() {};
        disk(intraType, INTRA_NAME) //
            .memoryLimit(128) // very large items, cache only a few
            .populateWith(IntraLineLoader.class) //
        ;

        bind(PatchListCacheImpl.class);
        bind(PatchListCache.class).to(PatchListCacheImpl.class);
      }
    };
  }

  private final Cache<PatchListKey, PatchList> fileCache;
  private final Cache<IntraLineDiffKey, IntraLineDiff> intraCache;
  private final boolean computeIntraline;

  @Inject
  PatchListCacheImpl(
      @Named(FILE_NAME) final Cache<PatchListKey, PatchList> fileCache,
      @Named(INTRA_NAME) final Cache<IntraLineDiffKey, IntraLineDiff> intraCache,
      @GerritServerConfig Config cfg) {
    this.fileCache = fileCache;
    this.intraCache = intraCache;

    this.computeIntraline =
        cfg.getBoolean("cache", INTRA_NAME, "enabled",
            cfg.getBoolean("cache", "diff", "intraline", true));
  }

  public PatchList get(final PatchListKey key) {
    return fileCache.get(key);
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
      IntraLineDiff d = intraCache.get(key);
      if (d == null) {
        d = new IntraLineDiff(IntraLineDiff.Status.ERROR);
      }
      return d;
    } else {
      return new IntraLineDiff(IntraLineDiff.Status.DISABLED);
    }
  }
}

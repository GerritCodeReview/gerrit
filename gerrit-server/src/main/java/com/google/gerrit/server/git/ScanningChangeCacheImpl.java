// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.server.git.SearchingChangeCacheImpl.ID_CACHE;

import com.google.common.base.Function;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Singleton
public class ScanningChangeCacheImpl implements ChangeCache {
  private static final Logger log =
      LoggerFactory.getLogger(ScanningChangeCacheImpl.class);

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeCache.class).to(ScanningChangeCacheImpl.class);
        cache(ID_CACHE,
            Project.NameKey.class,
            new TypeLiteral<List<Change>>() {})
          .maximumWeight(0)
          .loader(Loader.class);
      }
    };
  }

  private final LoadingCache<Project.NameKey, List<Change>> cache;

  @Inject
  ScanningChangeCacheImpl(
      @Named(ID_CACHE) LoadingCache<Project.NameKey, List<Change>> cache) {
    this.cache = cache;
  }

  @Override
  public List<Change> get(Project.NameKey name) {
    try {
      return cache.get(name);
    } catch (ExecutionException e) {
      log.warn("Cannot fetch changes for " + name, e);
      return Collections.emptyList();
    }
  }

  static class Loader extends CacheLoader<Project.NameKey, List<Change>> {
    private final GitRepositoryManager repoManager;
    private final ChangeNotes.Factory notesFactory;
    private final OneOffRequestContext requestContext;

    @Inject
    Loader(GitRepositoryManager repoManager,
        ChangeNotes.Factory notesFactory,
        OneOffRequestContext requestContext) {
      this.repoManager = repoManager;
      this.notesFactory = notesFactory;
      this.requestContext = requestContext;
    }

    @Override
    public List<Change> load(Project.NameKey key) throws Exception {
      try (Repository repo = repoManager.openRepository(key);
          ManualRequestContext ctx = requestContext.open()) {
        return Lists.transform(
            notesFactory.scan(repo, ctx.getReviewDbProvider().get(), key),
            new Function<ChangeNotes, Change>() {
              @Override
              public Change apply(ChangeNotes notes) {
                return notes.getChange();
              }
            });
      }
    }
  }
}

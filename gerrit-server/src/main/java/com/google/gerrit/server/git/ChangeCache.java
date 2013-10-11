// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Singleton
public class ChangeCache implements GitReferenceUpdatedListener {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeCache.class);
  private static final String ID_CACHE = "changes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
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
  ChangeCache(@Named(ID_CACHE) LoadingCache<Project.NameKey, List<Change>> cache) {
    this.cache = cache;
  }

  List<Change> get(Project.NameKey name) {
    try {
      return cache.get(name);
    } catch (ExecutionException e) {
      log.warn("Cannot fetch changes for " + name, e);
      return Collections.emptyList();
    }
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    if (event.getRefName().startsWith("refs/changes/")) {
      cache.invalidate(new Project.NameKey(event.getProjectName()));
    }
  }

  static class Loader extends CacheLoader<Project.NameKey, List<Change>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    Loader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public List<Change> load(Project.NameKey key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return Collections.unmodifiableList(db.changes().byProject(key).toList());
      } finally {
        db.close();
      }
    }
  }
}

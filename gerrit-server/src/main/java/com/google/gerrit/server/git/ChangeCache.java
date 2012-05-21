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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.git;

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class ChangeCache implements GitReferenceUpdatedListener {
  private static final String ID_CACHE = "changes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        TypeLiteral<Cache<Project.NameKey, List<Change>>> cache =
            new TypeLiteral<Cache<Project.NameKey, List<Change>>>() {};
        core(cache, ID_CACHE)
          .memoryLimit(1024)
          .maxAge(120, TimeUnit.MINUTES)
          .populateWith(Loader.class);
      }
    };
  }

  private final Cache<Project.NameKey, List<Change>> cache;

  @Inject
  ChangeCache(@Named(ID_CACHE) Cache<Project.NameKey, List<Change>> cache) {
    this.cache = cache;
  }

  List<Change> get(Project.NameKey name) throws OrmException {
    return cache.get(name);
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    cache.remove(Project.NameKey.parse(event.getProjectName()));
  }

  static class Loader extends EntryCreator<Project.NameKey, List<Change>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    Loader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public List<Change> createEntry(NameKey key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return Collections.unmodifiableList(db.changes().byProject(key).toList());
      } finally {
        db.close();
      }
    }
  }
}

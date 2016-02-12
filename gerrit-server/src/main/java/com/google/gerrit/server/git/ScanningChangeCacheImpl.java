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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            new TypeLiteral<List<ChangeNotes>>() {})
          .maximumWeight(0)
          .loader(Loader.class);
      }
    };
  }

  private final LoadingCache<Project.NameKey, List<ChangeNotes>> cache;

  @Inject
  ScanningChangeCacheImpl(
      @Named(ID_CACHE) LoadingCache<Project.NameKey, List<ChangeNotes>> cache) {
    this.cache = cache;
  }

  @Override
  public List<ChangeNotes> get(Project.NameKey name) {
    try {
      return cache.get(name);
    } catch (ExecutionException e) {
      log.warn("Cannot fetch changes for " + name, e);
      return Collections.emptyList();
    }
  }

  static class Loader extends CacheLoader<Project.NameKey, List<ChangeNotes>> {
    private final GitRepositoryManager repoManager;
    private final NotesMigration notesMigration;
    private final ChangeNotes.Factory notesFactory;
    private final OneOffRequestContext requestContext;

    @Inject
    Loader(GitRepositoryManager repoManager,
        NotesMigration notesMigration,
        ChangeNotes.Factory notesFactory,
        OneOffRequestContext requestContext) {
      this.repoManager = repoManager;
      this.notesMigration = notesMigration;
      this.notesFactory = notesFactory;
      this.requestContext = requestContext;
    }

    @Override
    public List<ChangeNotes> load(Project.NameKey key) throws Exception {
      try (Repository repo = repoManager.openRepository(key);
          ManualRequestContext ctx = requestContext.open()) {
        return scan(notesMigration, notesFactory, repo,
            ctx.getReviewDbProvider().get(), key);
      }
    }
  }

  public static List<ChangeNotes> scan(NotesMigration notesMigration,
      ChangeNotes.Factory notesFactory, Repository repo, ReviewDb db,
      Project.NameKey project) throws OrmException, IOException {
    if (!notesMigration.readChanges()) {
      return scanDb(notesFactory, repo, db);
    }

    return scanNotedb(notesFactory, repo, db, project);
  }

  private static List<ChangeNotes> scanDb(ChangeNotes.Factory notesFactory,
      Repository repo, ReviewDb db) throws OrmException, IOException {
    Map<String, Ref> refs =
        repo.getRefDatabase().getRefs(RefNames.REFS_CHANGES);
    Set<Change.Id> ids = new LinkedHashSet<>();
    for (Ref r : refs.values()) {
      Change.Id id = Change.Id.fromRef(r.getName());
      if (id != null) {
        ids.add(id);
      }
    }
    List<ChangeNotes> notes = new ArrayList<>(ids.size());
    // A batch size of N may overload get(Iterable), so use something smaller,
    // but still >1.
    for (List<Change.Id> batch : Iterables.partition(ids, 30)) {
      for (Change change : db.changes().get(batch)) {
        notes.add(notesFactory
            .createFromChangeOnlyWhenNotedbDisabled(change));
      }
    }
    return notes;
  }

  public static List<ChangeNotes> scanNotedb(ChangeNotes.Factory notesFactory,
      Repository repo, ReviewDb db, Project.NameKey project)
          throws OrmException, IOException {
    Map<String, Ref> refs =
        repo.getRefDatabase().getRefs(RefNames.REFS_CHANGES);
    List<ChangeNotes> changeNotes = new ArrayList<>(refs.size());
    for (Ref r : refs.values()) {
      Change.Id id = Change.Id.fromRef(r.getName());
      if (id != null) {
        changeNotes.add(notesFactory.create(db, project, id));
      }
    }
    return changeNotes;
  }
}

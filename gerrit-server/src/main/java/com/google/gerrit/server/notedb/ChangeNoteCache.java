// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.notedb.AbstractChangeNotes.Args;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

@Singleton
class ChangeNoteCache {
  @VisibleForTesting
  static final String CHANGE_NOTES = "change_notes";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeNoteCache.class);
        cache(CHANGE_NOTES,
            Key.class,
            ChangeNotesState.class)
          .maximumWeight(1000);
      }
    };
  }

  @AutoValue
  static abstract class Key {
    abstract Project.NameKey project();
    abstract Change.Id changeId();
    abstract ObjectId id();
  }

  @AutoValue
  static abstract class Value {
    abstract ChangeNotesState state();

    /**
     * If {@link #get} incurred a read of the ref, the {@link RevisionNoteMap}
     * populated during parsing; null if the value came from the cache.
     */
    @Nullable abstract RevisionNoteMap revisionNoteMap();
  }

  private class Loader implements Callable<ChangeNotesState> {
    private final Key key;
    private final ChangeNotesRevWalk rw;

    private RevisionNoteMap revisionNoteMap;

    private Loader(Key key, ChangeNotesRevWalk rw) {
      this.key = key;
      this.rw = rw;
    }

    @Override
    public ChangeNotesState call() throws ConfigInvalidException, IOException {
      ChangeNotesParser parser = new ChangeNotesParser(
          key.changeId(), key.id(), rw, args.noteUtil, args.metrics);
      ChangeNotesState result = parser.parseAll();
      revisionNoteMap = parser.getRevisionNoteMap();
      return result;
    }
  }

  private final Cache<Key, ChangeNotesState> cache;
  private final Args args;

  @Inject
  ChangeNoteCache(
      @Named(CHANGE_NOTES) Cache<Key, ChangeNotesState> cache,
      Args args) {
    this.cache = cache;
    this.args = args;
  }

  Value get(Project.NameKey project, Change.Id changeId,
      ObjectId metaId, ChangeNotesRevWalk rw) throws IOException {
    try {
      Key key =
          new AutoValue_ChangeNoteCache_Key(project, changeId, metaId.copy());
      Loader loader = new Loader(key, rw);
      ChangeNotesState s = cache.get(key, loader);
      return new AutoValue_ChangeNoteCache_Value(s, loader.revisionNoteMap);
    } catch (ExecutionException e) {
      throw new IOException(String.format(
              "Error loading %s in %s at %s",
              RefNames.changeMetaRef(changeId), project, metaId.name()),
          e);
    }
  }
}

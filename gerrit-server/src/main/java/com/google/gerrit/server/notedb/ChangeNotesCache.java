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
public class ChangeNotesCache {
  @VisibleForTesting
  static final String CACHE_NAME = "change_notes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeNotesCache.class);
        cache(CACHE_NAME,
            Key.class,
            ChangeNotesState.class)
          .maximumWeight(1000);
      }
    };
  }

  @AutoValue
  public abstract static class Key {
    abstract Project.NameKey project();
    abstract Change.Id changeId();
    abstract ObjectId id();
  }

  @AutoValue
  abstract static class Value {
    abstract ChangeNotesState state();

    /**
     * The {@link RevisionNoteMap} produced while parsing this change.
     * <p>
     * These instances are mutable and non-threadsafe, so it is only safe to
     * return it to the caller that actually incurred the cache miss. It is only
     * used as an optimization; {@link ChangeNotes} is capable of lazily loading
     * it as necessary.
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
      // This assignment only happens if call() was actually called, which only
      // happens when Cache#get(K, Callable<V>) incurs a cache miss.
      revisionNoteMap = parser.getRevisionNoteMap();
      return result;
    }
  }

  private final Cache<Key, ChangeNotesState> cache;
  private final Args args;

  @Inject
  ChangeNotesCache(
      @Named(CACHE_NAME) Cache<Key, ChangeNotesState> cache,
      Args args) {
    this.cache = cache;
    this.args = args;
  }

  Value get(Project.NameKey project, Change.Id changeId,
      ObjectId metaId, ChangeNotesRevWalk rw) throws IOException {
    try {
      Key key =
          new AutoValue_ChangeNotesCache_Key(project, changeId, metaId.copy());
      Loader loader = new Loader(key, rw);
      ChangeNotesState s = cache.get(key, loader);
      return new AutoValue_ChangeNotesCache_Value(s, loader.revisionNoteMap);
    } catch (ExecutionException e) {
      throw new IOException(String.format(
              "Error loading %s in %s at %s",
              RefNames.changeMetaRef(changeId), project, metaId.name()),
          e);
    }
  }
}

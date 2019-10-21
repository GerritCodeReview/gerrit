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
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.notedb.AbstractChangeNotes.Args;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class ChangeNotesCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting static final String CACHE_NAME = "change_notes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeNotesCache.class);
        persist(CACHE_NAME, Key.class, ChangeNotesState.class)
            .weigher(Weigher.class)
            .maximumWeight(10 << 20)
            .diskLimit(-1)
            .version(2)
            .keySerializer(Key.Serializer.INSTANCE)
            .valueSerializer(ChangeNotesState.Serializer.INSTANCE);
      }
    };
  }

  @AutoValue
  public abstract static class Key {
    static Key create(Project.NameKey project, Change.Id changeId, ObjectId id) {
      return new AutoValue_ChangeNotesCache_Key(project, changeId, id.copy());
    }

    abstract Project.NameKey project();

    abstract Change.Id changeId();

    abstract ObjectId id();

    @VisibleForTesting
    enum Serializer implements CacheSerializer<Key> {
      INSTANCE;

      @Override
      public byte[] serialize(Key object) {
        return Protos.toByteArray(
            ChangeNotesKeyProto.newBuilder()
                .setProject(object.project().get())
                .setChangeId(object.changeId().get())
                .setId(ObjectIdConverter.create().toByteString(object.id()))
                .build());
      }

      @Override
      public Key deserialize(byte[] in) {
        ChangeNotesKeyProto proto = Protos.parseUnchecked(ChangeNotesKeyProto.parser(), in);
        return Key.create(
            Project.nameKey(proto.getProject()),
            Change.id(proto.getChangeId()),
            ObjectIdConverter.create().fromByteString(proto.getId()));
      }
    }
  }

  public static class Weigher implements com.google.common.cache.Weigher<Key, ChangeNotesState> {
    // Single object overhead.
    private static final int O = 16;

    // Single pointer overhead.
    private static final int P = 8;

    // Single int overhead.
    private static final int I = 4;

    // Single IntKey overhead.
    private static final int K = O + I;

    // Single Timestamp overhead.
    private static final int T = O + 8;

    @Override
    public int weigh(Key key, ChangeNotesState state) {
      // Take all columns and all collection sizes into account, but use
      // estimated average element sizes rather than iterating over collections.
      // Numbers are largely hand-wavy based on
      // http://stackoverflow.com/questions/258120/what-is-the-memory-consumption-of-an-object-in-java
      return P
          + O
          + 20 // metaId
          + K // changeId
          + str(40) // changeKey
          + T // createdOn
          + T // lastUpdatedOn
          + P
          + K // owner
          + P
          + str(state.columns().branch())
          + P
          + patchSetId() // currentPatchSetId
          + P
          + str(state.columns().subject())
          + P
          + str(state.columns().topic())
          + P
          + str(state.columns().originalSubject())
          + P
          + str(state.columns().submissionId())
          + P // status
          + P
          + set(state.hashtags(), str(10))
          + P
          + list(state.patchSets(), patchSet())
          + P
          + reviewerSet(state.reviewers(), 2) // REVIEWER or CC
          + P
          + reviewerSet(state.reviewersByEmail(), 2) // REVIEWER or CC
          + P
          + reviewerSet(state.pendingReviewers(), 3) // includes REMOVED
          + P
          + reviewerSet(state.pendingReviewersByEmail(), 3) // includes REMOVED
          + P
          + list(state.allPastReviewers(), approval())
          + P
          + list(state.reviewerUpdates(), 4 * O + K + K + P)
          + P
          + list(state.assigneeUpdates(), 4 * O + K + K)
          + P
          + list(state.submitRecords(), P + list(2, str(4) + P + K) + P)
          + P
          + list(state.changeMessages(), changeMessage())
          + P
          + map(state.publishedComments().asMap(), comment())
          + 1 // isPrivate
          + 1 // workInProgress
          + 1 // reviewStarted
          + I; // updateCount
    }

    private static int str(String s) {
      if (s == null) {
        return P;
      }
      return str(s.length());
    }

    private static int str(int n) {
      return 8 + 24 + 2 * n;
    }

    private static int patchSetId() {
      return O + 4 + O + 4;
    }

    private static int set(Set<?> set, int elemSize) {
      if (set == null) {
        return P;
      }
      return hashtable(set.size(), elemSize);
    }

    private static int map(Map<?, ?> map, int elemSize) {
      if (map == null) {
        return P;
      }
      return hashtable(map.size(), elemSize);
    }

    private static int hashtable(int n, int elemSize) {
      // Made up numbers.
      int overhead = 32;
      int elemOverhead = O + 32;
      return overhead + elemOverhead * n * elemSize;
    }

    private static int list(List<?> list, int elemSize) {
      if (list == null) {
        return P;
      }
      return list(list.size(), elemSize);
    }

    private static int list(int n, int elemSize) {
      return O + O + n * (P + elemSize);
    }

    private static int hashBasedTable(
        Table<?, ?, ?> table, int numRows, int rowKey, int columnKey, int elemSize) {
      return O
          + hashtable(numRows, rowKey + hashtable(0, 0))
          + hashtable(table.size(), columnKey + elemSize);
    }

    private static int reviewerSet(ReviewerSet reviewers, int numRows) {
      final int rowKey = 1; // ReviewerStateInternal
      final int columnKey = K; // Account.Id
      final int cellValue = T; // Timestamp
      return hashBasedTable(reviewers.asTable(), numRows, rowKey, columnKey, cellValue);
    }

    private static int reviewerSet(ReviewerByEmailSet reviewers, int numRows) {
      final int rowKey = 1; // ReviewerStateInternal
      final int columnKey = P + 2 * str(20); // name and email, just a guess
      final int cellValue = T; // Timestamp
      return hashBasedTable(reviewers.asTable(), numRows, rowKey, columnKey, cellValue);
    }

    private static int patchSet() {
      return O
          + P
          + patchSetId()
          + str(40) // revision
          + P
          + K // uploader
          + P
          + T // createdOn
          + 1 // draft
          + str(40) // groups
          + P; // pushCertificate
    }

    private static int approval() {
      return O
          + P
          + patchSetId()
          + P
          + K
          + P
          + O
          + str(10)
          + 2 // value
          + P
          + T // granted
          + P // tag
          + P; // realAccountId
    }

    private static int changeMessage() {
      int key = K + str(20);
      return O
          + P
          + key
          + P
          + K // author
          + P
          + T // writtenON
          + str(64) // message
          + P
          + patchSetId()
          + P
          + P; // realAuthor
    }

    private static int comment() {
      int key = P + str(20) + P + str(32) + 4;
      int ident = O + 4;
      return O
          + P
          + key
          + 4 // lineNbr
          + P
          + ident // author
          + P
          + ident // realAuthor
          + P
          + T // writtenOn
          + 2 // side
          + str(32) // message
          + str(10) // parentUuid
          + (P + O + 4 + 4 + 4 + 4) / 2 // range on 50% of comments
          + P // tag
          + P
          + str(40) // revId
          + P
          + str(36); // serverId
    }
  }

  @AutoValue
  abstract static class Value {
    abstract ChangeNotesState state();

    /**
     * The {@link RevisionNoteMap} produced while parsing this change.
     *
     * <p>These instances are mutable and non-threadsafe, so it is only safe to return it to the
     * caller that actually incurred the cache miss. It is only used as an optimization; {@link
     * ChangeNotes} is capable of lazily loading it as necessary.
     */
    @Nullable
    abstract RevisionNoteMap<ChangeRevisionNote> revisionNoteMap();
  }

  private class Loader implements Callable<ChangeNotesState> {
    private final Key key;
    private final Supplier<ChangeNotesRevWalk> walkSupplier;

    private RevisionNoteMap<ChangeRevisionNote> revisionNoteMap;

    private Loader(Key key, Supplier<ChangeNotesRevWalk> walkSupplier) {
      this.key = key;
      this.walkSupplier = walkSupplier;
    }

    @Override
    public ChangeNotesState call() throws ConfigInvalidException, IOException {
      logger.atFine().log(
          "Load change notes for change %s of project %s", key.changeId(), key.project());
      ChangeNotesParser parser =
          new ChangeNotesParser(
              key.changeId(), key.id(), walkSupplier.get(), args.changeNoteJson, args.metrics);
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
  ChangeNotesCache(@Named(CACHE_NAME) Cache<Key, ChangeNotesState> cache, Args args) {
    this.cache = cache;
    this.args = args;
  }

  Value get(
      Project.NameKey project,
      Change.Id changeId,
      ObjectId metaId,
      Supplier<ChangeNotesRevWalk> walkSupplier)
      throws IOException {
    try {
      Key key = Key.create(project, changeId, metaId);
      Loader loader = new Loader(key, walkSupplier);
      ChangeNotesState s = cache.get(key, loader);
      return new AutoValue_ChangeNotesCache_Value(s, loader.revisionNoteMap);
    } catch (ExecutionException e) {
      throw new IOException(
          String.format(
              "Error loading %s in %s at %s",
              RefNames.changeMetaRef(changeId), project, metaId.name()),
          e);
    }
  }
}

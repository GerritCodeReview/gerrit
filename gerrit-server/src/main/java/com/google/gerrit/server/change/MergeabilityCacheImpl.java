// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Singleton
public class MergeabilityCacheImpl implements MergeabilityCache {
  private static final Logger log =
      LoggerFactory.getLogger(MergeabilityCacheImpl.class);

  private static final String CACHE_NAME = "mergeability";

  public static final BiMap<SubmitType, Character> SUBMIT_TYPES = ImmutableBiMap.of(
        SubmitType.FAST_FORWARD_ONLY, 'F',
        SubmitType.MERGE_IF_NECESSARY, 'M',
        SubmitType.REBASE_IF_NECESSARY, 'R',
        SubmitType.MERGE_ALWAYS, 'A',
        SubmitType.CHERRY_PICK, 'C');

  static {
    checkState(SUBMIT_TYPES.size() == SubmitType.values().length,
        "SubmitType <-> char BiMap needs updating");
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, EntryKey.class, Boolean.class)
            .maximumWeight(1 << 20)
            .weigher(MergeabilityWeigher.class)
            .loader(Loader.class);
        bind(MergeabilityCache.class).to(MergeabilityCacheImpl.class);
      }
    };
  }

  public static ObjectId toId(Ref ref) {
    return ref != null && ref.getObjectId() != null
        ? ref.getObjectId()
        : ObjectId.zeroId();
  }

  public static class EntryKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private ObjectId commit;
    private ObjectId into;
    private SubmitType submitType;
    private String mergeStrategy;

    // Only used for loading, not stored.
    private transient LoadHelper load;

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType,
        String mergeStrategy) {
      this.commit = checkNotNull(commit, "commit");
      this.into = checkNotNull(into, "into");
      this.submitType = checkNotNull(submitType, "submitType");
      this.mergeStrategy = checkNotNull(mergeStrategy, "mergeStrategy");
    }

    private EntryKey(ObjectId commit, ObjectId into, SubmitType submitType,
        String mergeStrategy, Branch.NameKey dest, Repository repo,
        ReviewDb db) {
      this(commit, into, submitType, mergeStrategy);
      load = new LoadHelper(dest, repo, db);
    }

    public ObjectId getCommit() {
      return commit;
    }

    public ObjectId getInto() {
      return into;
    }

    public SubmitType getSubmitType() {
      return submitType;
    }

    public String getMergeStrategy() {
      return mergeStrategy;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof EntryKey) {
        EntryKey k = (EntryKey) o;
        return commit.equals(k.commit)
            && into.equals(k.into)
            && submitType == k.submitType
            && mergeStrategy.equals(k.mergeStrategy);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(commit, into, submitType, mergeStrategy);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("commit", commit.name())
          .add("into", into.name())
          .addValue(submitType)
          .addValue(mergeStrategy)
          .toString();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      writeNotNull(out, commit);
      writeNotNull(out, into);
      Character c = SUBMIT_TYPES.get(submitType);
      if (c == null) {
        throw new IOException("Invalid submit type: " + submitType);
      }
      out.writeChar(c);
      writeString(out, mergeStrategy);
    }

    private void readObject(ObjectInputStream in) throws IOException {
      commit = readNotNull(in);
      into = readNotNull(in);
      char t = in.readChar();
      submitType = SUBMIT_TYPES.inverse().get(t);
      if (submitType == null) {
        throw new IOException("Invalid submit type code: " + t);
      }
      mergeStrategy = readString(in);
    }
  }

  private static class LoadHelper {
    private final Branch.NameKey dest;
    private final Repository repo;
    private final ReviewDb db;

    private LoadHelper(Branch.NameKey dest, Repository repo, ReviewDb db) {
      this.dest = checkNotNull(dest, "dest");
      this.repo = checkNotNull(repo, "repo");
      this.db = checkNotNull(db, "db");
    }
  }

  @Singleton
  public static class Loader extends CacheLoader<EntryKey, Boolean> {
    private final SubmitStrategyFactory submitStrategyFactory;

    @Inject
    Loader(SubmitStrategyFactory submitStrategyFactory) {
      this.submitStrategyFactory = submitStrategyFactory;
    }

    @Override
    public Boolean load(EntryKey key)
        throws NoSuchProjectException, MergeException, IOException {
      checkArgument(key.load != null, "Key cannot be loaded: %s", key);
      if (key.into.equals(ObjectId.zeroId())) {
        return true; // Assume yes on new branch.
      }
      try {
        Map<String, Ref> refs = key.load.repo.getAllRefs();
        RevWalk rw = CodeReviewCommit.newRevWalk(key.load.repo);
        try {
          RevFlag canMerge = rw.newFlag("CAN_MERGE");
          CodeReviewCommit rev = parse(rw, key.commit);
          rev.add(canMerge);
          CodeReviewCommit tip = parse(rw, key.into);
          Set<RevCommit> accepted = alreadyAccepted(rw, refs.values());
          accepted.add(tip);
          accepted.addAll(Arrays.asList(rev.getParents()));
          return submitStrategyFactory.create(
              key.submitType,
              key.load.db,
              key.load.repo,
              rw,
              null /*inserter*/,
              canMerge,
              accepted,
              key.load.dest).dryRun(tip, rev);
        } finally {
          rw.release();
        }
      } finally {
        key.load = null;
      }
    }

    private static Set<RevCommit> alreadyAccepted(RevWalk rw,
        Collection<Ref> refs) throws MissingObjectException, IOException {
      Set<RevCommit> accepted = Sets.newHashSet();
      for (Ref r : refs) {
        if (r.getName().startsWith(Constants.R_HEADS)
            || r.getName().startsWith(Constants.R_TAGS)) {
          try {
            accepted.add(rw.parseCommit(r.getObjectId()));
          } catch (IncorrectObjectTypeException nonCommit) {
            // Not a commit? Skip over it.
          }
        }
      }
      return accepted;
    }

    private static CodeReviewCommit parse(RevWalk rw, ObjectId id)
        throws MissingObjectException, IncorrectObjectTypeException,
        IOException {
      return (CodeReviewCommit) rw.parseCommit(id);
    }
  }

  public static class MergeabilityWeigher
      implements Weigher<EntryKey, Boolean> {
    @Override
    public int weigh(EntryKey k, Boolean v) {
      return 16 + 2 * (16 + 20) + 3 * 8 // Size of EntryKey, 64-bit JVM.
          + 8; // Size of Boolean.
    }
  }

  private final LoadingCache<EntryKey, Boolean> cache;

  @Inject
  MergeabilityCacheImpl(@Named(CACHE_NAME) LoadingCache<EntryKey, Boolean> cache) {
    this.cache = cache;
  }

  @Override
  public boolean get(ObjectId commit, Ref intoRef, SubmitType submitType,
      String mergeStrategy, Branch.NameKey dest, Repository repo, ReviewDb db) {
    ObjectId into = intoRef != null ? intoRef.getObjectId() : ObjectId.zeroId();
    EntryKey key =
        new EntryKey(commit, into, submitType, mergeStrategy, dest, repo, db);
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      log.error(String.format("Error checking mergeability of %s into %s (%s)",
            key.commit.name(), key.into.name(), key.submitType.name()),
          e.getCause());
      return false;
    }
  }

  @Override
  public boolean getIfPresent(ObjectId commit, Ref intoRef,
      SubmitType submitType, String mergeStrategy) {
    return cache.getIfPresent(
        new EntryKey(commit, toId(intoRef), submitType, mergeStrategy));
  }
}

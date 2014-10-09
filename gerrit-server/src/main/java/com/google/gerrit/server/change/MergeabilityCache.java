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
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

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
public class MergeabilityCache {
  private static final Logger log =
      LoggerFactory.getLogger(MergeabilityCache.class);

  private static final String CACHE_NAME = "mergeability";

  public static final BiMap<SubmitType, Character> SUBMIT_TYPES =
      ImmutableBiMap.<SubmitType, Character> builder()
        .put(SubmitType.FAST_FORWARD_ONLY, 'F')
        .put(SubmitType.MERGE_IF_NECESSARY, 'M')
        .put(SubmitType.REBASE_IF_NECESSARY, 'R')
        .put(SubmitType.MERGE_ALWAYS, 'A')
        .put(SubmitType.CHERRY_PICK, 'C')
        .build();

  static {
    checkState(SUBMIT_TYPES.size() == SubmitType.values().length,
        "SubmitType <-> char BiMap needs updating");
  }

  @SuppressWarnings("rawtypes")
  public static Key bindingKey() {
    return Key.get(new TypeLiteral<LoadingCache<EntryKey, Boolean>>() {},
        Names.named(CACHE_NAME));
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, EntryKey.class, Boolean.class)
            .maximumWeight(1 << 20)
            .weigher(MergeabilityWeigher.class)
            .loader(Loader.class);
        bind(MergeabilityCache.class);
      }
    };
  }

  public static class EntryKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private ObjectId commit;
    private ObjectId into;
    private SubmitType submitType;
    private String mergeStrategy;

    // Only used for loading, not stored.
    private transient Branch.NameKey dest;
    private transient Repository repo;

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType,
        String mergeStrategy) {
      this.commit = checkNotNull(commit, "commit");
      this.into = checkNotNull(into, "into");
      this.submitType = checkNotNull(submitType, "submitType");
      this.mergeStrategy = checkNotNull(mergeStrategy, "mergeStrategy");
    }

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType,
        String mergeStrategy, Branch.NameKey dest) {
      this(commit, into, submitType, mergeStrategy);
      this.dest = checkNotNull(dest, "dest");
    }

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType,
        String mergeStrategy, Branch.NameKey dest, Repository repo) {
      this(commit, into, submitType, mergeStrategy, dest);
      this.repo = checkNotNull(repo, "repo");
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

  @Singleton
  public static class Loader extends CacheLoader<EntryKey, Boolean> {
    private final Provider<ReviewDb> db;
    private final GitRepositoryManager repoManager;
    private final SubmitStrategyFactory submitStrategyFactory;

    @Inject
    Loader(GitRepositoryManager repoManager,
        Provider<ReviewDb> db,
        SubmitStrategyFactory submitStrategyFactory) {
      this.db = db;
      this.repoManager = repoManager;
      this.submitStrategyFactory = submitStrategyFactory;
    }

    @Override
    public Boolean load(EntryKey key)
        throws NoSuchProjectException, MergeException, IOException {
      // Keys constructed without a branch are only suitable for getIfPresent.
      checkArgument(key.dest != null,
          "Key cannot be loaded without a destination branch");
      boolean open = key.repo == null;
      Repository repo = open
          ? repoManager.openRepository(key.dest.getParentKey())
          : key.repo;
      try {
        return isMergeable(key, repo);
      } finally {
        key.dest = null;
        key.repo = null;
        if (open) {
          repo.close();
        }
      }
    }

    private boolean isMergeable(EntryKey key, Repository repo)
        throws NoSuchProjectException, MergeException, IOException {
      if (key.into.equals(ObjectId.zeroId())) {
        return true; // Assume yes on new branch.
      }
      Map<String, Ref> refs = repo.getAllRefs();
      RevWalk rw = CodeReviewCommit.newRevWalk(repo);
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
            db.get(),
            repo,
            rw,
            null /*inserter*/,
            canMerge,
            accepted,
            key.dest).dryRun(tip, rev);
      } finally {
        rw.release();
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

  private static class MergeabilityWeigher
      implements Weigher<EntryKey, Boolean> {
    @Override
    public int weigh(EntryKey k, Boolean v) {
      return 16 + 2 * (16 + 20) + 4 * 8 // Size of EntryKey, 64-bit JVM.
          + 1; // Size of Boolean.
    }
  }

  private final LoadingCache<EntryKey, Boolean> cache;

  @Inject
  MergeabilityCache(@Named(CACHE_NAME) LoadingCache<EntryKey, Boolean> cache) {
    this.cache = cache;
  }

  public Boolean getIfPresent(ObjectId commit, ObjectId into,
      SubmitType submitType, String mergeStrategy) {
    return cache.getIfPresent(
        new EntryKey(commit, into, submitType, mergeStrategy));
  }

  public boolean get(ObjectId commit, ObjectId into, SubmitType submitType,
      String mergeStrategy, Branch.NameKey dest) {
    return get(new EntryKey(commit, into, submitType, mergeStrategy, dest));
  }

  public boolean get(ObjectId commit, ObjectId into, SubmitType submitType,
      String mergeStrategy, Branch.NameKey dest, Repository repo) {
    return get(
        new EntryKey(commit, into, submitType, mergeStrategy, dest, repo));
  }

  private boolean get(EntryKey key) {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      log.error(String.format("Error checking mergeability of %s into %s (%s)",
            key.commit.name(), key.into.name(), key.submitType.name()),
          e.getCause());
      return false;
    }
  }
}

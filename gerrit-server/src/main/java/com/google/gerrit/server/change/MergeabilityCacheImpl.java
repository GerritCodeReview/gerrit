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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.strategy.SubmitDryRun;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MergeabilityCacheImpl implements MergeabilityCache {
  private static final Logger log = LoggerFactory.getLogger(MergeabilityCacheImpl.class);

  private static final String CACHE_NAME = "mergeability";

  public static final BiMap<SubmitType, Character> SUBMIT_TYPES =
      new ImmutableBiMap.Builder<SubmitType, Character>()
          .put(SubmitType.FAST_FORWARD_ONLY, 'F')
          .put(SubmitType.MERGE_IF_NECESSARY, 'M')
          .put(SubmitType.REBASE_ALWAYS, 'P')
          .put(SubmitType.REBASE_IF_NECESSARY, 'R')
          .put(SubmitType.MERGE_ALWAYS, 'A')
          .put(SubmitType.CHERRY_PICK, 'C')
          .build();

  static {
    checkState(
        SUBMIT_TYPES.size() == SubmitType.values().length,
        "SubmitType <-> char BiMap needs updating");
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, EntryKey.class, Boolean.class)
            .maximumWeight(1 << 20)
            .weigher(MergeabilityWeigher.class);
        bind(MergeabilityCache.class).to(MergeabilityCacheImpl.class);
      }
    };
  }

  public static ObjectId toId(Ref ref) {
    return ref != null && ref.getObjectId() != null ? ref.getObjectId() : ObjectId.zeroId();
  }

  public static class EntryKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private ObjectId commit;
    private ObjectId into;
    private SubmitType submitType;
    private String mergeStrategy;

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType, String mergeStrategy) {
      this.commit = checkNotNull(commit, "commit");
      this.into = checkNotNull(into, "into");
      this.submitType = checkNotNull(submitType, "submitType");
      this.mergeStrategy = checkNotNull(mergeStrategy, "mergeStrategy");
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

  private class Loader implements Callable<Boolean> {
    private final EntryKey key;
    private final Branch.NameKey dest;
    private final Repository repo;

    Loader(EntryKey key, Branch.NameKey dest, Repository repo) {
      this.key = key;
      this.dest = dest;
      this.repo = repo;
    }

    @Override
    public Boolean call() throws NoSuchProjectException, IntegrationException, IOException {
      if (key.into.equals(ObjectId.zeroId())) {
        return true; // Assume yes on new branch.
      }
      try (CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
        Set<RevCommit> accepted = SubmitDryRun.getAlreadyAccepted(repo, rw);
        accepted.add(rw.parseCommit(key.into));
        accepted.addAll(Arrays.asList(rw.parseCommit(key.commit).getParents()));
        return submitDryRun.run(key.submitType, repo, rw, dest, key.into, key.commit, accepted);
      }
    }
  }

  public static class MergeabilityWeigher implements Weigher<EntryKey, Boolean> {
    @Override
    public int weigh(EntryKey k, Boolean v) {
      return 16
          + 2 * (16 + 20)
          + 3 * 8 // Size of EntryKey, 64-bit JVM.
          + 8; // Size of Boolean.
    }
  }

  private final SubmitDryRun submitDryRun;
  private final Cache<EntryKey, Boolean> cache;

  @Inject
  MergeabilityCacheImpl(
      SubmitDryRun submitDryRun, @Named(CACHE_NAME) Cache<EntryKey, Boolean> cache) {
    this.submitDryRun = submitDryRun;
    this.cache = cache;
  }

  @Override
  public boolean get(
      ObjectId commit,
      Ref intoRef,
      SubmitType submitType,
      String mergeStrategy,
      Branch.NameKey dest,
      Repository repo) {
    ObjectId into = intoRef != null ? intoRef.getObjectId() : ObjectId.zeroId();
    EntryKey key = new EntryKey(commit, into, submitType, mergeStrategy);
    try {
      return cache.get(key, new Loader(key, dest, repo));
    } catch (ExecutionException | UncheckedExecutionException e) {
      log.error(
          String.format(
              "Error checking mergeability of %s into %s (%s)",
              key.commit.name(), key.into.name(), key.submitType.name()),
          e.getCause());
      return false;
    }
  }

  @Override
  public Boolean getIfPresent(
      ObjectId commit, Ref intoRef, SubmitType submitType, String mergeStrategy) {
    return cache.getIfPresent(new EntryKey(commit, toId(intoRef), submitType, mergeStrategy));
  }
}

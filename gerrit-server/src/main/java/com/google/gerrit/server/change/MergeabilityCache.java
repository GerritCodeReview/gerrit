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
import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

@Singleton
public class MergeabilityCache {
  private static final Logger log =
      LoggerFactory.getLogger(MergeabilityCache.class);

  private static final String CACHE_NAME = "mergeability";

  @SuppressWarnings("rawtypes")
  public static Key bindingKey() {
    return Key.get(new TypeLiteral<Cache<EntryKey, Boolean>>() {},
        Names.named(CACHE_NAME));
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, EntryKey.class, Boolean.class)
            .maximumWeight(1 << 20)
            .weigher(MergeabilityWeigher.class);
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

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType,
        String mergeStrategy) {
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

    private void writeObject(ObjectOutputStream out) throws IOException {
      writeNotNull(out, commit);
      writeNotNull(out, into);
      switch (submitType) {
        case FAST_FORWARD_ONLY:
          out.writeChar('F');
          break;
        case MERGE_IF_NECESSARY:
          out.writeChar('M');
          break;
        case REBASE_IF_NECESSARY:
          out.writeChar('R');
          break;
        case MERGE_ALWAYS:
          out.writeChar('A');
          break;
        case CHERRY_PICK:
          out.writeChar('C');
          break;
        default:
          throw new IOException("Invalid submit type: " + submitType);
      }
      writeString(out, mergeStrategy);
    }

    private void readObject(ObjectInputStream in) throws IOException {
      commit = readNotNull(in);
      into = readNotNull(in);
      char t = in.readChar();
      switch (t) {
        case 'F':
          submitType = SubmitType.FAST_FORWARD_ONLY;
          break;
        case 'M':
          submitType = SubmitType.MERGE_IF_NECESSARY;
          break;
        case 'R':
          submitType = SubmitType.REBASE_IF_NECESSARY;
          break;
        case 'A':
          submitType = SubmitType.MERGE_ALWAYS;
          break;
        case 'C':
          submitType = SubmitType.CHERRY_PICK;
          break;
        default:
          throw new IOException("Invalid submit type code: " + t);
      }
      mergeStrategy = readString(in);
    }
  }

  private static class MergeabilityWeigher
      implements Weigher<EntryKey, Boolean> {
    @Override
    public int weigh(EntryKey k, Boolean v) {
      // Size of EntryKey, 64-bit JVM.
      return 16 + 2 * (16 + 20) + 8 + 8 + 2 * k.mergeStrategy.length()
          + 1; // Size of Boolean.
    }
  }

  private final Cache<EntryKey, Boolean> cache;
  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> db;
  private final SubmitStrategyFactory submitStrategyFactory;

  @Inject
  MergeabilityCache(@Named(CACHE_NAME) Cache<EntryKey, Boolean> cache,
      GitRepositoryManager repoManager,
      Provider<ReviewDb> db,
      SubmitStrategyFactory submitStrategyFactory) {
    this.cache = cache;
    this.repoManager = repoManager;
    this.db = db;
    this.submitStrategyFactory = submitStrategyFactory;
  }

  public Boolean getIfPresent(ObjectId commit, ObjectId into,
      SubmitType submitType, String mergeStrategy) {
    return cache.getIfPresent(
        new EntryKey(commit, into, submitType, mergeStrategy));
  }

  public boolean get(ObjectId commit, ObjectId into, SubmitType submitType,
      String mergeStrategy, Branch.NameKey dest) {
    EntryKey key = new EntryKey(commit, into, submitType, mergeStrategy);
    Boolean result = cache.getIfPresent(key);
    if (result != null) {
      return result;
    }

    Project.NameKey p = dest.getParentKey();
    try {
      Repository repo = repoManager.openRepository(p);
      try {
        return load(key, dest, repo);
      } finally {
        repo.close();
      }
    } catch (IOException e) {
      return failed(key, e);
    }
  }

  public boolean get(ObjectId commit, ObjectId into, SubmitType submitType,
      String mergeStrategy, Branch.NameKey dest, Repository repo) {
    EntryKey key = new EntryKey(commit, into, submitType, mergeStrategy);
    Boolean result = cache.getIfPresent(key);
    if (result != null) {
      return result;
    }
    return load(key, dest, repo);
  }

  public boolean load(ObjectId commit, ObjectId into, SubmitType submitType,
      String mergeStrategy, Branch.NameKey dest, Repository repo) {
    EntryKey key = new EntryKey(commit, into, submitType, mergeStrategy);
    try {
      boolean result = isMergeable(key, dest, repo);
      // Racy put, but callers interested in avoiding duplicate work should not
      // be using load directly anyway.
      cache.put(key, result);
      return result;
    } catch (IOException e) {
      return failed(key, e);
    }
  }

  private boolean load(final EntryKey key, final Branch.NameKey dest,
      final Repository repo) {
    try {
      return cache.get(key, new Callable<Boolean>() {
        @Override
        public Boolean call() throws IOException {
          return isMergeable(key, dest, repo);
        }
      });
    } catch (ExecutionException e) {
      return failed(key, e);
    }
  }

  private boolean isMergeable(EntryKey key, Branch.NameKey dest,
      Repository repo) throws IOException {
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
          dest).dryRun(tip, rev);
    } catch (MergeException | IOException | NoSuchProjectException e) {
      return failed(key, e);
    } finally {
      rw.release();
    }
  }

  private static Set<RevCommit> alreadyAccepted(RevWalk rw, Collection<Ref> refs)
      throws MissingObjectException, IOException {
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
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return (CodeReviewCommit) rw.parseCommit(id);
  }

  private static boolean failed(EntryKey key, Throwable t) {
    log.error(String.format("Error checking mergeability of %s into %s (%s)",
        key.commit.name(), key.into.name(), key.submitType.name()), t);
    return false;
  }
}

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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class for managing an incrementing sequence backed by a git repository.
 *
 * <p>The current sequence number is stored as UTF-8 text in a blob pointed to by a ref in the
 * {@code refs/sequences/*} namespace. Multiple processes can share the same sequence by
 * incrementing the counter using normal git ref updates. To amortize the cost of these ref updates,
 * processes can increment the counter by a larger number and hand out numbers from that range in
 * memory until they run out. This means concurrent processes will hand out somewhat non-monotonic
 * numbers.
 */
public class RepoSequence {
  public interface Seed {
    int get() throws OrmException;
  }

  @VisibleForTesting
  static RetryerBuilder<RefUpdate.Result> retryerBuilder() {
    return RetryerBuilder.<RefUpdate.Result>newBuilder()
        .retryIfResult(Predicates.equalTo(RefUpdate.Result.LOCK_FAILURE))
        .withWaitStrategy(
            WaitStrategies.join(
                WaitStrategies.exponentialWait(5, TimeUnit.SECONDS),
                WaitStrategies.randomWait(50, TimeUnit.MILLISECONDS)))
        .withStopStrategy(StopStrategies.stopAfterDelay(30, TimeUnit.SECONDS));
  }

  private static final Retryer<RefUpdate.Result> RETRYER = retryerBuilder().build();

  private final GitRepositoryManager repoManager;
  private final Project.NameKey projectName;
  private final String refName;
  private final Seed seed;
  private final int batchSize;
  private final Runnable afterReadRef;
  private final Retryer<RefUpdate.Result> retryer;

  // Protects all non-final fields.
  private final Lock counterLock;

  private int limit;
  private int counter;

  @VisibleForTesting int acquireCount;

  public RepoSequence(
      GitRepositoryManager repoManager,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize) {
    this(repoManager, projectName, name, seed, batchSize, Runnables.doNothing(), RETRYER);
  }

  @VisibleForTesting
  RepoSequence(
      GitRepositoryManager repoManager,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize,
      Runnable afterReadRef,
      Retryer<RefUpdate.Result> retryer) {
    this.repoManager = checkNotNull(repoManager, "repoManager");
    this.projectName = checkNotNull(projectName, "projectName");
    this.refName = RefNames.REFS_SEQUENCES + checkNotNull(name, "name");
    this.seed = checkNotNull(seed, "seed");

    checkArgument(batchSize > 0, "expected batchSize > 0, got: %s", batchSize);
    this.batchSize = batchSize;
    this.afterReadRef = checkNotNull(afterReadRef, "afterReadRef");
    this.retryer = checkNotNull(retryer, "retryer");

    counterLock = new ReentrantLock(true);
  }

  public int next() throws OrmException {
    counterLock.lock();
    try {
      if (counter >= limit) {
        acquire(batchSize);
      }
      return counter++;
    } finally {
      counterLock.unlock();
    }
  }

  public ImmutableList<Integer> next(int count) throws OrmException {
    if (count == 0) {
      return ImmutableList.of();
    }
    checkArgument(count > 0, "count is negative: %s", count);
    counterLock.lock();
    try {
      List<Integer> ids = new ArrayList<>(count);
      while (counter < limit) {
        ids.add(counter++);
        if (ids.size() == count) {
          return ImmutableList.copyOf(ids);
        }
      }
      acquire(Math.max(count - ids.size(), batchSize));
      while (ids.size() < count) {
        ids.add(counter++);
      }
      return ImmutableList.copyOf(ids);
    } finally {
      counterLock.unlock();
    }
  }

  @VisibleForTesting
  public void set(int val) throws OrmException {
    // Don't bother spinning. This is only for tests, and a test that calls set
    // concurrently with other writes is doing it wrong.
    counterLock.lock();
    try {
      try (Repository repo = repoManager.openRepository(projectName);
          RevWalk rw = new RevWalk(repo)) {
        checkResult(store(repo, rw, null, val));
        counter = limit;
      } catch (IOException e) {
        throw new OrmException(e);
      }
    } finally {
      counterLock.unlock();
    }
  }

  private void acquire(int count) throws OrmException {
    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      TryAcquire attempt = new TryAcquire(repo, rw, count);
      checkResult(retryer.call(attempt));
      counter = attempt.next;
      limit = counter + count;
      acquireCount++;
    } catch (ExecutionException | RetryException e) {
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), OrmException.class);
      }
      throw new OrmException(e);
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private void checkResult(RefUpdate.Result result) throws OrmException {
    if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED) {
      throw new OrmException("failed to update " + refName + ": " + result);
    }
  }

  private class TryAcquire implements Callable<RefUpdate.Result> {
    private final Repository repo;
    private final RevWalk rw;
    private final int count;

    private int next;

    private TryAcquire(Repository repo, RevWalk rw, int count) {
      this.repo = repo;
      this.rw = rw;
      this.count = count;
    }

    @Override
    public RefUpdate.Result call() throws Exception {
      Ref ref = repo.exactRef(refName);
      afterReadRef.run();
      ObjectId oldId;
      if (ref == null) {
        oldId = ObjectId.zeroId();
        next = seed.get();
      } else {
        oldId = ref.getObjectId();
        next = parse(oldId);
      }
      return store(repo, rw, oldId, next + count);
    }

    private int parse(ObjectId id) throws IOException, OrmException {
      ObjectLoader ol = rw.getObjectReader().open(id, OBJ_BLOB);
      if (ol.getType() != OBJ_BLOB) {
        // In theory this should be thrown by open but not all implementations
        // may do it properly (certainly InMemoryRepository doesn't).
        throw new IncorrectObjectTypeException(id, OBJ_BLOB);
      }
      String str = CharMatcher.whitespace().trimFrom(new String(ol.getCachedBytes(), UTF_8));
      Integer val = Ints.tryParse(str);
      if (val == null) {
        throw new OrmException("invalid value in " + refName + " blob at " + id.name());
      }
      return val;
    }
  }

  private RefUpdate.Result store(Repository repo, RevWalk rw, @Nullable ObjectId oldId, int val)
      throws IOException {
    ObjectId newId;
    try (ObjectInserter ins = repo.newObjectInserter()) {
      newId = ins.insert(OBJ_BLOB, Integer.toString(val).getBytes(UTF_8));
      ins.flush();
    }
    RefUpdate ru = repo.updateRef(refName);
    if (oldId != null) {
      ru.setExpectedOldObjectId(oldId);
    }
    ru.setNewObjectId(newId);
    ru.setForceUpdate(true); // Required for non-commitish updates.
    return ru.update(rw);
  }
}

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
import static com.google.gerrit.reviewdb.client.RefNames.REFS;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_SEQUENCES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

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
  @FunctionalInterface
  public interface Seed {
    int get();
  }

  @VisibleForTesting
  static RetryerBuilder<RefUpdate> retryerBuilder() {
    return RetryerBuilder.<RefUpdate>newBuilder()
        .retryIfResult(ru -> ru != null && RefUpdate.Result.LOCK_FAILURE.equals(ru.getResult()))
        .withWaitStrategy(
            WaitStrategies.join(
                WaitStrategies.exponentialWait(5, TimeUnit.SECONDS),
                WaitStrategies.randomWait(50, TimeUnit.MILLISECONDS)))
        .withStopStrategy(StopStrategies.stopAfterDelay(30, TimeUnit.SECONDS));
  }

  private static final Retryer<RefUpdate> RETRYER = retryerBuilder().build();

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final Project.NameKey projectName;
  private final String refName;
  private final Seed seed;
  private final int floor;
  private final int batchSize;
  private final Runnable afterReadRef;
  private final Retryer<RefUpdate> retryer;

  // Protects all non-final fields.
  private final Lock counterLock;

  private int limit;
  private int counter;

  @VisibleForTesting int acquireCount;

  public RepoSequence(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize) {
    this(
        repoManager,
        gitRefUpdated,
        projectName,
        name,
        seed,
        batchSize,
        Runnables.doNothing(),
        RETRYER,
        0);
  }

  public RepoSequence(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize,
      int floor) {
    this(
        repoManager,
        gitRefUpdated,
        projectName,
        name,
        seed,
        batchSize,
        Runnables.doNothing(),
        RETRYER,
        floor);
  }

  @VisibleForTesting
  RepoSequence(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize,
      Runnable afterReadRef,
      Retryer<RefUpdate> retryer) {
    this(repoManager, gitRefUpdated, projectName, name, seed, batchSize, afterReadRef, retryer, 0);
  }

  RepoSequence(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize,
      Runnable afterReadRef,
      Retryer<RefUpdate> retryer,
      int floor) {
    this.repoManager = requireNonNull(repoManager, "repoManager");
    this.gitRefUpdated = requireNonNull(gitRefUpdated, "gitRefUpdated");
    this.projectName = requireNonNull(projectName, "projectName");

    checkArgument(
        name != null
            && !name.startsWith(REFS)
            && !name.startsWith(REFS_SEQUENCES.substring(REFS.length())),
        "name should be a suffix to follow \"refs/sequences/\", got: %s",
        name);
    this.refName = RefNames.REFS_SEQUENCES + name;

    this.seed = requireNonNull(seed, "seed");
    this.floor = floor;

    checkArgument(batchSize > 0, "expected batchSize > 0, got: %s", batchSize);
    this.batchSize = batchSize;
    this.afterReadRef = requireNonNull(afterReadRef, "afterReadRef");
    this.retryer = requireNonNull(retryer, "retryer");

    counterLock = new ReentrantLock(true);
  }

  public int next() {
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

  public ImmutableList<Integer> next(int count) {
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
  public void set(int val) {
    // Don't bother spinning. This is only for tests, and a test that calls set
    // concurrently with other writes is doing it wrong.
    counterLock.lock();
    try {
      try (Repository repo = repoManager.openRepository(projectName);
          RevWalk rw = new RevWalk(repo)) {
        IntBlob.store(repo, rw, projectName, refName, null, val, gitRefUpdated);
        counter = limit;
      } catch (IOException e) {
        throw new StorageException(e);
      }
    } finally {
      counterLock.unlock();
    }
  }

  public void increaseTo(int val) {
    counterLock.lock();
    try {
      try (Repository repo = repoManager.openRepository(projectName);
          RevWalk rw = new RevWalk(repo)) {
        TryIncreaseTo attempt = new TryIncreaseTo(repo, rw, val);
        RefUpdate ru = retryer.call(attempt);
        // Null update is a sentinel meaning nothing to do.
        if (ru != null) {
          RefUpdateUtil.checkResult(ru);
        }
        counter = limit;
      } catch (ExecutionException | RetryException e) {
        if (e.getCause() != null) {
          Throwables.throwIfInstanceOf(e.getCause(), StorageException.class);
        }
        throw new StorageException(e);
      } catch (IOException e) {
        throw new StorageException(e);
      }
    } finally {
      counterLock.unlock();
    }
  }

  private void acquire(int count) {
    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      TryAcquire attempt = new TryAcquire(repo, rw, count);
      RefUpdateUtil.checkResult(retryer.call(attempt));
      counter = attempt.next;
      limit = counter + count;
      acquireCount++;
    } catch (ExecutionException | RetryException e) {
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), StorageException.class);
      }
      throw new StorageException(e);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private class TryAcquire implements Callable<RefUpdate> {
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
    public RefUpdate call() throws Exception {
      Optional<IntBlob> blob = IntBlob.parse(repo, refName, rw);
      afterReadRef.run();
      ObjectId oldId;
      if (!blob.isPresent()) {
        oldId = ObjectId.zeroId();
        next = seed.get();
      } else {
        oldId = blob.get().id();
        next = blob.get().value();
      }
      next = Math.max(floor, next);
      return IntBlob.tryStore(repo, rw, projectName, refName, oldId, next + count, gitRefUpdated);
    }
  }

  private class TryIncreaseTo implements Callable<RefUpdate> {
    private final Repository repo;
    private final RevWalk rw;
    private final int value;

    private TryIncreaseTo(Repository repo, RevWalk rw, int value) {
      this.repo = repo;
      this.rw = rw;
      this.value = value;
    }

    @Override
    public RefUpdate call() throws Exception {
      Optional<IntBlob> blob = IntBlob.parse(repo, refName, rw);
      afterReadRef.run();
      ObjectId oldId;
      if (!blob.isPresent()) {
        oldId = ObjectId.zeroId();
      } else {
        oldId = blob.get().id();
        int next = blob.get().value();
        if (next >= value) {
          // A concurrent write updated the ref already to this or a higher value; return null as a
          // sentinel meaning nothing to do. Returning RefUpdate doesn't give us the flexibility to
          // return any other kind of sentinel, since it's a fairly thick object.
          return null;
        }
      }
      return IntBlob.tryStore(repo, rw, projectName, refName, oldId, value, gitRefUpdated);
    }
  }

  public static ReceiveCommand storeNew(ObjectInserter ins, String name, int val)
      throws IOException {
    ObjectId newId = ins.insert(OBJ_BLOB, Integer.toString(val).getBytes(UTF_8));
    return new ReceiveCommand(ObjectId.zeroId(), newId, RefNames.REFS_SEQUENCES + name);
  }
}

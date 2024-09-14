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
import static com.google.gerrit.entities.RefNames.REFS;
import static com.google.gerrit.entities.RefNames.REFS_SEQUENCES;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.REPO_SEQ;
import static java.util.Objects.requireNonNull;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.Sequence;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
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
public class RepoSequence implements Sequence {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FunctionalInterface
  public interface Seed {
    int get();
  }

  public static class RepoSequenceModule extends FactoryModule {
    public static final String SECTION_NOTE_DB = "noteDb";
    public static final String KEY_SEQUENCE_BATCH_SIZE = "sequenceBatchSize";
    public static final int DEFAULT_ACCOUNTS_SEQUENCE_BATCH_SIZE = 1;
    public static final int DEFAULT_GROUPS_SEQUENCE_BATCH_SIZE = 1;
    public static final int DEFAULT_CHANGES_SEQUENCE_BATCH_SIZE = 20;

    @Provides
    @Named(NAME_ACCOUNTS)
    Sequence getAccountSequence(
        @GerritServerConfig Config cfg,
        GitRepositoryManager repoManager,
        AllUsersName allUsers,
        GitReferenceUpdated gitReferenceUpdated) {
      int accountBatchSize =
          cfg.getInt(
              SECTION_NOTE_DB,
              NAME_ACCOUNTS,
              KEY_SEQUENCE_BATCH_SIZE,
              DEFAULT_ACCOUNTS_SEQUENCE_BATCH_SIZE);
      return new RepoSequence(
          repoManager,
          gitReferenceUpdated,
          allUsers,
          NAME_ACCOUNTS,
          () -> Sequences.FIRST_ACCOUNT_ID,
          accountBatchSize);
    }

    @Provides
    @Named(NAME_GROUPS)
    Sequence getGroupSequence(
        GitRepositoryManager repoManager,
        AllUsersName allUsers,
        GitReferenceUpdated gitReferenceUpdated) {
      return new RepoSequence(
          repoManager,
          gitReferenceUpdated,
          allUsers,
          NAME_GROUPS,
          () -> Sequences.FIRST_GROUP_ID,
          DEFAULT_GROUPS_SEQUENCE_BATCH_SIZE);
    }

    @Provides
    @Named(NAME_CHANGES)
    Sequence getChangesSequence(
        @GerritServerConfig Config cfg,
        GitRepositoryManager repoManager,
        AllProjectsName allProjects,
        GitReferenceUpdated gitReferenceUpdated) {
      int changeBatchSize =
          cfg.getInt(
              SECTION_NOTE_DB,
              NAME_CHANGES,
              KEY_SEQUENCE_BATCH_SIZE,
              DEFAULT_CHANGES_SEQUENCE_BATCH_SIZE);
      return new RepoSequence(
          repoManager,
          gitReferenceUpdated,
          allProjects,
          NAME_CHANGES,
          () -> Sequences.FIRST_CHANGE_ID,
          changeBatchSize);
    }
  }

  /** A groups sequence provider that does not fire git reference updates. */
  public static class DisabledGitRefUpdatedRepoGroupsSequenceProvider
      implements Provider<Sequence> {
    private static final int DEFAULT_GROUPS_SEQUENCE_BATCH_SIZE = 1;
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;

    @Inject
    DisabledGitRefUpdatedRepoGroupsSequenceProvider(
        GitRepositoryManager repoManager, AllUsersName allUsersName) {
      this.repoManager = repoManager;
      this.allUsers = allUsersName;
    }

    @Override
    public Sequence get() {
      return new RepoSequence(
          repoManager,
          GitReferenceUpdated.DISABLED,
          allUsers,
          NAME_GROUPS,
          () -> Sequences.FIRST_GROUP_ID,
          DEFAULT_GROUPS_SEQUENCE_BATCH_SIZE);
    }
  }

  @VisibleForTesting
  static RetryerBuilder<ImmutableList<Integer>> retryerBuilder() {
    return RetryerBuilder.<ImmutableList<Integer>>newBuilder()
        .retryIfException(
            t -> t instanceof StorageException && t.getCause() instanceof LockFailureException)
        .withWaitStrategy(
            WaitStrategies.join(
                WaitStrategies.exponentialWait(5, TimeUnit.SECONDS),
                WaitStrategies.randomWait(50, TimeUnit.MILLISECONDS)))
        .withStopStrategy(StopStrategies.stopAfterDelay(30, TimeUnit.SECONDS));
  }

  private static final Retryer<ImmutableList<Integer>> RETRYER = retryerBuilder().build();

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final Project.NameKey projectName;
  private final String refName;
  private final Seed seed;
  private final int floor;
  private final int batchSize;
  private final Runnable afterReadRef;
  private final Retryer<ImmutableList<Integer>> retryer;

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

  @VisibleForTesting
  RepoSequence(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize,
      Runnable afterReadRef,
      Retryer<ImmutableList<Integer>> retryer) {
    this(repoManager, gitRefUpdated, projectName, name, seed, batchSize, afterReadRef, retryer, 0);
  }

  private RepoSequence(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Project.NameKey projectName,
      String name,
      Seed seed,
      int batchSize,
      Runnable afterReadRef,
      Retryer<ImmutableList<Integer>> retryer,
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

    logger.atFine().log("sequence batch size for %s is %s", name, batchSize);
    counterLock = new ReentrantLock(true);
  }

  /**
   * Retrieves the next available sequence number.
   *
   * <p>This method is thread-safe.
   *
   * @return the next available sequence number
   */
  @Override
  public int next() {
    return Iterables.getOnlyElement(next(1));
  }

  /**
   * Retrieves the next N available sequence number.
   *
   * <p>This method is thread-safe.
   *
   * @param count the number of sequence numbers which should be returned
   * @return the next N available sequence numbers
   */
  @Override
  public ImmutableList<Integer> next(int count) {
    if (count == 0) {
      return ImmutableList.of();
    }
    checkArgument(count > 0, "count is negative: %s", count);

    try {
      return retryer.call(
          () -> {
            counterLock.lock();
            try {
              if (count == 1) {
                if (counter >= limit) {
                  acquire(batchSize);
                }
                return ImmutableList.of(counter++);
              }

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
          });
    } catch (ExecutionException | RetryException e) {
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), StorageException.class);
      }
      throw new StorageException(e);
    }
  }

  /**
   * Updates the next available sequence number in NoteDb in order to have a batch of sequence
   * numbers available that can be handed out. {@link #counter} stores the next sequence number that
   * can be handed out. When {@link #limit} is reached a new batch of sequence numbers needs to be
   * retrieved by calling this method.
   *
   * <p><strong>Note:</strong> Callers are required to acquire the {@link #counterLock} before
   * calling this method.
   *
   * @param count the number of sequence numbers which should be retrieved
   */
  private void acquire(int count) {
    try (RefUpdateContext ctx = RefUpdateContext.open(REPO_SEQ)) {
      try (Repository repo = repoManager.openRepository(projectName);
          RevWalk rw = new RevWalk(repo)) {
        logger.atFine().log("acquire %d ids on %s in %s", count, refName, projectName);
        Optional<IntBlob> blob = IntBlob.parse(repo, refName, rw);
        afterReadRef.run();
        ObjectId oldId;
        int next;
        if (!blob.isPresent()) {
          oldId = ObjectId.zeroId();
          next = seed.get();
        } else {
          oldId = blob.get().id();
          next = blob.get().value();
        }
        next = Math.max(floor, next);
        RefUpdate refUpdate =
            IntBlob.tryStore(repo, rw, projectName, refName, oldId, next + count, gitRefUpdated);
        RefUpdateUtil.checkResult(refUpdate);
        counter = next;
        limit = counter + count;
        acquireCount++;
      } catch (IOException e) {
        throw new StorageException(e);
      }
    }
  }

  @Override
  public void storeNew(int value) {
    counterLock.lock();
    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      Optional<IntBlob> blob = IntBlob.parse(repo, refName, rw);
      afterReadRef.run();
      ObjectId oldId;
      if (!blob.isPresent()) {
        oldId = ObjectId.zeroId();
      } else {
        oldId = blob.get().id();
      }
      RefUpdate refUpdate =
          IntBlob.tryStore(repo, rw, projectName, refName, oldId, value, gitRefUpdated);
      RefUpdateUtil.checkResult(refUpdate);
      counter = value;
      limit = counter + batchSize;
      acquireCount++;
    } catch (IOException e) {
      throw new StorageException(e);
    } finally {
      counterLock.unlock();
    }
  }

  @Override
  public int current() {
    counterLock.lock();
    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      Optional<IntBlob> blob = IntBlob.parse(repo, refName, rw);
      int current;
      if (!blob.isPresent()) {
        current = seed.get();
      } else {
        current = blob.get().value();
      }
      return current;
    } catch (IOException e) {
      throw new StorageException(e);
    } finally {
      counterLock.unlock();
    }
  }

  /**
   * Retrieves the last returned sequence number.
   *
   * <p>Explicitly calls {@link #next()} if this instance didn't return sequence number until now.
   */
  @Override
  public int last() {
    if (counter == 0) {
      @SuppressWarnings("unused")
      var unused = next();
    }
    return counter - 1;
  }
}

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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.fail;

import com.github.rholder.retry.BlockStrategy;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RepoSequenceTest {
  private static final Retryer<RefUpdate.Result> RETRYER =
      RepoSequence.retryerBuilder()
          .withBlockStrategy(
              new BlockStrategy() {
                @Override
                public void block(long sleepTime) {
                  // Don't sleep in tests.
                }
              })
          .build();

  @Rule public ExpectedException exception = ExpectedException.none();

  private InMemoryRepositoryManager repoManager;
  private Project.NameKey project;

  @Before
  public void setUp() throws Exception {
    repoManager = new InMemoryRepositoryManager();
    project = new Project.NameKey("project");
    repoManager.createRepository(project);
  }

  @Test
  public void oneCaller() throws Exception {
    int max = 20;
    for (int batchSize = 1; batchSize <= 10; batchSize++) {
      String name = "batch-size-" + batchSize;
      RepoSequence s = newSequence(name, 1, batchSize);
      for (int i = 1; i <= max; i++) {
        try {
          assertThat(s.next()).named("i=" + i + " for " + name).isEqualTo(i);
        } catch (OrmException e) {
          throw new AssertionError("failed batchSize=" + batchSize + ", i=" + i, e);
        }
      }
      assertThat(s.acquireCount)
          .named("acquireCount for " + name)
          .isEqualTo(divCeil(max, batchSize));
    }
  }

  @Test
  public void oneCallerNoLoop() throws Exception {
    RepoSequence s = newSequence("id", 1, 3);
    assertThat(s.acquireCount).isEqualTo(0);

    assertThat(s.next()).isEqualTo(1);
    assertThat(s.acquireCount).isEqualTo(1);
    assertThat(s.next()).isEqualTo(2);
    assertThat(s.acquireCount).isEqualTo(1);
    assertThat(s.next()).isEqualTo(3);
    assertThat(s.acquireCount).isEqualTo(1);

    assertThat(s.next()).isEqualTo(4);
    assertThat(s.acquireCount).isEqualTo(2);
    assertThat(s.next()).isEqualTo(5);
    assertThat(s.acquireCount).isEqualTo(2);
    assertThat(s.next()).isEqualTo(6);
    assertThat(s.acquireCount).isEqualTo(2);

    assertThat(s.next()).isEqualTo(7);
    assertThat(s.acquireCount).isEqualTo(3);
    assertThat(s.next()).isEqualTo(8);
    assertThat(s.acquireCount).isEqualTo(3);
    assertThat(s.next()).isEqualTo(9);
    assertThat(s.acquireCount).isEqualTo(3);

    assertThat(s.next()).isEqualTo(10);
    assertThat(s.acquireCount).isEqualTo(4);
  }

  @Test
  public void twoCallers() throws Exception {
    RepoSequence s1 = newSequence("id", 1, 3);
    RepoSequence s2 = newSequence("id", 1, 3);

    // s1 acquires 1-3; s2 acquires 4-6.
    assertThat(s1.next()).isEqualTo(1);
    assertThat(s2.next()).isEqualTo(4);
    assertThat(s1.next()).isEqualTo(2);
    assertThat(s2.next()).isEqualTo(5);
    assertThat(s1.next()).isEqualTo(3);
    assertThat(s2.next()).isEqualTo(6);

    // s2 acquires 7-9; s1 acquires 10-12.
    assertThat(s2.next()).isEqualTo(7);
    assertThat(s1.next()).isEqualTo(10);
    assertThat(s2.next()).isEqualTo(8);
    assertThat(s1.next()).isEqualTo(11);
    assertThat(s2.next()).isEqualTo(9);
    assertThat(s1.next()).isEqualTo(12);
  }

  @Test
  public void populateEmptyRefWithStartValue() throws Exception {
    RepoSequence s = newSequence("id", 1234, 10);
    assertThat(s.next()).isEqualTo(1234);
    assertThat(readBlob("id")).isEqualTo("1244");
  }

  @Test
  public void startIsIgnoredIfRefIsPresent() throws Exception {
    writeBlob("id", "1234");
    RepoSequence s = newSequence("id", 3456, 10);
    assertThat(s.next()).isEqualTo(1234);
    assertThat(readBlob("id")).isEqualTo("1244");
  }

  @Test
  public void retryOnLockFailure() throws Exception {
    // Seed existing ref value.
    writeBlob("id", "1");

    final AtomicBoolean doneBgUpdate = new AtomicBoolean(false);
    Runnable bgUpdate =
        new Runnable() {
          @Override
          public void run() {
            if (!doneBgUpdate.getAndSet(true)) {
              writeBlob("id", "1234");
            }
          }
        };

    RepoSequence s = newSequence("id", 1, 10, bgUpdate, RETRYER);
    assertThat(doneBgUpdate.get()).isFalse();
    assertThat(s.next()).isEqualTo(1234);
    // Single acquire call that results in 2 ref reads.
    assertThat(s.acquireCount).isEqualTo(1);
    assertThat(doneBgUpdate.get()).isTrue();
  }

  @Test
  public void failOnInvalidValue() throws Exception {
    ObjectId id = writeBlob("id", "not a number");
    exception.expect(OrmException.class);
    exception.expectMessage("invalid value in refs/sequences/id blob at " + id.name());
    newSequence("id", 1, 3).next();
  }

  @Test
  public void failOnWrongType() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<Repository> tr = new TestRepository<>(repo);
      tr.branch(RefNames.REFS_SEQUENCES + "id").commit().create();
      try {
        newSequence("id", 1, 3).next();
        fail();
      } catch (OrmException e) {
        assertThat(e.getCause()).isInstanceOf(ExecutionException.class);
        assertThat(e.getCause().getCause()).isInstanceOf(IncorrectObjectTypeException.class);
      }
    }
  }

  @Test
  public void failAfterRetryerGivesUp() throws Exception {
    final AtomicInteger bgCounter = new AtomicInteger(1234);
    Runnable bgUpdate =
        new Runnable() {
          @Override
          public void run() {
            writeBlob("id", Integer.toString(bgCounter.getAndAdd(1000)));
          }
        };
    RepoSequence s =
        newSequence(
            "id",
            1,
            10,
            bgUpdate,
            RetryerBuilder.<RefUpdate.Result>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build());
    exception.expect(OrmException.class);
    exception.expectMessage("failed to update refs/sequences/id: LOCK_FAILURE");
    s.next();
  }

  @Test
  public void nextWithCountOneCaller() throws Exception {
    RepoSequence s = newSequence("id", 1, 3);
    assertThat(s.next(2)).containsExactly(1, 2).inOrder();
    assertThat(s.acquireCount).isEqualTo(1);
    assertThat(s.next(2)).containsExactly(3, 4).inOrder();
    assertThat(s.acquireCount).isEqualTo(2);
    assertThat(s.next(2)).containsExactly(5, 6).inOrder();
    assertThat(s.acquireCount).isEqualTo(2);

    assertThat(s.next(3)).containsExactly(7, 8, 9).inOrder();
    assertThat(s.acquireCount).isEqualTo(3);
    assertThat(s.next(3)).containsExactly(10, 11, 12).inOrder();
    assertThat(s.acquireCount).isEqualTo(4);
    assertThat(s.next(3)).containsExactly(13, 14, 15).inOrder();
    assertThat(s.acquireCount).isEqualTo(5);

    assertThat(s.next(7)).containsExactly(16, 17, 18, 19, 20, 21, 22).inOrder();
    assertThat(s.acquireCount).isEqualTo(6);
    assertThat(s.next(7)).containsExactly(23, 24, 25, 26, 27, 28, 29).inOrder();
    assertThat(s.acquireCount).isEqualTo(7);
    assertThat(s.next(7)).containsExactly(30, 31, 32, 33, 34, 35, 36).inOrder();
    assertThat(s.acquireCount).isEqualTo(8);
  }

  @Test
  public void nextWithCountMultipleCallers() throws Exception {
    RepoSequence s1 = newSequence("id", 1, 3);
    RepoSequence s2 = newSequence("id", 1, 4);

    assertThat(s1.next(2)).containsExactly(1, 2).inOrder();
    assertThat(s1.acquireCount).isEqualTo(1);

    // s1 hasn't exhausted its last batch.
    assertThat(s2.next(2)).containsExactly(4, 5).inOrder();
    assertThat(s2.acquireCount).isEqualTo(1);

    // s1 acquires again to cover this request, plus a whole new batch.
    assertThat(s1.next(3)).containsExactly(3, 8, 9);
    assertThat(s1.acquireCount).isEqualTo(2);

    // s2 hasn't exhausted its last batch, do so now.
    assertThat(s2.next(2)).containsExactly(6, 7);
    assertThat(s2.acquireCount).isEqualTo(1);
  }

  private RepoSequence newSequence(String name, int start, int batchSize) {
    return newSequence(name, start, batchSize, Runnables.doNothing(), RETRYER);
  }

  private RepoSequence newSequence(
      String name,
      final int start,
      int batchSize,
      Runnable afterReadRef,
      Retryer<RefUpdate.Result> retryer) {
    return new RepoSequence(
        repoManager,
        project,
        name,
        new RepoSequence.Seed() {
          @Override
          public int get() {
            return start;
          }
        },
        batchSize,
        afterReadRef,
        retryer);
  }

  private ObjectId writeBlob(String sequenceName, String value) {
    String refName = RefNames.REFS_SEQUENCES + sequenceName;
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId newId = ins.insert(OBJ_BLOB, value.getBytes(UTF_8));
      ins.flush();
      RefUpdate ru = repo.updateRef(refName);
      ru.setNewObjectId(newId);
      assertThat(ru.forceUpdate()).isAnyOf(RefUpdate.Result.NEW, RefUpdate.Result.FORCED);
      return newId;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String readBlob(String sequenceName) throws Exception {
    String refName = RefNames.REFS_SEQUENCES + sequenceName;
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId id = repo.exactRef(refName).getObjectId();
      return new String(rw.getObjectReader().open(id).getCachedBytes(), UTF_8);
    }
  }

  private static long divCeil(float a, float b) {
    return Math.round(Math.ceil(a / b));
  }
}

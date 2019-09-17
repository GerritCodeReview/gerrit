// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.globalrefdb;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.reviewdb.client.RefNames;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

public class GlobalRefDatabaseTest extends AbstractDaemonTest {

  private ObjectId objectId1 = new ObjectId(1, 2, 3, 4, 5);
  private ObjectId objectId2 = new ObjectId(1, 2, 3, 4, 6);
  private ObjectId objectId3 = new ObjectId(1, 2, 3, 4, 7);

  private String refName = RefNames.REFS_HEADS + "branch";
  private Ref ref1 = ref(refName, objectId1);
  private Ref ref2 = ref(refName, objectId2);
  private Ref nullRef = zerosRef(refName);
  private Ref initialRef = ref(refName, ObjectId.zeroId());

  private Executor executor = Executors.newFixedThreadPool(1);

  private GlobalRefDatabase objectUnderTest;

  @Before
  public void setup() {
    this.objectUnderTest = new FakeGlobalRefDatabase();
  }

  @Test
  public void shouldCreateEntryInTheGlobalRefDBWhenNullRef() {
    assertThat(objectUnderTest.compareAndPut(project, nullRef, objectId1)).isTrue();
  }

  @Test
  public void shouldCreateEntryWhenProjectDoesNotExistsInTheGlobalRefDB() {
    assertThat(objectUnderTest.compareAndPut(project, initialRef, objectId1)).isTrue();
  }

  @Test
  public void shouldUpdateEntryWithNewRef() {
    objectUnderTest.compareAndPut(project, zerosRef(ref1.getName()), objectId1);

    assertThat(objectUnderTest.compareAndPut(project, ref1, objectId2)).isTrue();
  }

  @Test
  public void shouldRejectUpdateWhenLocalRepoIsOutdated() {
    objectUnderTest.compareAndPut(project, nullRef, objectId1);
    objectUnderTest.compareAndPut(project, ref1, objectId2);

    assertThat(objectUnderTest.compareAndPut(project, ref1, objectId3)).isFalse();
  }

  @Test
  public void shouldRejectUpdateWhenLocalRepoIsAheadOfTheGlobalRefDB() {
    objectUnderTest.compareAndPut(project, nullRef, objectId1);

    assertThat(objectUnderTest.compareAndPut(project, ref2, objectId3)).isFalse();
  }

  @Test
  public void shouldReturnIsUpToDateWhenProjectDoesNotExistsInTheGlobalRefDB() {
    assertThat(objectUnderTest.isUpToDate(project, initialRef)).isTrue();
  }

  @Test
  public void shouldReturnIsUpToDate() {
    objectUnderTest.compareAndPut(project, nullRef, objectId1);

    assertThat(objectUnderTest.isUpToDate(project, ref1)).isTrue();
  }

  @Test
  public void shouldReturnIsNotUpToDateWhenLocalRepoIsOutdated() {
    objectUnderTest.compareAndPut(project, nullRef, objectId1);

    assertThat(objectUnderTest.isUpToDate(project, nullRef)).isFalse();
  }

  @Test
  public void shouldReturnIsNotUpToDateWhenLocalRepoIsAheadOfTheGlobalRefDB() {
    objectUnderTest.compareAndPut(project, nullRef, objectId1);

    assertThat(objectUnderTest.isUpToDate(project, ref2)).isFalse();
  }

  /*
   * Purpose of this test is to show how the locRef api can be used.
   * This test contains a dummy implementation of the locRef api.
   * This implementation is not an example of how the locRef api should be implemented
   * it is just for simulation of the test scenario.
   *
   * Test scenario:
   * 1. Client 1 acquires lock for give ref and checks if ref is up to date with the global ref-db
   * 2. While Client 1 holds the lock Client 2 is trying to update the ref in the global ref-db
   *
   * Result:
   * Client 2 operation is executed after Client 1 releases the lock.
   */
  @Test
  public void shouldLockRef() throws Exception {
    // this lock is used to make sure that client thread had enough time to start
    CountDownLatch helperLock = new CountDownLatch(1);

    // when
    objectUnderTest.compareAndPut(project, ref1, objectId2);
    try (AutoCloseable refLock = objectUnderTest.lockRef(project, ref1.getName())) {
      // simulate concurrent client trying to execute some operation while current client holds the
      // lock
      executor.execute(new ConcurrentClient(helperLock));

      objectUnderTest.isUpToDate(project, ref2);
    }

    // then
    helperLock.await(1, TimeUnit.SECONDS);
  }

  private Ref ref(String refName, ObjectId objectId) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, refName, objectId);
  }

  private Ref zerosRef(String refName) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NEW, refName, ObjectId.zeroId());
  }

  private class ConcurrentClient implements Runnable {
    CountDownLatch helperLock;

    public ConcurrentClient(CountDownLatch helperLock) {
      this.helperLock = helperLock;
    }

    @Override
    public void run() {
      objectUnderTest.compareAndPut(project, ref2, objectId3);
      helperLock.countDown();
    }
  }
}

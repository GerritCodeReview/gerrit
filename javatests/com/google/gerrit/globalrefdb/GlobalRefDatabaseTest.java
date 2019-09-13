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
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.resetToStrict;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.reviewdb.client.Project;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.easymock.IAnswer;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

public class GlobalRefDatabaseTest {

  private ObjectId objectId1 = new ObjectId(1, 2, 3, 4, 5);
  private ObjectId objectId2 = new ObjectId(1, 2, 3, 4, 6);
  private ObjectId objectId3 = new ObjectId(1, 2, 3, 4, 7);

  private Ref ref1 = ref("Ref", objectId1);
  private Ref ref2 = ref("Ref", objectId2);
  private Ref nullRef = nullRef();
  private Ref initialRef = ref("Ref", ObjectId.zeroId());

  private Project.NameKey projectName = Project.nameKey("Sample-project-name");

  private Executor executor = Executors.newFixedThreadPool(1);

  private final Lock lock = new ReentrantLock();

  private GlobalRefDatabase objectUnderTest;

  @Before
  public void setup() {
    this.objectUnderTest = createNiceMock(GlobalRefDatabase.class);
  }

  @Test
  public void shouldCreateEntryInTheGlobalRefDBWhenNullRef() {
    expect(objectUnderTest.compareAndPut(projectName, nullRef, objectId1)).andStubReturn(true);
    replay(objectUnderTest);

    assertThat(objectUnderTest.compareAndPut(projectName, nullRef, objectId1)).isTrue();
  }

  @Test
  public void shouldCreateEntryWhenProjectDoesNotExistsInTheGlobalRefDB() {
    expect(objectUnderTest.compareAndPut(projectName, initialRef, objectId1)).andStubReturn(true);
    replay(objectUnderTest);

    assertThat(objectUnderTest.compareAndPut(projectName, initialRef, objectId1)).isTrue();
  }

  @Test
  public void shouldUpdateEntryWithNewRef() {
    expect(objectUnderTest.compareAndPut(projectName, ref1, objectId2)).andStubReturn(true);
    replay(objectUnderTest);

    objectUnderTest.compareAndPut(projectName, nullRef(), objectId1);

    assertThat(objectUnderTest.compareAndPut(projectName, ref1, objectId2)).isTrue();
  }

  @Test
  public void shouldRejectUpdateWhenLocalRepoIsOutdated() {
    expect(objectUnderTest.compareAndPut(projectName, ref2, objectId3)).andStubReturn(true);
    replay(objectUnderTest);

    objectUnderTest.compareAndPut(projectName, nullRef, objectId1);
    objectUnderTest.compareAndPut(projectName, ref1, objectId2);

    assertThat(objectUnderTest.compareAndPut(projectName, ref1, objectId3)).isFalse();
  }

  @Test
  public void shouldRejectUpdateWhenLocalRepoIsAheadOfTheGlobalRefDB() {
    expect(objectUnderTest.compareAndPut(projectName, ref1, objectId3)).andStubReturn(true);
    replay(objectUnderTest);

    objectUnderTest.compareAndPut(projectName, nullRef, objectId1);

    assertThat(objectUnderTest.compareAndPut(projectName, ref2, objectId3)).isFalse();
  }

  @Test
  public void shouldReturnIsUpToDateWhenProjectDoesNotExistsInTheGlobalRefDB() {
    expect(objectUnderTest.isUpToDate(projectName, initialRef)).andStubReturn(true);
    replay(objectUnderTest);

    assertThat(objectUnderTest.isUpToDate(projectName, initialRef)).isTrue();
  }

  @Test
  public void shouldReturnIsUpToDate() {
    expect(objectUnderTest.isUpToDate(projectName, ref1)).andStubReturn(true);
    replay(objectUnderTest);

    objectUnderTest.compareAndPut(projectName, nullRef, objectId1);

    assertThat(objectUnderTest.isUpToDate(projectName, ref1)).isTrue();
  }

  @Test
  public void shouldReturnIsNotUpToDateWhenLocalRepoIsOutdated() {
    expect(objectUnderTest.isUpToDate(projectName, ref1)).andStubReturn(true);
    replay(objectUnderTest);

    objectUnderTest.compareAndPut(projectName, nullRef, objectId1);

    assertThat(objectUnderTest.isUpToDate(projectName, nullRef)).isFalse();
  }

  @Test
  public void shouldReturnIsNotUpToDateWhenLocalRepoIsAheadOfTheGlobalRefDB() {
    expect(objectUnderTest.isUpToDate(projectName, ref1)).andStubReturn(true);
    replay(objectUnderTest);

    objectUnderTest.compareAndPut(projectName, nullRef, objectId1);

    assertThat(objectUnderTest.isUpToDate(projectName, ref2)).isFalse();
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
    // make sure that methods calls order is checked
    resetToStrict(objectUnderTest);

    // given
    expect(objectUnderTest.compareAndPut(projectName, ref1, objectId2)).andReturn(true);

    expect(objectUnderTest.lockRef(projectName, ref1.getName())).andAnswer(new LockRefAnswer());

    expect(objectUnderTest.isUpToDate(projectName, ref2)).andReturn(true);

    expect(objectUnderTest.compareAndPut(projectName, ref2, objectId3))
        .andAnswer(new CompareAndPutAnswer());

    replay(objectUnderTest);

    // when
    objectUnderTest.compareAndPut(projectName, ref1, objectId2);
    try (AutoCloseable refLock = objectUnderTest.lockRef(projectName, ref1.getName())) {
      // simulate concurrent client trying to execute some operation while current client holds the
      // lock
      executor.execute(new ConcurrentClient(helperLock));

      objectUnderTest.isUpToDate(projectName, ref2);
    }

    // then
    helperLock.await(1, TimeUnit.SECONDS);
    verify(objectUnderTest);
  }

  private Ref ref(String refName, ObjectId objectId) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, refName, objectId);
  }

  private Ref nullRef() {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NEW, null, ObjectId.zeroId());
  }

  private class LockRefAnswer implements IAnswer<AutoCloseable> {

    @Override
    public AutoCloseable answer() throws Throwable {
      lock.lock();
      return new AutoCloseable() {

        @Override
        public void close() throws Exception {
          lock.unlock();
        }
      };
    }
  }

  private class CompareAndPutAnswer implements IAnswer<Boolean> {

    @Override
    public Boolean answer() throws Throwable {
      lock.lock();
      try {
        return true;
      } finally {
        lock.unlock();
      }
    }
  }

  private class ConcurrentClient implements Runnable {
    CountDownLatch helperLock;

    public ConcurrentClient(CountDownLatch helperLock) {
      this.helperLock = helperLock;
    }

    @Override
    public void run() {
      objectUnderTest.compareAndPut(projectName, ref2, objectId3);
      helperLock.countDown();
    }
  }
}

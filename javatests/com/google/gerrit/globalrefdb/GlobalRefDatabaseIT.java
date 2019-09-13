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
import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.reviewdb.client.RefNames;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class GlobalRefDatabaseIT extends AbstractDaemonTest {

  private String refName = RefNames.REFS_HEADS + "branch";

  private ObjectId objectId1;
  private ObjectId objectId2;
  private ObjectId objectId3;

  private Ref ref1;
  private Ref ref2;
  private Ref ref3;
  private Ref nullRef = zerosRef(refName);
  private Ref initialRef = ref(refName, ObjectId.zeroId());

  private Executor executor = Executors.newFixedThreadPool(1);

  private GlobalRefDatabase objectUnderTest;

  @Before
  public void setup() throws Exception {
    this.objectUnderTest = new FakeGlobalRefDatabase();

    try (Repository repo = repoManager.openRepository(project)) {
      createChange(refName);
      ref1 = repo.getRefDatabase().exactRef(refName);
      objectId1 = ref1.getObjectId();
      createChange(refName);
      ref2 = repo.getRefDatabase().exactRef(refName);
      objectId2 = ref2.getObjectId();
      createChange(refName);
      ref3 = repo.getRefDatabase().exactRef(refName);
      objectId3 = ref3.getObjectId();
    }
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
   *
   * Test scenario:
   * 1. Client 1 acquires lock for given ref and checks if ref is up to date with the global ref-db
   * 2. Client 1 creates new change and updates ref in global ref-db
   * 3. While Client 1 holds the lock Client 2 is trying to update the ref in the global ref-db
   *
   * Result:
   * Client 1 operations are successful.
   * Client 2 operations are executed after Client 1 releases the lock. Client 2 operation failed
   * because Client 1 has updated the ref in global ref-db
   */
  @Test
  public void shouldLockRef() throws Exception {

    objectUnderTest.compareAndPut(project, ref1, objectId2);
    try (AutoCloseable refLock = objectUnderTest.lockRef(project, ref1.getName())) {
      // simulate concurrent client trying to execute some operation while current client holds the
      // lock
      executor.execute(new ConcurrentClient());

      if (objectUnderTest.isUpToDate(project, ref2)) {
        try (Repository repo = repoManager.openRepository(project)) {
          createChange(refName);

          Ref newRef = repo.getRefDatabase().exactRef(refName);
          assertThat(objectUnderTest.compareAndPut(project, ref2, newRef.getObjectId())).isTrue();
        }
      }
    }
  }

  private Ref ref(String refName, ObjectId objectId) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, refName, objectId);
  }

  private Ref zerosRef(String refName) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NEW, refName, ObjectId.zeroId());
  }

  private class ConcurrentClient implements Runnable {

    @Override
    public void run() {
      try (AutoCloseable lock = objectUnderTest.lockRef(project, ref1.getName())) {
        // some operations which require ref to be locked

        // compare and put should fail because other thread updated ref in the global ref-db
        assertThat(objectUnderTest.compareAndPut(project, ref2, objectId3)).isFalse();
      } catch (GlobalRefDbLockException e) {
        fail(e.getMessage());
      } catch (Exception e) {
        fail(e.getMessage());
      }
    }
  }
}

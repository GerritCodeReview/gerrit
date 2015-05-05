// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.ChangeMergeQueue;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.ChangeSetMerger;
import com.google.gerrit.server.git.ChangeSetMergeOp;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class MergeRacingIT extends AbstractSubmit{

  @Inject
  private ChangeMergeQueue changeMergeQueue;
  private Map<Change.Id, Semaphore> entry_lock;
  private Map<Change.Id, Semaphore> exit_lock;

  @Inject
  private Provider<MergeOp.Factory> mergeOpFactory;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    changeMergeQueue.setMergeBackend(new ChangeSetMergerMock());
    entry_lock = new HashMap<>();
    exit_lock = new HashMap<>();
  }

  @Test
  public void testRegularMerge() throws Exception {
    final Semaphore passed = new Semaphore(0);

    RevCommit initialHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change1 = createChange("Change 2", "b", "b");
    Change.Id id = change1.getChange().change().getId();
    change1.getChange().change().getDest();
    entry_lock.put(id, new Semaphore(1));
    exit_lock.put(id, new Semaphore(1));
    submit(change1.getChangeId());
    entry_lock.get(id).release();
    exit_lock.get(id).acquire();
    passed.release(1);



    new TimeoutTask(100, new Runnable() {
      @Override
      public void run() {
        try {

        } catch(Exception e) {
          throw new RuntimeException(e);
        }
      }
    }).start();
    assertThat(passed.availablePermits()).isEqualTo(1);
  }

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.CHERRY_PICK;
  }

  private class ChangeSetMergerMock implements ChangeSetMerger {
    @Override
    public void merge(ChangeSet changes) {
      try {
        for (Change.Id id : changes.ids()) {
          Semaphore entry = entry_lock.get(id);
          if (entry != null) {
            entry.acquire();
          }

          ChangeSetMergeOp csm = new ChangeSetMergeOp(server.getTestInjector());

          csm.merge(changes);

          Semaphore exit = exit_lock.get(id);
          if (exit != null) {
            exit.release();
          }
        }
      } catch (InterruptedException e) {
        //throw new IOError(e);
        e.printStackTrace();
      }
    }
  }
  private static final class TimeoutTask extends Thread {
    private long timeOutMs;
    private Runnable runnable;

    private TimeoutTask(long timeOutMs, Runnable runnable) {
      this.timeOutMs = timeOutMs;
      this.runnable = runnable;
    }

    @Override
    public void run() {
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() < (start + timeOutMs)) {
        runnable.run();
      }
    }
  }
}

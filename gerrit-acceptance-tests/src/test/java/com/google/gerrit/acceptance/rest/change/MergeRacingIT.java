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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.ChangeMergeQueue;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.ChangeSetMerger;
import com.google.inject.Inject;

import org.easymock.EasyMock;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

import java.io.IOError;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class MergeRacingIT extends AbstractSubmit{

  @Inject
  private ChangeMergeQueue changeMergeQueue;
  private Map<Change.Id, Semaphore> locks;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    ChangeSetMerger changeSetMerger = EasyMock.createStrictMock(ChangeSetMerger.class);
    changeMergeQueue.setMergeBackend(changeSetMerger);
    locks = new HashMap<>();
  }

  @Test
  public void testRegularMerge() throws Exception {
    RevCommit initialHead = getRemoteHead();


    testRepo.reset(initialHead);
    PushOneCommit.Result change1 = createChange("Change 2", "b", "b");
    Change.Id id = change1.getChange().change().getId();
    locks.put(id, new Semaphore(0));
    submit(change1.getChangeId());

    assertThat(locks.get(id).availablePermits()).isEqualTo(1);
  }

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.CHERRY_PICK;
  }

  class ChangeSetMergerMock implements ChangeSetMerger {
    @Override
    public void merge(ChangeSet changes) {
      try {
        for (Change.Id id : changes.ids()) {
          locks.get(id).acquire();
          // TODO(sbeller): actually set the state of the change to merged
          // in the data base.
        }
      } catch (InterruptedException e) {
        throw new IOError(e);
      }
    }
  }
}

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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ChangeIndexedCounter;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.restapi.config.IndexChanges;
import com.google.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IndexChangesIT extends AbstractDaemonTest {
  private static final long ASYNC_REINDEX_TIMEOUT = 10000;

  @Inject private DynamicSet<ChangeIndexedListener> changeIndexedListeners;

  private ChangeIndexedCounter changeIndexedCounter;
  private RegistrationHandle changeIndexedCounterHandle;

  @Before
  public void addChangeIndexedCounter() {
    changeIndexedCounter = new ChangeIndexedCounter();
    changeIndexedCounterHandle = changeIndexedListeners.add("gerrit", changeIndexedCounter);
  }

  @After
  public void removeChangeIndexedCounter() {
    if (changeIndexedCounterHandle != null) {
      changeIndexedCounterHandle.remove();
    }
  }

  @Test
  // @GerritConfig(name = "index.batchThreads", value = "1")
  public void indexRequestFromNonAdminRejected() throws Exception {
    String changeId = createChange().getChangeId();
    IndexChanges.Input in = new IndexChanges.Input();
    in.changes = ImmutableSet.of(changeId);
    changeIndexedCounter.clear();
    userRestSession.post("/config/server/index.changes", in).assertForbidden();
    assertNotIndexed(info(changeId));
  }

  @Test
  // @GerritConfig(name = "index.batchThreads", value = "1")
  public void indexVisibleChange() throws Exception {
    String changeId = createChange().getChangeId();
    IndexChanges.Input in = new IndexChanges.Input();
    in.changes = ImmutableSet.of(changeId);
    changeIndexedCounter.clear();
    adminRestSession.post("/config/server/index.changes", in).assertStatus(HttpStatus.SC_ACCEPTED);
    assertIndexed(info(changeId));
  }

  @Test
  // @GerritConfig(name = "index.batchThreads", value = "1")
  public void indexNonVisibleChange() throws Exception {
    String changeId = createChange().getChangeId();
    ChangeInfo changeInfo = info(changeId);
    blockRead("refs/heads/master");
    IndexChanges.Input in = new IndexChanges.Input();
    changeIndexedCounter.clear();
    in.changes = ImmutableSet.of(changeId);
    adminRestSession.post("/config/server/index.changes", in).assertStatus(HttpStatus.SC_ACCEPTED);
    assertIndexed(changeInfo);
  }

  @Test
  // @GerritConfig(name = "index.batchThreads", value = "5")
  public void indexMultipleChanges() throws Exception {
    ImmutableSet.Builder<String> changeIds = ImmutableSet.builder();
    for (int i = 0; i < 10; i++) {
      changeIds.add(createChange().getChangeId());
    }
    IndexChanges.Input in = new IndexChanges.Input();
    in.changes = changeIds.build();
    changeIndexedCounter.clear();
    adminRestSession.post("/config/server/index.changes", in).assertStatus(HttpStatus.SC_ACCEPTED);
    for (String changeId : in.changes) {
      assertIndexed(info(changeId));
    }
  }

  private void assertIndexed(ChangeInfo info) {
    assertChangeIndexedCounter(info, 1);
  }

  private void assertNotIndexed(ChangeInfo info) {
    assertChangeIndexedCounter(info, 0);
  }

  private void assertChangeIndexedCounter(ChangeInfo info, long expected) {
    Retryer<Long> r =
        RetryerBuilder.<Long>newBuilder()
            .retryIfResult(Predicates.not(Predicates.equalTo(expected)))
            .withWaitStrategy(
                WaitStrategies.exponentialWait(ASYNC_REINDEX_TIMEOUT, TimeUnit.MILLISECONDS))
            .withStopStrategy(
                StopStrategies.stopAfterDelay(ASYNC_REINDEX_TIMEOUT, TimeUnit.MILLISECONDS))
            .build();
    try {
      assertThat(
              r.call(
                  () -> {
                    return changeIndexedCounter.getCount(info);
                  }))
          .isEqualTo(expected);
    } catch (ExecutionException | RetryException e) {
      assert_().fail("Cause %s", e);
    }
  }
}

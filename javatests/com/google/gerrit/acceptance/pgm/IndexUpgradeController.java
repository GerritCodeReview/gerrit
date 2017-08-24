// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.index.OnlineUpgradeListener;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

class IndexUpgradeController implements OnlineUpgradeListener {
  @AutoValue
  abstract static class UpgradeAttempt {
    static UpgradeAttempt create(String name, int oldVersion, int newVersion) {
      return new AutoValue_IndexUpgradeController_UpgradeAttempt(name, oldVersion, newVersion);
    }

    abstract String name();

    abstract int oldVersion();

    abstract int newVersion();
  }

  private final int numExpected;
  private final CountDownLatch readyToStart;
  private final CountDownLatch started;
  private final CountDownLatch finished;

  private final List<UpgradeAttempt> startedAttempts;
  private final List<UpgradeAttempt> succeededAttempts;
  private final List<UpgradeAttempt> failedAttempts;

  IndexUpgradeController(int numExpected) {
    this.numExpected = numExpected;
    readyToStart = new CountDownLatch(1);
    started = new CountDownLatch(numExpected);
    finished = new CountDownLatch(numExpected);
    startedAttempts = new ArrayList<>();
    succeededAttempts = new ArrayList<>();
    failedAttempts = new ArrayList<>();
  }

  Module module() {
    return new AbstractModule() {
      @Override
      public void configure() {
        DynamicSet.bind(binder(), OnlineUpgradeListener.class)
            .toInstance(IndexUpgradeController.this);
      }
    };
  }

  @Override
  public synchronized void onStart(String name, int oldVersion, int newVersion) {
    UpgradeAttempt a = UpgradeAttempt.create(name, oldVersion, newVersion);
    try {
      readyToStart.await();
    } catch (InterruptedException e) {
      throw new AssertionError("interrupted waiting to start " + a, e);
    }
    checkState(
        started.getCount() > 0, "already started %s upgrades, can't start %s", numExpected, a);
    startedAttempts.add(a);
    started.countDown();
  }

  @Override
  public synchronized void onSuccess(String name, int oldVersion, int newVersion) {
    finish(UpgradeAttempt.create(name, oldVersion, newVersion), succeededAttempts);
  }

  @Override
  public synchronized void onFailure(String name, int oldVersion, int newVersion) {
    finish(UpgradeAttempt.create(name, oldVersion, newVersion), failedAttempts);
  }

  private synchronized void finish(UpgradeAttempt a, List<UpgradeAttempt> out) {
    checkState(readyToStart.getCount() == 0, "shouldn't be finishing upgrade before starting");
    checkState(
        finished.getCount() > 0, "already finished %s upgrades, can't finish %s", numExpected, a);
    out.add(a);
    finished.countDown();
  }

  void runUpgrades() throws Exception {
    readyToStart.countDown();
    started.await();
    finished.await();
  }

  synchronized ImmutableList<UpgradeAttempt> getStartedAttempts() {
    return ImmutableList.copyOf(startedAttempts);
  }

  synchronized ImmutableList<UpgradeAttempt> getSucceededAttempts() {
    return ImmutableList.copyOf(succeededAttempts);
  }

  synchronized ImmutableList<UpgradeAttempt> getFailedAttempts() {
    return ImmutableList.copyOf(failedAttempts);
  }
}

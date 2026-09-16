// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.server.project.PluginLockManager;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import java.util.concurrent.locks.Lock;
import org.junit.Test;

@TestPlugin(
    name = "plugin-lock-manager-it",
    sysModule = "com.google.gerrit.acceptance.server.project.PluginLockManagerIT$TestModule")
public class PluginLockManagerIT extends LightweightPluginDaemonTest {

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(PluginLockHolder.class).in(Scopes.SINGLETON);
    }
  }

  public static class PluginLockHolder {
    final PluginLockManager pluginLockManager;

    @Inject
    PluginLockHolder(PluginLockManager pluginLockManager) {
      this.pluginLockManager = pluginLockManager;
    }
  }

  @Test
  public void pluginCanAcquireAndReleaseALock() {
    Lock lock = pluginLockManager().getLock("nightly-sweep");

    assertThat(lock.tryLock()).isTrue();
    lock.unlock();
  }

  @Test
  public void sameArgsReturnsSameLock() {
    PluginLockManager pluginLockManager = pluginLockManager();

    Lock first = pluginLockManager.getLock("per-project-task", "project-a");
    Lock second = pluginLockManager.getLock("per-project-task", "project-a");

    assertThat(first).isSameInstanceAs(second);
  }

  private PluginLockManager pluginLockManager() {
    return plugin.getSysInjector().getInstance(PluginLockHolder.class).pluginLockManager;
  }
}

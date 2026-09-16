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

package com.google.gerrit.server.project;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.Inject;
import java.util.concurrent.locks.Lock;

/**
 * Plugin-facing wrapper around {@link LockManager} that fixes the {@code namespace} to {@code
 * plugins/<plugin_name>}. A plugin cannot accidentally claim another plugin's or Gerrit core's lock
 * namespace through this API.
 */
public class PluginLockManager {
  private final LockManager lockManager;
  private final String namespace;

  @Inject
  PluginLockManager(LockManager lockManager, @PluginName String pluginName) {
    this.lockManager = lockManager;
    this.namespace = "plugins/" + pluginName;
  }

  /**
   * Returns a lock owned by this plugin, identified by {@code minor} and further scoped by optional
   * {@code args}.
   */
  public Lock getLock(String functionality, String... args) {
    return lockManager.getLock(String.join("~", Lists.asList(namespace, functionality, args)));
  }
}

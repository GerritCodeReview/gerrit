// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/** Issues a configuration reload from the GerritServerConfigProvider and notify all listeners. */
@Singleton
public class GerritServerConfigReloader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GerritServerConfigProvider configProvider;
  private final DynamicSet<GerritConfigListener> configListeners;

  @Inject
  GerritServerConfigReloader(
      GerritServerConfigProvider configProvider, DynamicSet<GerritConfigListener> configListeners) {
    this.configProvider = configProvider;
    this.configListeners = configListeners;
  }

  /**
   * Reloads the Gerrit Server Configuration from disk. Synchronized to ensure that one issued
   * reload is fully completed before a new one starts.
   */
  public List<ConfigUpdatedEvent.Update> reloadConfig() {
    logger.atInfo().log("Starting server configuration reload");
    List<ConfigUpdatedEvent.Update> updates = fireUpdatedConfigEvent(configProvider.updateConfig());
    logger.atInfo().log("Server configuration reload completed succesfully");
    return updates;
  }

  public List<ConfigUpdatedEvent.Update> fireUpdatedConfigEvent(ConfigUpdatedEvent event) {
    ArrayList<ConfigUpdatedEvent.Update> result = new ArrayList<>();
    for (GerritConfigListener configListener : configListeners) {
      result.addAll(configListener.configUpdated(event));
    }
    return result;
  }
}

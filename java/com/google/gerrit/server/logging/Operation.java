// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import java.util.Optional;

public record Operation(String operationName, Optional<Metadata> metadata) {
  // Keep in sync with PluginMetrics.PLUGIN_LATENCY_NAME.
  public static final String PLUGIN_LATENCY_NAME = "plugin/latency";

  @Override
  public String toString() {
    if (!PLUGIN_LATENCY_NAME.equals(operationName()) || metadata.isEmpty()) {
      return operationName();
    }

    if (metadata.get().pluginName().isPresent()) {
      if (metadata.get().className().isPresent()) {
        return String.format(
            "%s (%s:%s)",
            operationName(), metadata.get().pluginName().get(), metadata.get().className().get());
      }
      return String.format("%s (%s)", operationName(), metadata.get().pluginName().get());
    } else if (metadata.get().className().isPresent()) {
      return String.format("%s (%s)", operationName(), metadata.get().className().get());
    }

    return operationName();
  }
}

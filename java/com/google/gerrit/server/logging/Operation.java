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

/**
 * Class to represent a named operation.
 *
 * <p>Note, it's intentional that this class doesn't implement {@link #equals(Object)} so that
 * operations that have the same name and metadata can be distinguished via the object identity
 * (that's also why this class is not a Java record).
 */
public class Operation {
  // Keep in sync with PluginMetrics.PLUGIN_LATENCY_NAME.
  public static final String PLUGIN_LATENCY_NAME = "plugin/latency";

  private final String operationName;
  private final Optional<Metadata> metadata;

  public Operation(String operationName, Optional<Metadata> metadata) {
    this.operationName = operationName;
    this.metadata = metadata;
  }

  public String getOperationName() {
    return operationName;
  }

  public Optional<Metadata> getMetadata() {
    return metadata;
  }

  /**
   * String representation of the operation that allows to know which code is triggering it. For
   * most operations this is only the operation name, but for {@code plugin/latency} operations it's
   * also including the plugin name and class from the metadata.
   */
  @Override
  public String toString() {
    if (!PLUGIN_LATENCY_NAME.equals(operationName) || metadata.isEmpty()) {
      return operationName;
    }

    if (metadata.get().pluginName().isPresent()) {
      if (metadata.get().className().isPresent()) {
        return String.format(
            "%s (%s:%s)",
            operationName, metadata.get().pluginName().get(), metadata.get().className().get());
      }
      return String.format("%s (%s)", operationName, metadata.get().pluginName().get());
    } else if (metadata.get().className().isPresent()) {
      return String.format("%s (%s)", operationName, metadata.get().className().get());
    }

    return operationName;
  }
}

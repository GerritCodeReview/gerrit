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

package com.google.gerrit.server.logging;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/**
 * Key-value pair for custom metadata that is provided by plugins.
 *
 * <p>PluginMetadata allows plugins to include custom metadata into the {@link Metadata} instances
 * that are provided as context for performance tracing.
 *
 * <p>Plugins should use PluginMetadata only for metadata kinds that are not known to Gerrit core
 * (metadata for which {@link Metadata} doesn't have a dedicated field).
 */
@AutoValue
public abstract class PluginMetadata {
  public static PluginMetadata create(String key, @Nullable String value) {
    return new AutoValue_PluginMetadata(key, Optional.ofNullable(value));
  }

  public abstract String key();

  public abstract Optional<String> value();
}

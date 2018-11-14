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

import com.google.common.collect.ImmutableSet;

public class GerritConfigListenerHelper {
  public static GerritConfigListener acceptIfChanged(ConfigKey... keys) {
    return e ->
        e.isEntriesUpdated(ImmutableSet.copyOf(keys))
            ? e.accept(ImmutableSet.copyOf(keys))
            : ConfigUpdatedEvent.NO_UPDATES;
  }
}

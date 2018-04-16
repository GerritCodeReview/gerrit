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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;

@AutoValue
public abstract class ConfigKey {
  public abstract String section();

  @Nullable
  public abstract String subsection();

  public abstract String name();

  public static ConfigKey create(String section, String subsection, String name) {
    return new AutoValue_ConfigKey(section, subsection, name);
  }

  public static ConfigKey create(String section, String name) {
    return new AutoValue_ConfigKey(section, null, name);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(section()).append(".");
    if (subsection() != null) {
      sb.append(subsection()).append(".");
    }
    sb.append(name());
    return sb.toString();
  }
}

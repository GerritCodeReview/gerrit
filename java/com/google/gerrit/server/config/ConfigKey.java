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

import java.util.Objects;

public class ConfigKey {
  public final String section;
  public final String subsection;
  public final String name;

  public ConfigKey(String section, String subsection, String name) {
    this.section = section;
    this.subsection = subsection;
    this.name = name;
  }

  public ConfigKey(String section, String name) {
    this.section = section;
    this.subsection = null;
    this.name = name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(section, subsection, name);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof ConfigKey)) {
      return false;
    }
    ConfigKey other = (ConfigKey) obj;
    return Objects.equals(this.section, other.section)
        && Objects.equals(this.subsection, other.subsection)
        && Objects.equals(this.name, other.name);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(section).append(".");
    if (subsection != null) {
      sb.append(subsection).append(".");
    }
    sb.append(name);
    return sb.toString();
  }
}

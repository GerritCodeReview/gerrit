// Copyright (C) 2013 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.common;

package com.google.gerrit.common;

import com.google.common.base.Objects;

import java.io.File;

public class PluginData {
  public final String name;
  public final String version;
  public final File pluginFile;

  public PluginData(String name, String version, File pluginFile) {
    this.name = name;
    this.version = version;
    this.pluginFile = pluginFile;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PluginData) {
      PluginData o = (PluginData) obj;
      return Objects.equal(name, o.name) && Objects.equal(version, o.version)
          && Objects.equal(pluginFile, o.pluginFile);
    }
    return super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, version, pluginFile);
  }
}
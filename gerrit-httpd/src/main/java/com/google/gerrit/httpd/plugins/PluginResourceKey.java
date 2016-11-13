// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.plugins;

import com.google.auto.value.AutoValue;
import com.google.gerrit.httpd.resources.ResourceKey;
import com.google.gerrit.server.plugins.Plugin;

@AutoValue
abstract class PluginResourceKey implements ResourceKey {
  static PluginResourceKey create(Plugin p, String r) {
    return new AutoValue_PluginResourceKey(p.getCacheKey(), r);
  }

  public abstract Plugin.CacheKey plugin();

  public abstract String resource();

  @Override
  public int weigh() {
    return resource().length() * 2;
  }
}

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

import com.google.gerrit.server.plugins.Plugin;

final class ResourceKey {
  private final Plugin.CacheKey plugin;
  private final String resource;

  ResourceKey(Plugin p, String r) {
    this.plugin = p.getCacheKey();
    this.resource = r;
  }

  int weigh() {
    return resource.length() * 2;
  }

  @Override
  public int hashCode() {
    return plugin.hashCode() * 31 + resource.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof ResourceKey) {
      ResourceKey rk = (ResourceKey) other;
      return plugin == rk.plugin && resource.equals(rk.resource);
    }
    return false;
  }
}

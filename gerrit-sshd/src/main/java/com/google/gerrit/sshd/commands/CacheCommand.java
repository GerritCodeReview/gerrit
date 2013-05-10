// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.common.cache.Cache;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import java.util.SortedSet;

abstract class CacheCommand extends SshCommand {
  @Inject
  protected DynamicMap<Cache<?, ?>> cacheMap;

  protected SortedSet<String> cacheNames() {
    SortedSet<String> names = Sets.newTreeSet();
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      names.add(cacheNameOf(e.getPluginName(), e.getExportName()));
    }
    return names;
  }

  protected String cacheNameOf(String plugin, String name) {
    if ("gerrit".equals(plugin)) {
      return name;
    } else {
      return plugin + "." + name;
    }
  }
}

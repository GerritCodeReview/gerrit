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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.ehcache.EhcachePoolImpl;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import javax.annotation.Nullable;

abstract class CacheCommand extends SshCommand {
  @SuppressWarnings("rawtypes")
  @Inject
  protected DynamicMap<Cache<?, ?>> cachesMap;

  @Inject
  @Nullable
  protected EhcachePoolImpl ehcaches;

  protected SortedSet<String> cacheNames() {
    SortedSet<String> names = Sets.newTreeSet();
    for (String plugin : cachesMap.plugins()) {
      for (String name : cachesMap.byPlugin(plugin).keySet()) {
        names.add(cacheNameOf(plugin, name));
      }
    }
    if (ehcaches != null) {
      String[] n = ehcaches.getCacheManager().getCacheNames();
      names.addAll(Arrays.asList(n));
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

  protected List<Ehcache> getAllEhcache() {
    if (ehcaches == null) {
      return Collections.emptyList();
    }

    CacheManager cm = ehcaches.getCacheManager();
    List<Ehcache> r = Lists.newArrayList();
    for (String name : cm.getCacheNames()) {
      r.add(cm.getEhcache(name));
    }
    return r;
  }
}

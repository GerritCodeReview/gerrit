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
import com.google.gerrit.server.cache.LocalCacheHandle;
import com.google.gerrit.server.cache.LocalCachePool;
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
  @Inject
  protected LocalCachePool localCaches;

  @Inject
  @Nullable
  protected EhcachePoolImpl ehcaches;

  protected SortedSet<String> cacheNames() {
    SortedSet<String> names = Sets.newTreeSet();
    for (LocalCacheHandle handle : localCaches.getCaches()) {
      names.add(handle.getName());
    }
    if (ehcaches != null) {
      String[] n = ehcaches.getCacheManager().getCacheNames();
      names.addAll(Arrays.asList(n));
    }
    return names;
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

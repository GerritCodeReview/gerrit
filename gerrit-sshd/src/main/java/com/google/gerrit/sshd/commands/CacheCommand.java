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

import com.google.gerrit.ehcache.EhcachePoolImpl;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

abstract class CacheCommand extends SshCommand {
  @Inject
  protected EhcachePoolImpl cachePool;

  protected SortedSet<String> cacheNames() {
    final SortedSet<String> names = new TreeSet<String>();
    for (final Ehcache c : getAllCaches()) {
      names.add(c.getName());
    }
    return names;
  }

  protected Ehcache[] getAllCaches() {
    final CacheManager cacheMgr = cachePool.getCacheManager();
    final String[] cacheNames = cacheMgr.getCacheNames();
    Arrays.sort(cacheNames);
    final Ehcache[] r = new Ehcache[cacheNames.length];
    for (int i = 0; i < cacheNames.length; i++) {
      r[i] = cacheMgr.getEhcache(cacheNames[i]);
    }
    return r;
  }
}

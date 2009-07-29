// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.patch.DiffCache;
import com.google.inject.Inject;

import net.sf.ehcache.Ehcache;

import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/** Causes the caches to purge all entries and reload. */
class AdminFlushCaches extends AbstractAdminCacheCommand {
  @Option(name = "--cache", usage = "flush named cache", metaVar = "NAME")
  private List<String> caches = new ArrayList<String>();

  @Option(name = "--all", usage = "flush all caches")
  private boolean all;

  @Option(name = "--list", usage = "list available caches")
  private boolean list;

  @Inject
  private DiffCache diffCache;

  PrintWriter p;

  @Override
  protected void run() throws Failure, UnsupportedEncodingException {
    assertIsAdministrator();

    p = toPrintWriter(err);
    if (list) {
      if (all || caches.size() > 0) {
        throw new Failure(1, "error: cannot use --list with --all or --cache");
      }
      doList();
      return;
    }

    if (all && caches.size() > 0) {
      throw new Failure(1, "error: cannot combine --all and --cache");
    } else if (!all && caches.size() == 1 && caches.contains("all")) {
      caches.clear();
      all = true;
    } else if (!all && caches.isEmpty()) {
      all = true;
    }

    final SortedSet<String> names = cacheNames();
    for (final String n : caches) {
      if (!names.contains(n)) {
        throw new Failure(1, "error: cache \"" + n + "\" not recognized");
      }
    }
    doBulkFlush();
  }

  private void doList() {
    for (final String name : cacheNames()) {
      p.print(name);
      p.print('\n');
    }
    p.flush();
  }

  private void doBulkFlush() {
    try {
      if (flush("groups")) {
        Common.getGroupCache().flush();
      }
      if (flush("projects")) {
        Common.getProjectCache().flush();
      }
      if (flush("accounts")) {
        Common.getAccountCache().flush();
      }

      for (final Ehcache c : getAllCaches()) {
        final String name = c.getName();
        if (diffCache.getName().equals(name)) {
          continue;
        }
        if (flush(name)) {
          try {
            c.removeAll();
          } catch (Throwable e) {
            p.println("error: cannot flush cache \"" + name + "\": " + e);
          }
        }
      }

      if (flush(diffCache.getName())) {
        try {
          diffCache.flush();
        } catch (Throwable e) {
          final String n = diffCache.getName();
          p.println("warning: cannot save cache \"" + n + "\": " + e);
        }
      }
    } finally {
      p.flush();
    }
  }

  private boolean flush(final String cacheName) {
    return all || caches.contains(cacheName);
  }
}

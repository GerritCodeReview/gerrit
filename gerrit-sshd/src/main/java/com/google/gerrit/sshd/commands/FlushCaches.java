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

package com.google.gerrit.sshd.commands;

import com.google.common.cache.Cache;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/** Causes the caches to purge all entries and reload. */
@RequiresCapability(GlobalCapability.FLUSH_CACHES)
@CommandMetaData(name = "flush-caches", descr = "Flush some/all server caches from memory")
final class FlushCaches extends CacheCommand {
  private static final String WEB_SESSIONS = "web_sessions";

  @Option(name = "--cache", usage = "flush named cache", metaVar = "NAME")
  private List<String> caches = new ArrayList<String>();

  @Option(name = "--all", usage = "flush all caches")
  private boolean all;

  @Option(name = "--list", usage = "list available caches")
  private boolean list;

  @Inject
  IdentifiedUser currentUser;

  @Override
  protected void run() throws Failure {
    if (caches.contains(WEB_SESSIONS)
        && !currentUser.getCapabilities().canAdministrateServer()) {
      String msg = String.format(
          "fatal: only site administrators can flush %s",
          WEB_SESSIONS);
      throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
    }

    if (list) {
      if (all || caches.size() > 0) {
        throw error("error: cannot use --list with --all or --cache");
      }
      doList();
      return;
    }

    if (all && caches.size() > 0) {
      throw error("error: cannot combine --all and --cache");
    } else if (!all && caches.size() == 1 && caches.contains("all")) {
      caches.clear();
      all = true;
    } else if (!all && caches.isEmpty()) {
      all = true;
    }

    final SortedSet<String> names = cacheNames();
    for (final String n : caches) {
      if (!names.contains(n)) {
        throw error("error: cache \"" + n + "\" not recognized");
      }
    }
    doBulkFlush();
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }

  private void doList() {
    for (final String name : cacheNames()) {
      stderr.print(name);
      stderr.print('\n');
    }
    stderr.flush();
  }

  private void doBulkFlush() {
    try {
      for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
        String n = cacheNameOf(e.getPluginName(), e.getExportName());
        if (flush(n)) {
          try {
            e.getProvider().get().invalidateAll();
          } catch (Throwable err) {
            stderr.println("error: cannot flush cache \"" + n + "\": " + err);
          }
        }
      }
    } finally {
      stderr.flush();
    }
  }

  private boolean flush(final String cacheName) {
    if (caches.contains(cacheName)) {
      return true;

    } else if (all) {
      if (WEB_SESSIONS.equals(cacheName)) {
        return false;
      }
      return true;

    } else {
      return false;
    }
  }
}

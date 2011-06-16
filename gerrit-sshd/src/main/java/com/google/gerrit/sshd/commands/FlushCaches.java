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

import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import net.sf.ehcache.Ehcache;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/** Causes the caches to purge all entries and reload. */
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

  private PrintWriter p;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canFlushCaches()) {
          String msg = String.format(
            "fatal: %s does not have \"Flush Caches\" capability.",
            currentUser.getUserName());
          throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
        }

        parseCommandLine();
        flush();
      }
    });
  }

  private void flush() throws Failure {
    if (caches.contains(WEB_SESSIONS) && !currentUser.isAdministrator()) {
      String msg = String.format(
          "fatal: only site administrators can flush %s",
          WEB_SESSIONS);
      throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
    }

    p = toPrintWriter(err);
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
      p.print(name);
      p.print('\n');
    }
    p.flush();
  }

  private void doBulkFlush() {
    try {
      for (final Ehcache c : getAllCaches()) {
        final String name = c.getName();
        if (flush(name)) {
          try {
            c.removeAll();
          } catch (Throwable e) {
            p.println("error: cannot flush cache \"" + name + "\": " + e);
          }
        }
      }
    } finally {
      p.flush();
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

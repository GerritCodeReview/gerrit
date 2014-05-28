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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.cache.Cache;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.CacheResource;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.FlushCache;
import com.google.gerrit.server.config.ListCaches;
import com.google.gerrit.server.config.ListCaches.OutputFormat;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

/** Causes the caches to purge all entries and reload. */
@RequiresCapability(GlobalCapability.FLUSH_CACHES)
@CommandMetaData(name = "flush-caches", description = "Flush some/all server caches from memory",
  runsAt = MASTER_OR_SLAVE)
final class FlushCaches extends CacheCommand {
  private static final String WEB_SESSIONS = "web_sessions";

  @Option(name = "--cache", usage = "flush named cache", metaVar = "NAME")
  private List<String> caches = new ArrayList<>();

  @Option(name = "--all", usage = "flush all caches")
  private boolean all;

  @Option(name = "--list", usage = "list available caches")
  private boolean list;

  @Inject
  private Provider<FlushCache> flushCache;

  @Inject
  private Provider<ListCaches> listCaches;

  @Override
  protected void run() throws Failure {
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

    List<String> names = cacheNames();
    for (String n : caches) {
      if (!names.contains(n)) {
        throw error("error: cache \"" + n + "\" not recognized");
      }
    }
    doBulkFlush();
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }

  @SuppressWarnings("unchecked")
  private List<String> cacheNames() throws UnloggedFailure {
    try {
      return (List<String>) listCaches.get().setFormat(OutputFormat.LIST)
          .apply(new ConfigResource());
    } catch (BadRequestException e) {
      throw die(e.getMessage());
    }
  }

  private void doList() throws UnloggedFailure {
    for (String name : cacheNames()) {
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
            flushCache.get().apply(
                new CacheResource(e.getPluginName(), e.getExportName(),
                    e.getProvider()), new FlushCache.Input());
          } catch (RestApiException err) {
            stderr.println("error: cannot flush cache \"" + n + "\": "
                + err.getMessage());
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

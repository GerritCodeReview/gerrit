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

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

/** Causes the caches to purge all entries and reload. */
class AdminFlushCaches extends AbstractCommand {
  PrintWriter p;

  @Override
  protected void run() throws Failure, UnsupportedEncodingException {
    assertIsAdministrator();
    p = toPrintWriter(err);
    try {
      final SelfPopulatingCache diffCache = getGerritServer().getDiffCache();

      Common.getGroupCache().flush();
      Common.getProjectCache().flush();
      Common.getAccountCache().flush();

      for (final Ehcache c : getGerritServer().getAllCaches()) {
        final String name = c.getName();
        if (diffCache.getName().equals(name)) {
          continue;
        }
        try {
          c.removeAll();
        } catch (Throwable e) {
          p.println("error: cannot flush cache \"" + name + "\": " + e);
        }
      }

      saveToDisk(diffCache);
    } finally {
      p.flush();
    }
  }

  private void saveToDisk(final Ehcache c) {
    try {
      c.flush();
    } catch (Throwable e) {
      p.println("warning: cannot save cache \"" + c.getName() + "\": " + e);
    }
  }
}

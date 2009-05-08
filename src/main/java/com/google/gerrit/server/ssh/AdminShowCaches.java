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

package com.google.gerrit.server.ssh;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Statistics;
import net.sf.ehcache.config.CacheConfiguration;

import org.spearce.jgit.lib.WindowCacheStatAccessor;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

/** Show the current cache states. */
class AdminShowCaches extends AbstractCommand {
  PrintWriter p;

  @Override
  protected void run(String[] args) throws Failure,
      UnsupportedEncodingException {
    assertIsAdministrator();
    p = toPrintWriter(out);

    for (final Ehcache cache : getGerritServer().getAllCaches()) {
      final CacheConfiguration cfg = cache.getCacheConfiguration();
      final boolean useDisk = cfg.isDiskPersistent() || cfg.isOverflowToDisk();
      final Statistics stat = cache.getStatistics();

      p.print("cache \"" + cache.getName() + "\"");
      if (useDisk) {
        p.print(" (memory, disk)");
      }
      p.println(":");
      fItemCount("items", stat.getObjectCount());
      if (useDisk) {
        fItemCount("items.memory", stat.getMemoryStoreObjectCount());
        fItemCount("items.disk", stat.getDiskStoreObjectCount());
      }
      fItemCount("evictions", stat.getEvictionCount());

      final long total = stat.getCacheHits() + stat.getCacheMisses();
      fTimeInterval("ttl.idle", cfg.getTimeToIdleSeconds());
      fTimeInterval("ttl.live", cfg.getTimeToLiveSeconds());
      fMilliseconds("avg.get", stat.getAverageGetTime());
      fPercent("hit%", stat.getCacheHits(), total);
      if (useDisk) {
        fPercent("hit%.memory", stat.getInMemoryHits(), total);
        fPercent("hit%.disk", stat.getOnDiskHits(), total);
      }

      p.println();
    }

    final Runtime r = Runtime.getRuntime();
    final long mMax = r.maxMemory();
    final long mFree = r.freeMemory();
    final long mTotal = r.totalMemory();
    final long mInuse = mTotal - mFree;
    final int jgitBytes = WindowCacheStatAccessor.getOpenBytes();

    p.println("JGit Buffer Cache:");
    fItemCount("open files", WindowCacheStatAccessor.getOpenFiles());
    fByteCount("loaded", jgitBytes);
    fPercent("mem%", jgitBytes, mTotal);
    p.println();

    p.println("JVM Heap:");
    fByteCount("max", mMax);
    fByteCount("inuse", mInuse);
    fPercent("mem%", mInuse, mTotal);
    p.println();

    p.flush();
  }

  private void fItemCount(final String name, final long value) {
    p.println(String.format("  %1$-12s: %2$15d", name, value));
  }

  private void fByteCount(final String name, double value) {
    String suffix = "bytes";
    if (value > 1024) {
      value /= 1024;
      suffix = "kb";
    }
    if (value > 1024) {
      value /= 1024;
      suffix = "mb";
    }
    if (value > 1024) {
      value /= 1024;
      suffix = "gb";
    }
    p.println(String.format("  %1$-12s: %2$6.2f %3$s", name, value, suffix));
  }

  private void fMilliseconds(final String name, final float ms) {
    p.println(String.format("  %1$-12s: %2$6.2f ms", name, ms));
  }

  private void fTimeInterval(final String name, double ttl) {
    if (ttl == 0) {
      p.println(String.format("  %1$-12s:        inf", name));
      return;
    }

    String suffix = "secs";
    if (ttl > 60) {
      ttl /= 60;
      suffix = "mins";
    }
    if (ttl > 60) {
      ttl /= 60;
      suffix = "hrs";
    }
    if (ttl > 24) {
      ttl /= 24;
      suffix = "days";
    }
    p.println(String.format("  %1$-12s: %2$6.2f %3$s", name, ttl, suffix));
  }

  private void fPercent(final String name, final long value, final long total) {
    final long pcent = 0 < total ? (100 * value) / total : 0;
    p.println(String.format("  %1$-12s: %2$3d%%", name, (int) pcent));
  }
}

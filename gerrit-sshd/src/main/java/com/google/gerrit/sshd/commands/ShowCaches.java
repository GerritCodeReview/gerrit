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

import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Statistics;
import net.sf.ehcache.config.CacheConfiguration;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.storage.file.WindowCacheStatAccessor;

import java.io.PrintWriter;

/** Show the current cache states. */
final class ShowCaches extends CacheCommand {
  @Inject
  IdentifiedUser currentUser;

  private PrintWriter p;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canViewCaches()) {
          String msg = String.format(
            "fatal: %s does not have \"View Caches\" capability.",
            currentUser.getUserName());
          throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
        }

        parseCommandLine();
        display();
      }
    });
  }

  private void display() {
    p = toPrintWriter(out);

    p.print(String.format(//
        "%1s %-18s %-4s|%-20s|  %-5s  |%-14s|\n" //
        , "" //
        , "Name" //
        , "Max" //
        , "Object Count" //
        , "AvgGet" //
        , "Hit Ratio" //
    ));
    p.print(String.format(//
        "%1s %-18s %-4s|%6s %6s %6s|  %-5s   |%-4s %-4s %-4s|\n" //
        , "" //
        , "" //
        , "Age" //
        , "Disk" //
        , "Mem" //
        , "Cnt" //
        , "" //
        , "Disk" //
        , "Mem" //
        , "Agg" //
    ));
    p.println("------------------"
        + "-------+--------------------+----------+--------------+");
    for (final Ehcache cache : getAllCaches()) {
      final CacheConfiguration cfg = cache.getCacheConfiguration();
      final boolean useDisk = cfg.isDiskPersistent() || cfg.isOverflowToDisk();
      final Statistics stat = cache.getStatistics();
      final long total = stat.getCacheHits() + stat.getCacheMisses();

      if (useDisk) {
        p.print(String.format(//
            "D %-18s %-4s|%6s %6s %6s| %7s  |%4s %4s %4s|\n" //
            , cache.getName() //
            , interval(cfg.getTimeToLiveSeconds()) //
            , count(stat.getDiskStoreObjectCount()) //
            , count(stat.getMemoryStoreObjectCount()) //
            , count(stat.getObjectCount()) //
            , duration(stat.getAverageGetTime()) //
            , percent(stat.getOnDiskHits(), total) //
            , percent(stat.getInMemoryHits(), total) //
            , percent(stat.getCacheHits(), total) //
            ));
      } else {
        p.print(String.format(//
            "  %-18s %-4s|%6s %6s %6s| %7s  |%4s %4s %4s|\n" //
            , cache.getName() //
            , interval(cfg.getTimeToLiveSeconds()) //
            , "", "" //
            , count(stat.getObjectCount()) //
            , duration(stat.getAverageGetTime()) //
            , "", "" //
            , percent(stat.getCacheHits(), total) //
            ));
      }
    }
    p.println();

    final Runtime r = Runtime.getRuntime();
    final long mMax = r.maxMemory();
    final long mFree = r.freeMemory();
    final long mTotal = r.totalMemory();
    final long mInuse = mTotal - mFree;
    final long jgitBytes = WindowCacheStatAccessor.getOpenBytes();

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

  private String count(long cnt) {
    if (cnt == 0) {
      return "";
    }
    return String.format("%6d", cnt);
  }

  private String duration(double ms) {
    if (Math.abs(ms) <= 0.05) {
      return "";
    }
    String suffix = "ms";
    if (ms >= 1000) {
      ms /= 1000;
      suffix = "s ";
    }
    return String.format("%4.1f%s", ms, suffix);
  }

  private String interval(double ttl) {
    if (ttl == 0) {
      return "inf";
    }

    String suffix = "s";
    if (ttl >= 60) {
      ttl /= 60;
      suffix = "m";

      if (ttl >= 60) {
        ttl /= 60;
        suffix = "h";
      }

      if (ttl >= 24) {
        ttl /= 24;
        suffix = "d";

        if (ttl >= 365) {
          ttl /= 365;
          suffix = "y";
        }
      }
    }

    return Integer.toString((int) ttl) + suffix;
  }

  private String percent(final long value, final long total) {
    if (total <= 0) {
      return "";
    }
    final long pcent = (100 * value) / total;
    return String.format("%3d%%", (int) pcent);
  }

  private void fPercent(final String name, final long value, final long total) {
    final long pcent = 0 < total ? (100 * value) / total : 0;
    p.println(String.format("  %1$-12s: %2$3d%%", name, (int) pcent));
  }
}

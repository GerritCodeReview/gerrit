// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import com.google.common.base.Strings;
import com.google.gerrit.common.Version;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

public class CacheDisplayUtils {

  private PrintWriter stdout;
  private int nw = 30;
  private long serverStarted;
  private Collection<CacheInfo> caches;

  public CacheDisplayUtils(
      PrintWriter stdout, int nw, long serverStarted, Collection<CacheInfo> caches) {
    this.stdout = stdout;
    this.nw = nw;
    this.serverStarted = serverStarted;
    this.caches = caches;
  }

  public CacheDisplayUtils(PrintWriter stdout, int nw, Collection<CacheInfo> caches) {
    this(stdout, nw, 0L, caches);
  }

  public CacheDisplayUtils(PrintWriter stdout, Collection<CacheInfo> caches) {
    this(stdout, 30, caches);
  }

  public void diplayCaches() {
    Date now = new Date();
    stdout.format(
        "%-25s %-20s      now  %16s\n",
        "Gerrit Code Review",
        Version.getVersion() != null ? Version.getVersion() : "",
        new SimpleDateFormat("HH:mm:ss   zzz").format(now));
    if (serverStarted != 0L) {
      stdout.format("%-25s %-20s   uptime %16s\n", "", "", uptime(now.getTime() - serverStarted));
    }
    stdout.print('\n');

    stdout.print(
        String.format( //
            "%1s %-" + nw + "s|%-21s|  %-5s |%-9s|\n" //
            ,
            "" //
            ,
            "Name" //
            ,
            "Entries" //
            ,
            "AvgGet" //
            ,
            "Hit Ratio" //
            ));
    stdout.print(
        String.format( //
            "%1s %-" + nw + "s|%6s %6s %7s|  %-5s  |%-4s %-4s|\n" //
            ,
            "" //
            ,
            "" //
            ,
            "Mem" //
            ,
            "Disk" //
            ,
            "Space" //
            ,
            "" //
            ,
            "Mem" //
            ,
            "Disk" //
            ));
    stdout.print("--");
    for (int i = 0; i < nw; i++) {
      stdout.print('-');
    }
    stdout.print("+---------------------+---------+---------+\n");

    printMemoryCoreCaches(caches);
    printMemoryPluginCaches(caches);
    printDiskCaches(caches);

    stdout.print('\n');
  }

  public void printMemoryCoreCaches(Collection<CacheInfo> caches) {
    for (CacheInfo cache : caches) {
      if (!cache.name.contains("-") && CacheInfo.CacheType.MEM.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  public void printMemoryPluginCaches(Collection<CacheInfo> caches) {
    for (CacheInfo cache : caches) {
      if (cache.name.contains("-") && CacheInfo.CacheType.MEM.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  public void printDiskCaches(Collection<CacheInfo> caches) {
    for (CacheInfo cache : caches) {
      if (CacheInfo.CacheType.DISK.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  public void printCache(CacheInfo cache) {
    stdout.print(
        String.format(
            "%1s %-" + nw + "s|%6s %6s %7s| %7s |%4s %4s|\n",
            CacheInfo.CacheType.DISK.equals(cache.type) ? "D" : "",
            cache.name,
            nullToEmpty(cache.entries.mem),
            nullToEmpty(cache.entries.disk),
            Strings.nullToEmpty(cache.entries.space),
            Strings.nullToEmpty(cache.averageGet),
            formatAsPercent(cache.hitRatio.mem),
            formatAsPercent(cache.hitRatio.disk)));
  }

  private static String nullToEmpty(Long l) {
    return l != null ? String.valueOf(l) : "";
  }

  private static String formatAsPercent(Integer i) {
    return i != null ? String.valueOf(i) + "%" : "";
  }

  private String uptime(long uptimeMillis) {
    if (uptimeMillis < 1000) {
      return String.format("%3d ms", uptimeMillis);
    }

    long uptime = uptimeMillis / 1000L;

    long min = uptime / 60;
    if (min < 60) {
      return String.format("%2d min %2d sec", min, uptime - min * 60);
    }

    long hr = uptime / 3600;
    if (hr < 24) {
      min = (uptime - hr * 3600) / 60;
      return String.format("%2d hrs %2d min", hr, min);
    }

    long days = uptime / (24 * 3600);
    hr = (uptime - (days * 24 * 3600)) / 3600;
    return String.format("%4d days %2d hrs", days, hr);
  }
}

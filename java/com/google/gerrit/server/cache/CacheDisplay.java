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
import com.google.gerrit.extensions.common.CacheInfo;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

public class CacheDisplay {

  private final Writer stdout;
  private final int nw;
  private final Collection<CacheInfo> caches;

  public CacheDisplay(Writer stdout, int nw, Collection<CacheInfo> caches) {
    this.stdout = stdout;
    this.nw = nw;
    this.caches = caches;
  }

  public CacheDisplay(Writer stdout, Collection<CacheInfo> caches) {
    this(stdout, 30, caches);
  }

  public void displayCaches() throws IOException {
    stdout.write(
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
    stdout.write(
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
    stdout.write("--");
    for (int i = 0; i < nw; i++) {
      stdout.write('-');
    }
    stdout.write("+---------------------+---------+---------+\n");
    printMemoryCoreCaches(caches);
    printMemoryPluginCaches(caches);
    printDiskCaches(caches);
    stdout.write('\n');
  }

  private void printMemoryCoreCaches(Collection<CacheInfo> caches) throws IOException {
    for (CacheInfo cache : caches) {
      if (!cache.name.contains("-") && CacheInfo.CacheType.MEM.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  private void printMemoryPluginCaches(Collection<CacheInfo> caches) throws IOException {
    for (CacheInfo cache : caches) {
      if (cache.name.contains("-") && CacheInfo.CacheType.MEM.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  private void printDiskCaches(Collection<CacheInfo> caches) throws IOException {
    for (CacheInfo cache : caches) {
      if (CacheInfo.CacheType.DISK.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  private void printCache(CacheInfo cache) throws IOException {
    stdout.write(
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
    return i != null ? i + "%" : "";
  }
}

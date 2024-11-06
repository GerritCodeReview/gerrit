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

package com.google.gerrit.extensions.common;

import com.google.gerrit.common.Nullable;

public class CacheInfo {

  public String name;
  public CacheType type;
  public EntriesInfo entries;
  public String averageGet;
  public HitRatioInfo hitRatio;

  public static class EntriesInfo {
    public Long mem;
    public Long disk;
    public String space;

    public void setMem(long mem) {
      this.mem = mem != 0 ? mem : null;
    }

    public void setDisk(long disk) {
      this.disk = disk != 0 ? disk : null;
    }

    public void setSpace(double value) {
      space = bytes(value);
    }

    public static String bytes(double value) {
      value /= 1024;
      String suffix = "k";

      if (value > 1024) {
        value /= 1024;
        suffix = "m";
      }
      if (value > 1024) {
        value /= 1024;
        suffix = "g";
      }
      return String.format("%1$6.2f%2$s", value, suffix).trim();
    }
  }

  public static class HitRatioInfo {
    public Integer mem;
    public Integer disk;

    public void setMem(long value, long total) {
      mem = percent(value, total);
    }

    public void setDisk(long value, long total) {
      disk = percent(value, total);
    }

    @Nullable
    private static Integer percent(long value, long total) {
      if (total <= 0) {
        return null;
      }
      return (int) ((100 * value) / total);
    }
  }

  public enum CacheType {
    MEM,
    DISK
  }
}

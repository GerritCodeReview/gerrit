// Copyright (C) 2015 The Android Open Source Project
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

import java.util.List;

public class BlameInfo {
  public List<Blame> blames;


  public static class BlameLine {
    public BlameMeta meta;
    public int from;
    public int to;
  }

  public static class BlameMeta {
    public String author;
    public String id;
    public int time;
    public String commitMsg;
    public int changeId;
    public int patchSetId;

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      BlameMeta blameMeta = (BlameMeta) o;

      return id.equals(blameMeta.id);

    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }
  }

  public static class Blame {
    public BlameMeta meta;
    public List<FromTo> fromTo;

    public static class FromTo {
      public int from;
      public int to;

      public FromTo(int from, int to) {
        this.from = from;
        this.to = to;
      }
    }
  }
}

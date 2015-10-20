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

  public static class Meta {
    public String author;
    public String id;
    public int time;
    public String commitMsg;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Meta meta = (Meta) o;

      return id.equals(meta.id);
    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }
  }

  public static class Blame {
    public Meta meta;
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

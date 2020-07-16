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

package com.google.gerrit.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabelTypes {
  protected List<LabelType> labelTypes;
  private transient volatile Map<String, LabelType> byLabel;
  private transient volatile Map<String, Integer> positions;

  protected LabelTypes() {}

  public LabelTypes(List<? extends LabelType> approvals) {
    labelTypes = Collections.unmodifiableList(new ArrayList<>(approvals));
  }

  public List<LabelType> getLabelTypes() {
    return labelTypes;
  }

  public LabelType byLabel(LabelId labelId) {
    return byLabel().get(labelId.get().toLowerCase());
  }

  public LabelType byLabel(String labelName) {
    return byLabel().get(labelName.toLowerCase());
  }

  private Map<String, LabelType> byLabel() {
    if (byLabel == null) {
      synchronized (this) {
        if (byLabel == null) {
          Map<String, LabelType> l = new HashMap<>();
          if (labelTypes != null) {
            for (LabelType t : labelTypes) {
              l.put(t.getName().toLowerCase(), t);
            }
          }
          byLabel = l;
        }
      }
    }
    return byLabel;
  }

  @Override
  public String toString() {
    return labelTypes.toString();
  }

  public Comparator<String> nameComparator() {
    final Map<String, Integer> positions = positions();
    return new Comparator<String>() {
      @Override
      public int compare(String left, String right) {
        int lp = position(left);
        int rp = position(right);
        int cmp = lp - rp;
        if (cmp == 0) {
          cmp = left.compareTo(right);
        }
        return cmp;
      }

      private int position(String name) {
        Integer p = positions.get(name);
        return p != null ? p : positions.size();
      }
    };
  }

  private Map<String, Integer> positions() {
    if (positions == null) {
      synchronized (this) {
        if (positions == null) {
          Map<String, Integer> p = new HashMap<>();
          if (labelTypes != null) {
            int i = 0;
            for (LabelType t : labelTypes) {
              p.put(t.getName(), i++);
            }
          }
          positions = p;
        }
      }
    }
    return positions;
  }
}

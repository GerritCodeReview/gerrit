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

package com.google.gerrit.common.data;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabelTypes {
  protected List<LabelType> labelTypes;
  private transient Map<String, LabelType> byId;
  private transient Map<String, LabelType> byLabel;
  private transient Map<String, Integer> positions;

  protected LabelTypes() {
  }

  public LabelTypes(final List<LabelType> approvals) {
    labelTypes = approvals;
    byId();
  }

  public List<LabelType> getLabelTypes() {
    return labelTypes;
  }

  public LabelType byId(String id) {
    return byId().get(id);
  }

  private Map<String, LabelType> byId() {
    if (byId == null) {
      byId = new HashMap<String, LabelType>();
      if (labelTypes != null) {
        for (final LabelType t : labelTypes) {
          byId.put(t.getId(), t);
        }
      }
    }
    return byId;
  }

  public LabelType byLabel(String labelName) {
    return byLabel().get(labelName.toLowerCase());
  }

  private Map<String, LabelType> byLabel() {
    if (byLabel == null) {
      byLabel = new HashMap<String, LabelType>();
      if (labelTypes != null) {
        for (LabelType t : labelTypes) {
          byLabel.put(t.getName().toLowerCase(), t);
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
        Integer lp = positions.get(left);
        Integer rp = positions.get(right);
        if (lp == rp) {
          return 0;
        } else if (lp == null) {
          return -1;
        } else if (rp == null) {
          return 1;
        } else {
          return lp - rp;
        }
      }
    };
  }

  private Map<String, Integer> positions() {
    if (positions == null) {
      positions = new HashMap<String, Integer>();
      if (labelTypes != null) {
        int i = 0;
        for (LabelType t : labelTypes) {
          positions.put(t.getName(), i++);
        }
      }
    }
    return positions;
  }
}

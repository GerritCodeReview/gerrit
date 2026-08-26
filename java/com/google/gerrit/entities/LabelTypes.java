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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class LabelTypes {
  private final ImmutableList<LabelType> labelTypes;
  private final ImmutableMap<String, LabelType> byLabel;
  private final ImmutableMap<String, Integer> positions;
  private final Comparator<String> nameComparator;

  public LabelTypes(List<? extends LabelType> approvals) {
    this.labelTypes = ImmutableList.copyOf(approvals);

    Map<String, LabelType> byLabelMap = Maps.newHashMapWithExpectedSize(labelTypes.size());
    Map<String, Integer> positionsMap = Maps.newHashMapWithExpectedSize(labelTypes.size());
    for (int i = 0; i < labelTypes.size(); i++) {
      LabelType t = labelTypes.get(i);
      byLabelMap.put(t.getName().toLowerCase(Locale.US), t);
      positionsMap.put(t.getName(), i);
    }
    this.byLabel = ImmutableMap.copyOf(byLabelMap);
    this.positions = ImmutableMap.copyOf(positionsMap);
    this.nameComparator =
        (left, right) -> {
          int lp = positions.getOrDefault(left, positions.size());
          int rp = positions.getOrDefault(right, positions.size());
          int cmp = lp - rp;
          if (cmp == 0) {
            cmp = left.compareTo(right);
          }
          return cmp;
        };
  }

  public List<LabelType> getLabelTypes() {
    return labelTypes;
  }

  public Optional<LabelType> byLabel(LabelId labelId) {
    return byLabel(labelId.get());
  }

  public Optional<LabelType> byLabel(String labelName) {
    return Optional.ofNullable(byLabel.get(labelName.toLowerCase(Locale.US)));
  }

  @Override
  public String toString() {
    return labelTypes.toString();
  }

  public Comparator<String> nameComparator() {
    return nameComparator;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof LabelTypes) {
      LabelTypes other = (LabelTypes) o;
      return labelTypes.equals(other.labelTypes);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return labelTypes.hashCode();
  }
}

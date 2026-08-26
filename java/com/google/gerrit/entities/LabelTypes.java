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
    this.byLabel = byLabel(labelTypes);
    this.positions = positions(labelTypes);
    this.nameComparator = nameComparator(positions);
  }

  public LabelTypes(Map<String, LabelType> byLabel) {
    this.labelTypes = ImmutableList.copyOf(byLabel.values());
    this.byLabel = ImmutableMap.copyOf(byLabel);
    this.positions = positions(labelTypes);
    this.nameComparator = nameComparator(positions);
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

  private static ImmutableMap<String, LabelType> byLabel(ImmutableList<LabelType> labelTypes) {
    Map<String, LabelType> l = Maps.newHashMapWithExpectedSize(labelTypes.size());
    for (LabelType t : labelTypes) {
      l.put(t.getName().toLowerCase(Locale.US), t);
    }
    return ImmutableMap.copyOf(l);
  }

  @Override
  public String toString() {
    return labelTypes.toString();
  }

  public Comparator<String> nameComparator() {
    return nameComparator;
  }

  private static Comparator<String> nameComparator(ImmutableMap<String, Integer> positions) {
    return (left, right) -> {
      int lp = positions.getOrDefault(left, positions.size());
      int rp = positions.getOrDefault(right, positions.size());
      int cmp = lp - rp;
      if (cmp == 0) {
        cmp = left.compareTo(right);
      }
      return cmp;
    };
  }

  private static ImmutableMap<String, Integer> positions(ImmutableList<LabelType> labelTypes) {
    Map<String, Integer> p = Maps.newHashMapWithExpectedSize(labelTypes.size());
    int i = 0;
    for (LabelType t : labelTypes) {
      p.put(t.getName(), i++);
    }
    return ImmutableMap.copyOf(p);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof LabelTypes other) {
      return labelTypes.equals(other.labelTypes);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return labelTypes.hashCode();
  }
}

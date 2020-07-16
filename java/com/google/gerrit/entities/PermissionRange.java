// Copyright (C) 2010 The Android Open Source Project
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
import java.util.List;

/**
 * Represents a closed interval [min, max] with a name. The special value [0, 0] is understood to be
 * the empty range.
 */
public class PermissionRange implements Comparable<PermissionRange> {
  public static class WithDefaults extends PermissionRange {
    protected int defaultMin;
    protected int defaultMax;

    protected WithDefaults() {}

    public WithDefaults(String name, int min, int max, int defMin, int defMax) {
      super(name, min, max);
      setDefaultRange(defMin, defMax);
    }

    public int getDefaultMin() {
      return defaultMin;
    }

    public int getDefaultMax() {
      return defaultMax;
    }

    public void setDefaultRange(int min, int max) {
      defaultMin = min;
      defaultMax = max;
    }

    /** @return all values between {@link #getMin()} and {@link #getMax()} */
    public List<Integer> getValuesAsList() {
      ArrayList<Integer> r = new ArrayList<>(getRangeSize());
      for (int i = min; i <= max; i++) {
        r.add(i);
      }
      return r;
    }

    /** @return number of values between {@link #getMin()} and {@link #getMax()} */
    public int getRangeSize() {
      return max - min;
    }
  }

  protected String name;
  protected int min;
  protected int max;

  protected PermissionRange() {}

  public PermissionRange(String name, int min, int max) {
    this.name = name;

    if (min <= max) {
      this.min = min;
      this.max = max;
    } else {
      this.min = 0;
      this.max = 0;
    }
  }

  public String getName() {
    return name;
  }

  public boolean isLabel() {
    return Permission.isLabel(getName());
  }

  public String getLabel() {
    return Permission.extractLabel(getName());
  }

  public int getMin() {
    return min;
  }

  public int getMax() {
    return max;
  }

  /** True if the value is within the range. */
  public boolean contains(int value) {
    return getMin() <= value && value <= getMax();
  }

  /** Normalize the value to fit within the bounds of the range. */
  public int squash(int value) {
    return Math.min(Math.max(getMin(), value), getMax());
  }

  /** True both {@link #getMin()} and {@link #getMax()} are 0. */
  public boolean isEmpty() {
    return getMin() == 0 && getMax() == 0;
  }

  @Override
  public int compareTo(PermissionRange o) {
    return getName().compareTo(o.getName());
  }

  @Override
  public String toString() {
    StringBuilder r = new StringBuilder();
    if (getMin() < 0 && getMax() == 0) {
      r.append(getMin());
      r.append(' ');
    } else {
      if (getMin() != getMax()) {
        if (0 <= getMin()) {
          r.append('+');
        }
        r.append(getMin());
        r.append("..");
      }
      if (0 <= getMax()) {
        r.append('+');
      }
      r.append(getMax());
      r.append(' ');
    }
    return r.toString();
  }
}

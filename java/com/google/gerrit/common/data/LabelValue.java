// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Shorts;
import java.util.List;
import java.util.Objects;

public class LabelValue {
  public static String formatValue(short value) {
    if (value < 0) {
      return Short.toString(value);
    } else if (value == 0) {
      return " 0";
    } else {
      return "+" + Short.toString(value);
    }
  }

  protected short value;
  protected String text;

  public LabelValue(short value, String text) {
    this.value = value;
    this.text = text;
  }

  public static LabelValue fromString(String src) {
    List<String> parts =
        ImmutableList.copyOf(
            Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().limit(2).split(src));
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("empty value");
    }
    String valueText = parts.size() > 1 ? parts.get(1) : "";
    return new LabelValue(Shorts.checkedCast(PermissionRule.parseInt(parts.get(0))), valueText);
  }

  protected LabelValue() {}

  public short getValue() {
    return value;
  }

  public String getText() {
    return text;
  }

  public String formatValue() {
    return formatValue(value);
  }

  public String format() {
    StringBuilder sb = new StringBuilder(formatValue());
    if (!text.isEmpty()) {
      sb.append(' ').append(text);
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LabelValue)) {
      return false;
    }
    LabelValue v = (LabelValue) o;
    return value == v.value && Objects.equals(text, v.text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, text);
  }

  @Override
  public String toString() {
    return format();
  }
}

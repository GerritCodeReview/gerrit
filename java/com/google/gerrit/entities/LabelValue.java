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

package com.google.gerrit.entities;

import static java.util.Objects.requireNonNull;

public record LabelValue(short value, String text) {
  public LabelValue {
    requireNonNull(text, "text");
  }

  public static String formatValue(short value) {
    if (value < 0) {
      return Short.toString(value);
    } else if (value == 0) {
      return " 0";
    } else {
      return "+" + value;
    }
  }

  public static LabelValue create(short value, String text) {
    return new LabelValue(value, text);
  }

  public String formatValue() {
    return formatValue(value());
  }

  public String format() {
    StringBuilder sb = new StringBuilder(formatValue());
    if (!text().isEmpty()) {
      sb.append(' ').append(text());
    }
    return sb.toString();
  }

  @Override
  public final String toString() {
    return format();
  }
}

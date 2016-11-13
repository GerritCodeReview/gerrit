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

package com.google.gerrit.server.util;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.PatchSetApproval;

/** A single vote on a label, consisting of a label name and a value. */
@AutoValue
public abstract class LabelVote {
  public static LabelVote parse(String text) {
    checkArgument(!Strings.isNullOrEmpty(text), "Empty label vote");
    if (text.charAt(0) == '-') {
      return create(text.substring(1), (short) 0);
    }
    short sign = 0;
    int i;
    for (i = text.length() - 1; i >= 0; i--) {
      int c = text.charAt(i);
      if (c == '-') {
        sign = (short) -1;
        break;
      } else if (c == '+') {
        sign = (short) 1;
        break;
      } else if (!('0' <= c && c <= '9')) {
        break;
      }
    }
    if (sign == 0) {
      return create(text, (short) 1);
    }
    return create(text.substring(0, i), (short) (sign * Short.parseShort(text.substring(i + 1))));
  }

  public static LabelVote parseWithEquals(String text) {
    checkArgument(!Strings.isNullOrEmpty(text), "Empty label vote");
    int e = text.lastIndexOf('=');
    checkArgument(e >= 0, "Label vote missing '=': %s", text);
    return create(text.substring(0, e), Short.parseShort(text.substring(e + 1), text.length()));
  }

  public static StringBuilder appendTo(StringBuilder sb, String label, short value) {
    if (value == (short) 0) {
      return sb.append('-').append(label);
    } else if (value < 0) {
      return sb.append(label).append(value);
    }
    return sb.append(label).append('+').append(value);
  }

  public static LabelVote create(String label, short value) {
    return new AutoValue_LabelVote(LabelType.checkNameInternal(label), value);
  }

  public static LabelVote create(PatchSetApproval psa) {
    return create(psa.getLabel(), psa.getValue());
  }

  public abstract String label();

  public abstract short value();

  public String format() {
    // Max short string length is "-32768".length() == 6.
    return appendTo(new StringBuilder(label().length() + 6), label(), value()).toString();
  }

  public String formatWithEquals() {
    if (value() <= (short) 0) {
      return label() + '=' + value();
    }
    return label() + "=+" + value();
  }

  @Override
  public String toString() {
    return format();
  }
}

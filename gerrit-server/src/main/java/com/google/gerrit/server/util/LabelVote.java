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

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.PatchSetApproval;

/** A single vote on a label, consisting of a label name and a value. */
public class LabelVote {
  public static LabelVote parse(String text) {
    checkArgument(!Strings.isNullOrEmpty(text), "Empty label vote");
    if (text.charAt(0) == '-') {
      return new LabelVote(text.substring(1), (short) 0);
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
      return new LabelVote(text, (short) 1);
    }
    return new LabelVote(text.substring(0, i).endsWith("=")
        ? text.substring(0, i - 1)
        : text.substring(0, i),
        (short) (sign * Short.parseShort(text.substring(i + 1))));
  }

  public static LabelVote parseWithEquals(String text) {
    checkArgument(!Strings.isNullOrEmpty(text), "Empty label vote");
    int e = text.lastIndexOf('=');
    checkArgument(e >= 0, "Label vote missing '=': %s", text);
    return new LabelVote(text.substring(0, e),
        Short.parseShort(text.substring(e + 1), text.length()));
  }

  private final String name;
  private final short value;

  public LabelVote(String name, short value) {
    this.name = LabelType.checkNameInternal(name);
    this.value = value;
  }

  public LabelVote(PatchSetApproval psa) {
    this(psa.getLabel(), psa.getValue());
  }

  public String getLabel() {
    return name;
  }

  public short getValue() {
    return value;
  }

  public String format() {
    if (value == (short) 0) {
      return '-' + name;
    } else if (value < 0) {
      return name + value;
    } else {
      return name + '+' + value;
    }
  }

  public String formatWithEquals() {
    if (value <= (short) 0) {
      return name + '=' + value;
    } else {
      return name + "=+" + value;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof LabelVote) {
      LabelVote l = (LabelVote) o;
      return Objects.equal(name, l.name)
          && value == l.value;
    }
    return false;
  }

  @Override
  public String toString() {
    return format();
  }
}

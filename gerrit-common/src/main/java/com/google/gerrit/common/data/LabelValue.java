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

import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;

public class LabelValue {
  @Deprecated
  public static LabelValue fromApprovalCategoryValue(ApprovalTypes ats,
      ApprovalCategoryValue acv) {
    ApprovalType at = ats.byId(acv.getCategoryId().get());
    return new LabelValue(at.getName(), acv.getValue(), acv.getName());
  }

  // TODO: pointer to ApprovalType?
  protected String labelName;
  protected short value;
  protected String text;

  public LabelValue(String labelName, short value, String text) {
    this.labelName = labelName;
    this.value = value;
    this.text = text;
  }

  protected LabelValue() {
  }

  public String getLabelName() {
    return labelName;
  }

  public short getValue() {
    return value;
  }

  public String getText() {
    return text;
  }

  public static String formatValue(short value) {
    if (value < 0) {
      return Short.toString(value);
    } else if (value == 0) {
      return " 0";
    } else {
      return "+" + Short.toString(value);
    }
  }

  public String formatValue() {
    return formatValue(getValue());
  }

  public static String format(String name, short value) {
    return new StringBuilder().append(formatValue(value))
        .append(' ').append(name).toString();
  }

  public String format() {
    return format(getLabelName(), getValue());
  }
}

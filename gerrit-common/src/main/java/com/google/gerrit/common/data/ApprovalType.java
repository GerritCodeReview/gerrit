// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.PatchSetApproval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalType {
  protected ApprovalCategory category;
  protected List<ApprovalCategoryValue> values;
  protected short maxNegative;
  protected short maxPositive;

  private transient Map<Short, ApprovalCategoryValue> byValue;

  protected ApprovalType() {
  }

  public ApprovalType(final ApprovalCategory ac,
      final List<ApprovalCategoryValue> valueList) {
    category = ac;
    values = new ArrayList<ApprovalCategoryValue>(valueList);
    Collections.sort(values, new Comparator<ApprovalCategoryValue>() {
      public int compare(ApprovalCategoryValue o1, ApprovalCategoryValue o2) {
        return o1.getValue() - o2.getValue();
      }
    });

    maxNegative = Short.MIN_VALUE;
    maxPositive = Short.MAX_VALUE;
    if (values.size() > 0) {
      if (values.get(0).getValue() < 0) {
        maxNegative = values.get(0).getValue();
      }
      if (values.get(values.size() - 1).getValue() > 0) {
        maxPositive = values.get(values.size() - 1).getValue();
      }
    }

    // Force the label name to pre-compute so we don't have data race conditions.
    getCategory().getLabelName();
  }

  public ApprovalCategory getCategory() {
    return category;
  }

  public List<ApprovalCategoryValue> getValues() {
    return values;
  }

  public ApprovalCategoryValue getMin() {
    if (values.isEmpty()) {
      return null;
    }
    return values.get(0);
  }

  public ApprovalCategoryValue getMax() {
    if (values.isEmpty()) {
      return null;
    }
    final ApprovalCategoryValue v = values.get(values.size() - 1);
    return v.getValue() > 0 ? v : null;
  }

  public boolean isMaxNegative(final PatchSetApproval ca) {
    return maxNegative == ca.getValue();
  }

  public boolean isMaxPositive(final PatchSetApproval ca) {
    return maxPositive == ca.getValue();
  }

  public ApprovalCategoryValue getValue(final short value) {
    initByValue();
    return byValue.get(value);
  }

  public ApprovalCategoryValue getValue(final PatchSetApproval ca) {
    initByValue();
    return byValue.get(ca.getValue());
  }

  private void initByValue() {
    if (byValue == null) {
      byValue = new HashMap<Short, ApprovalCategoryValue>();
      for (final ApprovalCategoryValue acv : values) {
        byValue.put(acv.getValue(), acv);
      }
    }
  }
}

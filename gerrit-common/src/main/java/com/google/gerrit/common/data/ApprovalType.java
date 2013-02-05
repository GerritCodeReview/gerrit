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

import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.PatchSetApproval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalType {
  public static ApprovalType fromApprovalCategory(ApprovalCategory ac,
      List<ApprovalCategoryValue> acvs) {
    List<LabelValue> values = new ArrayList<LabelValue>(acvs.size());
    for (ApprovalCategoryValue acv : acvs) {
      values.add(
          new LabelValue(ac.getLabelName(), acv.getValue(), acv.getName()));
    }
    ApprovalType at = new ApprovalType(ac.getLabelName(), values);
    at.setId(ac.getId().get());
    at.setAbbreviatedName(ac.getAbbreviatedName());
    at.setFunctionName(ac.getFunctionName());
    at.setCopyMinScore(ac.isCopyMinScore());
    at.setPosition(ac.getPosition());
    return at;
  }

  public static ApprovalType withDefaultValues(String name) {
    // TODO: validate/munge name
    List<LabelValue> values = new ArrayList<LabelValue>(2);
    values.add(new LabelValue(name, (short) 0, "Rejected"));
    values.add(new LabelValue(name, (short) 1, "Approved"));
    return new ApprovalType(name, values);
  }

  protected String name;

  protected String id;
  protected String abbreviatedName;
  protected String functionName;
  protected boolean copyMinScore;
  protected short position;

  protected List<LabelValue> values;
  protected short maxNegative;
  protected short maxPositive;


  private transient List<Integer> intList;
  private transient Map<Short, LabelValue> byValue;

  protected ApprovalType() {
  }

  public ApprovalType(String name, List<LabelValue> valueList) {
    // TODO: validate/munge name
    this.name = name;
    values = new ArrayList<LabelValue>(valueList);
    Collections.sort(values, new Comparator<LabelValue>() {
      public int compare(LabelValue o1, LabelValue o2) {
        return o1.getValue() - o2.getValue();
      }
    });
    functionName = "MaxWithBlock";

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
  }

  public String getName() {
    return name;
  }

  public String getId() {
    return id;
  }

  public List<LabelValue> getValues() {
    return values;
  }

  public LabelValue getMin() {
    if (values.isEmpty()) {
      return null;
    }
    return values.get(0);
  }

  public LabelValue getMax() {
    if (values.isEmpty()) {
      return null;
    }
    final LabelValue v = values.get(values.size() - 1);
    return v.getValue() > 0 ? v : null;
  }

  public boolean isMaxNegative(PatchSetApproval ca) {
    return maxNegative == ca.getValue();
  }

  public boolean isMaxPositive(PatchSetApproval ca) {
    return maxPositive == ca.getValue();
  }

  public LabelValue getValue(short value) {
    initByValue();
    return byValue.get(value);
  }

  public LabelValue getValue(final PatchSetApproval ca) {
    initByValue();
    return byValue.get(ca.getValue());
  }

  private void initByValue() {
    if (byValue == null) {
      byValue = new HashMap<Short, LabelValue>();
      for (final LabelValue v : values) {
        byValue.put(v.getValue(), v);
      }
    }
  }

  public List<Integer> getValuesAsList() {
    if (intList == null) {
      intList = new ArrayList<Integer>(values.size());
      for (LabelValue v : values) {
        intList.add(Integer.valueOf(v.getValue()));
      }
      Collections.sort(intList);
      Collections.reverse(intList);
    }
    return intList;
  }

  public boolean isCopyMinScore() {
    return copyMinScore;
  }

  public void setCopyMinScore(boolean copyMinScore) {
    this.copyMinScore = copyMinScore;
  }

  @Deprecated
  public ApprovalCategoryValue.Id getApprovalCategoryValueId(short value) {
    return new ApprovalCategoryValue.Id(getApprovalCategoryId(), value);
  }

  @Deprecated
  public ApprovalCategory.Id getApprovalCategoryId() {
    return new ApprovalCategory.Id(getId());
  }

  public String getFunctionName() {
    return functionName;
  }

  public String getAbbreviatedName() {
    return abbreviatedName;
  }

  public short getPosition() {
    return position;
  }

  public void setPosition(short position) {
    this.position = position;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setFunctionName(String functionName) {
    this.functionName = functionName;
  }

  public void setAbbreviatedName(String abbreviatedName) {
    this.abbreviatedName = abbreviatedName;
  }
}

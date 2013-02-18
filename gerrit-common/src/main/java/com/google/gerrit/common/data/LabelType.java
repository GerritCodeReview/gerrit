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

import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabelType {
  public static LabelType withDefaultValues(String id, String name) {
    checkId(id);
    checkName(name);
    List<LabelValue> values = new ArrayList<LabelValue>(2);
    values.add(new LabelValue((short) 0, "Rejected"));
    values.add(new LabelValue((short) 1, "Approved"));
    return new LabelType(id, name, values);
  }

  private static String checkId(String id) {
    if (id == null || id.length() > 4) {
      throw new IllegalArgumentException(
          "Illegal label ID \"" + id + "\"");
    }
    return id;
  }

  private static String checkName(String name) {
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!((c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') ||
            c == '-')) {
        throw new IllegalArgumentException(
            "Illegal label name \"" + name + "\"");
      }
    }
    return name;
  }

  public static String defaultAbbreviation(String name) {
    StringBuilder abbr = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        abbr.append(c);
      }
    }
    if (abbr.length() == 0) {
      abbr.append(Character.toUpperCase(name.charAt(0)));
    }
    return abbr.toString();
  }

  protected String name;

  protected String id;
  protected String abbreviatedName;
  protected String functionName;
  protected boolean copyMinScore;

  protected List<LabelValue> values;
  protected short maxNegative;
  protected short maxPositive;

  private transient boolean block;
  private transient List<Integer> intList;
  private transient Map<Short, LabelValue> byValue;

  protected LabelType() {
  }

  public LabelType(String id, String name, List<LabelValue> valueList) {
    this.id = checkId(id);
    this.name = checkName(name);
    values = new ArrayList<LabelValue>(valueList);
    Collections.sort(values, new Comparator<LabelValue>() {
      public int compare(LabelValue o1, LabelValue o2) {
        return o1.getValue() - o2.getValue();
      }
    });

    abbreviatedName = defaultAbbreviation(name);
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

  public String getAbbreviatedName() {
    return abbreviatedName;
  }

  public void setAbbreviatedName(String abbreviatedName) {
    this.abbreviatedName = abbreviatedName;
  }

  public String getFunctionName() {
    return functionName;
  }

  public void setFunctionName(String functionName) {
    this.functionName = functionName;
  }

  public boolean isBlock() {
    return block;
  }

  public void setBlock(boolean block) {
    this.block = block;
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

  public boolean isCopyMinScore() {
    return copyMinScore;
  }

  public void setCopyMinScore(boolean copyMinScore) {
    this.copyMinScore = copyMinScore;
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

  public LabelId getLabelId() {
    return new LabelId(getId());
  }
}

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
  public static LabelType withDefaultValues(String name) {
    checkName(name);
    List<LabelValue> values = new ArrayList<LabelValue>(2);
    values.add(new LabelValue((short) 0, "Rejected"));
    values.add(new LabelValue((short) 1, "Approved"));
    return new LabelType(name, values);
  }

  private static String checkName(String name) {
    if ("SUBM".equals(name)) {
      throw new IllegalArgumentException(
          "Reserved label name \"" + name + "\"");
    }
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

  private static List<LabelValue> sortValues(List<LabelValue> values) {
    values = new ArrayList<LabelValue>(values);
    if (values.size() <= 1) {
      return Collections.unmodifiableList(values);
    }
    Collections.sort(values, new Comparator<LabelValue>() {
      public int compare(LabelValue o1, LabelValue o2) {
        return o1.getValue() - o2.getValue();
      }
    });
    short min = values.get(0).getValue();
    short max = values.get(values.size() - 1).getValue();
    short v = min;
    short i = 0;
    List<LabelValue> result = new ArrayList<LabelValue>(max - min + 1);
    // Fill in any missing values with empty text.
    while (i < values.size()) {
      while (v < values.get(i).getValue()) {
        result.add(new LabelValue(v++, ""));
      }
      v++;
      result.add(values.get(i++));
    }
    return Collections.unmodifiableList(result);
  }

  protected String name;

  protected String abbreviatedName;
  protected String functionName;
  protected boolean copyMinScore;
  protected boolean copyMaxScore;

  protected List<LabelValue> values;
  protected short maxNegative;
  protected short maxPositive;

  private transient boolean canOverride;
  private transient List<Integer> intList;
  private transient Map<Short, LabelValue> byValue;

  protected LabelType() {
  }

  public LabelType(String name, List<LabelValue> valueList) {
    this.name = checkName(name);
    canOverride = true;
    values = sortValues(valueList);

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

  public boolean matches(PatchSetApproval psa) {
    return psa.getLabelId().get().equalsIgnoreCase(name);
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

  public boolean canOverride() {
    return canOverride;
  }

  public void setCanOverride(boolean canOverride) {
    this.canOverride = canOverride;
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

  public boolean isCopyMaxScore() {
    return copyMaxScore;
  }

  public void setCopyMaxScore(boolean copyMaxScore) {
    this.copyMaxScore = copyMaxScore;
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
    return new LabelId(name);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(name).append('[');
    LabelValue min = getMin();
    LabelValue max = getMax();
    if (min != null && max != null) {
      sb.append(new PermissionRange(Permission.forLabel(name), min.getValue(),
          max.getValue()).toString().trim());
    } else if (min != null) {
      sb.append(min.formatValue().trim());
    } else if (max != null) {
      sb.append(max.formatValue().trim());
    }
    sb.append(']');
    return sb.toString();
  }
}

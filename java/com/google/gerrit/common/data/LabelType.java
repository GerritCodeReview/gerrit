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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.LabelTypeInfo;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LabelType {
  public static final boolean DEF_ALLOW_POST_SUBMIT = true;
  public static final boolean DEF_CAN_OVERRIDE = true;
  public static final boolean DEF_COPY_ALL_SCORES_IF_NO_CHANGE = true;
  public static final boolean DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE = false;
  public static final boolean DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE = false;
  public static final boolean DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE = false;
  public static final boolean DEF_COPY_MAX_SCORE = false;
  public static final boolean DEF_COPY_MIN_SCORE = false;
  public static final boolean DEF_IGNORE_SELF_APPROVAL = false;

  public static LabelType withDefaultValues(String name) {
    checkName(name);
    List<LabelValue> values = new ArrayList<>(2);
    values.add(LabelValue.create((short) 0, "Rejected"));
    values.add(LabelValue.create((short) 1, "Approved"));
    return new LabelType(name, values);
  }

  public static String checkName(String name) {
    checkNameInternal(name);
    if ("SUBM".equals(name)) {
      throw new IllegalArgumentException("Reserved label name \"" + name + "\"");
    }
    return name;
  }

  public static String checkNameInternal(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Empty label name");
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if ((i == 0 && c == '-')
          || !((c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '-')) {
        throw new IllegalArgumentException("Illegal label name \"" + name + "\"");
      }
    }
    return name;
  }

  private static List<LabelValue> sortValues(List<LabelValue> values) {
    values = new ArrayList<>(values);
    if (values.isEmpty()) {
      return Collections.emptyList();
    }
    values = values.stream().sorted(comparing(LabelValue::value)).collect(toList());
    short v = values.get(0).value();
    short i = 0;
    ArrayList<LabelValue> result = new ArrayList<>();
    // Fill in any missing values with empty text.
    while (i < values.size()) {
      while (v < values.get(i).value()) {
        result.add(LabelValue.create(v++, ""));
      }
      v++;
      result.add(values.get(i++));
    }
    result.trimToSize();
    return Collections.unmodifiableList(result);
  }

  private String name;

  private LabelFunction function;

  private boolean copyMinScore;
  private boolean copyMaxScore;
  private boolean copyAllScoresOnMergeFirstParentUpdate;
  private boolean copyAllScoresOnTrivialRebase;
  private boolean copyAllScoresIfNoCodeChange;
  private boolean copyAllScoresIfNoChange;
  private boolean allowPostSubmit;
  private boolean ignoreSelfApproval;
  private short defaultValue;

  private List<LabelValue> values;
  private short maxNegative;
  private short maxPositive;

  private boolean canOverride;
  private List<String> refPatterns;
  private Map<Short, LabelValue> byValue;

  public LabelType(String name, List<LabelValue> valueList) {
    this.name = checkName(name);
    canOverride = true;
    values = sortValues(valueList);
    defaultValue = 0;

    function = LabelFunction.MAX_WITH_BLOCK;

    maxNegative = Short.MIN_VALUE;
    maxPositive = Short.MAX_VALUE;
    if (values.size() > 0) {
      if (values.get(0).value() < 0) {
        maxNegative = values.get(0).value();
      }
      if (values.get(values.size() - 1).value() > 0) {
        maxPositive = values.get(values.size() - 1).value();
      }
    }
    setCanOverride(DEF_CAN_OVERRIDE);
    setCopyAllScoresIfNoChange(DEF_COPY_ALL_SCORES_IF_NO_CHANGE);
    setCopyAllScoresIfNoCodeChange(DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE);
    setCopyAllScoresOnTrivialRebase(DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
    setCopyAllScoresOnMergeFirstParentUpdate(DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE);
    setCopyMaxScore(DEF_COPY_MAX_SCORE);
    setCopyMinScore(DEF_COPY_MIN_SCORE);
    setAllowPostSubmit(DEF_ALLOW_POST_SUBMIT);
    setIgnoreSelfApproval(DEF_IGNORE_SELF_APPROVAL);

    byValue = new HashMap<>();
    for (LabelValue v : values) {
      byValue.put(v.value(), v);
    }
  }

  public String getName() {
    return name;
  }

  public boolean matches(PatchSetApproval psa) {
    return psa.labelId().get().equalsIgnoreCase(name);
  }

  public LabelFunction getFunction() {
    return function;
  }

  public void setFunction(@Nullable LabelFunction function) {
    this.function = function;
  }

  public boolean canOverride() {
    return canOverride;
  }

  public List<String> getRefPatterns() {
    return refPatterns;
  }

  public void setCanOverride(boolean canOverride) {
    this.canOverride = canOverride;
  }

  public boolean allowPostSubmit() {
    return allowPostSubmit;
  }

  public void setAllowPostSubmit(boolean allowPostSubmit) {
    this.allowPostSubmit = allowPostSubmit;
  }

  public boolean ignoreSelfApproval() {
    return ignoreSelfApproval;
  }

  public void setIgnoreSelfApproval(boolean ignoreSelfApproval) {
    this.ignoreSelfApproval = ignoreSelfApproval;
  }

  public void setRefPatterns(List<String> refPatterns) {
    if (refPatterns != null) {
      this.refPatterns =
          refPatterns.stream().collect(collectingAndThen(toList(), Collections::unmodifiableList));
    } else {
      this.refPatterns = null;
    }
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
    return values.get(values.size() - 1);
  }

  public short getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(short defaultValue) {
    this.defaultValue = defaultValue;
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

  public boolean isCopyAllScoresOnMergeFirstParentUpdate() {
    return copyAllScoresOnMergeFirstParentUpdate;
  }

  public void setCopyAllScoresOnMergeFirstParentUpdate(
      boolean copyAllScoresOnMergeFirstParentUpdate) {
    this.copyAllScoresOnMergeFirstParentUpdate = copyAllScoresOnMergeFirstParentUpdate;
  }

  public boolean isCopyAllScoresOnTrivialRebase() {
    return copyAllScoresOnTrivialRebase;
  }

  public void setCopyAllScoresOnTrivialRebase(boolean copyAllScoresOnTrivialRebase) {
    this.copyAllScoresOnTrivialRebase = copyAllScoresOnTrivialRebase;
  }

  public boolean isCopyAllScoresIfNoCodeChange() {
    return copyAllScoresIfNoCodeChange;
  }

  public void setCopyAllScoresIfNoCodeChange(boolean copyAllScoresIfNoCodeChange) {
    this.copyAllScoresIfNoCodeChange = copyAllScoresIfNoCodeChange;
  }

  public boolean isCopyAllScoresIfNoChange() {
    return copyAllScoresIfNoChange;
  }

  public void setCopyAllScoresIfNoChange(boolean copyAllScoresIfNoChange) {
    this.copyAllScoresIfNoChange = copyAllScoresIfNoChange;
  }

  public boolean isMaxNegative(PatchSetApproval ca) {
    return maxNegative == ca.value();
  }

  public boolean isMaxPositive(PatchSetApproval ca) {
    return maxPositive == ca.value();
  }

  public LabelValue getValue(short value) {
    return byValue.get(value);
  }

  public LabelValue getValue(PatchSetApproval ca) {
    return byValue.get(ca.value());
  }

  public LabelId getLabelId() {
    return LabelId.create(name);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(name).append('[');
    LabelValue min = getMin();
    LabelValue max = getMax();
    if (min != null && max != null) {
      sb.append(
          new PermissionRange(Permission.forLabel(name), min.value(), max.value())
              .toString()
              .trim());
    } else if (min != null) {
      sb.append(min.formatValue().trim());
    } else if (max != null) {
      sb.append(max.formatValue().trim());
    }
    sb.append(']');
    return sb.toString();
  }

  public LabelTypeInfo toLabelTypeInfo() {
    LabelTypeInfo labelInfo = new LabelTypeInfo();

    labelInfo.name = name;
    labelInfo.function = function.toString();
    labelInfo.branches = refPatterns;

    labelInfo.values = values.stream().collect(toMap(LabelValue::formatValue, LabelValue::text));
    labelInfo.defaultValue = defaultValue;

    labelInfo.copyMinScore = falseToNull(copyMinScore);
    labelInfo.copyMaxScore = falseToNull(copyMaxScore);
    labelInfo.copyAllScoresOnMergeFirstParentUpdate =
        falseToNull(copyAllScoresOnMergeFirstParentUpdate);
    labelInfo.copyAllScoresOnTrivialRebase = falseToNull(copyAllScoresOnTrivialRebase);
    labelInfo.copyAllScoresIfNoCodeChange = falseToNull(copyAllScoresIfNoCodeChange);
    labelInfo.copyAllScoresIfNoChange = falseToNull(copyAllScoresIfNoChange);
    labelInfo.allowPostSubmit = falseToNull(allowPostSubmit);
    labelInfo.ignoreSelfApproval = falseToNull(ignoreSelfApproval);
    labelInfo.canOverride = falseToNull(canOverride);

    return labelInfo;
  }

  private static Boolean falseToNull(boolean value) {
    return value ? true : null;
  }
}

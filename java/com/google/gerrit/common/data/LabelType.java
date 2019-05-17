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
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.LabelTypeInfo;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AutoValue
public abstract class LabelType {
  public static final boolean DEF_ALLOW_POST_SUBMIT = true;
  public static final boolean DEF_CAN_OVERRIDE = true;
  public static final boolean DEF_COPY_ALL_SCORES_IF_NO_CHANGE = true;
  public static final boolean DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE = false;
  public static final boolean DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE = false;
  public static final boolean DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE = false;
  public static final boolean DEF_COPY_MAX_SCORE = false;
  public static final boolean DEF_COPY_MIN_SCORE = false;
  public static final boolean DEF_IGNORE_SELF_APPROVAL = false;

  // private transient Map<Short, LabelValue> byValue;

  private LabelType(String name, List<LabelValue> valueList) {
    // this.name = checkName(name);
    // canOverride = true;
    // values = sortValues(valueList);
    // defaultValue = 0;
    //
    // function = LabelFunction.MAX_WITH_BLOCK;
    //
    // maxNegative = Short.MIN_VALUE;
    // maxPositive = Short.MAX_VALUE;
    // if (values.size() > 0) {
    //   if (values.get(0).value() < 0) {
    //     maxNegative = values.get(0).value();
    //   }
    //   if (values.get(values.size() - 1).value() > 0) {
    //     maxPositive = values.get(values.size() - 1).value();
    //   }
    // }
    // setCanOverride(DEF_CAN_OVERRIDE);
    // setCopyAllScoresIfNoChange(DEF_COPY_ALL_SCORES_IF_NO_CHANGE);
    // setCopyAllScoresIfNoCodeChange(DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE);
    // setCopyAllScoresOnTrivialRebase(DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
    // setCopyAllScoresOnMergeFirstParentUpdate(DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE);
    // setCopyMaxScore(DEF_COPY_MAX_SCORE);
    // setCopyMinScore(DEF_COPY_MIN_SCORE);
    // setAllowPostSubmit(DEF_ALLOW_POST_SUBMIT);
    // setIgnoreSelfApproval(DEF_IGNORE_SELF_APPROVAL);
    //
    // byValue = new HashMap<>();
    // for (LabelValue v : values) {
    //   byValue.put(v.value(), v);
    // }
  }

  public static LabelType create(String name, List<LabelValue> valueList) {
    return null; // new LabelType(name, valueList);
  }

  public static LabelType withDefaultValues(String name) {
    checkName(name);
    List<LabelValue> values = new ArrayList<>(2);
    values.add(LabelValue.create((short) 0, "Rejected"));
    values.add(LabelValue.create((short) 1, "Approved"));
    return LabelType.create(name, values);
  }

  public abstract String name();

  @Nullable
  public abstract LabelFunction function();

  public abstract boolean canOverride();

  @Nullable
  public abstract List<String> refPatterns();

  public abstract boolean allowPostSubmit();

  public abstract boolean ignoreSelfApproval();

  public abstract List<LabelValue> values();

  public abstract short defaultValue();

  public abstract boolean copyMinScore();

  public abstract boolean copyMaxScore();

  public abstract boolean copyAllScoresOnMergeFirstParentUpdate();

  public abstract boolean copyAllScoresOnTrivialRebase();

  public abstract boolean copyAllScoresIfNoCodeChange();

  public abstract boolean copyAllScoresIfNoChange();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return null;
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

  public boolean matches(PatchSetApproval psa) {
    return psa.labelId().get().equalsIgnoreCase(name);
  }

  public void setFunction(@Nullable LabelFunction function) {
    this.toBuilder().function(function);
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

  @AutoValue.Builder
  public abstract class Builder {
    public abstract Builder name(String name);

    public abstract Builder function(LabelFunction labelFunction);

    public abstract Builder canOverride(boolean canOverride);

    public abstract Builder refPatterns(List<String> refPatterns);

    public abstract Builder allowPostSubmit(boolean allowPostSubmit);

    public abstract Builder ignoreSelfApproval(boolean ignoreSelfApproval);

    public abstract Builder setIgnoreSelfApproval(boolean ignoreSelfApproval);

    public abstract Builder values(List<LabelValue> labelValues);

    public abstract Builder defaultValue(short defaultValue);

    public abstract Builder copyMinScore(boolean copyMinScore);

    public abstract Builder copyMaxScore(boolean copyMaxScore);

    public abstract Builder copyAllScoresOnMergeFirstParentUpdate(
        boolean copyAllScoresOnMergeFirstParentUpdate);

    public abstract Builder copyAllScoresOnTrivialRebase(boolean copyAllScoresOnTrivialRebase);

    public abstract Builder copyAllScoresIfNoCodeChange(boolean copyAllScoresIfNoCodeChange);

    public abstract Builder copyAllScoresIfNoChange(boolean copyAllScoresIfNoChange);

    public abstract LabelType build();
  }
}

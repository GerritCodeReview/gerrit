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

package com.google.gerrit.entities;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@AutoValue
public abstract class LabelType {
  public static final boolean DEF_ALLOW_POST_SUBMIT = true;
  public static final boolean DEF_CAN_OVERRIDE = true;
  public static final boolean DEF_COPY_ALL_SCORES_IF_NO_CHANGE = true;
  public static final boolean DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE = false;
  public static final boolean DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE = false;
  public static final boolean DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE = false;
  public static final boolean DEF_COPY_ANY_SCORE = false;
  public static final boolean DEF_COPY_MAX_SCORE = false;
  public static final boolean DEF_COPY_MIN_SCORE = false;
  public static final ImmutableList<Short> DEF_COPY_VALUES = ImmutableList.of();
  public static final boolean DEF_IGNORE_SELF_APPROVAL = false;

  public static LabelType withDefaultValues(String name) {
    checkName(name);
    List<LabelValue> values = new ArrayList<>(2);
    values.add(LabelValue.create((short) 0, "Rejected"));
    values.add(LabelValue.create((short) 1, "Approved"));
    return create(name, values);
  }

  public static String checkName(String name) throws IllegalArgumentException {
    checkNameInternal(name);
    if ("SUBM".equals(name)) {
      throw new IllegalArgumentException("Reserved label name \"" + name + "\"");
    }
    return name;
  }

  public static String checkNameInternal(String name) throws IllegalArgumentException {
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

  private static ImmutableList<LabelValue> sortValues(List<LabelValue> values) {
    if (values.isEmpty()) {
      return ImmutableList.of();
    }
    values = values.stream().sorted(comparing(LabelValue::getValue)).collect(toList());
    short v = values.get(0).getValue();
    short i = 0;
    ImmutableList.Builder<LabelValue> result = ImmutableList.builder();
    // Fill in any missing values with empty text.
    while (i < values.size()) {
      while (v < values.get(i).getValue()) {
        result.add(LabelValue.create(v++, ""));
      }
      v++;
      result.add(values.get(i++));
    }
    return result.build();
  }

  public abstract String getName();

  public abstract LabelFunction getFunction();

  public abstract boolean isCopyAnyScore();

  public abstract boolean isCopyMinScore();

  public abstract boolean isCopyMaxScore();

  public abstract boolean isCopyAllScoresOnMergeFirstParentUpdate();

  public abstract boolean isCopyAllScoresOnTrivialRebase();

  public abstract boolean isCopyAllScoresIfNoCodeChange();

  public abstract boolean isCopyAllScoresIfNoChange();

  public abstract ImmutableList<Short> getCopyValues();

  public abstract boolean isAllowPostSubmit();

  public abstract boolean isIgnoreSelfApproval();

  public abstract short getDefaultValue();

  public abstract ImmutableList<LabelValue> getValues();

  public abstract short getMaxNegative();

  public abstract short getMaxPositive();

  public abstract boolean isCanOverride();

  @Nullable
  public abstract ImmutableList<String> getRefPatterns();

  public abstract ImmutableMap<Short, LabelValue> getByValue();

  public static LabelType create(String name, List<LabelValue> valueList) {
    return LabelType.builder(name, valueList).build();
  }

  public static LabelType.Builder builder(String name, List<LabelValue> valueList) {
    return (new AutoValue_LabelType.Builder())
        .setName(name)
        .setValues(valueList)
        .setDefaultValue((short) 0)
        .setFunction(LabelFunction.MAX_WITH_BLOCK)
        .setMaxNegative(Short.MIN_VALUE)
        .setMaxPositive(Short.MAX_VALUE)
        .setCanOverride(DEF_CAN_OVERRIDE)
        .setCopyAllScoresIfNoChange(DEF_COPY_ALL_SCORES_IF_NO_CHANGE)
        .setCopyAllScoresIfNoCodeChange(DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE)
        .setCopyAllScoresOnTrivialRebase(DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE)
        .setCopyAllScoresOnMergeFirstParentUpdate(DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE)
        .setCopyAnyScore(DEF_COPY_ANY_SCORE)
        .setCopyMaxScore(DEF_COPY_MAX_SCORE)
        .setCopyMinScore(DEF_COPY_MIN_SCORE)
        .setCopyValues(DEF_COPY_VALUES)
        .setAllowPostSubmit(DEF_ALLOW_POST_SUBMIT)
        .setIgnoreSelfApproval(DEF_IGNORE_SELF_APPROVAL);
  }

  public boolean matches(PatchSetApproval psa) {
    return psa.labelId().get().equalsIgnoreCase(getName());
  }

  public LabelValue getMin() {
    if (getValues().isEmpty()) {
      return null;
    }
    return getValues().get(0);
  }

  public LabelValue getMax() {
    if (getValues().isEmpty()) {
      return null;
    }
    return getValues().get(getValues().size() - 1);
  }

  public boolean isMaxNegative(PatchSetApproval ca) {
    return getMaxNegative() == ca.value();
  }

  public boolean isMaxPositive(PatchSetApproval ca) {
    return getMaxPositive() == ca.value();
  }

  public LabelValue getValue(short value) {
    return getByValue().get(value);
  }

  public LabelValue getValue(PatchSetApproval ca) {
    return getByValue().get(ca.value());
  }

  public LabelId getLabelId() {
    return LabelId.create(getName());
  }

  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder(getName()).append('[');
    LabelValue min = getMin();
    LabelValue max = getMax();
    if (min != null && max != null) {
      sb.append(
          new PermissionRange(Permission.forLabel(getName()), min.getValue(), max.getValue())
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

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setFunction(LabelFunction function);

    public abstract Builder setCanOverride(boolean canOverride);

    public abstract Builder setAllowPostSubmit(boolean allowPostSubmit);

    public abstract Builder setIgnoreSelfApproval(boolean ignoreSelfApproval);

    public abstract Builder setRefPatterns(@Nullable List<String> refPatterns);

    public abstract Builder setValues(List<LabelValue> values);

    public abstract Builder setDefaultValue(short defaultValue);

    public abstract Builder setCopyAnyScore(boolean copyAnyScore);

    public abstract Builder setCopyMinScore(boolean copyMinScore);

    public abstract Builder setCopyMaxScore(boolean copyMaxScore);

    public abstract Builder setCopyAllScoresOnMergeFirstParentUpdate(
        boolean copyAllScoresOnMergeFirstParentUpdate);

    public abstract Builder setCopyAllScoresOnTrivialRebase(boolean copyAllScoresOnTrivialRebase);

    public abstract Builder setCopyAllScoresIfNoCodeChange(boolean copyAllScoresIfNoCodeChange);

    public abstract Builder setCopyAllScoresIfNoChange(boolean copyAllScoresIfNoChange);

    public abstract Builder setCopyValues(Collection<Short> copyValues);

    public abstract Builder setMaxNegative(short maxNegative);

    public abstract Builder setMaxPositive(short maxPositive);

    public abstract ImmutableList<LabelValue> getValues();

    protected abstract String getName();

    protected abstract ImmutableList<Short> getCopyValues();

    protected abstract Builder setByValue(ImmutableMap<Short, LabelValue> byValue);

    @Nullable
    protected abstract ImmutableList<String> getRefPatterns();

    protected abstract LabelType autoBuild();

    public LabelType build() throws IllegalArgumentException {
      setName(checkName(getName()));
      if (getRefPatterns() == null || getRefPatterns().isEmpty()) {
        // Empty to null
        setRefPatterns(null);
      }

      List<LabelValue> valueList = sortValues(getValues());
      setValues(valueList);
      if (!valueList.isEmpty()) {
        if (valueList.get(0).getValue() < 0) {
          setMaxNegative(valueList.get(0).getValue());
        }
        if (valueList.get(valueList.size() - 1).getValue() > 0) {
          setMaxPositive(valueList.get(valueList.size() - 1).getValue());
        }
      }

      ImmutableMap.Builder<Short, LabelValue> byValue = ImmutableMap.builder();
      for (LabelValue v : valueList) {
        byValue.put(v.getValue(), v);
      }
      setByValue(byValue.build());

      setCopyValues(ImmutableList.sortedCopyOf(getCopyValues()));

      return autoBuild();
    }
  }
}

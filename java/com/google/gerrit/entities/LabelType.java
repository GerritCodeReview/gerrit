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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AutoValue
public abstract class LabelType {
  public static final boolean DEF_ALLOW_POST_SUBMIT = true;
  public static final boolean DEF_CAN_OVERRIDE = true;
  public static final boolean DEF_IGNORE_SELF_APPROVAL = false;

  public static LabelType withDefaultValues(String name) {
    checkName(name);
    List<LabelValue> values = new ArrayList<>(2);
    values.add(LabelValue.create((short) 0, "Rejected"));
    values.add(LabelValue.create((short) 1, "Approved"));
    return create(name, values);
  }

  @CanIgnoreReturnValue
  public static String checkName(String name) throws IllegalArgumentException {
    checkNameInternal(name);
    if ("SUBM".equals(name)) {
      throw new IllegalArgumentException("Reserved label name \"" + name + "\"");
    }
    return name;
  }

  @CanIgnoreReturnValue
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

  public abstract Optional<String> getDescription();

  public abstract LabelFunction getFunction();

  public abstract boolean isAllowPostSubmit();

  public abstract boolean isIgnoreSelfApproval();

  public abstract short getDefaultValue();

  public abstract ImmutableList<LabelValue> getValues();

  public abstract short getMaxNegative();

  public abstract short getMaxPositive();

  public abstract boolean isCanOverride();

  public abstract Optional<String> getCopyCondition();

  @Nullable
  public abstract ImmutableList<String> getRefPatterns();

  public abstract ImmutableMap<Short, LabelValue> getByValue();

  public static LabelType create(String name, List<LabelValue> valueList) {
    return LabelType.builder(name, valueList).build();
  }

  public static LabelType.Builder builder(String name, List<LabelValue> valueList) {
    return new AutoValue_LabelType.Builder()
        .setName(name)
        .setDescription(Optional.empty())
        .setValues(valueList)
        .setDefaultValue((short) 0)
        .setFunction(LabelFunction.MAX_WITH_BLOCK)
        .setMaxNegative(Short.MIN_VALUE)
        .setMaxPositive(Short.MAX_VALUE)
        .setCanOverride(DEF_CAN_OVERRIDE)
        .setAllowPostSubmit(DEF_ALLOW_POST_SUBMIT)
        .setIgnoreSelfApproval(DEF_IGNORE_SELF_APPROVAL);
  }

  public boolean matches(PatchSetApproval psa) {
    return psa.labelId().get().equalsIgnoreCase(getName());
  }

  @Nullable
  public LabelValue getMin() {
    if (getValues().isEmpty()) {
      return null;
    }
    return getValues().get(0);
  }

  @Nullable
  public LabelValue getMax() {
    if (getValues().isEmpty()) {
      return null;
    }
    return getValues().get(getValues().size() - 1);
  }

  public boolean isMaxNegative(PatchSetApproval ca) {
    return isMaxNegative(ca.value());
  }

  public boolean isMaxNegative(short value) {
    return getMaxNegative() == value;
  }

  public boolean isMaxPositive(PatchSetApproval ca) {
    return isMaxPositive(ca.value());
  }

  public boolean isMaxPositive(short value) {
    return getMaxPositive() == value;
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

    public abstract Builder setDescription(Optional<String> description);

    /**
     * @deprecated All label functions except {@code PATCH_SET_LOCK} are deprecated in favour of
     *     using submit requirements. When submit requirements are used the label function needs to
     *     be set to {@code NO_BLOCK} (or {@code NO_OP} which is semantically the same). This is to
     *     override the default label function which is {@code MAX_WITH_BLOCK} and which should not
     *     be used in combination with a submit requirement.
     */
    @Deprecated
    public abstract Builder setFunction(LabelFunction function);

    /**
     * Sets the label function to {@code NO_BLOCK}, e.g. to override the default label function
     * which is {@code MAX_WITH_BLOCK} and which should not be used in combination with a submit
     * requirement. .
     *
     * <p>In contrast to most other label functions {@code NO_BLOCK} is not deprecated.
     *
     * <p>Use this method to set the label function to {@code NO_BLOCK}, instead of calling {@code
     * setFunction(NO_BLOCK)} which is deprecated.
     *
     * <p>Note, {@code NO_OP} is semantically the same as {@code NO_BLOCK}, hence this method should
     * also be used, instead of calling {@code setFunction(NO_OP)}.
     *
     * @return the instance of this builder to allow chaining calls.
     */
    @CanIgnoreReturnValue
    @SuppressWarnings("deprecation")
    public Builder setNoBlockFunction() {
      return setFunction(LabelFunction.NO_BLOCK);
    }

    /**
     * Sets the label function to {@code PATCH_SET_LOCK}.
     *
     * <p>In contrast to most other label functions {@code PATCH_SET_LOCK} is not deprecated.
     *
     * <p>Use this method to set the label function to {@code PATCH_SET_LOCK}, instead of calling
     * {@code setFunction(PATCH_SET_LOCK)} which is deprecated.
     *
     * @return the instance of this builder to allow chaining calls.
     */
    @CanIgnoreReturnValue
    @SuppressWarnings("deprecation")
    public Builder setPatchSetLockFunction() {
      return setFunction(LabelFunction.PATCH_SET_LOCK);
    }

    public abstract Builder setCanOverride(boolean canOverride);

    public abstract Builder setAllowPostSubmit(boolean allowPostSubmit);

    public abstract Builder setIgnoreSelfApproval(boolean ignoreSelfApproval);

    public abstract Builder setRefPatterns(@Nullable List<String> refPatterns);

    public abstract Builder setValues(List<LabelValue> values);

    public abstract Builder setDefaultValue(short defaultValue);

    public abstract Builder setCopyCondition(@Nullable String copyCondition);

    public abstract Builder setMaxNegative(short maxNegative);

    public abstract Builder setMaxPositive(short maxPositive);

    public abstract ImmutableList<LabelValue> getValues();

    protected abstract String getName();

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

      ImmutableList<LabelValue> valueList = sortValues(getValues());
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

      return autoBuild();
    }
  }
}

// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.server.util.LabelVote;
import java.util.Optional;

/** Permission representing a label. */
public class LabelPermission implements ChangePermissionOrLabel {
  private final String name;

  /**
   * Construct a reference to a label permission.
   *
   * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
   */
  public LabelPermission(String name) {
    this.name = LabelType.checkName(name);
  }

  /** @return name of the label, e.g. {@code "Code-Review"}. */
  public String label() {
    return name;
  }

  /** @return name used in {@code project.config} permissions. */
  @Override
  public Optional<String> permissionName() {
    return Optional.of(Permission.forLabel(label()));
  }

  @Override
  public String describeForException() {
    return "label " + label();
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof LabelPermission && name.equals(((LabelPermission) other).name);
  }

  @Override
  public String toString() {
    return "Label[" + name + ']';
  }

  /** A {@link LabelPermission} at a specific value. */
  public static class WithValue implements ChangePermissionOrLabel {
    private final LabelVote label;

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
     * @param value numeric score assigned to the label.
     */
    public WithValue(String name, short value) {
      this(LabelVote.create(name, value));
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param label label name and vote.
     */
    public WithValue(LabelVote label) {
      this.label = checkNotNull(label, "LabelVote");
    }

    /** @return name of the label, e.g. {@code "Code-Review"}. */
    public String label() {
      return label.label();
    }

    /** @return specific value of the label, e.g. 1 or 2. */
    public short value() {
      return label.value();
    }

    /** @return name used in {@code project.config} permissions. */
    @Override
    public Optional<String> permissionName() {
      return Optional.of(Permission.forLabel(label()));
    }

    @Override
    public String describeForException() {
      return "label " + label.formatWithEquals();
    }

    @Override
    public int hashCode() {
      return label.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof WithValue && label.equals(((WithValue) other).label);
    }

    @Override
    public String toString() {
      return "Label[" + label.format() + ']';
    }
  }
}

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
import static com.google.gerrit.server.permissions.LabelPermission.ForUser.ON_BEHALF_OF;
import static com.google.gerrit.server.permissions.LabelPermission.ForUser.SELF;

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.server.util.LabelVote;

/** Permission representing a label. */
public class LabelPermission implements ChangePermissionOrLabel {
  public enum ForUser {
    SELF,
    ON_BEHALF_OF;
  }

  private final ForUser forUser;
  private final String name;

  /**
   * Construct a reference to a label permission.
   *
   * @param type type description of the label.
   */
  public LabelPermission(LabelType type) {
    this(SELF, type);
  }

  /**
   * Construct a reference to a label permission.
   *
   * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
   * @param type type description of the label.
   */
  public LabelPermission(ForUser forUser, LabelType type) {
    this(forUser, type.getName());
  }

  /**
   * Construct a reference to a label permission.
   *
   * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
   */
  public LabelPermission(String name) {
    this(SELF, name);
  }

  /**
   * Construct a reference to a label permission.
   *
   * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
   * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
   */
  public LabelPermission(ForUser forUser, String name) {
    this.forUser = checkNotNull(forUser, "ForUser");
    this.name = LabelType.checkName(name);
  }

  /** @return {@code SELF} or {@code ON_BEHALF_OF} (or labelAs). */
  public ForUser forUser() {
    return forUser;
  }

  /** @return name of the label, e.g. {@code "Code-Review"}. */
  public String label() {
    return name;
  }

  @Override
  public String describeForException() {
    if (forUser == ON_BEHALF_OF) {
      return "label on behalf of " + name;
    }
    return "label " + name;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof LabelPermission) {
      LabelPermission b = (LabelPermission) other;
      return forUser == b.forUser && name.equals(b.name);
    }
    return false;
  }

  @Override
  public String toString() {
    if (forUser == ON_BEHALF_OF) {
      return "LabelAs[" + name + ']';
    }
    return "Label[" + name + ']';
  }

  /** A {@link LabelPermission} at a specific value. */
  public static class WithValue implements ChangePermissionOrLabel {
    private final ForUser forUser;
    private final LabelVote label;

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param type description of the label.
     * @param value numeric score assigned to the label.
     */
    public WithValue(LabelType type, LabelValue value) {
      this(SELF, type, value);
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param type description of the label.
     * @param value numeric score assigned to the label.
     */
    public WithValue(LabelType type, short value) {
      this(SELF, type.getName(), value);
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
     * @param type description of the label.
     * @param value numeric score assigned to the label.
     */
    public WithValue(ForUser forUser, LabelType type, LabelValue value) {
      this(forUser, type.getName(), value.getValue());
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
     * @param type description of the label.
     * @param value numeric score assigned to the label.
     */
    public WithValue(ForUser forUser, LabelType type, short value) {
      this(forUser, type.getName(), value);
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
     * @param value numeric score assigned to the label.
     */
    public WithValue(String name, short value) {
      this(SELF, name, value);
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
     * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
     * @param value numeric score assigned to the label.
     */
    public WithValue(ForUser forUser, String name, short value) {
      this(forUser, LabelVote.create(name, value));
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param label label name and vote.
     */
    public WithValue(LabelVote label) {
      this(SELF, label);
    }

    /**
     * Construct a reference to a label at a specific value.
     *
     * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
     * @param label label name and vote.
     */
    public WithValue(ForUser forUser, LabelVote label) {
      this.forUser = checkNotNull(forUser, "ForUser");
      this.label = checkNotNull(label, "LabelVote");
    }

    /** @return {@code SELF} or {@code ON_BEHALF_OF} (or labelAs). */
    public ForUser forUser() {
      return forUser;
    }

    /** @return name of the label, e.g. {@code "Code-Review"}. */
    public String label() {
      return label.label();
    }

    /** @return specific value of the label, e.g. 1 or 2. */
    public short value() {
      return label.value();
    }

    @Override
    public String describeForException() {
      if (forUser == ON_BEHALF_OF) {
        return "label on behalf of " + label.formatWithEquals();
      }
      return "label " + label.formatWithEquals();
    }

    @Override
    public int hashCode() {
      return label.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof WithValue) {
        WithValue b = (WithValue) other;
        return forUser == b.forUser && label.equals(b.label);
      }
      return false;
    }

    @Override
    public String toString() {
      if (forUser == ON_BEHALF_OF) {
        return "LabelAs[" + label.format() + ']';
      }
      return "Label[" + label.format() + ']';
    }
  }
}

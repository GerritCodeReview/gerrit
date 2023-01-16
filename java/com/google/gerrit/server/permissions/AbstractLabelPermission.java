// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.server.permissions.AbstractLabelPermission.ForUser.ON_BEHALF_OF;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.LabelType;
import com.google.gerrit.server.util.LabelVote;

/** Abstract permission representing a label. */
public abstract class AbstractLabelPermission implements ChangePermissionOrLabel {
  public enum ForUser {
    SELF,
    ON_BEHALF_OF
  }

  protected final ForUser forUser;
  protected final String name;

  /**
   * Construct a reference to an abstract label permission.
   *
   * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
   * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
   */
  public AbstractLabelPermission(ForUser forUser, String name) {
    this.forUser = requireNonNull(forUser, "ForUser");
    this.name = LabelType.checkName(name);
  }

  /** Returns {@code SELF} or {@code ON_BEHALF_OF} (or labelAs). */
  public ForUser forUser() {
    return forUser;
  }

  /** Returns name of the label, e.g. {@code "Code-Review"}. */
  public String label() {
    return name;
  }

  protected abstract String permissionPrefix();

  protected String permissionName() {
    if (forUser == ON_BEHALF_OF) {
      return permissionPrefix() + "As";
    }
    return permissionPrefix();
  }

  @Override
  public final String describeForException() {
    if (forUser == ON_BEHALF_OF) {
      return permissionPrefix() + " on behalf of " + name;
    }
    return permissionPrefix() + " " + name;
  }

  @Override
  public final int hashCode() {
    return (permissionPrefix() + name).hashCode();
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public final boolean equals(Object other) {
    if (this.getClass().isAssignableFrom(other.getClass())) {
      AbstractLabelPermission b = (AbstractLabelPermission) other;
      return forUser == b.forUser && name.equals(b.name);
    }
    return false;
  }

  @Override
  public final String toString() {
    return permissionName() + "[" + name + ']';
  }

  /** A {@link AbstractLabelPermission} at a specific value. */
  public abstract static class WithValue implements ChangePermissionOrLabel {
    private final ForUser forUser;
    private final LabelVote label;

    /**
     * Construct a reference to an abstract label permission at a specific value.
     *
     * @param forUser {@code SELF} (default) or {@code ON_BEHALF_OF} for labelAs behavior.
     * @param label label name and vote.
     */
    public WithValue(ForUser forUser, LabelVote label) {
      this.forUser = requireNonNull(forUser, "ForUser");
      this.label = requireNonNull(label, "LabelVote");
    }

    /** Returns {@code SELF} or {@code ON_BEHALF_OF} (or labelAs). */
    public ForUser forUser() {
      return forUser;
    }

    /** Returns name of the label, e.g. {@code "Code-Review"}. */
    public String label() {
      return label.label();
    }

    /** Returns specific value of the label, e.g. 1 or 2. */
    public short value() {
      return label.value();
    }

    public abstract String permissionName();

    @Override
    public final String describeForException() {
      if (forUser == ON_BEHALF_OF) {
        return permissionName() + " on behalf of " + label.formatWithEquals();
      }
      return permissionName() + " " + label.formatWithEquals();
    }

    @Override
    public final int hashCode() {
      return (permissionName() + label).hashCode();
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public final boolean equals(Object other) {
      if (this.getClass().isAssignableFrom(other.getClass())) {
        AbstractLabelPermission.WithValue b = (AbstractLabelPermission.WithValue) other;
        return forUser == b.forUser && label.equals(b.label);
      }
      return false;
    }

    @Override
    public final String toString() {
      if (forUser == ON_BEHALF_OF) {
        return permissionName() + "As[" + label.format() + ']';
      }
      return permissionName() + "[" + label.format() + ']';
    }
  }
}

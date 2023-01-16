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

import static com.google.gerrit.server.permissions.AbstractLabelPermission.ForUser.SELF;

import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.server.util.LabelVote;

/** Permission representing a label. */
public class LabelPermission extends AbstractLabelPermission {
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
    super(forUser, name);
  }

  @Override
  public String permissionPrefix() {
    return "label";
  }

  /** A {@link LabelPermission} at a specific value. */
  public static class WithValue extends AbstractLabelPermission.WithValue {
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
      super(forUser, label);
    }

    @Override
    public String permissionName() {
      return "label";
    }
  }
}

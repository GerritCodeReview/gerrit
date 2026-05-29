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

import static com.google.gerrit.server.permissions.AbstractLabelPermission.ForUser.SELF;

import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.server.util.LabelVote;

/** Permission representing a label removal. */
public class LabelRemovalPermission extends AbstractLabelPermission {
  /**
   * Construct a reference to a label removal permission.
   *
   * @param type type description of the label.
   */
  public LabelRemovalPermission(LabelType type) {
    this(type.getName());
  }

  /**
   * Construct a reference to a label removal permission.
   *
   * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
   */
  public LabelRemovalPermission(String name) {
    super(SELF, name);
  }

  @Override
  public String permissionPrefix() {
    return "removeLabel";
  }

  /** A {@link LabelRemovalPermission} at a specific value. */
  public static class WithValue extends AbstractLabelPermission.WithValue {
    /**
     * Construct a reference to a label removal at a specific value.
     *
     * @param type description of the label.
     * @param value numeric score assigned to the label.
     */
    public WithValue(LabelType type, LabelValue value) {
      this(type.getName(), value.getValue());
    }

    /**
     * Construct a reference to a label removal at a specific value.
     *
     * @param type description of the label.
     * @param value numeric score assigned to the label.
     */
    public WithValue(LabelType type, short value) {
      this(type.getName(), value);
    }

    /**
     * Construct a reference to a label removal at a specific value.
     *
     * @param name name of the label, e.g. {@code "Code-Review"} or {@code "Verified"}.
     * @param value numeric score assigned to the label.
     */
    public WithValue(String name, short value) {
      this(LabelVote.create(name, value));
    }

    /**
     * Construct a reference to a label removal at a specific value.
     *
     * @param label label name and vote.
     */
    public WithValue(LabelVote label) {
      super(SELF, label);
    }

    @Override
    public String permissionName() {
      return "removeLabel";
    }
  }
}

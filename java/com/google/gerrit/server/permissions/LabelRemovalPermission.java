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

import com.google.gerrit.entities.LabelType;

/** Permission representing label removal (on behalf of other users). */
public class LabelRemovalPermission implements ChangePermissionOrLabel {
  private final String name;

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
    this.name = LabelType.checkName(name);
  }

  /** Returns name of the label, e.g. {@code "Code-Review"}. */
  public String label() {
    return name;
  }

  @Override
  public String describeForException() {
    return "remove vote of label " + name;
  }

  @Override
  public int hashCode() {
    return ("remove " + name).hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof LabelRemovalPermission) {
      LabelRemovalPermission b = (LabelRemovalPermission) other;
      return name.equals(b.name);
    }
    return false;
  }

  @Override
  public String toString() {
    return "RemoveLabelAs[" + name + ']';
  }
}

// Copyright (C) 2026 The Android Open Source Project
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
package com.google.gerrit.extensions.api.changes;

import com.google.common.base.MoreObjects;
import java.util.Set;
import java.util.TreeSet;

/** Describes the change-scoped permissions the calling user has on a change. */
public class ChangePermissionsInfo {

  /**
   * Names of the change-scoped permissions the calling user has on this change.
   *
   * <p>Each entry is a permission name from {@link com.google.gerrit.entities.Permission} (e.g.
   * {@code aiReview}). A permission is listed iff it is granted; an absent name means "not
   * granted".
   */
  public Set<String> permissions = new TreeSet<>();

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("permissions", permissions).toString();
  }
}

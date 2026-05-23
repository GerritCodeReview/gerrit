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

/** Describes the change-scoped permissions the calling user has on a change. */
public class ChangePermissionsInfo {

  /**
   * Whether the calling user can delete published comments or change messages on this change.
   *
   * <p>Omitted (i.e. {@code null}) when the user does not have the permission. Callers should treat
   * {@code null} as "not granted".
   */
  public Boolean deleteComment;

  /**
   * Whether the calling user can use AI-assisted review features on this change.
   *
   * <p>Omitted (i.e. {@code null}) when the user does not have the permission. Callers should treat
   * {@code null} as "not granted".
   */
  public Boolean aiReview;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("deleteComment", deleteComment)
        .add("aiReview", aiReview)
        .toString();
  }
}

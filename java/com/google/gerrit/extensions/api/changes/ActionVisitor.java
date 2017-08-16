// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;

/**
 * Extension point called during population of {@link ActionInfo} maps.
 *
 * <p>Each visitor may mutate the input {@link ActionInfo}, or filter it out of the map entirely.
 * When multiple extensions are registered, the order in which they are executed is undefined.
 */
@ExtensionPoint
public interface ActionVisitor {
  /**
   * Visit a change-level action.
   *
   * <p>Callers may mutate the input {@link ActionInfo}, or return false to omit the action from the
   * map entirely. Inputs other than the {@link ActionInfo} should be considered immutable.
   *
   * @param name name of the action, as a key into the {@link ActionInfo} map returned by the REST
   *     API.
   * @param actionInfo action being visited; caller may mutate.
   * @param changeInfo information about the change to which this action belongs; caller should
   *     treat as immutable.
   * @return true if the action should remain in the map, or false to omit it.
   */
  boolean visit(String name, ActionInfo actionInfo, ChangeInfo changeInfo);

  /**
   * Visit a revision-level action.
   *
   * <p>Callers may mutate the input {@link ActionInfo}, or return false to omit the action from the
   * map entirely. Inputs other than the {@link ActionInfo} should be considered immutable.
   *
   * @param name name of the action, as a key into the {@link ActionInfo} map returned by the REST
   *     API.
   * @param actionInfo action being visited; caller may mutate.
   * @param changeInfo information about the change to which this action belongs; caller should
   *     treat as immutable.
   * @param revisionInfo information about the revision to which this action belongs; caller should
   *     treat as immutable.
   * @return true if the action should remain in the map, or false to omit it.
   */
  boolean visit(
      String name, ActionInfo actionInfo, ChangeInfo changeInfo, RevisionInfo revisionInfo);
}

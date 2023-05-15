// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.validators;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

/** Listener to provide validation of custom keyed values changes. */
@ExtensionPoint
public interface CustomKeyedValueValidationListener {
  /**
   * Invoked by Gerrit before custom keyed values are changed.
   *
   * @param change the change on which the custom keyed values are changed
   * @param toAdd the custom keyed values to be added
   * @param toRemove the custom keys to be removed
   * @throws ValidationException if validation fails
   */
  void validateCustomKeyedValues(
      Change change, ImmutableMap<String, String> toAdd, ImmutableSet<String> toRemove)
      throws ValidationException;
}

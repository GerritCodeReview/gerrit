// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.extensions.api.access;

import java.util.Locale;

/** Gerrit permission for hosts, projects, refs, changes, labels and plugins. */
public interface GerritPermission {
  /**
   * A description in the context of an exception message.
   *
   * <p>Should be grammatical when used in the construction "not permitted: [description] on
   * [resource]", although individual {@code PermissionBackend} implementations may vary the
   * wording.
   */
  String describeForException();

  static String describeEnumValue(Enum<?> value) {
    return value.name().toLowerCase(Locale.US).replace('_', ' ');
  }
}

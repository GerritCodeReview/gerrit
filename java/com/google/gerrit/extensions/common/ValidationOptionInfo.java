// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import java.util.Objects;

/** Representation of a validation option that the user can specify upon upload. */
public class ValidationOptionInfo {
  public final String name;
  public final String description;

  public ValidationOptionInfo(String name, String description) {
    this.name = name;
    this.description = description;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ValidationOptionInfo) {
      ValidationOptionInfo validationInfo = (ValidationOptionInfo) o;
      return Objects.equals(name, validationInfo.name)
          && Objects.equals(description, validationInfo.description);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description);
  }
}

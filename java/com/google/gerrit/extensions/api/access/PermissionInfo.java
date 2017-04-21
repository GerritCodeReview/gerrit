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
package com.google.gerrit.extensions.api.access;

import java.util.Map;
import java.util.Objects;

public class PermissionInfo {
  public String label;
  public String role;
  public Boolean exclusive;
  public Map<String, PermissionRuleInfo> rules;

  public PermissionInfo(String label, String role, Boolean exclusive) {
    this.label = label;
    this.role = role;
    this.exclusive = exclusive;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PermissionInfo) {
      PermissionInfo p = (PermissionInfo) obj;
      return Objects.equals(label, p.label)
          && Objects.equals(exclusive, p.exclusive)
          && Objects.equals(rules, p.rules);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, exclusive, rules);
  }
}

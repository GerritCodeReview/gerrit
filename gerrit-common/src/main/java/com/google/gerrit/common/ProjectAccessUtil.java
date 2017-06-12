// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectAccessUtil {
  public static List<AccessSection> mergeSections(List<AccessSection> src) {
    Map<String, AccessSection> map = new LinkedHashMap<>();
    for (final AccessSection section : src) {
      if (section.getPermissions().isEmpty()) {
        continue;
      }

      final AccessSection prior = map.get(section.getName());
      if (prior != null) {
        prior.mergeFrom(section);
      } else {
        map.put(section.getName(), section);
      }
    }
    return new ArrayList<>(map.values());
  }

  public static List<AccessSection> removeEmptyPermissionsAndSections(
      final List<AccessSection> src) {
    final Set<AccessSection> sectionsToRemove = new HashSet<>();
    for (final AccessSection section : src) {
      final Set<Permission> permissionsToRemove = new HashSet<>();
      for (final Permission permission : section.getPermissions()) {
        if (permission.getRules().isEmpty()) {
          permissionsToRemove.add(permission);
        }
      }
      for (final Permission permissionToRemove : permissionsToRemove) {
        section.remove(permissionToRemove);
      }
      if (section.getPermissions().isEmpty()) {
        sectionsToRemove.add(section);
      }
    }
    for (final AccessSection sectionToRemove : sectionsToRemove) {
      src.remove(sectionToRemove);
    }
    return src;
  }
}

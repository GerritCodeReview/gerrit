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

package com.google.gerrit.server;

import com.google.inject.Module;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModuleOverloader {
  public static List<Module> override(List<Module> modules, List<Module> overrideCandidates) {
    if (overrideCandidates == null || overrideCandidates.isEmpty()) {
      return modules;
    }

    // group candidates by annotation existence
    Map<Boolean, List<Module>> grouped =
        overrideCandidates
            .stream()
            .collect(
                Collectors.groupingBy(m -> m.getClass().getAnnotation(ModuleImpl.class) != null));

    // add all non annotated libs to modules list
    List<Module> libs = grouped.get(Boolean.FALSE);
    if (libs != null) {
      modules.addAll(libs);
    }

    List<Module> overrides = grouped.get(Boolean.TRUE);
    if (overrides == null) {
      return modules;
    }

    // swipe cache implementation with alternative provided in lib
    return modules
        .stream()
        .map(
            m -> {
              ModuleImpl a = m.getClass().getAnnotation(ModuleImpl.class);
              if (a == null) {
                return m;
              }
              return overrides
                  .stream()
                  .filter(
                      o ->
                          o.getClass()
                              .getAnnotation(ModuleImpl.class)
                              .name()
                              .equalsIgnoreCase(a.name()))
                  .findFirst()
                  .orElse(m);
            })
        .collect(Collectors.toList());
  }

  private ModuleOverloader() {}
}

// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks;

import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.inject.Singleton;

/** Formats a {@link Checker} as JSON. */
@Singleton
public class CheckerJson {
  public CheckerInfo format(Checker checker) {
    CheckerInfo info = new CheckerInfo();
    info.uuid = checker.getUuid();
    info.name = checker.getName();
    info.description = checker.getDescription().orElse(null);
    info.url = checker.getUrl().orElse(null);
    info.repository = checker.getRepository().get();
    info.status = checker.getStatus();
    info.createdOn = checker.getCreatedOn();
    info.updatedOn = checker.getUpdatedOn();
    return info;
  }
}

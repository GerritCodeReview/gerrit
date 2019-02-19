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

package com.google.gerrit.plugins.checkers;

import com.google.gerrit.plugins.checkers.api.CheckInfo;
import com.google.inject.Singleton;

/** Formats a {@link Check} as JSON. */
@Singleton
public class CheckJson {
  public CheckInfo format(Check check) {
    CheckInfo info = new CheckInfo();
    info.checkerUUID = check.key().checkerUUID();
    info.changeNumber = check.key().patchSet().changeId.id;
    info.project = check.key().project().get();
    info.patchSetId = check.key().patchSet().patchSetId;
    info.state = check.state();

    info.url = check.url().orElse(null);
    info.started = check.started().orElse(null);
    info.finished = check.finished().orElse(null);

    info.created = check.created();
    info.updated = check.updated();
    return info;
  }
}

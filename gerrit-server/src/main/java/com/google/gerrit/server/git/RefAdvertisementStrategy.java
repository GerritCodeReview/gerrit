// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git;

import org.eclipse.jgit.lib.Config;

/** Strategy for advertising refs in upload-pack. */
public enum RefAdvertisementStrategy {
  /** Advertise all refs. */
  ALL(false),

  /** Omit most Gerrit-related refs. */
  OMIT_GERRIT(true);

  public static RefAdvertisementStrategy get(Config cfg) {
    return cfg.getEnum("upload", null, "refAdvertisementStrategy", RefAdvertisementStrategy.ALL);
  }

  private final boolean omitChangeRefs;

  private RefAdvertisementStrategy(boolean omitChangeRefs) {
    this.omitChangeRefs = omitChangeRefs;
  }

  public boolean omitAnyRefs() {
    return omitChangeRefs;
  }

  public boolean omitChangeRefs() {
    return omitChangeRefs;
  }
}

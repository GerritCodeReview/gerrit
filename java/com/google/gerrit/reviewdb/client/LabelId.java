// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class LabelId {
  static final String LEGACY_SUBMIT_NAME = "SUBM";

  public static LabelId create(String n) {
    return new AutoValue_LabelId(n);
  }

  public static LabelId legacySubmit() {
    return create(LEGACY_SUBMIT_NAME);
  }

  abstract String id();

  public String get() {
    return id();
  }
}

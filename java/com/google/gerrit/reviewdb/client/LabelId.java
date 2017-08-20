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

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

public class LabelId extends StringKey<com.google.gwtorm.client.Key<?>> {
  private static final long serialVersionUID = 1L;

  static final String LEGACY_SUBMIT_NAME = "SUBM";

  public static LabelId legacySubmit() {
    return new LabelId(LEGACY_SUBMIT_NAME);
  }

  @Column(id = 1)
  public String id;

  public LabelId() {}

  public LabelId(String n) {
    id = n;
  }

  @Override
  public String get() {
    return id;
  }

  @Override
  protected void set(String newValue) {
    id = newValue;
  }
}

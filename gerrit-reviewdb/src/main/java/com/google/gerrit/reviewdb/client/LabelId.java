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

  public static final LabelId SUBMIT = new LabelId("SUBM");

  @Column(id = 1)
  protected String id;

  protected LabelId() {
  }

  public LabelId(final String n) {
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

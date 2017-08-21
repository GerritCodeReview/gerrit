// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoValue;
import org.junit.Test;

public class AutoValueTest {
  @AutoValue
  abstract static class Auto {
    static Auto create(String val) {
      return new AutoValue_AutoValueTest_Auto(val);
    }

    abstract String val();
  }

  @Test
  public void autoValue() {
    Auto a = Auto.create("foo");
    assertThat(a.val()).isEqualTo("foo");
    assertThat(a.toString()).isEqualTo("Auto{val=foo}");
  }
}

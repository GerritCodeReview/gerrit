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

package com.google.gerrit.server;

import static com.google.common.truth.Truth.assertThat;

import java.util.regex.Pattern;
import org.junit.Test;

public class ChangeUtilTest {
  @Test
  public void changeMessageUuid() throws Exception {
    Pattern pat = Pattern.compile("^[0-9a-f]{8}_[0-9a-f]{8}$");
    assertThat("abcd1234_0987fedc").matches(pat);

    String id1 = ChangeUtil.messageUuid();
    assertThat(id1).matches(pat);

    String id2 = ChangeUtil.messageUuid();
    assertThat(id2).isNotEqualTo(id1);
    assertThat(id2).matches(pat);
  }
}

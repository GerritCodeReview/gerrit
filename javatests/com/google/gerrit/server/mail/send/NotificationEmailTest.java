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

package com.google.gerrit.server.mail.send;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.mail.send.NotificationEmail.getInstanceAndProjectName;
import static com.google.gerrit.server.mail.send.NotificationEmail.getShortProjectName;

import org.junit.Test;

public class NotificationEmailTest {
  @Test
  public void instanceAndProjectName() throws Exception {
    assertThat(getInstanceAndProjectName("test", "/my/api")).isEqualTo("test/api");
    assertThat(getInstanceAndProjectName("test", "/api")).isEqualTo("test/api");
    assertThat(getInstanceAndProjectName("test", "api")).isEqualTo("test/api");
  }

  @Test
  public void instanceAndProjectNameNull() throws Exception {
    assertThat(getInstanceAndProjectName(null, "/my/api")).isEqualTo("...api");
    assertThat(getInstanceAndProjectName(null, "/api")).isEqualTo("api");
    assertThat(getInstanceAndProjectName(null, "api")).isEqualTo("api");
  }

  @Test
  public void shortProjectName() throws Exception {
    assertThat(getShortProjectName("api")).isEqualTo("api");
    assertThat(getShortProjectName("/api")).isEqualTo("api");
    assertThat(getShortProjectName("/my/api")).isEqualTo("...api");
    assertThat(getShortProjectName("/my/sub/project")).isEqualTo("...project");
  }
}

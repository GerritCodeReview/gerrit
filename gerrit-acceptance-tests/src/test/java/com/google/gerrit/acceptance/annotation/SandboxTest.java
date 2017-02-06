// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.annotation;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import org.junit.After;
import org.junit.Test;

@Sandboxed
public class SandboxTest extends AbstractDaemonTest {
  @After
  public void addUser() throws Exception {
    gApi.accounts().create("sandboxuser");
  }

  @Test
  public void userNotPresent1() throws Exception {
    assertThat(gApi.accounts().query("sandboxuser").get()).isEmpty();
  }

  @Test
  public void userNotPresent2() throws Exception {
    assertThat(gApi.accounts().query("sandboxuser").get()).isEmpty();
  }
}

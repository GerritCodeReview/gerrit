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

package com.google.gerrit.pgm;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class LsTest extends AbstractProgramTest {
  @Test
  public void testLsRunsProperly() throws Exception {
    Ls ls = new Ls();
    int exitCode = ls.main(new String[0]);
    assertThat(exitCode).named("Ls exit code").isEqualTo(0);
  }
}

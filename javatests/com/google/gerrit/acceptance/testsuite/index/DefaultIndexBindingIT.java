// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.testing.SystemPropertiesTestRule;
import javax.inject.Inject;
import org.junit.ClassRule;
import org.junit.Test;

/** Test to check that the expected index backend was bound depending on sys/env properties. */
public class DefaultIndexBindingIT extends AbstractDaemonTest {
  @ClassRule
  public static SystemPropertiesTestRule systemProperties =
      new SystemPropertiesTestRule(IndexType.SYS_PROP, "");

  @Inject private ChangeIndexCollection changeIndex;

  @Test
  public void fakeIsBoundByDefault() throws Exception {
    assertThat(System.getProperty(IndexType.SYS_PROP)).isEmpty();
    assertThat(changeIndex.getSearchIndex()).isInstanceOf(AbstractFakeIndex.FakeChangeIndex.class);
  }
}

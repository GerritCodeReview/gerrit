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

package com.google.gerrit.elasticsearch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import org.junit.Test;

public class ElasticVersionTest {
  @Test
  public void supportedVersion() throws Exception {
    assertThat(ElasticVersion.forVersion("7.4.0")).isEqualTo(ElasticVersion.V7_4);
    assertThat(ElasticVersion.forVersion("7.4.1")).isEqualTo(ElasticVersion.V7_4);

    assertThat(ElasticVersion.forVersion("7.5.0")).isEqualTo(ElasticVersion.V7_5);
    assertThat(ElasticVersion.forVersion("7.5.1")).isEqualTo(ElasticVersion.V7_5);

    assertThat(ElasticVersion.forVersion("7.6.0")).isEqualTo(ElasticVersion.V7_6);
    assertThat(ElasticVersion.forVersion("7.6.1")).isEqualTo(ElasticVersion.V7_6);

    assertThat(ElasticVersion.forVersion("7.7.0")).isEqualTo(ElasticVersion.V7_7);
    assertThat(ElasticVersion.forVersion("7.7.1")).isEqualTo(ElasticVersion.V7_7);

    assertThat(ElasticVersion.forVersion("7.8.0")).isEqualTo(ElasticVersion.V7_8);
    assertThat(ElasticVersion.forVersion("7.8.1")).isEqualTo(ElasticVersion.V7_8);
  }

  @Test
  public void unsupportedVersion() throws Exception {
    ElasticVersion.UnsupportedVersion thrown =
        assertThrows(
            ElasticVersion.UnsupportedVersion.class, () -> ElasticVersion.forVersion("4.0.0"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Unsupported version: [4.0.0]. Supported versions: "
                + ElasticVersion.supportedVersions());
  }
}

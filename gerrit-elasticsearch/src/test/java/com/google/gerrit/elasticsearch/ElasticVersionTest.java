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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ElasticVersionTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void supportedVersion() throws Exception {
    assertThat(ElasticVersion.forVersion("2.4.0")).isEqualTo(ElasticVersion.V2_4);
    assertThat(ElasticVersion.forVersion("2.4.6")).isEqualTo(ElasticVersion.V2_4);

    assertThat(ElasticVersion.forVersion("5.6.0")).isEqualTo(ElasticVersion.V5_6);
    assertThat(ElasticVersion.forVersion("5.6.11")).isEqualTo(ElasticVersion.V5_6);

    assertThat(ElasticVersion.forVersion("6.2.0")).isEqualTo(ElasticVersion.V6_2);
    assertThat(ElasticVersion.forVersion("6.2.4")).isEqualTo(ElasticVersion.V6_2);

    assertThat(ElasticVersion.forVersion("6.3.0")).isEqualTo(ElasticVersion.V6_3);
    assertThat(ElasticVersion.forVersion("6.3.2")).isEqualTo(ElasticVersion.V6_3);

    assertThat(ElasticVersion.forVersion("6.4.0")).isEqualTo(ElasticVersion.V6_4);
    assertThat(ElasticVersion.forVersion("6.4.1")).isEqualTo(ElasticVersion.V6_4);
  }

  @Test
  public void unsupportedVersion() throws Exception {
    exception.expect(ElasticVersion.UnsupportedVersion.class);
    exception.expectMessage(
        "Unsupported version: [4.0.0]. Supported versions: " + ElasticVersion.supportedVersions());
    ElasticVersion.forVersion("4.0.0");
  }

  @Test
  public void version6() throws Exception {
    assertThat(ElasticVersion.V6_2.isV6()).isTrue();
    assertThat(ElasticVersion.V6_3.isV6()).isTrue();
    assertThat(ElasticVersion.V6_4.isV6()).isTrue();
    assertThat(ElasticVersion.V5_6.isV6()).isFalse();
  }
}

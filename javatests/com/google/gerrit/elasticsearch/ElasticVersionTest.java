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

import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class ElasticVersionTest extends GerritBaseTests {
  @Test
  public void supportedVersion() throws Exception {
    assertThat(ElasticVersion.forVersion("6.7.0")).isEqualTo(ElasticVersion.V6_7);
    assertThat(ElasticVersion.forVersion("6.7.1")).isEqualTo(ElasticVersion.V6_7);

    assertThat(ElasticVersion.forVersion("6.8.0")).isEqualTo(ElasticVersion.V6_8);
    assertThat(ElasticVersion.forVersion("6.8.1")).isEqualTo(ElasticVersion.V6_8);

    assertThat(ElasticVersion.forVersion("7.0.0")).isEqualTo(ElasticVersion.V7_0);
    assertThat(ElasticVersion.forVersion("7.0.1")).isEqualTo(ElasticVersion.V7_0);

    assertThat(ElasticVersion.forVersion("7.1.0")).isEqualTo(ElasticVersion.V7_1);
    assertThat(ElasticVersion.forVersion("7.1.1")).isEqualTo(ElasticVersion.V7_1);

    assertThat(ElasticVersion.forVersion("7.2.0")).isEqualTo(ElasticVersion.V7_2);
    assertThat(ElasticVersion.forVersion("7.2.1")).isEqualTo(ElasticVersion.V7_2);

    assertThat(ElasticVersion.forVersion("7.3.0")).isEqualTo(ElasticVersion.V7_3);
    assertThat(ElasticVersion.forVersion("7.3.1")).isEqualTo(ElasticVersion.V7_3);

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
    exception.expect(ElasticVersion.UnsupportedVersion.class);
    exception.expectMessage(
        "Unsupported version: [4.0.0]. Supported versions: " + ElasticVersion.supportedVersions());
    ElasticVersion.forVersion("4.0.0");
  }

  @Test
  public void atLeastMinorVersion() throws Exception {
    assertThat(ElasticVersion.V6_7.isAtLeastMinorVersion(ElasticVersion.V6_7)).isTrue();
    assertThat(ElasticVersion.V6_8.isAtLeastMinorVersion(ElasticVersion.V6_8)).isTrue();
    assertThat(ElasticVersion.V7_0.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_1.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_2.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_3.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_4.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_5.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_6.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_7.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
    assertThat(ElasticVersion.V7_8.isAtLeastMinorVersion(ElasticVersion.V6_7)).isFalse();
  }

  @Test
  public void version6OrLater() throws Exception {
    assertThat(ElasticVersion.V6_7.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V6_8.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_0.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_1.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_2.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_3.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_4.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_5.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_6.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_7.isV6OrLater()).isTrue();
    assertThat(ElasticVersion.V7_8.isV6OrLater()).isTrue();
  }

  @Test
  public void version7OrLater() throws Exception {
    assertThat(ElasticVersion.V6_7.isV7OrLater()).isFalse();
    assertThat(ElasticVersion.V6_8.isV7OrLater()).isFalse();
    assertThat(ElasticVersion.V7_0.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_1.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_2.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_3.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_4.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_5.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_6.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_7.isV7OrLater()).isTrue();
    assertThat(ElasticVersion.V7_8.isV7OrLater()).isTrue();
  }
}

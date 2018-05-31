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

import com.google.gerrit.elasticsearch.ElasticRestClientProvider.ElasticsearchVersion;
import com.google.gerrit.elasticsearch.ElasticRestClientProvider.InvalidVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ElasticsearchVersionTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void supportedVersion() throws Exception {
    assertThat(ElasticsearchVersion.forVersion("2.4")).isEqualTo(ElasticsearchVersion.V2_4);
    assertThat(ElasticsearchVersion.forVersion("2.4.6")).isEqualTo(ElasticsearchVersion.V2_4);
    assertThat(ElasticsearchVersion.forVersion("5.6")).isEqualTo(ElasticsearchVersion.V5_6);
    assertThat(ElasticsearchVersion.forVersion("5.6.9")).isEqualTo(ElasticsearchVersion.V5_6);
    assertThat(ElasticsearchVersion.forVersion("6.2")).isEqualTo(ElasticsearchVersion.V6_2);
    assertThat(ElasticsearchVersion.forVersion("6.2.4")).isEqualTo(ElasticsearchVersion.V6_2);
  }

  @Test
  public void unsupportedVersion() throws Exception {
    exception.expect(InvalidVersion.class);
    exception.expectMessage("Invalid version: 4.0");
    ElasticsearchVersion.forVersion("4.0");
  }
}

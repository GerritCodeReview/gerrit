// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class MetadataTest {

  @Test
  public void stringForLoggingOmitsEmptyOptionalValuesAndReformatsOptionalValuesThatArePresent() {
    Metadata metadata = Metadata.builder().accountId(1000001).branchName("refs/heads/foo").build();
    assertThat(metadata.toStringForLoggingLazy().evaluate())
        .isEqualTo("Metadata{accountId=1000001, branchName=refs/heads/foo, pluginMetadata=[]}");
  }

  @Test
  public void
      stringForLoggingOmitsEmptyOptionalValuesAndReformatsOptionalValuesThatArePresentNoFieldsSet() {
    Metadata metadata = Metadata.builder().build();
    assertThat(metadata.toStringForLoggingLazy().evaluate())
        .isEqualTo("Metadata{pluginMetadata=[]}");
  }
}

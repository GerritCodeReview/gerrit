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

/** Unit tests for {@link Metadata}. */
public class MetadataTest {
  private final String OPERATION_NAME = "operation";

  @Test
  public void decorateOperationName() {
    // operation is not decorated if metadata is empty or doesn't contain relevant fields
    assertThat(Metadata.empty().decorateOperation(OPERATION_NAME)).isEqualTo(OPERATION_NAME);
    assertThat(Metadata.builder().projectName("project").build().decorateOperation(OPERATION_NAME))
        .isEqualTo(OPERATION_NAME);

    // plugin name and class are included only if the operation name is "plugin/latency"
    assertThat(Metadata.empty().decorateOperation("plugin/latency")).isEqualTo("plugin/latency");
    assertThat(Metadata.builder().pluginName("plugin").build().decorateOperation("plugin/latency"))
        .isEqualTo("plugin/latency (plugin)");
    assertThat(Metadata.builder().className("class").build().decorateOperation("plugin/latency"))
        .isEqualTo("plugin/latency (class)");
    assertThat(
            Metadata.builder()
                .pluginName("plugin")
                .className("class")
                .build()
                .decorateOperation("plugin/latency"))
        .isEqualTo("plugin/latency (plugin:class)");
    assertThat(Metadata.builder().pluginName("plugin").build().decorateOperation(OPERATION_NAME))
        .isEqualTo(OPERATION_NAME);
    assertThat(Metadata.builder().className("class").build().decorateOperation(OPERATION_NAME))
        .isEqualTo(OPERATION_NAME);
    assertThat(
            Metadata.builder()
                .pluginName("plugin")
                .className("class")
                .build()
                .decorateOperation(OPERATION_NAME))
        .isEqualTo(OPERATION_NAME);

    // thread name is included if available
    assertThat(Metadata.builder().thread("thread").build().decorateOperation(OPERATION_NAME))
        .isEqualTo("[thread] " + OPERATION_NAME);
    assertThat(Metadata.builder().thread("thread").build().decorateOperation("plugin/latency"))
        .isEqualTo("[thread] plugin/latency");
    assertThat(
            Metadata.builder()
                .thread("thread")
                .pluginName("plugin")
                .build()
                .decorateOperation("plugin/latency"))
        .isEqualTo("[thread] plugin/latency (plugin)");
    assertThat(
            Metadata.builder()
                .thread("thread")
                .className("class")
                .build()
                .decorateOperation("plugin/latency"))
        .isEqualTo("[thread] plugin/latency (class)");
    assertThat(
            Metadata.builder()
                .thread("thread")
                .pluginName("plugin")
                .className("class")
                .build()
                .decorateOperation("plugin/latency"))
        .isEqualTo("[thread] plugin/latency (plugin:class)");

    // view name is included if available
    assertThat(Metadata.builder().restViewName("MyView").build().decorateOperation(OPERATION_NAME))
        .isEqualTo(OPERATION_NAME + " (view: MyView)");
    assertThat(
            Metadata.builder().restViewName("MyView").build().decorateOperation("plugin/latency"))
        .isEqualTo("plugin/latency (view: MyView)");
    assertThat(
            Metadata.builder()
                .restViewName("MyView")
                .thread("thread")
                .pluginName("plugin")
                .build()
                .decorateOperation("plugin/latency"))
        .isEqualTo("[thread] plugin/latency (plugin) (view: MyView)");
    assertThat(
            Metadata.builder()
                .restViewName("MyView")
                .thread("thread")
                .className("class")
                .build()
                .decorateOperation("plugin/latency"))
        .isEqualTo("[thread] plugin/latency (class) (view: MyView)");
    assertThat(
            Metadata.builder()
                .restViewName("MyView")
                .thread("thread")
                .pluginName("plugin")
                .className("class")
                .build()
                .decorateOperation("plugin/latency"))
        .isEqualTo("[thread] plugin/latency (plugin:class) (view: MyView)");
  }

  @Test
  public void stringForLoggingOmitsEmptyOptionalValuesAndReformatsOptionalValuesThatArePresent() {
    Metadata metadata = Metadata.builder().accountId(1000001).branchName("refs/heads/foo").build();
    assertThat(metadata.toStringForLogging())
        .isEqualTo("Metadata{accountId=1000001, branchName=refs/heads/foo}");
  }

  @Test
  public void
      stringForLoggingOmitsEmptyOptionalValuesAndReformatsOptionalValuesThatArePresentNoFieldsSet() {
    Metadata metadata = Metadata.builder().build();
    assertThat(metadata.toStringForLogging()).isEqualTo("Metadata{}");
  }
}

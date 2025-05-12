// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.Nullable;
import java.util.Optional;
import org.junit.Test;

/** Unit test for {@link DefaultBooleanProjectConfig}. */
public class DefaultBooleanProjectConfigTest {
  @Test
  public void tryParse() {
    // assert valid values for key with subsection
    assertTryParseOk(
        "section.subsection.name=false",
        "section",
        "subsection",
        "name",
        DefaultBooleanProjectConfig.Value.FALSE);
    assertTryParseOk(
        "section.subsection.name=FALSE",
        "section",
        "subsection",
        "name",
        DefaultBooleanProjectConfig.Value.FALSE);
    assertTryParseOk(
        "section.subsection.name=true",
        "section",
        "subsection",
        "name",
        DefaultBooleanProjectConfig.Value.TRUE);
    assertTryParseOk(
        "section.subsection.name=TRUE",
        "section",
        "subsection",
        "name",
        DefaultBooleanProjectConfig.Value.TRUE);
    assertTryParseOk(
        "section.subsection.name=forced",
        "section",
        "subsection",
        "name",
        DefaultBooleanProjectConfig.Value.FORCED);
    assertTryParseOk(
        "section.subsection.name=FORCED",
        "section",
        "subsection",
        "name",
        DefaultBooleanProjectConfig.Value.FORCED);

    // assert valid values for key without subsection
    assertTryParseOk(
        "section.name=false", "section", null, "name", DefaultBooleanProjectConfig.Value.FALSE);
    assertTryParseOk(
        "section.name=FALSE", "section", null, "name", DefaultBooleanProjectConfig.Value.FALSE);
    assertTryParseOk(
        "section.name=true", "section", null, "name", DefaultBooleanProjectConfig.Value.TRUE);
    assertTryParseOk(
        "section.name=TRUE", "section", null, "name", DefaultBooleanProjectConfig.Value.TRUE);
    assertTryParseOk(
        "section.name=forced", "section", null, "name", DefaultBooleanProjectConfig.Value.FORCED);
    assertTryParseOk(
        "section.name=FORCED", "section", null, "name", DefaultBooleanProjectConfig.Value.FORCED);

    // assert invalid values
    assertTryParseFail(null);

    // key is missing
    assertTryParseFail("true");
    assertTryParseFail("TRUE");
    assertTryParseFail("=true");
    assertTryParseFail("=TRUE");

    // value is missing
    assertTryParseFail("receive.rejectImplicitMerges");
    assertTryParseFail("receive.rejectImplicitMerges=");

    // key and value not separated by '='
    assertTryParseFail("receive.rejectImplicitMerges:true");

    // section or name is missing
    assertTryParseFail("foo=true");

    // key has too many parts
    assertTryParseFail("section.subsection.subsubsection.name=true");

    // invalid value
    assertTryParseFail("section.subsubsection.name=disabled");

    // empty key parts
    assertTryParseFail("section.=true");
    assertTryParseFail(".key=true");
    assertTryParseFail("section..key=true");
    assertTryParseFail(".subsection.key=true");
    assertTryParseFail("section.subsection.=true");
    assertTryParseFail("section..=true");
    assertTryParseFail(".subsection.=true");
    assertTryParseFail("..key=true");
    assertTryParseFail(".=true");
    assertTryParseFail("..=true");
  }

  private void assertTryParseOk(
      String s,
      String expectedSection,
      @Nullable String expectedSubSection,
      String expectedName,
      DefaultBooleanProjectConfig.Value expectedDefaultValue) {
    Optional<DefaultBooleanProjectConfig> defaultBooleanProjectConfig =
        DefaultBooleanProjectConfig.tryParse(s);
    assertThat(defaultBooleanProjectConfig).isPresent();

    assertThat(defaultBooleanProjectConfig.get().section()).isEqualTo(expectedSection);
    assertThat(defaultBooleanProjectConfig.get().subSection())
        .isEqualTo(Optional.ofNullable(expectedSubSection));
    assertThat(defaultBooleanProjectConfig.get().name()).isEqualTo(expectedName);
    assertThat(defaultBooleanProjectConfig.get().defaultValue()).isEqualTo(expectedDefaultValue);
  }

  private void assertTryParseFail(String s) {
    assertThat(DefaultBooleanProjectConfig.tryParse(s)).isEmpty();
  }
}

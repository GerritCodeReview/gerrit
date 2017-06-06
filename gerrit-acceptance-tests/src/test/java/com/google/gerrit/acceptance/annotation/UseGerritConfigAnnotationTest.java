// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.acceptance.GerritConfig;
import org.junit.Test;

public class UseGerritConfigAnnotationTest extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "section.name", value = "value")
  public void testOne() {
    assertThat(cfg.getString("section", null, "name")).isEqualTo("value");
  }

  @Test
  @GerritConfig(name = "section.subsection.name", value = "value")
  public void testOneWithSubsection() {
    assertThat(cfg.getString("section", "subsection", "name")).isEqualTo("value");
  }

  @Test
  @GerritConfig(name = "section.name", value = "value")
  @GerritConfig(name = "section1.name", value = "value1")
  @GerritConfig(name = "section.subsection.name", value = "value")
  @GerritConfig(name = "section.subsection1.name", value = "value1")
  public void testMultiple() {
    assertThat(cfg.getString("section", null, "name")).isEqualTo("value");
    assertThat(cfg.getString("section1", null, "name")).isEqualTo("value1");
    assertThat(cfg.getString("section", "subsection", "name")).isEqualTo("value");
    assertThat(cfg.getString("section", "subsection1", "name")).isEqualTo("value1");
  }

  @Test
  @GerritConfig(
    name = "section.name",
    values = {"value-1", "value-2"}
  )
  public void testList() {
    assertThat(cfg.getStringList("section", null, "name"))
        .asList()
        .containsExactly("value-1", "value-2");
  }

  @Test
  @GerritConfig(
    name = "section.subsection.name",
    values = {"value-1", "value-2"}
  )
  public void testListWithSubsection() {
    assertThat(cfg.getStringList("section", "subsection", "name"))
        .asList()
        .containsExactly("value-1", "value-2");
  }

  @Test
  @GerritConfig(
    name = "section.name",
    value = "value-1",
    values = {"value-2", "value-3"}
  )
  public void valueHasPrecedenceOverValues() {
    assertThat(cfg.getStringList("section", null, "name"))
        .asList()
        .containsExactly("value-1");
  }
}

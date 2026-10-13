// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.Section;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class InitLuceneOptionsTest {
  private Section index;
  private Section container;
  private InitLuceneOptions step;

  @Before
  public void setUp() {
    index = mock(Section.class);
    container = mock(Section.class);
    Section.Factory sections = mock(Section.Factory.class);
    when(sections.get("index", null)).thenReturn(index);
    when(sections.get("container", null)).thenReturn(container);
    when(container.getList("javaOptions")).thenReturn(new String[0]);
    step = new InitLuceneOptions(mock(ConsoleUI.class), sections);
  }

  @Test
  public void enablesVectorApiForDefaultIndexAndPreservesOtherOptions() {
    when(container.getList("javaOptions"))
        .thenReturn(new String[] {"-XX:+UseCompactObjectHeaders", "--add-modules=java.sql"});
    step.run();
    verify(container)
        .setList(
            "javaOptions",
            List.of(
                "-XX:+UseCompactObjectHeaders",
                "--add-modules=java.sql",
                "--add-modules=jdk.incubator.vector"));
  }

  @Test
  public void enablesVectorApiForLuceneIndex() {
    when(index.get("type")).thenReturn("lucene");
    step.run();
    verify(container).setList("javaOptions", List.of("--add-modules=jdk.incubator.vector"));
  }

  @Test
  public void skipsFakeIndex() {
    when(index.get("type")).thenReturn("fake");
    step.run();
    verify(container, never()).setList(eq("javaOptions"), anyList());
  }

  @Test
  public void skipsCustomIndex() {
    when(index.get("type")).thenReturn("custom");
    step.run();
    verify(container, never()).setList(eq("javaOptions"), anyList());
  }

  @Test
  public void preservesExistingModuleOptions() {
    for (String option :
        List.of(
            "--add-modules=jdk.incubator.vector",
            "--add-modules=java.sql,jdk.incubator.vector",
            "-Xmx1g --add-modules=jdk.incubator.vector -Xss1m")) {
      when(container.getList("javaOptions")).thenReturn(new String[] {option});
      step.run();
    }
    verify(container, never()).setList(eq("javaOptions"), anyList());
  }

  @Test
  public void preservesSpaceSeparatedModuleOption() {
    when(container.getList("javaOptions"))
        .thenReturn(new String[] {"--add-modules jdk.incubator.vector"});
    step.run();
    verify(container, never()).setList(eq("javaOptions"), anyList());
  }

  @Test
  public void doesNotMistakeSystemPropertyForModuleOption() {
    when(container.getList("javaOptions"))
        .thenReturn(new String[] {"-Dexample=jdk.incubator.vector"});
    step.run();
    verify(container)
        .setList(
            "javaOptions",
            List.of("-Dexample=jdk.incubator.vector", "--add-modules=jdk.incubator.vector"));
  }
}

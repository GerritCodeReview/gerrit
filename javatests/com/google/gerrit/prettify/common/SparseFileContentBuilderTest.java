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

package com.google.gerrit.prettify.common;

import static com.google.gerrit.prettify.common.testing.SparseFileContentSubject.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import org.junit.Ignore;
import org.junit.Test;

public class SparseFileContentBuilderTest {

  @Test
  public void addLineWithNegativeNumber() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(10);
    assertThrows(IllegalArgumentException.class, () -> builder.addLine(-1, "First line"));

    assertThrows(IllegalArgumentException.class, () -> builder.addLine(-5, "First line"));
  }

  @Test
  @Ignore
  public void addLineNumberZeroFileSize() {
    // Temporary ignore - see comments in SparseFileContentBuilder.build() method
    SparseFileContentBuilder builder = new SparseFileContentBuilder(0);
    assertThrows(IllegalArgumentException.class, () -> builder.addLine(0, "First line"));
  }

  @Test
  @Ignore
  public void addLineNumberNonZeroFileSize() {
    // Temporary ignore - see comments in SparseFileContentBuilder.build() method
    SparseFileContentBuilder builder = new SparseFileContentBuilder(5);
    assertThrows(IllegalArgumentException.class, () -> builder.addLine(5, "First line"));
    assertThrows(IllegalArgumentException.class, () -> builder.addLine(6, "First line"));
    assertThrows(IllegalArgumentException.class, () -> builder.addLine(7, "First line"));
  }

  @Test
  public void addLineIncorrectOrder() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(5);

    builder.addLine(0, "First line");
    builder.addLine(1, "Second line");
    builder.addLine(3, "Third line");
    builder.addLine(4, "Fourth line");
    assertThrows(IllegalArgumentException.class, () -> builder.addLine(4, "Other Line"));

    assertThrows(IllegalArgumentException.class, () -> builder.addLine(2, "Other Line"));
  }

  @Test
  public void emptyContentZeroSize() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(0);

    SparseFileContent content = builder.build();
    assertThat(content).getSize().isEqualTo(0);
    assertThat(content).getRangesCount().isEqualTo(0);
    assertThat(content).lines().isEmpty();
  }

  @Test
  public void emptyContentNonZeroSize() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(4);
    SparseFileContent content = builder.build();
    assertThat(content).getSize().isEqualTo(4);
    assertThat(content).getRangesCount().isEqualTo(0);
    assertThat(content).lines().isEmpty();
  }

  @Test
  public void oneLineContentLineNumberZero() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(1);

    builder.addLine(0, "First line");
    SparseFileContent content = builder.build();
    assertThat(content).getSize().isEqualTo(1);
    assertThat(content).getRangesCount().isEqualTo(1);
    assertThat(content).lines().containsExactlyEntriesIn(ImmutableMap.of(0, "First line"));
  }

  @Test
  public void oneLineContentLineNumberNotZero() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(6);

    builder.addLine(5, "First line");
    SparseFileContent content = builder.build();
    assertThat(content).getSize().isEqualTo(6);
    assertThat(content).getRangesCount().isEqualTo(1);
    assertThat(content).lines().containsExactlyEntriesIn(ImmutableMap.of(5, "First line"));
  }

  @Test
  public void multiLineContinuousContentStartingFromZero() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(5);

    builder.addLine(0, "First line");
    builder.addLine(1, "Second line");
    builder.addLine(2, "Third line");
    SparseFileContent content = builder.build();
    assertThat(content).getSize().isEqualTo(5);
    assertThat(content).getRangesCount().isEqualTo(1);
    assertThat(content)
        .lines()
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                0, "First line",
                1, "Second line",
                2, "Third line"));
  }

  @Test
  public void multiLineContentStartingFromNonZeroLine() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(8);

    builder.addLine(5, "First line");
    builder.addLine(6, "Second line");
    builder.addLine(7, "Third line");
    SparseFileContent content = builder.build();
    assertThat(content).getSize().isEqualTo(8);
    assertThat(content).getRangesCount().isEqualTo(1);
    assertThat(content)
        .lines()
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                5, "First line",
                6, "Second line",
                7, "Third line"));
  }

  @Test
  public void multiLineContentWithGaps() {
    SparseFileContentBuilder builder = new SparseFileContentBuilder(10000);
    builder.addLine(0, "First line");
    builder.addLine(1, "Second line");
    builder.addLine(3, "Third line");
    builder.addLine(4, "Fourth line");
    builder.addLine(5, "Fifth line");
    builder.addLine(6, "Sixth line");
    builder.addLine(10, "Seventh line");
    SparseFileContent content = builder.build();
    assertThat(content).getSize().isEqualTo(10000);
    assertThat(content).getRangesCount().isEqualTo(3);
    assertThat(content)
        .lines()
        .containsExactlyEntriesIn(
            ImmutableMap.builder()
                .put(0, "First line")
                .put(1, "Second line")
                .put(3, "Third line")
                .put(4, "Fourth line")
                .put(5, "Fifth line")
                .put(6, "Sixth line")
                .put(10, "Seventh line")
                .build());
  }
}

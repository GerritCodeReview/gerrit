// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builder for the end position of a range. Used by {@link TestCommentCreation} and {@link
 * TestRobotCommentCreation}.
 */
public class StartAwarePositionBuilder<T> {
  private final TestRange.Builder testRangeBuilder;
  private final Consumer<TestRange> rangeConsumer;
  private final Function<String, T> fileFunction;

  public StartAwarePositionBuilder(
      TestRange.Builder testRangeBuilder,
      Consumer<TestRange> rangeConsumer,
      Function<String, T> fileFunction) {
    this.testRangeBuilder = testRangeBuilder;
    this.rangeConsumer = rangeConsumer;
    this.fileFunction = fileFunction;
  }

  /** Line of the end position of the range. */
  public PositionBuilder<FileBuilder<T>> toLine(int endLine) {
    return new PositionBuilder<>(
        endCharOffset -> {
          TestRange.Position end =
              TestRange.Position.builder().line(endLine).charOffset(endCharOffset).build();
          TestRange range = testRangeBuilder.setEnd(end).build();
          rangeConsumer.accept(range);
          return new FileBuilder<>(fileFunction);
        });
  }
}

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
import java.util.function.IntFunction;

/** Util class used by {@link TestCommentCreation} and {@link TestRobotCommentCreation}. */
public class TestCommentUtil {

  /** Builder for the file specification of line/range comments. */
  public static class FileBuilder<T> {
    private final Function<String, T> nextStepProvider;

    public FileBuilder(Function<String, T> nextStepProvider) {
      this.nextStepProvider = nextStepProvider;
    }

    /** File on which the comment should be added. */
    public T ofFile(String file) {
      return nextStepProvider.apply(file);
    }
  }

  /** Builder to simplify a position specification. */
  public static class PositionBuilder<T> {
    private final IntFunction<T> nextStepProvider;

    public PositionBuilder(IntFunction<T> nextStepProvider) {
      this.nextStepProvider = nextStepProvider;
    }

    /** Character offset within the line. A value of 0 indicates the beginning of the line. */
    public T charOffset(int characterOffset) {
      return nextStepProvider.apply(characterOffset);
    }
  }

  /** Builder for the end position of a range. */
  public static class StartAwarePositionBuilder<T> {
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
            return new FileBuilder<T>(fileFunction);
          });
    }
  }

  enum CommentSide {
    PATCHSET_COMMIT(1),
    AUTO_MERGE_COMMIT(0),
    PARENT_COMMIT(-1),
    SECOND_PARENT_COMMIT(-2);

    private final short numericSide;

    CommentSide(int numericSide) {
      this.numericSide = (short) numericSide;
    }

    public short getNumericSide() {
      return numericSide;
    }
  }
}

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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;

/**
 * Representation of a range used for testing purposes.
 *
 * @param start Start position of the range. (inclusive)
 * @param end End position of the range. (exclusive)
 */
public record TestRange(Position start, Position end) {
  public TestRange {
    requireNonNull(start, "start");
    requireNonNull(end, "end");
  }

  static Builder builder() {
    return new AutoBuilder_TestRange_Builder();
  }

  @AutoBuilder
  abstract static class Builder {

    abstract Builder setStart(Position start);

    abstract Builder setEnd(Position end);

    abstract TestRange build();
  }

  /**
   * Position (start/end) of a range.
   *
   * @param line 1-based line.
   * @param charOffset 0-based character offset within the line.
   */
  public record Position(int line, int charOffset) {

    static Builder builder() {
      return new AutoBuilder_TestRange_Position_Builder();
    }

    @AutoBuilder
    abstract static class Builder {

      abstract Builder line(int line);

      abstract Builder charOffset(int characterOffset);

      abstract Position build();
    }
  }
}

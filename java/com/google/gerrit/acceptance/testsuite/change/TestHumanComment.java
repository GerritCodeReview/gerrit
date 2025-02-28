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
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/**
 * Representation of a human comment used for testing purposes.
 *
 * @param uuid The UUID of the comment. Should be unique.
 * @param parentUuid UUID of another comment to which this comment is a reply.
 * @param tag Tag of a comment.
 * @param unresolved Unresolved state of a comment.
 */
public record TestHumanComment(
    String uuid, Optional<String> parentUuid, Optional<String> tag, boolean unresolved) {
  public TestHumanComment {
    requireNonNull(uuid, "uuid");
    requireNonNull(parentUuid, "parentUuid");
    requireNonNull(tag, "tag");
  }

  static Builder builder() {
    return new AutoBuilder_TestHumanComment_Builder();
  }

  @AutoBuilder
  abstract static class Builder {
    abstract Builder uuid(String uuid);

    abstract Builder parentUuid(@Nullable String parentUuid);

    abstract Builder tag(@Nullable String tag);

    abstract Builder unresolved(boolean unresolved);

    abstract TestHumanComment build();
  }
}

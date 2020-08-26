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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/** Representation of a human comment used for testing purposes. */
@AutoValue
public abstract class TestHumanComment {

  /** The UUID of the comment. Should be unique. */
  public abstract String uuid();

  /** UUID of another comment to which this comment is a reply. */
  public abstract Optional<String> parentUuid();

  static Builder builder() {
    return new AutoValue_TestHumanComment.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder uuid(String uuid);

    abstract Builder parentUuid(@Nullable String parentUuid);

    abstract TestHumanComment build();
  }
}

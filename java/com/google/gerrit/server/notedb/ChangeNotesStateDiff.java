// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;

/**
 * The diff between two {@link ChangeNotesState} instances.
 */
@AutoValue
public abstract class ChangeNotesStateDiff {

  public static Builder builder() {
    return new AutoValue_ChangeNotesStateDiff.Builder();
  }

  @AutoValue
  public static abstract class Vote {

    public static Vote create(String name) {
      return new AutoValue_ChangeNotesStateDiff_Vote(name);
    }

    public abstract String name();
  }

  @AutoValue
  public static abstract class Comment {

    public static Comment create(String text) {
      return new AutoValue_ChangeNotesStateDiff_Comment(text);
    }

    public abstract String text();
  }

  public abstract Optional<String> oldTopic();

  public abstract Optional<String> newTopic();

  public abstract ImmutableList<Vote> addedVotes();

  public abstract ImmutableList<Vote> removedVotes();

  public abstract ImmutableList<Comment> addedComments();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setOldTopic(String oldTopic);

    public abstract Builder setOldTopic(Optional<String> oldTopic);

    public abstract Builder setNewTopic(String newTopic);

    public abstract Builder setNewTopic(Optional<String> newTopic);

    public abstract Builder setAddedVotes(List<Vote> addedVotes);

    public abstract Builder setRemovedVotes(List<Vote> removedVotes);

    public abstract Builder setAddedComments(List<Comment> addedComments);

    public abstract ChangeNotesStateDiff build();
  }
}

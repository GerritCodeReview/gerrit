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

package com.google.gerrit.server.query.change;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.entities.LabelType;
import java.util.Optional;

/** A query on label votes. Supports numeric values, e.g. label=value, and also max, min and any. */
@AutoValue
public abstract class LabelVoteQuery {
  public enum VoteType {
    /** The maximum positive label vote for this label is required. */
    MAX,

    /** The maximum negative label vote for this label is required. */
    MIN,

    /** Any vote for this label is required. */
    ANY,

    /** A specific numeric vote identified by {@link #value()} is required. */
    NUMERIC
  }

  /**
   * Creates a {@link LabelVoteQuery}. Supports the following query string formats:
   *
   * <ul>
   *   <li>label=+value
   *   <li>label=-value
   *   <li>label=MAX
   *   <li>label=MIN
   *   <li>label=ANY
   * </ul>
   */
  public static LabelVoteQuery parseWithEquals(String text) {
    checkArgument(!Strings.isNullOrEmpty(text), "Empty label vote");
    int e = text.lastIndexOf('=');
    checkArgument(e >= 0, "Label vote missing '=': %s", text);
    String label = text.substring(0, e);
    String voteValue = text.substring(e + 1);
    try {
      return create(label, Short.parseShort(voteValue));
    } catch (NumberFormatException exception) {
      VoteType voteType = VoteType.valueOf(voteValue);
      if (voteType == VoteType.NUMERIC) {
        throw new IllegalArgumentException("\"NUMERIC\" is not allowed as vote value.", exception);
      }
      return create(label, /* value= */ (short) 0, voteType);
    }
  }

  static LabelVoteQuery create(String label, short value) {
    return new AutoValue_LabelVoteQuery(
        LabelType.checkNameInternal(label), Optional.of(value), VoteType.NUMERIC);
  }

  static LabelVoteQuery create(String label, short value, VoteType voteType) {
    if (voteType == VoteType.NUMERIC) {
      return new AutoValue_LabelVoteQuery(
          LabelType.checkNameInternal(label), Optional.of(value), voteType);
    }
    return new AutoValue_LabelVoteQuery(
        LabelType.checkNameInternal(label), Optional.empty(), voteType);
  }

  /** Label name. */
  public abstract String label();

  /** Label value. Relevant only if {@link #voteType}. is equal to {@link VoteType#NUMERIC}. */
  public abstract Optional<Short> value();

  /** Type of the vote query (MAX, MIN, ANY, NUMERIC). */
  public abstract VoteType voteType();
}

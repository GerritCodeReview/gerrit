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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import java.time.Instant;

/**
 * A single update to the attention set. To reconstruct the attention set these instances are parsed
 * in reverse chronological order. Since each update contains all required information and
 * invalidates all previous state, only the most recent record is relevant for each user.
 *
 * <p>See {@link AttentionSetInput} for the representation in the API.
 */
@AutoValue
public abstract class AttentionSetUpdate {

  /** Users can be added to or removed from the attention set. */
  public enum Operation {
    ADD,
    REMOVE
  }

  /**
   * The time at which this status was set. This is null for instances to be written because the
   * timestamp in the commit message will be used.
   */
  @Nullable
  public abstract Instant timestamp();

  /** The user included in or excluded from the attention set. */
  public abstract Account.Id account();

  /** Indicates whether the user is added to or removed from the attention set. */
  public abstract Operation operation();

  /** A short human readable reason that explains this status (e.g. "manual"). */
  public abstract String reason();

  /**
   * Create an instance from data read from NoteDB. This includes the timestamp taken from the
   * commit.
   */
  public static AttentionSetUpdate createFromRead(
      Instant timestamp, Account.Id account, Operation operation, String reason) {
    return new AutoValue_AttentionSetUpdate(timestamp, account, operation, reason);
  }

  /**
   * Create an instance to be written to NoteDB. This has no timestamp because the timestamp of the
   * commit will be used.
   */
  public static AttentionSetUpdate createForWrite(
      Account.Id account, Operation operation, String reason) {
    return new AutoValue_AttentionSetUpdate(null, account, operation, reason);
  }
}

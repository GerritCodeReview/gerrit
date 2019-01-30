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

package com.google.gerrit.server.verifier;

import com.google.auto.value.AutoValue;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** Definition of a verifier. */
@AutoValue
public abstract class Verifier {

  /**
   * Returns the UUID of the verifier.
   *
   * <p>The UUID is unique across all verifiers.
   *
   * @return UUID
   */
  public abstract String getUuid();

  /**
   * Returns the display name of the verifier.
   *
   * <p>Verifier names are not unique, verifiers with the same name may exist.
   *
   * @return display name of the verifier
   */
  public abstract String getName();

  /**
   * Returns the description of the verifier.
   *
   * <p>Verifiers may not have a description, in this case {@link Optional#empty()} is returned.
   *
   * @return the description of the verifier
   */
  public abstract Optional<String> getDescription();

  /**
   * Returns the creation timestamp of the verifier.
   *
   * @return the creation timestamp
   */
  public abstract Timestamp getCreatedOn();

  /**
   * Returns the ref state of the verifier.
   *
   * @return the ref state
   */
  public abstract Optional<ObjectId> getRefState();

  public abstract Builder toBuilder();

  public static Builder builder(String uuid) {
    return new AutoValue_Verifier.Builder().setUuid(uuid);
  }

  /** A builder for an {@link Verifier}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** @see #getUuid() */
    public abstract Builder setUuid(String uuid);

    /** @see #getName() */
    public abstract Builder setName(String name);

    /** @see #getDescription() */
    public abstract Builder setDescription(String description);

    /** @see #getCreatedOn() */
    public abstract Builder setCreatedOn(Timestamp createdOn);

    /** @see #getRefState() */
    public abstract Builder setRefState(ObjectId refState);

    public abstract Verifier build();
  }
}

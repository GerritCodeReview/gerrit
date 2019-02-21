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

package com.google.gerrit.plugins.checks;

import com.google.auto.value.AutoValue;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** Definition of a checker. */
@AutoValue
public abstract class Checker {

  /**
   * Returns the UUID of the checker.
   *
   * <p>The UUID is a SHA-1 that is unique across all checkers.
   *
   * @return UUID
   */
  public abstract String getUuid();

  /**
   * Returns the display name of the checker.
   *
   * <p>Checker names are not unique, checkers with the same name may exist.
   *
   * @return display name of the checker
   */
  public abstract String getName();

  /**
   * Returns the description of the checker.
   *
   * <p>Checkers may not have a description, in this case {@link Optional#empty()} is returned.
   *
   * @return the description of the checker
   */
  public abstract Optional<String> getDescription();

  /**
   * Returns the URL of the checker.
   *
   * <p>Checkers may not have a URL, in this case {@link Optional#empty()} is returned.
   *
   * @return the URL of the checker
   */
  public abstract Optional<String> getUrl();

  /**
   * Returns the repository to which the checker applies.
   *
   * <p>The repository is the exact name of a repository (no prefix, no regexp).
   *
   * @return the repository to which the checker applies
   */
  public abstract Project.NameKey getRepository();

  /**
   * Returns the status of the checker.
   *
   * @return the status of the checker.
   */
  public abstract CheckerStatus getStatus();

  /**
   * Returns the creation timestamp of the checker.
   *
   * @return the creation timestamp
   */
  public abstract Timestamp getCreatedOn();

  /**
   * Returns the timestamp of when the checker was last updated.
   *
   * @return the last updated timestamp
   */
  public abstract Timestamp getUpdatedOn();

  /**
   * Returns the ref state of the checker.
   *
   * @return the ref state
   */
  public abstract ObjectId getRefState();

  public abstract Builder toBuilder();

  public static Builder builder(String uuid) {
    return new AutoValue_Checker.Builder().setUuid(uuid);
  }

  /** A builder for an {@link Checker}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setUuid(String uuid);

    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setUrl(String url);

    public abstract Builder setRepository(Project.NameKey repository);

    public abstract Builder setStatus(CheckerStatus status);

    public abstract Builder setCreatedOn(Timestamp createdOn);

    public abstract Builder setUpdatedOn(Timestamp updatedOn);

    public abstract Builder setRefState(ObjectId refState);

    public abstract Checker build();
  }
}

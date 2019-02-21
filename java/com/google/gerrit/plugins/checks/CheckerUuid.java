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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.RefNames;
import java.security.MessageDigest;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class CheckerUuid {
  /**
   * Creates a new UUID for a checker.
   *
   * <p>The creation of the UUID is non-deterministic. This means invoking this method multiple
   * times with the same parameters will result in a different UUID for each call.
   *
   * @param checkerName checker name.
   * @return checker UUID.
   */
  public static String make(String checkerName) {
    MessageDigest md = Constants.newMessageDigest();
    md.update(Constants.encode("checker " + checkerName + "\n"));
    md.update(Constants.encode(String.valueOf(Math.random())));
    return ObjectId.fromRaw(md.digest()).name();
  }

  /**
   * Checks whether the given checker UUID has a valid format.
   *
   * @param checkerUuid the checker UUID to check
   * @return {@code true} if the given checker UUID has a valid format, otherwise {@code false}
   */
  public static boolean isUuid(@Nullable String checkerUuid) {
    return checkerUuid != null && ObjectId.isId(checkerUuid);
  }

  /**
   * Checks whether the given checker UUID has a valid format.
   *
   * @param checkerUuid the checker UUID to check
   * @return the checker UUID
   * @throws IllegalStateException if the given checker UUID has an invalid format
   */
  public static String checkUuid(String checkerUuid) {
    checkState(isUuid(checkerUuid), "invalid checker UUID: %s", checkerUuid);
    return checkerUuid;
  }

  /**
   * Parses a checker UUID from a checker ref.
   *
   * @param ref the ref from which a checker UUID should be parsed
   * @return the checker UUID, {@link Optional#empty()} if the given ref is null or not a valid
   *     checker ref
   */
  public static Optional<String> fromRef(@Nullable Ref ref) {
    return fromRef(ref != null ? ref.getName() : (String) null);
  }

  /**
   * Parses a checker UUID from a checker ref name.
   *
   * @param refName the name of the ref from which a checker UUID should be parsed
   * @return the checker UUID, {@link Optional#empty()} if the given ref name is null or not a valid
   *     checker ref name
   */
  public static Optional<String> fromRef(@Nullable String refName) {
    if (refName == null || !CheckerRef.isRefsCheckers(refName)) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        RefNames.parseShardedUuidFromRefPart(refName.substring(CheckerRef.REFS_CHECKERS.length())));
  }

  private CheckerUuid() {}
}

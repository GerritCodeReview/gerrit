// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server;

import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Extension point that allows to control which identity should be recorded in the reflog for ref
 * updates done by a user or done on behalf of a user.
 */
public interface RefLogIdentityProvider {
  /**
   * Creates a {@link PersonIdent} for the given user that should be used as the user identity in
   * the reflog for ref updates done by this user or done on behalf of this user.
   *
   * <p>The returned {@link PersonIdent} is created with the current timestamp and the system
   * default timezone.
   *
   * @param user the user for which a reflog identity should be created
   */
  default PersonIdent newRefLogIdent(IdentifiedUser user) {
    return newRefLogIdent(user, Instant.now(), ZoneId.systemDefault());
  }

  /**
   * Creates a {@link PersonIdent} for the given user that should be used as the user identity in
   * the reflog for ref updates done by this user or done on behalf of this user.
   *
   * @param user the user for which a reflog identity should be created
   * @param when the timestamp that should be used to create the {@link PersonIdent}
   * @param zoneId the zone ID identifying the timezone that should be used to create the {@link
   *     PersonIdent}
   */
  PersonIdent newRefLogIdent(IdentifiedUser user, Instant when, ZoneId zoneId);
}

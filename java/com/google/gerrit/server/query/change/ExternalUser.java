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

package com.google.gerrit.server.query.change;

import static com.google.common.flogger.LazyArgs.lazy;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;

/**
 * Represents a user that does not have a Gerrit account.
 *
 * <p>This user is limited in what they can do on Gerrit. For now, we only guarantee that permission
 * checking - including ref filtering works.
 *
 * <p>This class is thread-safe.
 */
public class ExternalUser extends CurrentUser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ExternalUser create(Collection<ExternalId.Key> externalIdKeys);
  }

  private final GroupBackend groupBackend;
  private final ImmutableSet<ExternalId.Key> externalIdKeys;

  private GroupMembership effectiveGroups;

  @Inject
  public ExternalUser(
      GroupBackend groupBackend, @Assisted Collection<ExternalId.Key> externalIdKeys) {
    this.groupBackend = groupBackend;
    this.externalIdKeys = ImmutableSet.copyOf(externalIdKeys);
  }

  @Override
  public ImmutableSet<ExternalId.Key> getExternalIdKeys() {
    return externalIdKeys;
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    synchronized (this) {
      if (effectiveGroups == null) {
        effectiveGroups = groupBackend.membershipsOf(this);
        logger.atFinest().log(
            "Known groups of %s: %s", getLoggableName(), lazy(effectiveGroups::getKnownGroups));
      }
    }
    return effectiveGroups;
  }

  @Override
  public Object getCacheKey() {
    return this; // Caching is tied to this exact instance.
  }
}

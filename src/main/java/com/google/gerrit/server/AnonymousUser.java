// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collections;
import java.util.Set;

/** An anonymous user who has not yet authenticated. */
@Singleton
public class AnonymousUser extends CurrentUser {
  private final Set<AccountGroup.Id> effectiveGroups;

  @Inject
  AnonymousUser(final SystemConfig cfg) {
    super(cfg);
    effectiveGroups = Collections.singleton(cfg.anonymousGroupId);
  }

  @Override
  public Set<AccountGroup.Id> getEffectiveGroups() {
    return effectiveGroups;
  }

  @Override
  public Set<Change.Id> getStarredChanges() {
    return Collections.emptySet();
  }

  @Override
  public String toString() {
    return "ANONYMOUS";
  }
}

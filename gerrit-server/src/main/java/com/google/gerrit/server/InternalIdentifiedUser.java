// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupMembership;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User identity for code that needs an IdentifiedUser when none is available.
 * <p>
 * An InternalIdentifiedUser has no real identity, it acts as the server and
 * can access anything it wants, anytime it wants, given the JVM's own direct
 * access to data.
 */
public class InternalIdentifiedUser extends IdentifiedUser {
  public interface Factory {
    InternalIdentifiedUser create(String username);
  }

  private final String userName;

  @AssistedInject
  InternalIdentifiedUser(CapabilityControl.Factory capabilityControlFactory,
      @Assisted String userName) {
    super(capabilityControlFactory);
    this.userName = userName;
  }

  @Override
  public String getUserName() {
    return userName;
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    return GroupMembership.EMPTY;
  }

  @Override
  public Set<Change.Id> getStarredChanges() {
    return Collections.emptySet();
  }

  @Override
  public Collection<AccountProjectWatch> getNotificationFilters() {
    return Collections.emptySet();
  }

  @Override
  public String toString() {
    return "InternalIdentifiedUser";
  }
}

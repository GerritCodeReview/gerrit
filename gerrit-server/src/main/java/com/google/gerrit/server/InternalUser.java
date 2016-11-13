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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupMembership;
import com.google.inject.Inject;

/**
 * User identity for plugin code that needs an identity.
 *
 * <p>An InternalUser has no real identity, it acts as the server and can access anything it wants,
 * anytime it wants, given the JVM's own direct access to data. Plugins may use this when they need
 * to have a CurrentUser with read permission on anything.
 *
 * @see PluginUser
 */
public class InternalUser extends CurrentUser {
  public interface Factory {
    InternalUser create();
  }

  @VisibleForTesting
  @Inject
  public InternalUser(CapabilityControl.Factory capabilityControlFactory) {
    super(capabilityControlFactory);
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    return GroupMembership.EMPTY;
  }

  @Override
  public boolean isInternalUser() {
    return true;
  }

  @Override
  public String toString() {
    return "InternalUser";
  }
}

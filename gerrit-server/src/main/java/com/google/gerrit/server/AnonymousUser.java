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

import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import java.util.Collections;

/** An anonymous user who has not yet authenticated. */
public class AnonymousUser extends CurrentUser {
  @Inject
  AnonymousUser(CapabilityControl.Factory capabilityControlFactory) {
    super(capabilityControlFactory);
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    return new ListGroupMembership(Collections.singleton(SystemGroupBackend.ANONYMOUS_USERS));
  }

  @Override
  public String toString() {
    return "ANONYMOUS";
  }
}

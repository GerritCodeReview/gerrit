// Copyright (C) 2010 The Android Open Source Project
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
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/** Identity of a peer daemon process that isn't this JVM. */
public class PeerDaemonUser extends CurrentUser {
  /** Magic username used by peers when they authenticate. */
  public static final String USER_NAME = "Gerrit Code Review";

  public interface Factory {
    PeerDaemonUser create(@Assisted SocketAddress peer);
  }

  private final SocketAddress peer;

  @Inject
  protected PeerDaemonUser(CapabilityControl.Factory capabilityControlFactory,
      @Assisted SocketAddress peer) {
    super(capabilityControlFactory);
    this.peer = peer;
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

  public SocketAddress getRemoteAddress() {
    return peer;
  }

  @Override
  public String toString() {
    return "PeerDaemon[address " + getRemoteAddress() + "]";
  }
}

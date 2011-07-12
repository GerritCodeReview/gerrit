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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.inject.servlet.RequestScoped;

import java.util.Collection;
import java.util.Set;

/**
 * Information about the currently logged in user.
 * <p>
 * This is a {@link RequestScoped} property managed by Guice.
 *
 * @see AnonymousUser
 * @see IdentifiedUser
 */
public abstract class CurrentUser {
  private final CapabilityControl.Factory capabilityControlFactory;
  private final AccessPath accessPath;

  private CapabilityControl capabilities;

  protected CurrentUser(
      CapabilityControl.Factory capabilityControlFactory,
      AccessPath accessPath) {
    this.capabilityControlFactory = capabilityControlFactory;
    this.accessPath = accessPath;
  }

  /** How this user is accessing the Gerrit Code Review application. */
  public final AccessPath getAccessPath() {
    return accessPath;
  }

  /**
   * Get the set of groups the user is currently a member of.
   * <p>
   * The returned set may be a subset of the user's actual groups; if the user's
   * account is currently deemed to be untrusted then the effective group set is
   * only the anonymous and registered user groups. To enable additional groups
   * (and gain their granted permissions) the user must update their account to
   * use only trusted authentication providers.
   *
   * @return active groups for this user.
   */
  public abstract Set<AccountGroup.UUID> getEffectiveGroups();

  /** Set of changes starred by this user. */
  public abstract Set<Change.Id> getStarredChanges();

  /** Filters selecting changes the user wants to monitor. */
  public abstract Collection<AccountProjectWatch> getNotificationFilters();

  /** Unique name of the user on this server, if one has been assigned. */
  public String getUserName() {
    return null;
  }

  /** Capabilities available to this user account considering "All-Projects". */
  public CapabilityControl getCapabilities() {
    CapabilityControl ctl = capabilities;
    if (ctl == null) {
      ctl = capabilityControlFactory.create(this, null);
      capabilities = ctl;
    }
    return ctl;
  }

  /** Capabilities available to this user account considering other projects. */
  public CapabilityControl getCapabilityByProject(
      final Project.NameKey projectName) {
    return capabilityControlFactory.create(this, projectName);
  }
}

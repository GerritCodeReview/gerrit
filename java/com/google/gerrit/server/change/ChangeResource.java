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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class ChangeResource implements RestResource {
  public static final TypeLiteral<RestView<ChangeResource>> CHANGE_KIND = new TypeLiteral<>() {};

  public interface Factory {
    ChangeResource create(ChangeNotes notes, CurrentUser user);

    ChangeResource create(ChangeData changeData, CurrentUser user);
  }

  private final PermissionBackend permissionBackend;
  private final ChangeData changeData;
  private final CurrentUser user;

  @AssistedInject
  ChangeResource(
      PermissionBackend permissionBackend,
      ChangeData.Factory changeDataFactory,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user) {
    this.permissionBackend = permissionBackend;
    this.changeData = changeDataFactory.create(notes);
    this.user = user;
  }

  @AssistedInject
  ChangeResource(
      PermissionBackend permissionBackend,
      @Assisted ChangeData changeData,
      @Assisted CurrentUser user) {
    this.permissionBackend = permissionBackend;
    this.changeData = changeData;
    this.user = user;
  }

  public PermissionBackend.ForChange permissions() {
    return permissionBackend.user(user).change(getNotes());
  }

  public CurrentUser getUser() {
    return user;
  }

  public Change.Id getId() {
    return changeData.getId();
  }

  /** Returns true if {@link #getUser()} is the change's owner. */
  public boolean isUserOwner() {
    Account.Id owner = getChange().getOwner();
    return user.isIdentifiedUser() && user.asIdentifiedUser().getAccountId().equals(owner);
  }

  public Change getChange() {
    return changeData.change();
  }

  public Project.NameKey getProject() {
    return getChange().getProject();
  }

  public ChangeNotes getNotes() {
    return changeData.notes();
  }

  public ChangeData getChangeData() {
    return changeData;
  }

  public Change.Id getVirtualId() {
    return getChangeData().virtualId();
  }
}

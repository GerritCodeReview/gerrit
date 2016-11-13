// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.TypeLiteral;

/**
 * Represents change edit resource, that is actualy two kinds of resources:
 *
 * <ul>
 *   <li>the change edit itself
 *   <li>a path within the edit
 * </ul>
 *
 * distinguished by whether path is null or not.
 */
public class ChangeEditResource implements RestResource {
  public static final TypeLiteral<RestView<ChangeEditResource>> CHANGE_EDIT_KIND =
      new TypeLiteral<RestView<ChangeEditResource>>() {};

  private final ChangeResource change;
  private final ChangeEdit edit;
  private final String path;

  public ChangeEditResource(ChangeResource change, ChangeEdit edit, String path) {
    this.change = change;
    this.edit = edit;
    this.path = path;
  }

  // TODO(davido): Make this cacheable.
  // Should just depend on the SHA-1 of the edit itself.
  public boolean isCacheable() {
    return false;
  }

  public ChangeResource getChangeResource() {
    return change;
  }

  public ChangeControl getControl() {
    return getChangeResource().getControl();
  }

  public Change getChange() {
    return edit.getChange();
  }

  public ChangeEdit getChangeEdit() {
    return edit;
  }

  public String getPath() {
    return path;
  }

  Account.Id getAccountId() {
    return getUser().getAccountId();
  }

  IdentifiedUser getUser() {
    return edit.getUser();
  }
}

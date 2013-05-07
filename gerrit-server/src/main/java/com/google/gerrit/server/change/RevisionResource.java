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

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.TypeLiteral;

public class RevisionResource implements RestResource {
  public static final TypeLiteral<RestView<RevisionResource>> REVISION_KIND =
      new TypeLiteral<RestView<RevisionResource>>() {};

  public static enum Type {
    PUBLISHED,
    DRAFT;
  }

  private final ChangeResource change;
  private final PatchSet ps;
  private final Type type;

  public RevisionResource(ChangeResource change, PatchSet ps, Type type) {
    this.change = change;
    this.ps = ps;
    this.type = type;
  }

  public ChangeControl getControl() {
    return change.getControl();
  }

  public Change getChange() {
    return getControl().getChange();
  }

  public PatchSet getPatchSet() {
    return ps;
  }

  public Type getType() {
    return type;
  }

  Account.Id getAccountId() {
    return ((IdentifiedUser) getControl().getCurrentUser()).getAccountId();
  }

  void checkPublished() throws ResourceNotFoundException {
    checkType(Type.PUBLISHED);
  }

  private void checkType(Type type) throws ResourceNotFoundException {
    if (this.type != type) {
      throw new ResourceNotFoundException();
    }
  }
}

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
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.inject.TypeLiteral;

public class EditFileResource implements RestResource {
  public static final TypeLiteral<RestView<EditFileResource>> EDIT_FILE_KIND =
      new TypeLiteral<RestView<EditFileResource>>() {};

  private final ChangeEditResource rev;
  private final Patch.Key key;

  EditFileResource(ChangeEditResource rev, String name) {
    this.rev = rev;
    this.key = new Patch.Key(
        new PatchSet.Id(rev.getChange().getId(), 0),
        name);
  }

  public Patch.Key getPatchKey() {
    return key;
  }

  public boolean isCacheable() {
    return rev.isCacheable();
  }

  public String getPath() {
    return getPatchKey().getFileName();
  }

  Account.Id getAccountId() {
    return rev.getAccountId();
  }

  public ChangeEdit getChangeEdit() {
    return rev.getChangeEdit();
  }
}

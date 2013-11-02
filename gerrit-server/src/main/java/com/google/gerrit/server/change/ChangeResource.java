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

import com.google.common.base.Objects;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.ObjectId;

public class ChangeResource implements RestResource, HasETag {
  public static final TypeLiteral<RestView<ChangeResource>> CHANGE_KIND =
      new TypeLiteral<RestView<ChangeResource>>() {};

  private final ChangeControl control;

  public ChangeResource(ChangeControl control) {
    this.control = control;
  }

  protected ChangeResource(ChangeResource copy) {
    this.control = copy.control;
  }

  public ChangeControl getControl() {
    return control;
  }

  public Change getChange() {
    return getControl().getChange();
  }

  @Override
  public String getETag() {
    CurrentUser user = control.getCurrentUser();
    Hasher h = Hashing.md5().newHasher()
      .putLong(getChange().getLastUpdatedOn().getTime())
      .putInt(getChange().getRowVersion())
      .putBoolean(user.getStarredChanges().contains(getChange().getId()))
      .putInt(user.isIdentifiedUser()
          ? ((IdentifiedUser) user).getAccountId().get()
          : 0);

    byte[] buf = new byte[20];
    for (ProjectState p : control.getProjectControl().getProjectState().tree()) {
      ObjectId id = p.getConfig().getRevision();
      Objects.firstNonNull(id, ObjectId.zeroId()).copyRawTo(buf, 0);
      h.putBytes(buf);
    }
    return h.hash().toString();
  }
}

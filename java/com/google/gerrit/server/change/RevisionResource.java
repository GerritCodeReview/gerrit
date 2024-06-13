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

import com.google.common.hash.Hasher;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.TypeLiteral;
import java.util.Optional;

public class RevisionResource implements RestResource {
  public static final TypeLiteral<RestView<RevisionResource>> REVISION_KIND =
      new TypeLiteral<>() {};

  public static RevisionResource createNonCacheable(ChangeResource change, PatchSet ps) {
    return new RevisionResource(change, ps, Optional.empty(), false);
  }

  private final ChangeResource changeResource;
  private final PatchSet ps;
  private final Optional<ChangeEdit> edit;
  private final boolean cacheable;

  public RevisionResource(ChangeResource changeResource, PatchSet ps) {
    this(changeResource, ps, Optional.empty());
  }

  public RevisionResource(ChangeResource changeResource, PatchSet ps, Optional<ChangeEdit> edit) {
    this(changeResource, ps, edit, true);
  }

  private RevisionResource(
      ChangeResource changeResource, PatchSet ps, Optional<ChangeEdit> edit, boolean cacheable) {
    this.changeResource = changeResource;
    this.ps = ps;
    this.edit = edit;
    this.cacheable = cacheable;
  }

  public boolean isCacheable() {
    return cacheable;
  }

  public PermissionBackend.ForChange permissions() {
    return changeResource.permissions();
  }

  public ChangeResource getChangeResource() {
    return changeResource;
  }

  public Change getChange() {
    return changeResource.getChange();
  }

  public Project.NameKey getProject() {
    return getChange().getProject();
  }

  public ChangeNotes getNotes() {
    return changeResource.getNotes();
  }

  public PatchSet getPatchSet() {
    return ps;
  }

  public void prepareETag(Hasher h, CurrentUser user) {
    // Conservative estimate: refresh the revision if its parent change has changed, so we don't
    // have to check whether a given modification affected this revision specifically.
    changeResource.prepareETag(h, user);
  }

  public Account.Id getAccountId() {
    return getUser().getAccountId();
  }

  public CurrentUser getUser() {
    return changeResource.getUser();
  }

  public Optional<ChangeEdit> getEdit() {
    return edit;
  }

  @Override
  public String toString() {
    String s = ps.id().toString();
    if (edit.isPresent()) {
      s = "edit:" + s;
    }
    return s;
  }

  public boolean isCurrent() {
    return ps.id().equals(getChange().currentPatchSetId());
  }
}

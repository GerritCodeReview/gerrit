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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.TypeLiteral;
import java.util.Optional;

public class RevisionResource implements RestResource, HasETag {
  public static final TypeLiteral<RestView<RevisionResource>> REVISION_KIND =
      new TypeLiteral<RestView<RevisionResource>>() {};

  private final ChangeResource change;
  private final PatchSet ps;
  private final Optional<ChangeEdit> edit;
  private boolean cacheable = true;

  public RevisionResource(ChangeResource change, PatchSet ps) {
    this(change, ps, Optional.empty());
  }

  public RevisionResource(ChangeResource change, PatchSet ps, Optional<ChangeEdit> edit) {
    this.change = change;
    this.ps = ps;
    this.edit = edit;
  }

  public boolean isCacheable() {
    return cacheable;
  }

  public ChangeResource getChangeResource() {
    return change;
  }

  public ChangeControl getControl() {
    return getChangeResource().getControl();
  }

  public Change getChange() {
    return getControl().getChange();
  }

  public Project.NameKey getProject() {
    return getChange().getProject();
  }

  public ChangeNotes getNotes() {
    return getChangeResource().getNotes();
  }

  public PatchSet getPatchSet() {
    return ps;
  }

  @Override
  public String getETag() {
    // Conservative estimate: refresh the revision if its parent change has
    // changed, so we don't have to check whether a given modification affected
    // this revision specifically.
    return change.getETag();
  }

  Account.Id getAccountId() {
    return getUser().getAccountId();
  }

  CurrentUser getUser() {
    return getControl().getUser();
  }

  RevisionResource doNotCache() {
    cacheable = false;
    return this;
  }

  public Optional<ChangeEdit> getEdit() {
    return edit;
  }

  @Override
  public String toString() {
    String s = ps.getId().toString();
    if (edit.isPresent()) {
      s = "edit:" + s;
    }
    return s;
  }

  public boolean isCurrent() {
    return ps.getId().equals(getChange().currentPatchSetId());
  }
}

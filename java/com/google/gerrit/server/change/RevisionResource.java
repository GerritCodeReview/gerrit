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
import com.google.common.hash.Hashing;
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
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.TypeLiteral;
import java.util.Optional;

public class RevisionResource implements RestResource, HasETag {
  public static final TypeLiteral<RestView<RevisionResource>> REVISION_KIND =
      new TypeLiteral<RestView<RevisionResource>>() {};

  public static RevisionResource createNonCachable(ChangeResource change, PatchSet ps) {
    return new RevisionResource(change, ps, Optional.empty(), false);
  }

  private final ChangeResource change;
  private final PatchSet ps;
  private final Optional<ChangeEdit> edit;
  private final boolean cacheable;

  public RevisionResource(ChangeResource change, PatchSet ps) {
    this(change, ps, Optional.empty());
  }

  public RevisionResource(ChangeResource change, PatchSet ps, Optional<ChangeEdit> edit) {
    this(change, ps, edit, true);
  }

  private RevisionResource(
      ChangeResource change, PatchSet ps, Optional<ChangeEdit> edit, boolean cachable) {
    this.change = change;
    this.ps = ps;
    this.edit = edit;
    this.cacheable = cachable;
  }

  public boolean isCacheable() {
    return cacheable;
  }

  public PermissionBackend.ForChange permissions() {
    return change.permissions();
  }

  public ChangeResource getChangeResource() {
    return change;
  }

  public Change getChange() {
    return getChangeResource().getChange();
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
    Hasher h = Hashing.murmur3_128().newHasher();
    prepareETag(h, getUser());
    return h.hash().toString();
  }

  public void prepareETag(Hasher h, CurrentUser user) {
    // Conservative estimate: refresh the revision if its parent change has changed, so we don't
    // have to check whether a given modification affected this revision specifically.
    change.prepareETag(h, user);
  }

  public Account.Id getAccountId() {
    return getUser().getAccountId();
  }

  public CurrentUser getUser() {
    return getChangeResource().getUser();
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

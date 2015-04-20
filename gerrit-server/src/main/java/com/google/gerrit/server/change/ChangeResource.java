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

import com.google.common.base.MoreObjects;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.ObjectId;

public class ChangeResource implements RestResource, HasETag {
  public static final TypeLiteral<RestView<ChangeResource>> CHANGE_KIND =
      new TypeLiteral<RestView<ChangeResource>>() {};

  private final ChangeControl control;
  private final RebaseChange rebaseChange;

  public ChangeResource(ChangeControl control, RebaseChange rebaseChange) {
    this.control = control;
    this.rebaseChange = rebaseChange;
  }

  protected ChangeResource(ChangeResource copy) {
    this.control = copy.control;
    this.rebaseChange = copy.rebaseChange;
  }

  public ChangeControl getControl() {
    return control;
  }

  public Change getChange() {
    return getControl().getChange();
  }

  public ChangeNotes getNotes() {
    return getControl().getNotes();
  }


  // This includes all information relevant for ETag computation
  // unrelated to the UI.
  public void prepareETag(Hasher h, CurrentUser user) {
    h.putLong(getChange().getLastUpdatedOn().getTime())
      .putInt(getChange().getRowVersion())
      .putInt(user.isIdentifiedUser()
          ? ((IdentifiedUser) user).getAccountId().get()
          : 0)
      .putBoolean(rebaseChange != null && rebaseChange.canRebase(this));
    byte[] buf = new byte[20];
    ObjectId noteId;
    try {
      noteId = getNotes().loadRevision();
    } catch (OrmException e) {
      noteId = null; // This ETag will be invalidated if it loads next time.
    }
    hashObjectId(h, noteId, buf);
    // TODO(dborowitz): Include more notedb and other related refs, e.g. drafts
    // and edits.

    for (ProjectState p : control.getProjectControl().getProjectState().tree()) {
      hashObjectId(h, p.getConfig().getRevision(), buf);
    }
  }

  @Override
  public String getETag() {
    CurrentUser user = control.getCurrentUser();
    Hasher h = Hashing.md5().newHasher()
        .putBoolean(user.getStarredChanges().contains(getChange().getId()));
    prepareETag(h, user);
    return h.hash().toString();
  }

  private void hashObjectId(Hasher h, ObjectId id, byte[] buf) {
    MoreObjects.firstNonNull(id, ObjectId.zeroId()).copyRawTo(buf, 0);
    h.putBytes(buf);
  }
}

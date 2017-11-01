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

package com.google.gerrit.server.edit;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A single user's edit for a change.
 *
 * <p>There is max. one edit per user per change. Edits are stored on refs:
 * refs/users/UU/UUUU/edit-CCCC/P where UU/UUUU is sharded representation of user account, CCCC is
 * change number and P is the patch set number it is based on.
 */
public class ChangeEdit {
  private final Change change;
  private final String editRefName;
  private final RevCommit editCommit;
  private final PatchSet basePatchSet;

  public ChangeEdit(
      Change change, String editRefName, RevCommit editCommit, PatchSet basePatchSet) {
    this.change = checkNotNull(change);
    this.editRefName = checkNotNull(editRefName);
    this.editCommit = checkNotNull(editCommit);
    this.basePatchSet = checkNotNull(basePatchSet);
  }

  public Change getChange() {
    return change;
  }

  public String getRefName() {
    return editRefName;
  }

  public RevCommit getEditCommit() {
    return editCommit;
  }

  public PatchSet getBasePatchSet() {
    return basePatchSet;
  }

  // This includes all information relevant for ETag computation
  // unrelated to the UI.
  public void prepareETag(Hasher h, CurrentUser user) {
    h.putInt(JSON_FORMAT_VERSION)
        .putLong(getChange().getLastUpdatedOn().getTime())
        .putInt(getChange().getRowVersion())
        .putInt(user.isIdentifiedUser() ? user.getAccountId().get() : 0);

    if (user.isIdentifiedUser()) {
      for (AccountGroup.UUID uuid : user.getEffectiveGroups().getKnownGroups()) {
        h.putBytes(uuid.get().getBytes(UTF_8));
      }
    }

    byte[] buf = new byte[20];
    Set<Account.Id> accounts = new HashSet<>();
    accounts.add(getChange().getOwner());
    if (getChange().getAssignee() != null) {
      accounts.add(getChange().getAssignee());
    }
    try {
      patchSetUtil
          .byChange(db.get(), notes)
          .stream()
          .map(ps -> ps.getUploader())
          .forEach(accounts::add);

      // It's intentional to include the states for *all* reviewers into the ETag computation.
      // We need the states of all current reviewers and CCs because they are part of ChangeInfo.
      // Including removed reviewers is a cheap way of making sure that the states of accounts that
      // posted a message on the change are included. Loading all change messages to find the exact
      // set of accounts that posted a message is too expensive. However everyone who posts a
      // message is automatically added as reviewer. Hence if we include removed reviewers we can
      // be sure that we have all accounts that posted messages on the change.
      accounts.addAll(approvalUtil.getReviewers(db.get(), notes).all());
    } catch (OrmException e) {
      // This ETag will be invalidated if it loads next time.
    }
    accounts.stream().forEach(a -> hashAccount(h, accountCache.get(a), buf));

    ObjectId noteId;
    try {
      noteId = notes.loadRevision();
    } catch (OrmException e) {
      noteId = null; // This ETag will be invalidated if it loads next time.
    }
    hashObjectId(h, noteId, buf);
    // TODO(dborowitz): Include more NoteDb and other related refs, e.g. drafts
    // and edits.

    Iterable<ProjectState> projectStateTree;
    try {
      projectStateTree = projectCache.checkedGet(getProject()).tree();
    } catch (IOException e) {
      log.error(String.format("could not load project %s while computing etag", getProject()));
      projectStateTree = ImmutableList.of();
    }

    for (ProjectState p : projectStateTree) {
      hashObjectId(h, p.getConfig().getRevision(), buf);
    }
  }

  @Override
  public String getETag() {
    Hasher h = Hashing.murmur3_128().newHasher();
    if (user.isIdentifiedUser()) {
      h.putString(starredChangesUtil.getObjectId(user.getAccountId(), getId()).name(), UTF_8);
    }
    prepareETag(h, user);
    return h.hash().toString();
  }
}

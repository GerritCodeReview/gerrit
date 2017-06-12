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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Date;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** A single delta related to a specific patch-set of a change. */
public abstract class AbstractChangeUpdate {
  protected final NotesMigration migration;
  protected final ChangeNoteUtil noteUtil;
  protected final String anonymousCowardName;
  protected final Account.Id accountId;
  protected final Account.Id realAccountId;
  protected final PersonIdent authorIdent;
  protected final Date when;

  @Nullable private final ChangeNotes notes;
  private final Change change;
  private final PersonIdent serverIdent;

  protected PatchSet.Id psId;
  private ObjectId result;

  protected AbstractChangeUpdate(
      NotesMigration migration,
      ChangeControl ctl,
      PersonIdent serverIdent,
      String anonymousCowardName,
      ChangeNoteUtil noteUtil,
      Date when) {
    this.migration = migration;
    this.noteUtil = noteUtil;
    this.serverIdent = new PersonIdent(serverIdent, when);
    this.anonymousCowardName = anonymousCowardName;
    this.notes = ctl.getNotes();
    this.change = notes.getChange();
    this.accountId = accountId(ctl.getUser());
    Account.Id realAccountId = accountId(ctl.getUser().getRealUser());
    this.realAccountId = realAccountId != null ? realAccountId : accountId;
    this.authorIdent = ident(noteUtil, serverIdent, anonymousCowardName, ctl.getUser(), when);
    this.when = when;
  }

  protected AbstractChangeUpdate(
      NotesMigration migration,
      ChangeNoteUtil noteUtil,
      PersonIdent serverIdent,
      String anonymousCowardName,
      @Nullable ChangeNotes notes,
      @Nullable Change change,
      Account.Id accountId,
      Account.Id realAccountId,
      PersonIdent authorIdent,
      Date when) {
    checkArgument(
        (notes != null && change == null) || (notes == null && change != null),
        "exactly one of notes or change required");
    this.migration = migration;
    this.noteUtil = noteUtil;
    this.serverIdent = new PersonIdent(serverIdent, when);
    this.anonymousCowardName = anonymousCowardName;
    this.notes = notes;
    this.change = change != null ? change : notes.getChange();
    this.accountId = accountId;
    this.realAccountId = realAccountId;
    this.authorIdent = authorIdent;
    this.when = when;
  }

  private static void checkUserType(CurrentUser user) {
    checkArgument(
        (user instanceof IdentifiedUser) || (user instanceof InternalUser),
        "user must be IdentifiedUser or InternalUser: %s",
        user);
  }

  private static Account.Id accountId(CurrentUser u) {
    checkUserType(u);
    return (u instanceof IdentifiedUser) ? u.getAccountId() : null;
  }

  private static PersonIdent ident(
      ChangeNoteUtil noteUtil,
      PersonIdent serverIdent,
      String anonymousCowardName,
      CurrentUser u,
      Date when) {
    checkUserType(u);
    if (u instanceof IdentifiedUser) {
      return noteUtil.newIdent(
          u.asIdentifiedUser().getAccount(), when, serverIdent, anonymousCowardName);
    } else if (u instanceof InternalUser) {
      return serverIdent;
    }
    throw new IllegalStateException();
  }

  public Change.Id getId() {
    return change.getId();
  }

  @Nullable
  public ChangeNotes getNotes() {
    return notes;
  }

  public Change getChange() {
    return change;
  }

  public Date getWhen() {
    return when;
  }

  public PatchSet.Id getPatchSetId() {
    return psId;
  }

  public void setPatchSetId(PatchSet.Id psId) {
    checkArgument(psId == null || psId.getParentKey().equals(getId()));
    this.psId = psId;
  }

  public Account.Id getAccountId() {
    checkState(
        accountId != null,
        "author identity for %s is not from an IdentifiedUser: %s",
        getClass().getSimpleName(),
        authorIdent.toExternalString());
    return accountId;
  }

  public Account.Id getNullableAccountId() {
    return accountId;
  }

  protected PersonIdent newIdent(Account author, Date when) {
    return noteUtil.newIdent(author, when, serverIdent, anonymousCowardName);
  }

  /** Whether no updates have been done. */
  public abstract boolean isEmpty();

  /**
   * @return the NameKey for the project where the update will be stored, which is not necessarily
   *     the same as the change's project.
   */
  protected abstract Project.NameKey getProjectName();

  protected abstract String getRefName();

  /**
   * Apply this update to the given inserter.
   *
   * @param rw walk for reading back any objects needed for the update.
   * @param ins inserter to write to; callers should not flush.
   * @param curr the current tip of the branch prior to this update.
   * @return commit ID produced by inserting this update's commit, or null if this update is a no-op
   *     and should be skipped. The zero ID is a valid return value, and indicates the ref should be
   *     deleted.
   * @throws OrmException if a Gerrit-level error occurred.
   * @throws IOException if a lower-level error occurred.
   */
  final ObjectId apply(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws OrmException, IOException {
    if (isEmpty()) {
      return null;
    }

    // Allow this method to proceed even if migration.failChangeWrites() = true.
    // This may be used by an auto-rebuilding step that the caller does not plan
    // to actually store.

    checkArgument(rw.getObjectReader().getCreatedFromInserter() == ins);
    ObjectId z = ObjectId.zeroId();
    CommitBuilder cb = applyImpl(rw, ins, curr);
    if (cb == null) {
      result = z;
      return z; // Impl intends to delete the ref.
    } else if (cb == NO_OP_UPDATE) {
      return null; // Impl is a no-op.
    }
    cb.setAuthor(authorIdent);
    cb.setCommitter(new PersonIdent(serverIdent, when));
    if (!curr.equals(z)) {
      cb.setParentId(curr);
    } else {
      cb.setParentIds(); // Ref is currently nonexistent, commit has no parents.
    }
    if (cb.getTreeId() == null) {
      if (curr.equals(z)) {
        cb.setTreeId(emptyTree(ins)); // No parent, assume empty tree.
      } else {
        RevCommit p = rw.parseCommit(curr);
        cb.setTreeId(p.getTree()); // Copy tree from parent.
      }
    }
    result = ins.insert(cb);
    return result;
  }

  /**
   * Create a commit containing the contents of this update.
   *
   * @param ins inserter to write to; callers should not flush.
   * @return a new commit builder representing this commit, or null to indicate the meta ref should
   *     be deleted as a result of this update. The parent, author, and committer fields in the
   *     return value are always overwritten. The tree ID may be unset by this method, which
   *     indicates to the caller that it should be copied from the parent commit. To indicate that
   *     this update is a no-op (but this could not be determined by {@link #isEmpty()}), return the
   *     sentinel {@link #NO_OP_UPDATE}.
   * @throws OrmException if a Gerrit-level error occurred.
   * @throws IOException if a lower-level error occurred.
   */
  protected abstract CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws OrmException, IOException;

  protected static final CommitBuilder NO_OP_UPDATE = new CommitBuilder();

  ObjectId getResult() {
    return result;
  }

  public boolean allowWriteToNewRef() {
    return true;
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    return ins.insert(Constants.OBJ_TREE, new byte[] {});
  }

  protected void verifyComment(Comment c) {
    checkArgument(c.revId != null, "RevId required for comment: %s", c);
    checkArgument(
        c.author.getId().equals(getAccountId()),
        "The author for the following comment does not match the author of" + " this %s (%s): %s",
        getClass().getSimpleName(),
        getAccountId(),
        c);
    checkArgument(
        c.getRealAuthor().getId().equals(realAccountId),
        "The real author for the following comment does not match the real"
            + " author of this %s (%s): %s",
        getClass().getSimpleName(),
        realAccountId,
        c);
  }
}

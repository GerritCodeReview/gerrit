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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Date;

/** A single delta related to a specific patch-set of a change. */
public abstract class AbstractChangeUpdate {
  protected final NotesMigration migration;
  protected final GitRepositoryManager repoManager;
  protected final ChangeControl ctl;
  protected final String anonymousCowardName;
  protected final PersonIdent serverIdent;
  protected final Date when;

  protected PatchSet.Id psId;
  private ObjectId result;

  protected AbstractChangeUpdate(NotesMigration migration,
      GitRepositoryManager repoManager,
      ChangeControl ctl,
      PersonIdent serverIdent,
      String anonymousCowardName,
      Date when) {
    this.migration = migration;
    this.repoManager = repoManager;
    this.ctl = ctl;
    this.serverIdent = serverIdent;
    this.anonymousCowardName = anonymousCowardName;
    this.when = when;
  }

  public ChangeNotes getChangeNotes() {
    return ctl.getNotes();
  }

  public Change getChange() {
    return ctl.getChange();
  }

  public Date getWhen() {
    return when;
  }

  public IdentifiedUser getUser() {
    return ctl.getUser().asIdentifiedUser();
  }

  public PatchSet.Id getPatchSetId() {
    return psId;
  }

  public void setPatchSetId(PatchSet.Id psId) {
    checkArgument(psId == null || psId.getParentKey().equals(ctl.getId()));
    this.psId = psId;
  }

  protected PersonIdent newIdent(Account author, Date when) {
    return ChangeNoteUtil.newIdent(author, when, serverIdent,
        anonymousCowardName);
  }

  /** Whether no updates have been done. */
  public abstract boolean isEmpty();

  /**
   * @return the NameKey for the project where the update will be stored,
   *    which is not necessarily the same as the change's project.
   */
  protected abstract Project.NameKey getProjectName();

  protected abstract String getRefName();

  /**
   * Apply this update to the given inserter.
   *
   * @param rw walk for reading back any objects needed for the update.
   * @param ins inserter to write to; callers should not flush.
   * @param curr the current tip of the branch prior to this update.
   * @return commit ID produced by inserting this update's commit, or null if
   *     this is update is a no-op and should be skipped. The zero ID is a valid
   *     return value, and indicates the ref should be deleted.
   * @throws OrmException if a Gerrit-level error occurred.
   * @throws IOException if a lower-level error occurred.
   */
  final ObjectId apply(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws OrmException, IOException {
    if (isEmpty()) {
      return null;
    }
    ObjectId z = ObjectId.zeroId();
    CommitBuilder cb = applyImpl(ins);
    if (cb == null) {
      result = z;
      return z; // Impl intends to delete the ref.
    }
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
   * @return a new commit builder representing this commit, or null to indicate
   *     the meta ref should be deleted as a result of this update. The parent
   *     field in the return value is always overwritten. The tree ID may be
   *     unset by this method, which indicates to the caller that it should be
   *     copied from the parent commit.
   * @throws OrmException if a Gerrit-level error occurred.
   * @throws IOException if a lower-level error occurred.
   */
  // TODO(dborowitz): ChangeUpdate needs to be able to reread its ChangeNotes at
  // the old SHA-1, which would imply passing curr here.
  protected abstract CommitBuilder applyImpl(ObjectInserter ins)
      throws OrmException, IOException;

  ObjectId getResult() {
    return result;
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    return ins.insert(Constants.OBJ_TREE, new byte[] {});
  }
}

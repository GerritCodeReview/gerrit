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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;

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

  public enum Status {
    OK, EMPTY, DELETE_REF;
  }

  public final Status apply(CommitBuilder cb, ObjectInserter ins)
      throws OrmException, IOException {
    if (isEmpty()) {
      return Status.EMPTY;
    }
    return applyImpl(cb, ins);
  }

  protected abstract Status applyImpl(CommitBuilder cb, ObjectInserter ins)
      throws OrmException, IOException;

  void setResult(ObjectId result) {
    this.result = result.copy();
  }

  ObjectId getResult() {
    return result;
  }
}

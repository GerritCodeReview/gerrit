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
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.Date;

/** A single delta related to a specific patch-set of a change. */
public abstract class AbstractChangeUpdate extends VersionedMetaData {
  protected final NotesMigration migration;
  protected final GitRepositoryManager repoManager;
  protected final MetaDataUpdate.User updateFactory;
  protected final ChangeControl ctl;
  protected final String anonymousCowardName;
  protected final PersonIdent serverIdent;
  protected final Date when;
  protected PatchSet.Id psId;

  AbstractChangeUpdate(NotesMigration migration,
      GitRepositoryManager repoManager,
      MetaDataUpdate.User updateFactory, ChangeControl ctl,
      PersonIdent serverIdent,
      String anonymousCowardName,
      Date when) {
    this.migration = migration;
    this.repoManager = repoManager;
    this.updateFactory = updateFactory;
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

  private void load() throws IOException {
    if (migration.writeChanges() && getRevision() == null) {
      try (Repository repo = repoManager.openMetadataRepository(getProjectName())) {
        load(repo);
      } catch (ConfigInvalidException e) {
        throw new IOException(e);
      }
    }
  }

  public void setInserter(ObjectInserter inserter) {
    this.inserter = inserter;
  }

  @Override
  public BatchMetaDataUpdate openUpdate(MetaDataUpdate update) throws IOException {
    throw new UnsupportedOperationException("use openUpdate()");
  }

  public BatchMetaDataUpdate openUpdate() throws IOException {
    return openUpdateInBatch(null);
  }

  public BatchMetaDataUpdate openUpdateInBatch(BatchRefUpdate bru)
      throws IOException {
    if (migration.writeChanges()) {
      load();
      MetaDataUpdate md =
          updateFactory.create(getProjectName(),
              repoManager.openMetadataRepository(getProjectName()), getUser(),
              bru);
      md.setAllowEmpty(true);
      return super.openUpdate(md);
    }
    return new BatchMetaDataUpdate() {
      @Override
      public void write(CommitBuilder commit) {
        // Do nothing.
      }

      @Override
      public void write(VersionedMetaData config, CommitBuilder commit) {
        // Do nothing.
      }

      @Override
      public RevCommit createRef(String refName) {
        return null;
      }

      @Override
      public void removeRef(String refName) {
        // Do nothing.
      }

      @Override
      public RevCommit commit() {
        return null;
      }

      @Override
      public RevCommit commitAt(ObjectId revision) {
        return null;
      }

      @Override
      public void close() {
        // Do nothing.
      }
    };
  }

  @Override
  public RevCommit commit(MetaDataUpdate md) throws IOException {
    throw new UnsupportedOperationException("use commit()");
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    //Do nothing; just reads the current revision.
  }

  protected PersonIdent newIdent(Account author, Date when) {
    return ChangeNoteUtil.newIdent(author, when, serverIdent,
        anonymousCowardName);
  }

  /** Writes commit to a BatchMetaDataUpdate without committing the batch. */
  public abstract void writeCommit(BatchMetaDataUpdate batch)
      throws OrmException, IOException;

  /**
   * @return the NameKey for the project where the update will be stored,
   *    which is not necessarily the same as the change's project.
   */
  protected abstract Project.NameKey getProjectName();
}

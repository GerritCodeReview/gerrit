// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checkers.db;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.checkers.CheckerRef;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.notedb.AbstractChangeNotes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;

public class CheckNotes extends AbstractChangeNotes<CheckRevisionNote> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    CheckNotes create(Change change);
  }

  private final Change change;

  private ImmutableListMultimap<RevId, NoteDbCheck> entities;
  private CheckRevisionNoteMap<CheckRevisionNote> revisionNoteMap;
  private ObjectId metaId;

  @Inject
  CheckNotes(Args args, @Assisted Change change) {
    super(args, change.getId());
    this.change = change;
  }

  public ImmutableListMultimap<RevId, NoteDbCheck> getChecks() {
    return entities;
  }

  @Override
  public String getRefName() {
    return CheckerRef.checksRef(getChangeId());
  }

  @Nullable
  public ObjectId getMetaId() {
    return metaId;
  }

  @Override
  protected void onLoad(LoadHandle handle) throws IOException, ConfigInvalidException {
    metaId = handle.id();
    if (metaId == null) {
      loadDefaults();
      return;
    }
    metaId = metaId.copy();

    logger.atFine().log(
        "Load check notes for change %s of project %s", getChangeId(), getProjectName());
    RevCommit tipCommit = handle.walk().parseCommit(metaId);
    ObjectReader reader = handle.walk().getObjectReader();
    revisionNoteMap =
        CheckRevisionNoteMap.parseChecks(
            args.changeNoteJson, reader, NoteMap.read(reader, tipCommit));
    ListMultimap<RevId, NoteDbCheck> cs = MultimapBuilder.hashKeys().arrayListValues().build();
    for (Map.Entry<RevId, CheckRevisionNote> rn : revisionNoteMap.revisionNotes.entrySet()) {
      for (NoteDbCheck c : rn.getValue().getComments()) {
        cs.put(rn.getKey(), c);
      }
    }
    entities = ImmutableListMultimap.copyOf(cs);
  }

  @Override
  protected void loadDefaults() {
    entities = ImmutableListMultimap.of();
  }

  @Override
  public Project.NameKey getProjectName() {
    return change.getProject();
  }
}

// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment.Status;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.AbstractChangeNotes.Args;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

public class ChangeRevisionNotesMigrator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FunctionalInterface
  public interface Action {
    Map<ObjectId, ChangeRevisionNote> migrate(Map<ObjectId, ChangeRevisionNote> notes);
  }

  @FunctionalInterface
  public interface CommitMessageBuilder {
    String build();
  }

  private static class ProcessingStage<T> {
    final Ref ref;
    final T data;

    ProcessingStage(Ref ref, T data) {
      this.ref = ref;
      this.data = data;
    }
  }

  private final Args args;
  private final ChangeNoteUtil changeNoteUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final GitRepositoryManager gitRepositoryManager;
  private final NoteDbUpdateExecutor noteDbUpdateExecutor;
  private final PersonIdent serverIdent;
  private final CurrentUser currentUser;
  private final NameKey project;

  public interface Factory {
    ChangeRevisionNotesMigrator create(CurrentUser user, Project.NameKey project);
  }

  @Inject
  ChangeRevisionNotesMigrator(
      Args args,
      ChangeNoteUtil changeNoteUtil,
      ChangeNotes.Factory changeNotesFactory,
      GitRepositoryManager gitRepositoryManager,
      NoteDbUpdateExecutor noteDbUpdateExecutor,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted CurrentUser currentUser,
      @Assisted Project.NameKey project) {
    this.args = args;
    this.changeNoteUtil = changeNoteUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.gitRepositoryManager = gitRepositoryManager;
    this.noteDbUpdateExecutor = noteDbUpdateExecutor;
    this.serverIdent = serverIdent;
    this.currentUser = currentUser;
    this.project = project;
  }

  public void migrate(Action action, CommitMessageBuilder builder) throws IOException {
    try (Repository repo = args.repoManager.openRepository(project);
        ChangeNotesRevWalk walk = ChangeNotesCommit.newRevWalk(repo)) {
      repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES).stream()
          .map(ref -> parseNotes(ref, walk))
          .flatMap(Optional::stream)
          .filter(this::hasCommentFromForeignServer)
          .map(stage -> doMigrate(stage, action))
          .forEach(stage -> commitChanges(stage, builder));
    }
  }

  private Optional<ProcessingStage<RevisionNoteMap<ChangeRevisionNote>>> parseNotes(
      Ref changeRef, ChangeNotesRevWalk walk) {
    try (ObjectReader reader = walk.getObjectReader()) {
      ChangeNotesCommit tipCommit = walk.parseCommit(changeRef.getObjectId());

      RevisionNoteMap<ChangeRevisionNote> revisionNoteMap =
          RevisionNoteMap.parse(
              args.changeNoteJson, reader, NoteMap.read(reader, tipCommit), Status.PUBLISHED);

      return Optional.of(new ProcessingStage<>(changeRef, revisionNoteMap));
    } catch (IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log(
          "cannot parse ref %s for migration in %s", changeRef.getName(), project.get());
      return Optional.empty();
    }
  }

  private boolean hasCommentFromForeignServer(
      ProcessingStage<RevisionNoteMap<ChangeRevisionNote>> changeNotes) {
    return changeNotes.data.revisionNotes.values().stream()
        .map(ChangeRevisionNote::getEntities)
        .flatMap(List::stream)
        .anyMatch(comment -> !comment.serverId.equals(args.serverId));
  }

  private ProcessingStage<RevisionNoteMap<ChangeRevisionNote>> doMigrate(
      ProcessingStage<RevisionNoteMap<ChangeRevisionNote>> stage, Action action) {
    var unused = action.migrate(stage.data.revisionNotes);
    return new ProcessingStage<>(stage.ref, stage.data);
  }

  private void commitChanges(
      ProcessingStage<RevisionNoteMap<ChangeRevisionNote>> stage, CommitMessageBuilder builder) {

    System.out.println(builder.build());
    try (OpenRepo openRepo = OpenRepo.open(gitRepositoryManager, project)) {

      ChangeNotes changeNotes =
          changeNotesFactory.create(project, Change.Id.fromRef(stage.ref.getName()));

      ListMultimap<String, MigrateServerIdsChangeUpdate> updates =
          MultimapBuilder.hashKeys().arrayListValues().build();
      updates.put(
          stage.ref.getName(),
          new MigrateServerIdsChangeUpdate(
              project, stage, changeNotes, currentUser, serverIdent, changeNoteUtil));
      openRepo.addUpdatesNoLimits(updates);

      try (var unused = RefUpdateContext.open(RefUpdateType.CHANGE_MODIFICATION)) {
        Optional<BatchRefUpdate> execute =
            noteDbUpdateExecutor.execute(
                openRepo,
                false,
                false,
                ImmutableList.of(),
                null,
                serverIdent,
                "migrate serverId in comments");
        // TODO validate execution results
        System.out.println(execute);
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("serverId migration failed for %s", stage.ref.getName());
    }
  }

  private static class MigrateServerIdsChangeUpdate extends AbstractChangeUpdate {

    private final Project.NameKey project;
    private final ProcessingStage<RevisionNoteMap<ChangeRevisionNote>> stage;

    protected MigrateServerIdsChangeUpdate(
        Project.NameKey project,
        ProcessingStage<RevisionNoteMap<ChangeRevisionNote>> stage,
        ChangeNotes change,
        CurrentUser user,
        PersonIdent serverIdent,
        ChangeNoteUtil noteUtil) {
      super(change, user, serverIdent, noteUtil, Instant.now());
      this.project = project;
      this.stage = stage;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    protected Project.NameKey getProjectName() {
      return project;
    }

    @Override
    protected String getRefName() {
      return stage.ref.getName();
    }

    @Override
    protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
        throws IOException {
      CommitBuilder commitBuilder = new CommitBuilder();
      TreeFormatter treeFormatter = new TreeFormatter(stage.data.revisionNotes.size());
      for (Map.Entry<ObjectId, ChangeRevisionNote> changeNoteEntry :
          stage.data.revisionNotes.entrySet()) {
        String noteJson =
            noteUtil.getChangeNoteJson().getGson().toJson(changeNoteEntry.getValue().getEntities());
        ObjectId noteBlob = ins.insert(Constants.OBJ_BLOB, noteJson.getBytes(Charsets.UTF_8));
        treeFormatter.append(changeNoteEntry.getKey().name(), FileMode.REGULAR_FILE, noteBlob);
      }
      commitBuilder.setTreeId(ins.insert(treeFormatter));
      return commitBuilder;
    }
  }
}

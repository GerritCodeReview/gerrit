// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.ChangeNotesCommit;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.server.notedb.PatchSetState;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Program to migrate draft changes and changes with draft patch sets to private changes.
 *
 * <p>The program rewrites the change meta branch of all changes that
 *
 * <ul>
 *   <li>are draft changes or were draft changes once
 *   <li>have draft draft patch sets or had draft patch sets once
 * </ul>
 *
 * <p>On the rewrite of a change meta branch in each commit a DRAFT change status is converted to
 * NEW and a DRAFT patch set status is converted to PUBLISHED.
 *
 * <p>If at any point in time the change is a draft or has draft patch sets, then at this point the
 * change is set to private if it isn't private yet.
 *
 * <p>If at any point in time the change is no draft or the last draft patch set is published or
 * removed the change is set to non-private if it is private and if it wasn't set private by the
 * user.
 *
 * <p>If a change is private before the migration, it will still be private after the migration.
 *
 * <p>Changes for which the history is rewritten must be reindexed. Since the program can't reindex
 * changes, the caller must either do a full offline reindex of all changes or manually reindex the
 * changes listed in the output.
 */
public class MigrateDraftChangesForNoteDb extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(MigrateDraftChangesForNoteDb.class);

  @Argument(index = 0, required = true, metaVar = "FILE", usage = "output")
  private String fileName;

  private static final String DRAFT = "DRAFT";

  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();

  @Inject private GitRepositoryManager repoManager;
  @Inject @GerritPersonIdent private PersonIdent serverIdent;

  @Override
  public int run() throws Exception {
    Injector dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector, dbInjector.createChildInjector(SchemaVersionCheck.module()));
    manager.start();
    dbInjector.injectMembers(this);

    Path path = Paths.get(fileName);
    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      Set<Project.NameKey> projects = repoManager.list();
      monitor.beginTask("Migrate draft changes and changes with draft patch sets", projects.size());
      for (Project.NameKey project : projects) {
        migrateToPrivateChanges(writer, project);
        monitor.update(1);
      }
      monitor.endTask();
    }

    manager.stop();
    return 0;
  }

  private void migrateToPrivateChanges(BufferedWriter writer, Project.NameKey project)
      throws IOException, ConfigInvalidException {
    try (Repository git = repoManager.openRepository(project);
        ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(git);
        ObjectInserter oi = git.newObjectInserter()) {
      Set<Change.Id> changeIds =
          git.getRefDatabase()
              .getRefs(RefNames.REFS_CHANGES)
              .values()
              .stream()
              .map(r -> Change.Id.fromRef(r.getName()))
              .filter(Objects::nonNull)
              .collect(toSet());

      if (changeIds.isEmpty()) {
        return;
      }

      BatchRefUpdate bru = migrateToPrivateChanges(git, rw, oi, project, changeIds);
      bru.execute(rw, NullProgressMonitor.INSTANCE);
      changeIds.clear();
      for (ReceiveCommand cmd : bru.getCommands()) {
        Change.Id changeId = Change.Id.fromRef(cmd.getRefName());
        if (cmd.getResult() == ReceiveCommand.Result.OK) {
          // The change needs to be reindexed, but we can't do this from this
          // program, just
          // output the change so that the caller can reindex in manually.
          writer.write(changeId.toString() + "\n");
        } else {
          log.error(
              "Migrate to private change failed for %s: %s: %s: %s",
              project.get(), cmd, cmd.getResult(), cmd.getMessage());
        }
      }
    }
  }

  private BatchRefUpdate migrateToPrivateChanges(
      Repository git,
      ChangeNotesRevWalk rw,
      ObjectInserter oi,
      Project.NameKey project,
      Set<Change.Id> changeIds)
      throws IOException, ConfigInvalidException {
    BatchRefUpdate bru =
        git.getRefDatabase()
            .newBatchUpdate()
            .setAtomic(false)
            .setAllowNonFastForwards(true)
            .setRefLogIdent(serverIdent)
            .setRefLogMessage(MigrateDraftChangesForNoteDb.class.getSimpleName(), true);

    for (Change.Id changeId : changeIds) {
      ReceiveCommand cmd = migrateToPrivateChange(git, rw, oi, project, changeId);
      if (cmd != null) {
        bru.addCommand(cmd);
      }
    }

    oi.flush();
    return bru;
  }

  private ReceiveCommand migrateToPrivateChange(
      Repository git,
      ChangeNotesRevWalk rw,
      ObjectInserter oi,
      Project.NameKey project,
      Change.Id changeId)
      throws IOException, ConfigInvalidException {
    String refName = RefNames.changeMetaRef(changeId);
    Ref ref = git.exactRef(refName);
    if (ref == null || ref.getObjectId() == null) {
      return null;
    }

    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);
    rw.markStart(rw.parseCommit(ref.getObjectId()));

    ObjectId current = null;
    ChangeNotesCommit c;

    // Keep track of draft patch sets. The change should be set to non-private if all draft patch
    // sets have been published or deleted (unless the user explicitly set the change to private).
    Set<Integer> draftPatchSets = new HashSet<>();

    // Keep track of whether the change is private. If a change is already private we don't need
    // to set it to private again.
    boolean isPrivate = false;

    // Keep track of whether the user explicitly set the change to private. In this case we must
    // not remove the private flag when the change status turns to non-draft or when all draft
    // patch sets have been published or deleted.
    boolean isPrivateByUser = false;

    // Keep track of whether we started rewriting the branch. In this case all follow-up commits
    // need to be rewritten as well.
    boolean rewriting = false;

    while ((c = rw.next()) != null) {
      // Read change status, patch set state and private flag from commit message footers
      String changeStatus = getChangeStatus(c);
      Pair<Integer, String> patchSetState = getPatchSetState(c);
      checkNotNull(patchSetState, "expected patch set state in [%s]", c.getFullMessage());
      Boolean isPrivateFooterValue = isPrivate(c);

      boolean hasDraftStatus = DRAFT.equals(changeStatus);
      boolean hasDraftPatchSetStatus = DRAFT.equals(patchSetState.getSecond());
      boolean needsRewrite = rewriting || hasDraftStatus || hasDraftPatchSetStatus;

      boolean lastDraftPatchSetWasPublishedOrDeleted = false;
      if (hasDraftPatchSetStatus) {
        // Keep track of draft patch sets.
        draftPatchSets.add(patchSetState.getFirst());
      } else if ((PatchSetState.PUBLISHED.name().equals(patchSetState.getSecond())
              || PatchSetState.DELETED.name().equals(patchSetState.getSecond()))
          && !draftPatchSets.isEmpty()) {
        // Keep track of draft patch sets.
        draftPatchSets.remove(patchSetState.getFirst());

        // Check whether the last draft patch set was published or deleted. The change should be
        // set to non-private if all draft patch sets have been published or deleted (unless the
        // user explicitly set the change to private).
        lastDraftPatchSetWasPublishedOrDeleted = draftPatchSets.isEmpty();
      }

      boolean removePrivateFooter = false;
      if (isPrivateFooterValue != null) {
        isPrivateByUser = isPrivateFooterValue;
        if ((isPrivate && isPrivateFooterValue)
            || (!isPrivateFooterValue
                && (DRAFT.equals(changeStatus) || !draftPatchSets.isEmpty()))) {
          // The existing Private footer should be removed if:
          // a) the footer value is true, but the change is already private
          // b) the footer value is false, but the change should stay private because the change
          //    is a draft or has draft patch sets
          needsRewrite = true;
          removePrivateFooter = true;
        }
        if (!removePrivateFooter) {
          // If the Private footer is not removed it is changing the private state of the change.
          isPrivate = isPrivateFooterValue;
        }
      }

      if (!needsRewrite) {
        // If rewriting is not needed we can just reuse the original commit.
        current = c;
        continue;
      }

      // Split commit message into message and footer part. Footers have a strictly defined format
      // and we can safely use string replacements to update them. The message part can contain
      // arbitrary content and we don't want to modify it.
      String origMsg = c.getFullMessage();
      int footerStart = getFooterStart(c);
      if (footerStart < 0) {
        // Commits in the change meta branch must always have at least one footer.
        throw new ConfigInvalidException(
            String.format("No footer found for %s/%s", project.get(), changeId.get()));
      }
      String footers = origMsg.substring(footerStart);
      String newMsg = origMsg.substring(0, footerStart);

      if (removePrivateFooter) {
        // Unset the private footer since we figured out above that it should be removed.
        footers = setPrivate(footers, null);
      }

      if (hasDraftStatus || hasDraftPatchSetStatus) {
        if (hasDraftStatus) {
          // Replace the DRAFT change status with the NEW change status.
          footers = replaceDraftStatus(footers);
        }
        if (hasDraftPatchSetStatus) {
          // Replace the DRAFT patch set state with the PUBLISHED patch set state.
          footers = replaceDraftPatchSetStatus(c, footers);
        }
        if (!isPrivate) {
          // The change isn't private yet, but either the change status is draft or the change has
          // draft patch sets. Hence we turn it private now.
          footers = setPrivate(footers, true);
          isPrivate = true;
        }
      } else if (isPrivate && !isPrivateByUser && lastDraftPatchSetWasPublishedOrDeleted) {
        // Turn non-draft private change to non-private if the last draft patch set was published
        // or deleted and the user didn't set the change to private explicitly.
        footers = setPrivate(footers, false);
        isPrivate = false;
      }

      // Put back together the full commit message.
      newMsg += footers;

      if (!rewriting && newMsg.equals(origMsg)) {
        // We didn't start rewritting the change meta branch yet and the commit message wasn't
        // modified. Hence can just reuse the original commit.
        current = c;
        continue;
      }

      // Now we start rewriting the change meta branch. This means all follow-up commits must be
      // rewritten as well.
      rewriting = true;

      // Rewrite the change meta commit. Copy the original tree and author/committer information.
      CommitBuilder cb = new CommitBuilder();
      if (current != null) {
        cb.setParentId(current);
      }
      cb.setTreeId(c.getTree());
      cb.setAuthor(c.getAuthorIdent());
      cb.setCommitter(c.getCommitterIdent());
      cb.setMessage(newMsg);
      cb.setEncoding(c.getEncoding());
      current = oi.insert(cb);
    }

    if (!rewriting) {
      // No rewrite was done. Hence we don't need to update the change meta branch.
      return null;
    }

    return new ReceiveCommand(
        ref.getObjectId(), current, ref.getName(), ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
  }

  private int getFooterStart(ChangeNotesCommit commit) {
    if (commit.getFooterLines().isEmpty()) {
      return -1;
    }

    String msg = commit.getFullMessage();
    int lastParagraphLfLf = msg.lastIndexOf("\n\n");
    int lastParagraphCrLfCrLf = msg.lastIndexOf("\r\n\r\n");
    if (lastParagraphLfLf > lastParagraphCrLfCrLf) {
      return lastParagraphLfLf + 2;
    }
    return lastParagraphCrLfCrLf + 4;
  }

  private static Boolean isPrivate(ChangeNotesCommit c) {
    List<String> v = c.getFooterLineValues(ChangeNoteUtil.FOOTER_PRIVATE);
    if (v.isEmpty()) {
      return null;
    }
    return Boolean.parseBoolean(Iterables.getOnlyElement(v));
  }

  private static String getChangeStatus(ChangeNotesCommit c) {
    List<String> v = c.getFooterLineValues(ChangeNoteUtil.FOOTER_STATUS);
    if (v.isEmpty()) {
      return null;
    }
    return Iterables.getOnlyElement(v).toUpperCase();
  }

  private static String replaceDraftStatus(String footers) {
    String footerPrefix = ChangeNoteUtil.FOOTER_STATUS.getName() + ": ";
    String oldStatusFooter = footerPrefix + DRAFT.toLowerCase() + "\n";
    String newStatusFooter = footerPrefix + Change.Status.NEW.name().toLowerCase() + "\n";
    return footers.replace(oldStatusFooter, newStatusFooter);
  }

  private static Pair<Integer, String> getPatchSetState(ChangeNotesCommit c) {
    List<String> v = c.getFooterLineValues(ChangeNoteUtil.FOOTER_PATCH_SET);
    if (v.size() != 1) {
      return null;
    }

    String psIdLine = v.get(0);
    int s = psIdLine.indexOf(' ');

    Integer psId = Ints.tryParse(s < 0 ? psIdLine : psIdLine.substring(0, s));
    if (psId == null) {
      return null;
    }

    if (s < 0) {
      return new Pair<>(psId, null);
    }

    String withParens = psIdLine.substring(s + 1);
    if (!withParens.startsWith("(") || !withParens.endsWith(")")) {
      return null;
    }

    return new Pair<>(psId, withParens.substring(1, withParens.length() - 1).toUpperCase());
  }

  private static String replaceDraftPatchSetStatus(ChangeNotesCommit c, String footers) {
    String v = Iterables.getOnlyElement(c.getFooterLineValues(ChangeNoteUtil.FOOTER_PATCH_SET));
    String footerPrefix = ChangeNoteUtil.FOOTER_PATCH_SET.getName() + ": ";
    String oldPatchSetStatusFooter = footerPrefix + v + "\n";
    String newPatchSetStatusFooter =
        footerPrefix
            + v.replace(DRAFT.toLowerCase(), PatchSetState.PUBLISHED.name().toLowerCase())
            + "\n";
    return footers.replace(oldPatchSetStatusFooter, newPatchSetStatusFooter);
  }

  private static String setPrivate(String footers, Boolean value) {
    String footerPrefix = ChangeNoteUtil.FOOTER_PRIVATE.getName() + ": ";
    if (value != null) {
      String oldPrivateFooter = footerPrefix + Boolean.toString(!value) + "\n";
      String newPrivateFooter = footerPrefix + Boolean.toString(value) + "\n";
      if (footers.contains(oldPrivateFooter)) {
        return footers.replace(oldPrivateFooter, newPrivateFooter);
      }
      if (!footers.endsWith("\n")) {
        footers += "\n";
      }
      return footers + footerPrefix + Boolean.toString(value) + "\n";
    }

    String privateFooterTrue = footerPrefix + "true\n";
    String privateFooterFalse = footerPrefix + "false\n";
    footers = footers.replace(privateFooterTrue, "");
    return footers.replace(privateFooterFalse, "");
  }

  private static class Pair<F, S> {
    private final F first;
    private final S second;

    Pair(F first, S second) {
      this.first = first;
      this.second = second;
    }

    public F getFirst() {
      return first;
    }

    public S getSecond() {
      return second;
    }
  }
}

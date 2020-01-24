// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptBuilder.IntraLineDiffCalculatorResult;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

public class PatchScriptFactory implements Callable<PatchScript> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {

    PatchScriptFactory create(
        ChangeNotes notes,
        String fileName,
        @Assisted("patchSetA") PatchSet.Id patchSetA,
        @Assisted("patchSetB") PatchSet.Id patchSetB,
        DiffPreferencesInfo diffPrefs);

    PatchScriptFactory create(
        ChangeNotes notes,
        String fileName,
        int parentNum,
        PatchSet.Id patchSetB,
        DiffPreferencesInfo diffPrefs);
  }

  private final GitRepositoryManager repoManager;
  private final PatchSetUtil psUtil;
  private final Provider<PatchScriptBuilder> builderFactory;
  private final PatchListCache patchListCache;
  private final CommentsUtil commentsUtil;

  private final String fileName;
  @Nullable private final PatchSet.Id psa;
  private final int parentNum;
  private final PatchSet.Id psb;
  private final DiffPreferencesInfo diffPrefs;
  private final ChangeEditUtil editReader;
  private final Provider<CurrentUser> userProvider;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;

  private final Change.Id changeId;
  private boolean loadHistory = true;
  private boolean loadComments = true;

  private ChangeNotes notes;

  @AssistedInject
  PatchScriptFactory(
      GitRepositoryManager grm,
      PatchSetUtil psUtil,
      Provider<PatchScriptBuilder> builderFactory,
      PatchListCache patchListCache,
      CommentsUtil commentsUtil,
      ChangeEditUtil editReader,
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      @Assisted ChangeNotes notes,
      @Assisted String fileName,
      @Assisted("patchSetA") @Nullable PatchSet.Id patchSetA,
      @Assisted("patchSetB") PatchSet.Id patchSetB,
      @Assisted DiffPreferencesInfo diffPrefs) {
    this.repoManager = grm;
    this.psUtil = psUtil;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.notes = notes;
    this.commentsUtil = commentsUtil;
    this.editReader = editReader;
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;

    this.fileName = fileName;
    this.psa = patchSetA;
    this.parentNum = -1;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;

    changeId = patchSetB.changeId();
  }

  @AssistedInject
  PatchScriptFactory(
      GitRepositoryManager grm,
      PatchSetUtil psUtil,
      Provider<PatchScriptBuilder> builderFactory,
      PatchListCache patchListCache,
      CommentsUtil commentsUtil,
      ChangeEditUtil editReader,
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      @Assisted ChangeNotes notes,
      @Assisted String fileName,
      @Assisted int parentNum,
      @Assisted PatchSet.Id patchSetB,
      @Assisted DiffPreferencesInfo diffPrefs) {
    this.repoManager = grm;
    this.psUtil = psUtil;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.notes = notes;
    this.commentsUtil = commentsUtil;
    this.editReader = editReader;
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;

    this.fileName = fileName;
    this.psa = null;
    this.parentNum = parentNum;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;

    changeId = patchSetB.changeId();
    checkArgument(parentNum >= 0, "parentNum must be >= 0");
  }

  public void setLoadHistory(boolean load) {
    loadHistory = load;
  }

  public void setLoadComments(boolean load) {
    loadComments = load;
  }

  @Override
  public PatchScript call()
      throws LargeObjectException, AuthException, InvalidChangeOperationException, IOException,
          PermissionBackendException {

    try {
      permissionBackend.currentUser().change(notes).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new NoSuchChangeException(changeId, e);
    }

    if (!projectCache.checkedGet(notes.getProjectName()).statePermitsRead()) {
      throw new NoSuchChangeException(changeId);
    }

    try (Repository git = repoManager.openRepository(notes.getProjectName())) {
      try {
        validatePatchSetId(psa);
        validatePatchSetId(psb);

        ObjectId aId = getAId().orElse(null);
        ObjectId bId = getBId().orElse(null);
        boolean changeEdit = false;
        if (bId == null) {
          // Change edit: create synthetic PatchSet corresponding to the edit.
          Optional<ChangeEdit> edit = editReader.byChange(notes);
          if (!edit.isPresent()) {
            throw new NoSuchChangeException(notes.getChangeId());
          }
          bId = edit.get().getEditCommit();
          changeEdit = true;
        }

        final PatchList list = listFor(keyFor(aId, bId, diffPrefs.ignoreWhitespace));
        final PatchScriptBuilder b = newBuilder();
        final PatchListEntry content = list.get(fileName);

        Optional<ImmutableList<Patch>> history = loadHistory(content, changeEdit);
        Optional<CommentDetail> comments =
            loadComments(content, changeEdit, history.orElse(ImmutableList.of()));

        return b.toPatchScript(git, list, content, comments.orElse(null), history.orElse(null));
      } catch (PatchListNotAvailableException e) {
        throw new NoSuchChangeException(changeId, e);
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("File content unavailable");
        throw new NoSuchChangeException(changeId, e);
      } catch (org.eclipse.jgit.errors.LargeObjectException err) {
        throw new LargeObjectException("File content is too large", err);
      }
    } catch (RepositoryNotFoundException e) {
      logger.atSevere().withCause(e).log("Repository %s not found", notes.getProjectName());
      throw new NoSuchChangeException(changeId, e);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot open repository %s", notes.getProjectName());
      throw new NoSuchChangeException(changeId, e);
    }
  }

  private Optional<CommentDetail> loadComments(
      PatchListEntry content, boolean changeEdit, ImmutableList<Patch> history) {
    if (!loadComments) {
      return Optional.empty();
    }
    return new CommentsLoader(psa, psb, userProvider, notes, commentsUtil)
        .load(
            changeEdit,
            content.getChangeType(),
            content.getOldName(),
            content.getNewName(),
            history);
  }

  private Optional<ImmutableList<Patch>> loadHistory(PatchListEntry content, boolean changeEdit) {
    if (!loadHistory) {
      return Optional.empty();
    }
    HistoryLoader loader = new HistoryLoader(psa, psb, psUtil, notes, fileName);
    return Optional.of(loader.load(changeEdit, content.getChangeType(), content.getOldName()));
  }

  private Optional<ObjectId> getAId() {
    if (psa == null) {
      return Optional.empty();
    }
    checkState(parentNum < 0, "expected no parentNum when psa is present");
    checkArgument(psa.get() != 0, "edit not supported for left side");
    return Optional.of(getCommitId(psa));
  }

  private Optional<ObjectId> getBId() {
    if (psb.get() == 0) {
      // Change edit
      return Optional.empty();
    }
    return Optional.of(getCommitId(psb));
  }

  private PatchListKey keyFor(ObjectId aId, ObjectId bId, Whitespace whitespace) {
    if (parentNum < 0) {
      return PatchListKey.againstCommit(aId, bId, whitespace);
    }
    return PatchListKey.againstParentNum(parentNum + 1, bId, whitespace);
  }

  private PatchList listFor(PatchListKey key) throws PatchListNotAvailableException {
    return patchListCache.get(key, notes.getProjectName());
  }

  private PatchScriptBuilder newBuilder() {
    final PatchScriptBuilder b = builderFactory.get();
    b.setChange(notes.getChange());
    b.setDiffPrefs(diffPrefs);
    if (diffPrefs.intralineDifference) {
      b.setIntraLineDiffCalculator(
          new IntraLineDiffCalculator(patchListCache, notes.getProjectName(), diffPrefs));
    }
    return b;
  }

  private ObjectId getCommitId(PatchSet.Id psId) {
    PatchSet ps = psUtil.get(notes, psId);
    if (ps == null) {
      throw new NoSuchChangeException(psId.changeId());
    }
    return ps.commitId();
  }

  private void validatePatchSetId(PatchSet.Id psId) throws NoSuchChangeException {
    if (psId == null) { // OK, means use base;
    } else if (changeId.equals(psId.changeId())) { // OK, same change;
    } else {
      throw new NoSuchChangeException(changeId);
    }
  }

  private static class HistoryLoader {
    private final PatchSet.Id psa;
    private final PatchSet.Id psb;
    private final PatchSetUtil psUtil;
    private final ChangeNotes notes;
    private final String fileName;

    HistoryLoader(
        PatchSet.Id psa, PatchSet.Id psb, PatchSetUtil psUtil, ChangeNotes notes, String fileName) {
      this.psa = psa;
      this.psb = psb;
      this.psUtil = psUtil;
      this.notes = notes;
      this.fileName = fileName;
    }

    private ImmutableList<Patch> load(boolean changeEdit, ChangeType changeType, String oldName) {
      // This seems like a cheap trick. It doesn't properly account for a
      // file that gets renamed between patch set 1 and patch set 2. We
      // will wind up packing the wrong Patch object because we didn't do
      // proper rename detection between the patch sets.
      //
      ImmutableList.Builder<Patch> historyBuilder = ImmutableList.builder();
      for (PatchSet ps : psUtil.byChange(notes)) {
        String name = fileName;
        if (psa != null) {
          switch (changeType) {
            case COPIED:
            case RENAMED:
              if (ps.id().equals(psa)) {
                name = oldName;
              }
              break;

            case MODIFIED:
            case DELETED:
            case ADDED:
            case REWRITE:
              break;
          }
        }

        Patch p = new Patch(Patch.key(ps.id(), name));
        historyBuilder.add(p);
      }
      if (changeEdit) {
        Patch p = new Patch(Patch.key(PatchSet.id(psb.changeId(), 0), fileName));
        historyBuilder.add(p);
      }
      return historyBuilder.build();
    }
  }

  private static class CommentsLoader {
    private final PatchSet.Id psa;
    private final PatchSet.Id psb;
    private final Provider<CurrentUser> userProvider;
    private final ChangeNotes notes;
    private final CommentsUtil commentsUtil;
    private CommentDetail comments;

    CommentsLoader(
        PatchSet.Id psa,
        PatchSet.Id psb,
        Provider<CurrentUser> userProvider,
        ChangeNotes notes,
        CommentsUtil commentsUtil) {
      this.psa = psa;
      this.psb = psb;
      this.userProvider = userProvider;
      this.notes = notes;
      this.commentsUtil = commentsUtil;
    }

    private Optional<CommentDetail> load(
        boolean changeEdit,
        ChangeType changeType,
        String oldName,
        String newName,
        ImmutableList<Patch> history) {
      // TODO: Implement this method with CommentDetailBuilder (this class doesn't exists yet).
      // This is a legacy code which create final object and populate it and then returns it.
      if (changeEdit) {
        return Optional.empty();
      }
      Map<Patch.Key, Patch> byKey = new HashMap<>();
      history.forEach(p -> byKey.put(p.getKey(), p));

      comments = new CommentDetail(psa, psb);
      switch (changeType) {
        case ADDED:
        case MODIFIED:
          loadPublished(byKey, newName);
          break;

        case DELETED:
          loadPublished(byKey, newName);
          break;

        case COPIED:
        case RENAMED:
          if (psa != null) {
            loadPublished(byKey, oldName);
          }
          loadPublished(byKey, newName);
          break;

        case REWRITE:
          break;
      }

      CurrentUser user = userProvider.get();
      if (user.isIdentifiedUser()) {
        Account.Id me = user.getAccountId();
        switch (changeType) {
          case ADDED:
          case MODIFIED:
            loadDrafts(byKey, me, newName);
            break;

          case DELETED:
            loadDrafts(byKey, me, newName);
            break;

          case COPIED:
          case RENAMED:
            if (psa != null) {
              loadDrafts(byKey, me, oldName);
            }
            loadDrafts(byKey, me, newName);
            break;

          case REWRITE:
            break;
        }
      }
      return Optional.of(comments);
    }

    private void loadPublished(Map<Patch.Key, Patch> byKey, String file) {
      for (Comment c : commentsUtil.publishedByChangeFile(notes, file)) {
        comments.include(notes.getChangeId(), c);
        PatchSet.Id psId = PatchSet.id(notes.getChangeId(), c.key.patchSetId);
        Patch.Key pKey = Patch.key(psId, c.key.filename);
        Patch p = byKey.get(pKey);
        if (p != null) {
          p.setCommentCount(p.getCommentCount() + 1);
        }
      }
    }

    private void loadDrafts(Map<Patch.Key, Patch> byKey, Account.Id me, String file) {
      for (Comment c : commentsUtil.draftByChangeFileAuthor(notes, file, me)) {
        comments.include(notes.getChangeId(), c);
        PatchSet.Id psId = PatchSet.id(notes.getChangeId(), c.key.patchSetId);
        Patch.Key pKey = Patch.key(psId, c.key.filename);
        Patch p = byKey.get(pKey);
        if (p != null) {
          p.setDraftCount(p.getDraftCount() + 1);
        }
      }
    }
  }

  private static class IntraLineDiffCalculator
      implements PatchScriptBuilder.IntraLineDiffCalculator {

    private final PatchListCache patchListCache;
    private final Project.NameKey projectKey;
    private final DiffPreferencesInfo diffPrefs;

    IntraLineDiffCalculator(
        PatchListCache patchListCache, Project.NameKey projectKey, DiffPreferencesInfo diffPrefs) {
      this.patchListCache = patchListCache;
      this.projectKey = projectKey;
      this.diffPrefs = diffPrefs;
    }

    @Override
    public IntraLineDiffCalculatorResult calculateIntraLineDiff(
        ImmutableList<Edit> edits,
        Set<Edit> editsDueToRebase,
        ObjectId aId,
        ObjectId bId,
        Text aSrc,
        Text bSrc,
        ObjectId bTreeId,
        String bPath) {
      IntraLineDiff d =
          patchListCache.getIntraLineDiff(
              IntraLineDiffKey.create(aId, bId, diffPrefs.ignoreWhitespace),
              IntraLineDiffArgs.create(
                  aSrc, bSrc, edits, editsDueToRebase, projectKey, bTreeId, bPath));
      if (d == null) {
        return IntraLineDiffCalculatorResult.FAILURE;
      }
      switch (d.getStatus()) {
        case EDIT_LIST:
          return IntraLineDiffCalculatorResult.success(d.getEdits());

        case ERROR:
          return IntraLineDiffCalculatorResult.FAILURE;

        case TIMEOUT:
          return IntraLineDiffCalculatorResult.TIMEOUT;

        case DISABLED:
        default:
          return IntraLineDiffCalculatorResult.NO_RESULT;
      }
    }
  }
}

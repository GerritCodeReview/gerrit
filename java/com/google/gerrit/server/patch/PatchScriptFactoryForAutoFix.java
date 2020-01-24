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

package com.google.gerrit.server.patch;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
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
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

public class PatchScriptFactoryForAutoFix implements Callable<PatchScript> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {

    PatchScriptFactoryForAutoFix create(
        Repository git,
        ChangeNotes notes,
        String fileName,
        PatchSet patchSet,
        ImmutableList<FixReplacement> fixReplacements,
        DiffPreferencesInfo diffPrefs);
  }

  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Change.Id changeId;
  private final ChangeNotes notes;
  private final Provider<PatchScriptBuilder> builderFactory;
  private final Repository git;
  private final PatchSet patchSet;
  private final String fileName;
  private final DiffPreferencesInfo diffPrefs;
  private final ImmutableList<FixReplacement> fixReplacements;

  @AssistedInject
  PatchScriptFactoryForAutoFix(
      Provider<PatchScriptBuilder> builderFactory,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      @Assisted Repository git,
      @Assisted ChangeNotes notes,
      @Assisted String fileName,
      @Assisted PatchSet patchSet,
      @Assisted ImmutableList<FixReplacement> fixReplacements,
      @Assisted DiffPreferencesInfo diffPrefs) {
    this.notes = notes;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.changeId = patchSet.id().changeId();
    this.git = git;
    this.patchSet = patchSet;
    this.fileName = fileName;
    this.fixReplacements = fixReplacements;
    this.builderFactory = builderFactory;
    this.diffPrefs = diffPrefs;
  }

  @Override
  public PatchScript call()
      throws LargeObjectException, AuthException, InvalidChangeOperationException, IOException,
          PermissionBackendException, ResourceNotFoundException {

    try {
      permissionBackend.currentUser().change(notes).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new NoSuchChangeException(changeId);
    }

    if (!projectCache.checkedGet(notes.getProjectName()).statePermitsRead()) {
      throw new NoSuchChangeException(changeId);
    }

    return createPatchScript();
  }

  private PatchScript createPatchScript() throws LargeObjectException, ResourceNotFoundException {
    checkState(patchSet.id().get() != 0, "edit not supported for left side");
    PatchScriptBuilder b = newBuilder();
    try {
      ObjectId baseId = patchSet.commitId();
      return b.toPatchScript(git, baseId, fileName, fixReplacements);
    } catch (ResourceConflictException e) {
      logger.atSevere().withCause(e).log("AutoFix replacements is not valid");
      throw new IllegalStateException("AutoFix replacements is not valid", e);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("File content unavailable");
      throw new NoSuchChangeException(notes.getChangeId(), e);
    } catch (org.eclipse.jgit.errors.LargeObjectException err) {
      throw new LargeObjectException("File content is too large", err);
    }
  }

  private PatchScriptBuilder newBuilder() {
    PatchScriptBuilder b = builderFactory.get();
    b.setChange(notes.getChange());
    b.setDiffPrefs(diffPrefs);
    return b;
  }
}

// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

public class ChangeResource implements RestResource, HasETag {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * JSON format version number for ETag computations.
   *
   * <p>Should be bumped on any JSON format change (new fields, etc.) so that otherwise unmodified
   * changes get new ETags.
   */
  public static final int JSON_FORMAT_VERSION = 1;

  public static final TypeLiteral<RestView<ChangeResource>> CHANGE_KIND =
      new TypeLiteral<RestView<ChangeResource>>() {};

  public interface Factory {
    ChangeResource create(ChangeNotes notes, CurrentUser user);
  }

  private static final String ZERO_ID_STRING = ObjectId.zeroId().name();

  private final AccountCache accountCache;
  private final ApprovalsUtil approvalUtil;
  private final PatchSetUtil patchSetUtil;
  private final PermissionBackend permissionBackend;
  private final StarredChangesUtil starredChangesUtil;
  private final ProjectCache projectCache;
  private final PluginSetContext<ChangeETagComputation> changeETagComputation;
  private final ChangeNotes notes;
  private final CurrentUser user;

  @Inject
  ChangeResource(
      AccountCache accountCache,
      ApprovalsUtil approvalUtil,
      PatchSetUtil patchSetUtil,
      PermissionBackend permissionBackend,
      StarredChangesUtil starredChangesUtil,
      ProjectCache projectCache,
      PluginSetContext<ChangeETagComputation> changeETagComputation,
      @Assisted ChangeNotes notes,
      @Assisted CurrentUser user) {
    this.accountCache = accountCache;
    this.approvalUtil = approvalUtil;
    this.patchSetUtil = patchSetUtil;
    this.permissionBackend = permissionBackend;
    this.starredChangesUtil = starredChangesUtil;
    this.projectCache = projectCache;
    this.changeETagComputation = changeETagComputation;
    this.notes = notes;
    this.user = user;
  }

  public PermissionBackend.ForChange permissions() {
    return permissionBackend.user(user).change(notes);
  }

  public CurrentUser getUser() {
    return user;
  }

  public Change.Id getId() {
    return notes.getChangeId();
  }

  /** @return true if {@link #getUser()} is the change's owner. */
  public boolean isUserOwner() {
    Account.Id owner = getChange().getOwner();
    return user.isIdentifiedUser() && user.asIdentifiedUser().getAccountId().equals(owner);
  }

  public Change getChange() {
    return notes.getChange();
  }

  public Project.NameKey getProject() {
    return getChange().getProject();
  }

  public ChangeNotes getNotes() {
    return notes;
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
      patchSetUtil.byChange(notes).stream().map(PatchSet::uploader).forEach(accounts::add);

      // It's intentional to include the states for *all* reviewers into the ETag computation.
      // We need the states of all current reviewers and CCs because they are part of ChangeInfo.
      // Including removed reviewers is a cheap way of making sure that the states of accounts that
      // posted a message on the change are included. Loading all change messages to find the exact
      // set of accounts that posted a message is too expensive. However everyone who posts a
      // message is automatically added as reviewer. Hence if we include removed reviewers we can
      // be sure that we have all accounts that posted messages on the change.
      accounts.addAll(approvalUtil.getReviewers(notes).all());
    } catch (StorageException e) {
      // This ETag will be invalidated if it loads next time.
    }

    for (Account.Id accountId : accounts) {
      Optional<AccountState> accountState = accountCache.get(accountId);
      if (accountState.isPresent()) {
        hashAccount(h, accountState.get(), buf);
      } else {
        h.putInt(accountId.get());
      }
    }

    ObjectId noteId;
    try {
      noteId = notes.loadRevision();
    } catch (StorageException e) {
      noteId = null; // This ETag will be invalidated if it loads next time.
    }
    hashObjectId(h, noteId, buf);
    // TODO(dborowitz): Include more NoteDb and other related refs, e.g. drafts
    // and edits.

    Iterable<ProjectState> projectStateTree;
    try {
      projectStateTree = projectCache.checkedGet(getProject()).tree();
    } catch (IOException e) {
      logger.atSevere().log("could not load project %s while computing etag", getProject());
      projectStateTree = ImmutableList.of();
    }

    for (ProjectState p : projectStateTree) {
      hashObjectId(h, p.getConfig().getRevision(), buf);
    }

    changeETagComputation.runEach(
        c -> {
          String pluginETag = c.getETag(notes.getProjectName(), notes.getChangeId());
          if (pluginETag != null) {
            h.putString(pluginETag, UTF_8);
          }
        });
  }

  @Override
  public String getETag() {
    try (TraceTimer ignored =
        TraceContext.newTimer(
            "Compute change ETag",
            Metadata.builder()
                .changeId(notes.getChangeId().get())
                .projectName(notes.getProjectName().get())
                .build())) {
      Hasher h = Hashing.murmur3_128().newHasher();
      if (user.isIdentifiedUser()) {
        h.putString(starredChangesUtil.getObjectId(user.getAccountId(), getId()).name(), UTF_8);
      }
      prepareETag(h, user);
      return h.hash().toString();
    }
  }

  private void hashObjectId(Hasher h, ObjectId id, byte[] buf) {
    MoreObjects.firstNonNull(id, ObjectId.zeroId()).copyRawTo(buf, 0);
    h.putBytes(buf);
  }

  private void hashAccount(Hasher h, AccountState accountState, byte[] buf) {
    h.putInt(accountState.account().id().get());
    h.putString(MoreObjects.firstNonNull(accountState.account().metaId(), ZERO_ID_STRING), UTF_8);
    accountState.externalIds().stream().forEach(e -> hashObjectId(h, e.blobId(), buf));
  }
}

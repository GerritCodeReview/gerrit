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
import static java.util.stream.Collectors.toSet;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ListMultimap;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

public class ChangeResource implements RestResource, HasETag {
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
    ChangeResource create(ChangeControl ctl);
  }

  private final Provider<ReviewDb> db;
  private final AccountCache accountCache;
  private final ApprovalsUtil approvalUtil;
  private final PermissionBackend permissionBackend;
  private final StarredChangesUtil starredChangesUtil;
  private final ChangeControl control;

  @Inject
  ChangeResource(
      Provider<ReviewDb> db,
      AccountCache accountCache,
      ApprovalsUtil approvalUtil,
      PermissionBackend permissionBackend,
      StarredChangesUtil starredChangesUtil,
      @Assisted ChangeControl control) {
    this.db = db;
    this.accountCache = accountCache;
    this.approvalUtil = approvalUtil;
    this.permissionBackend = permissionBackend;
    this.starredChangesUtil = starredChangesUtil;
    this.control = control;
  }

  public PermissionBackend.ForChange permissions() {
    return permissionBackend.user(getControl().getUser()).change(getNotes());
  }

  public ChangeControl getControl() {
    return control;
  }

  public IdentifiedUser getUser() {
    return getControl().getUser().asIdentifiedUser();
  }

  public Change.Id getId() {
    return getControl().getId();
  }

  /** @return true if {@link #getUser()} is the change's owner. */
  public boolean isUserOwner() {
    CurrentUser user = getControl().getUser();
    Account.Id owner = getChange().getOwner();
    return user.isIdentifiedUser() && user.asIdentifiedUser().getAccountId().equals(owner);
  }

  public Change getChange() {
    return getControl().getChange();
  }

  public Project.NameKey getProject() {
    return getChange().getProject();
  }

  public ChangeNotes getNotes() {
    return getControl().getNotes();
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
    try {
      ListMultimap<PatchSet.Id, PatchSetApproval> approvals =
          approvalUtil.byChange(db.get(), getNotes());
      ReviewerSet reviewers = approvalUtil.getReviewers(getNotes(), approvals.values());
      accounts.addAll(approvals.values().stream().map(a -> a.getAccountId()).collect(toSet()));
      accounts.addAll(reviewers.byState(ReviewerStateInternal.REVIEWER));
      accounts.addAll(reviewers.byState(ReviewerStateInternal.CC));
    } catch (OrmException e) {
      // This ETag will be invalidated if it loads next time.
    }
    accounts
        .stream()
        .forEach(
            a ->
                hashObjectId(
                    h, ObjectId.fromString(accountCache.get(a).getAccount().getMetaId()), buf));

    ObjectId noteId;
    try {
      noteId = getNotes().loadRevision();
    } catch (OrmException e) {
      noteId = null; // This ETag will be invalidated if it loads next time.
    }
    hashObjectId(h, noteId, buf);
    // TODO(dborowitz): Include more NoteDb and other related refs, e.g. drafts
    // and edits.

    for (ProjectState p : control.getProjectControl().getProjectState().tree()) {
      hashObjectId(h, p.getConfig().getRevision(), buf);
    }
  }

  @Override
  public String getETag() {
    CurrentUser user = control.getUser();
    Hasher h = Hashing.murmur3_128().newHasher();
    if (user.isIdentifiedUser()) {
      h.putString(starredChangesUtil.getObjectId(user.getAccountId(), getId()).name(), UTF_8);
    }
    prepareETag(h, user);
    return h.hash().toString();
  }

  private void hashObjectId(Hasher h, ObjectId id, byte[] buf) {
    MoreObjects.firstNonNull(id, ObjectId.zeroId()).copyRawTo(buf, 0);
    h.putBytes(buf);
  }
}

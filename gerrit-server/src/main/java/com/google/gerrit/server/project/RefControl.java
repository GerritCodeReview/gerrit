// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.FailedPermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages access control for Git references (aka branches, tags). */
public class RefControl {
  private static final Logger log = LoggerFactory.getLogger(RefControl.class);

  private final ProjectControl projectControl;
  private final String refName;

  /** All permissions that apply to this reference. */
  private final PermissionCollection relevant;

  /** Cached set of permissions matching this user. */
  private final Map<String, List<PermissionRule>> effective;

  private Boolean owner;
  private Boolean canForgeAuthor;
  private Boolean canForgeCommitter;
  private Boolean isVisible;

  RefControl(ProjectControl projectControl, String ref, PermissionCollection relevant) {
    this.projectControl = projectControl;
    this.refName = ref;
    this.relevant = relevant;
    this.effective = new HashMap<>();
  }

  public String getRefName() {
    return refName;
  }

  public ProjectControl getProjectControl() {
    return projectControl;
  }

  public CurrentUser getUser() {
    return projectControl.getUser();
  }

  public RefControl forUser(CurrentUser who) {
    ProjectControl newCtl = projectControl.forUser(who);
    if (relevant.isUserSpecific()) {
      return newCtl.controlForRef(getRefName());
    }
    return new RefControl(newCtl, getRefName(), relevant);
  }

  /** Is this user a ref owner? */
  public boolean isOwner() {
    if (owner == null) {
      if (canPerform(Permission.OWNER)) {
        owner = true;

      } else {
        owner = projectControl.isOwner();
      }
    }
    return owner;
  }

  /** Can this user see this reference exists? */
  public boolean isVisible() {
    if (isVisible == null) {
      isVisible =
          (getUser().isInternalUser() || canPerform(Permission.READ))
              && isProjectStatePermittingRead();
    }
    return isVisible;
  }

  /** Can this user see other users change edits? */
  public boolean isEditVisible() {
    return canViewPrivateChanges();
  }

  /** True if this reference is visible by all REGISTERED_USERS */
  public boolean isVisibleByRegisteredUsers() {
    List<PermissionRule> access = relevant.getPermission(Permission.READ);
    List<PermissionRule> overridden = relevant.getOverridden(Permission.READ);
    Set<ProjectRef> allows = new HashSet<>();
    Set<ProjectRef> blocks = new HashSet<>();
    for (PermissionRule rule : access) {
      if (rule.isBlock()) {
        blocks.add(relevant.getRuleProps(rule));
      } else if (SystemGroupBackend.isAnonymousOrRegistered(rule.getGroup())) {
        allows.add(relevant.getRuleProps(rule));
      }
    }
    for (PermissionRule rule : overridden) {
      if (SystemGroupBackend.isAnonymousOrRegistered(rule.getGroup())) {
        blocks.remove(relevant.getRuleProps(rule));
      }
    }
    blocks.removeAll(allows);
    return blocks.isEmpty() && !allows.isEmpty();
  }

  private boolean canUpload() {
    return projectControl.controlForRef("refs/for/" + getRefName()).canPerform(Permission.PUSH)
        && isProjectStatePermittingWrite();
  }

  /** @return true if this user can add a new patch set to this ref */
  boolean canAddPatchSet() {
    return projectControl
            .controlForRef("refs/for/" + getRefName())
            .canPerform(Permission.ADD_PATCH_SET)
        && isProjectStatePermittingWrite();
  }

  /** @return true if this user can submit merge patch sets to this ref */
  private boolean canUploadMerges() {
    return projectControl
            .controlForRef("refs/for/" + getRefName())
            .canPerform(Permission.PUSH_MERGE)
        && isProjectStatePermittingWrite();
  }

  /** @return true if this user can rebase changes on this ref */
  boolean canRebase() {
    return canPerform(Permission.REBASE) && isProjectStatePermittingWrite();
  }

  /** @return true if this user can submit patch sets to this ref */
  boolean canSubmit(boolean isChangeOwner) {
    if (RefNames.REFS_CONFIG.equals(refName)) {
      // Always allow project owners to submit configuration changes.
      // Submitting configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond submitting to the configuration.
      return projectControl.isOwner();
    }
    return canPerform(Permission.SUBMIT, isChangeOwner) && isProjectStatePermittingWrite();
  }

  /** @return true if the user can update the reference as a fast-forward. */
  private boolean canUpdate() {
    if (RefNames.REFS_CONFIG.equals(refName) && !projectControl.isOwner()) {
      // Pushing requires being at least project owner, in addition to push.
      // Pushing configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond pushing to the configuration.

      // On the AllProjects project the owner access right cannot be assigned,
      // this why for the AllProjects project we allow administrators to push
      // configuration changes if they have push without being project owner.
      if (!(projectControl.getProjectState().isAllProjects() && projectControl.isAdmin())) {
        return false;
      }
    }
    return canPerform(Permission.PUSH) && isProjectStatePermittingWrite();
  }

  /** @return true if the user can rewind (force push) the reference. */
  private boolean canForceUpdate() {
    if (!isProjectStatePermittingWrite()) {
      return false;
    }

    if (canPushWithForce()) {
      return true;
    }

    switch (getUser().getAccessPath()) {
      case GIT:
        return false;

      case JSON_RPC:
      case REST_API:
      case SSH_COMMAND:
      case UNKNOWN:
      case WEB_BROWSER:
      default:
        return (isOwner() && !isForceBlocked(Permission.PUSH)) || projectControl.isAdmin();
    }
  }

  private boolean isProjectStatePermittingWrite() {
    return getProjectControl().getProject().getState().equals(ProjectState.ACTIVE);
  }

  private boolean isProjectStatePermittingRead() {
    return getProjectControl().getProject().getState().equals(ProjectState.READ_ONLY)
        || isProjectStatePermittingWrite();
  }

  private boolean canPushWithForce() {
    if (!isProjectStatePermittingWrite()
        || (RefNames.REFS_CONFIG.equals(refName) && !projectControl.isOwner())) {
      // Pushing requires being at least project owner, in addition to push.
      // Pushing configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond pushing to the configuration.
      return false;
    }
    return canForcePerform(Permission.PUSH);
  }

  /**
   * Determines whether the user can create a new Git ref.
   *
   * @param repo repository on which user want to create
   * @param object the object the user will start the reference with.
   * @return {@code true} if the user specified can create a new Git ref
   */
  public boolean canCreate(Repository repo, RevObject object) {
    if (!isProjectStatePermittingWrite()) {
      return false;
    }

    if (object instanceof RevCommit) {
      if (!canPerform(Permission.CREATE)) {
        // No create permissions.
        return false;
      }
      return canCreateCommit(repo, (RevCommit) object);
    } else if (object instanceof RevTag) {
      final RevTag tag = (RevTag) object;
      try (RevWalk rw = new RevWalk(repo)) {
        rw.parseBody(tag);
      } catch (IOException e) {
        return false;
      }

      // If tagger is present, require it matches the user's email.
      //
      final PersonIdent tagger = tag.getTaggerIdent();
      if (tagger != null) {
        boolean valid;
        if (getUser().isIdentifiedUser()) {
          final String addr = tagger.getEmailAddress();
          valid = getUser().asIdentifiedUser().hasEmailAddress(addr);
        } else {
          valid = false;
        }
        if (!valid && !canForgeCommitter()) {
          return false;
        }
      }

      RevObject tagObject = tag.getObject();
      if (tagObject instanceof RevCommit) {
        if (!canCreateCommit(repo, (RevCommit) tagObject)) {
          return false;
        }
      } else {
        if (!canCreate(repo, tagObject)) {
          return false;
        }
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      //
      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        return canPerform(Permission.CREATE_SIGNED_TAG);
      }
      return canPerform(Permission.CREATE_TAG);
    } else {
      return false;
    }
  }

  private boolean canCreateCommit(Repository repo, RevCommit commit) {
    if (canUpdate()) {
      // If the user has push permissions, they can create the ref regardless
      // of whether they are pushing any new objects along with the create.
      return true;
    } else if (isMergedIntoBranchOrTag(repo, commit)) {
      // If the user has no push permissions, check whether the object is
      // merged into a branch or tag readable by this user. If so, they are
      // not effectively "pushing" more objects, so they can create the ref
      // even if they don't have push permission.
      return true;
    }
    return false;
  }

  private boolean isMergedIntoBranchOrTag(Repository repo, RevCommit commit) {
    try (RevWalk rw = new RevWalk(repo)) {
      List<Ref> refs = new ArrayList<>(repo.getRefDatabase().getRefs(Constants.R_HEADS).values());
      refs.addAll(repo.getRefDatabase().getRefs(Constants.R_TAGS).values());
      return projectControl.isMergedIntoVisibleRef(repo, rw, commit, refs);
    } catch (IOException e) {
      String msg =
          String.format(
              "Cannot verify permissions to commit object %s in repository %s",
              commit.name(), projectControl.getProject().getNameKey());
      log.error(msg, e);
    }
    return false;
  }

  /**
   * Determines whether the user can delete the Git ref controlled by this object.
   *
   * @return {@code true} if the user specified can delete a Git ref.
   */
  private boolean canDelete() {
    if (!isProjectStatePermittingWrite() || (RefNames.REFS_CONFIG.equals(refName))) {
      // Never allow removal of the refs/meta/config branch.
      // Deleting the branch would destroy all Gerrit specific
      // metadata about the project, including its access rules.
      // If a project is to be removed from Gerrit, its repository
      // should be removed first.
      return false;
    }

    switch (getUser().getAccessPath()) {
      case GIT:
        return canPushWithForce() || canPerform(Permission.DELETE);

      case JSON_RPC:
      case REST_API:
      case SSH_COMMAND:
      case UNKNOWN:
      case WEB_BROWSER:
      default:
        return (isOwner() && !isForceBlocked(Permission.PUSH))
            || canPushWithForce()
            || canPerform(Permission.DELETE)
            || projectControl.isAdmin();
    }
  }

  /** @return true if this user can forge the author line in a commit. */
  private boolean canForgeAuthor() {
    if (canForgeAuthor == null) {
      canForgeAuthor = canPerform(Permission.FORGE_AUTHOR);
    }
    return canForgeAuthor;
  }

  /** @return true if this user can forge the committer line in a commit. */
  private boolean canForgeCommitter() {
    if (canForgeCommitter == null) {
      canForgeCommitter = canPerform(Permission.FORGE_COMMITTER);
    }
    return canForgeCommitter;
  }

  /** @return true if this user can forge the server on the committer line. */
  private boolean canForgeGerritServerIdentity() {
    return canPerform(Permission.FORGE_SERVER);
  }

  /** @return true if this user can abandon a change for this ref */
  boolean canAbandon() {
    return canPerform(Permission.ABANDON);
  }

  /** @return true if this user can remove a reviewer for a change. */
  boolean canRemoveReviewer() {
    return canPerform(Permission.REMOVE_REVIEWER);
  }

  /** @return true if this user can view draft changes. */
  boolean canViewDrafts() {
    return canPerform(Permission.VIEW_DRAFTS);
  }

  /** @return true if this user can view private changes. */
  boolean canViewPrivateChanges() {
    return canPerform(Permission.VIEW_PRIVATE_CHANGES);
  }

  /** @return true if this user can publish draft changes. */
  boolean canPublishDrafts() {
    return canPerform(Permission.PUBLISH_DRAFTS);
  }

  /** @return true if this user can delete draft changes. */
  boolean canDeleteDrafts() {
    return canPerform(Permission.DELETE_DRAFTS);
  }

  /** @return true if this user can delete their own changes. */
  boolean canDeleteOwnChanges() {
    return canPerform(Permission.DELETE_OWN_CHANGES);
  }

  /** @return true if this user can edit topic names. */
  boolean canEditTopicName() {
    return canPerform(Permission.EDIT_TOPIC_NAME);
  }

  /** @return true if this user can edit hashtag names. */
  boolean canEditHashtags() {
    return canPerform(Permission.EDIT_HASHTAGS);
  }

  boolean canEditAssignee() {
    return canPerform(Permission.EDIT_ASSIGNEE);
  }

  /** @return true if this user can force edit topic names. */
  boolean canForceEditTopicName() {
    return canForcePerform(Permission.EDIT_TOPIC_NAME);
  }

  /** All value ranges of any allowed label permission. */
  List<PermissionRange> getLabelRanges(boolean isChangeOwner) {
    List<PermissionRange> r = new ArrayList<>();
    for (Map.Entry<String, List<PermissionRule>> e : relevant.getDeclaredPermissions()) {
      if (Permission.isLabel(e.getKey())) {
        int min = 0;
        int max = 0;
        for (PermissionRule rule : e.getValue()) {
          if (projectControl.match(rule, isChangeOwner)) {
            min = Math.min(min, rule.getMin());
            max = Math.max(max, rule.getMax());
          }
        }
        if (min != 0 || max != 0) {
          r.add(new PermissionRange(e.getKey(), min, max));
        }
      }
    }
    return r;
  }

  /** The range of permitted values associated with a label permission. */
  PermissionRange getRange(String permission) {
    return getRange(permission, false);
  }

  /** The range of permitted values associated with a label permission. */
  PermissionRange getRange(String permission, boolean isChangeOwner) {
    if (Permission.hasRange(permission)) {
      return toRange(permission, access(permission, isChangeOwner));
    }
    return null;
  }

  private static class AllowedRange {
    private int allowMin;
    private int allowMax;
    private int blockMin = Integer.MIN_VALUE;
    private int blockMax = Integer.MAX_VALUE;

    void update(PermissionRule rule) {
      if (rule.isBlock()) {
        blockMin = Math.max(blockMin, rule.getMin());
        blockMax = Math.min(blockMax, rule.getMax());
      } else {
        allowMin = Math.min(allowMin, rule.getMin());
        allowMax = Math.max(allowMax, rule.getMax());
      }
    }

    int getAllowMin() {
      return allowMin;
    }

    int getAllowMax() {
      return allowMax;
    }

    int getBlockMin() {
      // ALLOW wins over BLOCK on the same project
      return Math.min(blockMin, allowMin - 1);
    }

    int getBlockMax() {
      // ALLOW wins over BLOCK on the same project
      return Math.max(blockMax, allowMax + 1);
    }
  }

  private PermissionRange toRange(String permissionName, List<PermissionRule> ruleList) {
    Map<ProjectRef, AllowedRange> ranges = new HashMap<>();
    for (PermissionRule rule : ruleList) {
      ProjectRef p = relevant.getRuleProps(rule);
      AllowedRange r = ranges.get(p);
      if (r == null) {
        r = new AllowedRange();
        ranges.put(p, r);
      }
      r.update(rule);
    }
    int allowMin = 0;
    int allowMax = 0;
    int blockMin = Integer.MIN_VALUE;
    int blockMax = Integer.MAX_VALUE;
    for (AllowedRange r : ranges.values()) {
      allowMin = Math.min(allowMin, r.getAllowMin());
      allowMax = Math.max(allowMax, r.getAllowMax());
      blockMin = Math.max(blockMin, r.getBlockMin());
      blockMax = Math.min(blockMax, r.getBlockMax());
    }

    // BLOCK wins over ALLOW across projects
    int min = Math.max(allowMin, blockMin + 1);
    int max = Math.min(allowMax, blockMax - 1);
    return new PermissionRange(permissionName, min, max);
  }

  /** True if the user has this permission. Works only for non labels. */
  boolean canPerform(String permissionName) {
    return canPerform(permissionName, false);
  }

  boolean canPerform(String permissionName, boolean isChangeOwner) {
    return doCanPerform(permissionName, isChangeOwner, false);
  }

  /** True if the user is blocked from using this permission. */
  public boolean isBlocked(String permissionName) {
    return !doCanPerform(permissionName, false, true);
  }

  private boolean doCanPerform(String permissionName, boolean isChangeOwner, boolean blockOnly) {
    List<PermissionRule> access = access(permissionName, isChangeOwner);
    List<PermissionRule> overridden = relevant.getOverridden(permissionName);
    Set<ProjectRef> allows = new HashSet<>();
    Set<ProjectRef> blocks = new HashSet<>();
    for (PermissionRule rule : access) {
      if (rule.isBlock() && !rule.getForce()) {
        blocks.add(relevant.getRuleProps(rule));
      } else {
        allows.add(relevant.getRuleProps(rule));
      }
    }
    for (PermissionRule rule : overridden) {
      blocks.remove(relevant.getRuleProps(rule));
    }
    blocks.removeAll(allows);
    return blocks.isEmpty() && (!allows.isEmpty() || blockOnly);
  }

  /** True if the user has force this permission. Works only for non labels. */
  private boolean canForcePerform(String permissionName) {
    List<PermissionRule> access = access(permissionName);
    List<PermissionRule> overridden = relevant.getOverridden(permissionName);
    Set<ProjectRef> allows = new HashSet<>();
    Set<ProjectRef> blocks = new HashSet<>();
    for (PermissionRule rule : access) {
      if (rule.isBlock()) {
        blocks.add(relevant.getRuleProps(rule));
      } else if (rule.getForce()) {
        allows.add(relevant.getRuleProps(rule));
      }
    }
    for (PermissionRule rule : overridden) {
      if (rule.getForce()) {
        blocks.remove(relevant.getRuleProps(rule));
      }
    }
    blocks.removeAll(allows);
    return blocks.isEmpty() && !allows.isEmpty();
  }

  /** True if for this permission force is blocked for the user. Works only for non labels. */
  private boolean isForceBlocked(String permissionName) {
    List<PermissionRule> access = access(permissionName);
    List<PermissionRule> overridden = relevant.getOverridden(permissionName);
    Set<ProjectRef> allows = new HashSet<>();
    Set<ProjectRef> blocks = new HashSet<>();
    for (PermissionRule rule : access) {
      if (rule.isBlock()) {
        blocks.add(relevant.getRuleProps(rule));
      } else if (rule.getForce()) {
        allows.add(relevant.getRuleProps(rule));
      }
    }
    for (PermissionRule rule : overridden) {
      if (rule.getForce()) {
        blocks.remove(relevant.getRuleProps(rule));
      }
    }
    blocks.removeAll(allows);
    return !blocks.isEmpty();
  }

  /** Rules for the given permission, or the empty list. */
  private List<PermissionRule> access(String permissionName) {
    return access(permissionName, false);
  }

  /** Rules for the given permission, or the empty list. */
  private List<PermissionRule> access(String permissionName, boolean isChangeOwner) {
    List<PermissionRule> rules = effective.get(permissionName);
    if (rules != null) {
      return rules;
    }

    rules = relevant.getPermission(permissionName);

    List<PermissionRule> mine = new ArrayList<>(rules.size());
    for (PermissionRule rule : rules) {
      if (projectControl.match(rule, isChangeOwner)) {
        mine.add(rule);
      }
    }

    if (mine.isEmpty()) {
      mine = Collections.emptyList();
    }
    effective.put(permissionName, mine);
    return mine;
  }

  ForRef asForRef() {
    return new ForRefImpl();
  }

  private class ForRefImpl extends ForRef {
    @Override
    public ForRef user(CurrentUser user) {
      return forUser(user).asForRef().database(db);
    }

    @Override
    public ForChange change(ChangeData cd) {
      try {
        return cd.changeControl().forUser(getUser()).asForChange(cd, db);
      } catch (OrmException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    @Override
    public ForChange change(ChangeNotes notes) {
      Project.NameKey project = getProjectControl().getProject().getNameKey();
      Change change = notes.getChange();
      checkArgument(
          project.equals(change.getProject()),
          "expected change in project %s, not %s",
          project,
          change.getProject());
      return getProjectControl().controlFor(notes).asForChange(null, db);
    }

    @Override
    public void check(RefPermission perm) throws AuthException, PermissionBackendException {
      if (!can(perm)) {
        throw new AuthException(perm.describeForException() + " not permitted");
      }
    }

    @Override
    public Set<RefPermission> test(Collection<RefPermission> permSet)
        throws PermissionBackendException {
      EnumSet<RefPermission> ok = EnumSet.noneOf(RefPermission.class);
      for (RefPermission perm : permSet) {
        if (can(perm)) {
          ok.add(perm);
        }
      }
      return ok;
    }

    private boolean can(RefPermission perm) throws PermissionBackendException {
      switch (perm) {
        case READ:
          return isVisible();
        case CREATE:
          // TODO This isn't an accurate test.
          return canPerform(perm.permissionName().get());
        case DELETE:
          return canDelete();
        case UPDATE:
          return canUpdate();
        case FORCE_UPDATE:
          return canForceUpdate();

        case FORGE_AUTHOR:
          return canForgeAuthor();
        case FORGE_COMMITTER:
          return canForgeCommitter();
        case FORGE_SERVER:
          return canForgeGerritServerIdentity();
        case MERGE:
          return canUploadMerges();

        case CREATE_CHANGE:
          return canUpload();

        case UPDATE_BY_SUBMIT:
          return projectControl.controlForRef("refs/for/" + getRefName()).canSubmit(true);

        case BYPASS_REVIEW:
          return canForgeAuthor()
              && canForgeCommitter()
              && canForgeGerritServerIdentity()
              && canUploadMerges()
              && !projectControl.getProjectState().isUseSignedOffBy();
      }
      throw new PermissionBackendException(perm + " unsupported");
    }
  }
}

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

package com.google.gerrit.server.project;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AbstractAgreement;
import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupAgreement;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ContributorAgreement;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReplicationUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/** Access control management for a user accessing a project's data. */
public class ProjectControl {
  private static final Logger log =
      LoggerFactory.getLogger(ProjectControl.class);

  public static final int VISIBLE = 1 << 0;
  public static final int OWNER = 1 << 1;

  public static class GenericFactory {
    private final ProjectCache projectCache;

    @Inject
    GenericFactory(final ProjectCache pc) {
      projectCache = pc;
    }

    public ProjectControl controlFor(Project.NameKey nameKey, CurrentUser user)
        throws NoSuchProjectException {
      final ProjectState p = projectCache.get(nameKey);
      if (p == null) {
        throw new NoSuchProjectException(nameKey);
      }
      return p.controlFor(user);
    }
  }

  public static class Factory {
    private final Provider<PerRequestProjectControlCache> userCache;

    @Inject
    Factory(Provider<PerRequestProjectControlCache> uc) {
      userCache = uc;
    }

    public ProjectControl controlFor(final Project.NameKey nameKey)
        throws NoSuchProjectException {
      return userCache.get().get(nameKey);
    }

    public ProjectControl validateFor(final Project.NameKey nameKey)
        throws NoSuchProjectException {
      return validateFor(nameKey, VISIBLE);
    }

    public ProjectControl ownerFor(final Project.NameKey nameKey)
        throws NoSuchProjectException {
      return validateFor(nameKey, OWNER);
    }

    public ProjectControl validateFor(final Project.NameKey nameKey,
        final int need) throws NoSuchProjectException {
      final ProjectControl c = controlFor(nameKey);
      if ((need & VISIBLE) == VISIBLE && c.isVisible()) {
        return c;
      }
      if ((need & OWNER) == OWNER && c.isOwner()) {
        return c;
      }
      throw new NoSuchProjectException(nameKey);
    }
  }

  interface AssistedFactory {
    ProjectControl create(CurrentUser who, ProjectState ps);
  }

  private final Set<AccountGroup.UUID> uploadGroups;
  private final Set<AccountGroup.UUID> receiveGroups;

  private final String canonicalWebUrl;
  private final SchemaFactory<ReviewDb> schema;
  private final CurrentUser user;
  private final ProjectState state;
  private final GroupCache groupCache;
  private final PermissionCollection.Factory permissionFilter;

  private List<SectionMatcher> allSections;
  private Map<String, RefControl> refControls;
  private Boolean declaredOwner;

  @Inject
  ProjectControl(@GitUploadPackGroups Set<AccountGroup.UUID> uploadGroups,
      @GitReceivePackGroups Set<AccountGroup.UUID> receiveGroups,
      final SchemaFactory<ReviewDb> schema, final GroupCache groupCache,
      final PermissionCollection.Factory permissionFilter,
      @CanonicalWebUrl @Nullable final String canonicalWebUrl,
      @Assisted CurrentUser who, @Assisted ProjectState ps) {
    this.uploadGroups = uploadGroups;
    this.receiveGroups = receiveGroups;
    this.schema = schema;
    this.groupCache = groupCache;
    this.permissionFilter = permissionFilter;
    this.canonicalWebUrl = canonicalWebUrl;
    user = who;
    state = ps;
  }

  public ProjectControl forUser(CurrentUser who) {
    ProjectControl r = state.controlFor(who);
    // Not per-user, and reusing saves lookup time.
    r.allSections = allSections;
    return r;
  }

  public ChangeControl controlFor(final Change change) {
    return new ChangeControl(controlForRef(change.getDest()), change);
  }

  public RefControl controlForRef(Branch.NameKey ref) {
    return controlForRef(ref.get());
  }

  public RefControl controlForRef(String refName) {
    if (refControls == null) {
      refControls = new HashMap<String, RefControl>();
    }
    RefControl ctl = refControls.get(refName);
    if (ctl == null) {
      PermissionCollection relevant =
          permissionFilter.filter(access(), refName, user.getUserName());
      ctl = new RefControl(this, refName, relevant);
      refControls.put(refName, ctl);
    }
    return ctl;
  }

  public CurrentUser getCurrentUser() {
    return user;
  }

  public ProjectState getProjectState() {
    return state;
  }

  public Project getProject() {
    return state.getProject();
  }

  /** Can this user see this project exists? */
  public boolean isVisible() {
    return visibleForReplication()
        || canPerformOnAnyRef(Permission.READ);
  }

  public boolean canAddRefs() {
    return (canPerformOnAnyRef(Permission.CREATE)
        || isOwnerAnyRef());
  }

  /** Can this user see all the refs in this projects? */
  public boolean allRefsAreVisible() {
    return visibleForReplication()
        || canPerformOnAllRefs(Permission.READ);
  }

  /** Is this project completely visible for replication? */
  boolean visibleForReplication() {
    return user instanceof ReplicationUser
        && ((ReplicationUser) user).isEverythingVisible();
  }

  /** Is this user a project owner? Ownership does not imply {@link #isVisible()} */
  public boolean isOwner() {
    return isDeclaredOwner()
      || user.getCapabilities().canAdministrateServer();
  }

  private boolean isDeclaredOwner() {
    if (declaredOwner == null) {
      declaredOwner = state.isOwner(user.getEffectiveGroups());
    }
    return declaredOwner;
  }

  /** Does this user have ownership on at least one reference name? */
  public boolean isOwnerAnyRef() {
    return canPerformOnAnyRef(Permission.OWNER)
        || user.getCapabilities().canAdministrateServer();
  }

  /** @return true if the user can upload to at least one reference */
  public Capable canPushToAtLeastOneRef() {
    if (! canPerformOnAnyRef(Permission.PUSH) &&
        ! canPerformOnAnyRef(Permission.PUSH_TAG)) {
      String pName = state.getProject().getName();
      return new Capable("Upload denied for project '" + pName + "'");
    }
    Project project = state.getProject();
    if (project.isUseContributorAgreements()) {
      try {
        return verifyActiveContributorAgreement();
      } catch (OrmException e) {
        log.error("Cannot query database for agreements", e);
        return new Capable("Cannot verify contribution agreement");
      }
    }
    return Capable.OK;
  }

  private Capable verifyActiveContributorAgreement() throws OrmException {
    if (! (user instanceof IdentifiedUser)) {
      return new Capable("Must be logged in to verify Contributor Agreement");
    }
    final IdentifiedUser iUser = (IdentifiedUser) user;
    final ReviewDb db = schema.open();

    AbstractAgreement bestAgreement = null;
    ContributorAgreement bestCla = null;
    try {

      OUTER: for (AccountGroup.UUID groupUUID : iUser.getEffectiveGroups()) {
        AccountGroup group = groupCache.get(groupUUID);
        if (group == null) {
          continue;
        }

        final List<AccountGroupAgreement> temp =
            db.accountGroupAgreements().byGroup(group.getId()).toList();

        Collections.reverse(temp);

        for (final AccountGroupAgreement a : temp) {
          final ContributorAgreement cla =
              db.contributorAgreements().get(a.getAgreementId());
          if (cla == null) {
            continue;
          }

          bestAgreement = a;
          bestCla = cla;
          break OUTER;
        }
      }

      if (bestAgreement == null) {
        final List<AccountAgreement> temp =
            db.accountAgreements().byAccount(iUser.getAccountId()).toList();

        Collections.reverse(temp);

        for (final AccountAgreement a : temp) {
          final ContributorAgreement cla =
              db.contributorAgreements().get(a.getAgreementId());
          if (cla == null) {
            continue;
          }

          bestAgreement = a;
          bestCla = cla;
          break;
        }
      }
    } finally {
      db.close();
    }


    if (bestCla != null && !bestCla.isActive()) {
      final StringBuilder msg = new StringBuilder();
      msg.append(bestCla.getShortName());
      msg.append(" contributor agreement is expired.\n");
      if (canonicalWebUrl != null) {
        msg.append("\nPlease complete a new agreement");
        msg.append(":\n\n  ");
        msg.append(canonicalWebUrl);
        msg.append("#");
        msg.append(PageLinks.SETTINGS_AGREEMENTS);
        msg.append("\n");
      }
      msg.append("\n");
      return new Capable(msg.toString());
    }

    if (bestCla != null && bestCla.isRequireContactInformation()) {
      boolean fail = false;
      fail |= missing(iUser.getAccount().getFullName());
      fail |= missing(iUser.getAccount().getPreferredEmail());
      fail |= !iUser.getAccount().isContactFiled();

      if (fail) {
        final StringBuilder msg = new StringBuilder();
        msg.append(bestCla.getShortName());
        msg.append(" contributor agreement requires");
        msg.append(" current contact information.\n");
        if (canonicalWebUrl != null) {
          msg.append("\nPlease review your contact information");
          msg.append(":\n\n  ");
          msg.append(canonicalWebUrl);
          msg.append("#");
          msg.append(PageLinks.SETTINGS_CONTACT);
          msg.append("\n");
        }
        msg.append("\n");
        return new Capable(msg.toString());
      }
    }

    if (bestAgreement != null) {
      switch (bestAgreement.getStatus()) {
        case VERIFIED:
          return Capable.OK;
        case REJECTED:
          return new Capable(bestCla.getShortName()
              + " contributor agreement was rejected."
              + "\n       (rejected on " + bestAgreement.getReviewedOn()
              + ")\n");
        case NEW:
          return new Capable(bestCla.getShortName()
              + " contributor agreement is still pending review.\n");
      }
    }

    final StringBuilder msg = new StringBuilder();
    msg.append(" A Contributor Agreement must be completed before uploading");
    if (canonicalWebUrl != null) {
      msg.append(":\n\n  ");
      msg.append(canonicalWebUrl);
      msg.append("#");
      msg.append(PageLinks.SETTINGS_AGREEMENTS);
      msg.append("\n");
    } else {
      msg.append(".");
    }
    msg.append("\n");
    return new Capable(msg.toString());
  }

  private static boolean missing(final String value) {
    return value == null || value.trim().equals("");
  }

  private boolean canPerformOnAnyRef(String permissionName) {
    for (SectionMatcher matcher : access()) {
      AccessSection section = matcher.section;
      Permission permission = section.getPermission(permissionName);
      if (permission == null) {
        continue;
      }

      for (PermissionRule rule : permission.getRules()) {
        if (rule.isBlock() || rule.isDeny() || !match(rule)) {
          continue;
        }

        // Being in a group that was granted this permission is only an
        // approximation.  There might be overrides and doNotInherit
        // that would render this to be false.
        //
        if (controlForRef(section.getName()).canPerform(permissionName)) {
          return true;
        } else {
          break;
        }
      }
    }

    return false;
  }

  private boolean canPerformOnAllRefs(String permission) {
    boolean canPerform = false;
    Set<String> patterns = allRefPatterns(permission);
    if (patterns.contains(AccessSection.ALL)) {
      // Only possible if granted on the pattern that
      // matches every possible reference.  Check all
      // patterns also have the permission.
      //
      for (final String pattern : patterns) {
        if (controlForRef(pattern).canPerform(permission)) {
          canPerform = true;
        } else {
          return false;
        }
      }
    }
    return canPerform;
  }

  private Set<String> allRefPatterns(String permissionName) {
    Set<String> all = new HashSet<String>();
    for (SectionMatcher matcher : access()) {
      AccessSection section = matcher.section;
      Permission permission = section.getPermission(permissionName);
      if (permission != null) {
        all.add(section.getName());
      }
    }
    return all;
  }

  private List<SectionMatcher> access() {
    if (allSections == null) {
      allSections = state.getAllSections();
    }
    return allSections;
  }

  boolean match(PermissionRule rule) {
    return match(rule.getGroup().getUUID());
  }

  boolean match(AccountGroup.UUID uuid) {
    if (AccountGroup.PROJECT_OWNERS.equals(uuid)) {
      return isDeclaredOwner();
    } else {
      return user.getEffectiveGroups().contains(uuid);
    }
  }

  public boolean canRunUploadPack() {
    for (AccountGroup.UUID group : uploadGroups) {
      if (match(group)) {
        return true;
      }
    }
    return false;
  }

  public boolean canRunReceivePack() {
    for (AccountGroup.UUID group : receiveGroups) {
      if (match(group)) {
        return true;
      }
    }
    return false;
  }

  public boolean canCreateChildProject() {
    return user.getCapabilityByProject(state.getProject().getNameKey())
        .canCreateProject();
  }
}

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

import com.google.common.collect.Lists;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/** Access control management for a user accessing a project's data. */
public class ProjectControl {
  public static final int VISIBLE = 1 << 0;
  public static final int OWNER = 1 << 1;

  public static class GenericFactory {
    private final ProjectCache projectCache;

    @Inject
    GenericFactory(final ProjectCache pc) {
      projectCache = pc;
    }

    public ProjectControl controlFor(Project.NameKey nameKey, CurrentUser user)
        throws NoSuchProjectException, IOException {
      final ProjectState p = projectCache.checkedGet(nameKey);
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
  private final CurrentUser user;
  private final ProjectState state;
  private final PermissionCollection.Factory permissionFilter;
  private final Collection<ContributorAgreement> contributorAgreements;

  private List<SectionMatcher> allSections;
  private List<SectionMatcher> localSections;
  private LabelTypes labelTypes;
  private Map<String, RefControl> refControls;
  private Boolean declaredOwner;

  @Inject
  ProjectControl(@GitUploadPackGroups Set<AccountGroup.UUID> uploadGroups,
      @GitReceivePackGroups Set<AccountGroup.UUID> receiveGroups,
      final ProjectCache pc, final PermissionCollection.Factory permissionFilter,
      @CanonicalWebUrl @Nullable final String canonicalWebUrl,
      @Assisted CurrentUser who, @Assisted ProjectState ps) {
    this.uploadGroups = uploadGroups;
    this.receiveGroups = receiveGroups;
    this.permissionFilter = permissionFilter;
    this.contributorAgreements = pc.getAllProjects().getConfig().getContributorAgreements();
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

  public LabelTypes getLabelTypes() {
    if (labelTypes == null) {
      labelTypes = state.getLabelTypes();
    }
    return labelTypes;
  }

  private boolean isHidden() {
    return getProject().getState().equals(Project.State.HIDDEN);
  }

  /** Can this user see this project exists? */
  public boolean isVisible() {
    return (user instanceof InternalUser
        || canPerformOnAnyRef(Permission.READ)) && !isHidden();
  }

  public boolean canAddRefs() {
    return (canPerformOnAnyRef(Permission.CREATE)
        || isOwnerAnyRef());
  }

  /** Can this user see all the refs in this projects? */
  public boolean allRefsAreVisible() {
    return allRefsAreVisibleExcept(Collections.<String> emptySet());
  }

  public boolean allRefsAreVisibleExcept(Set<String> except) {
    return user instanceof InternalUser
        || canPerformOnAllRefs(Permission.READ, except);
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
    if (state.isUseContributorAgreements()) {
      return verifyActiveContributorAgreement();
    }
    return Capable.OK;
  }

  public Set<GroupReference> getAllGroups() {
    return getGroups(access());
  }

  public Set<GroupReference> getLocalGroups() {
    return getGroups(localAccess());
  }

  private static Set<GroupReference> getGroups(
      final List<SectionMatcher> sectionMatcherList) {
    final Set<GroupReference> all = new HashSet<GroupReference>();
    for (final SectionMatcher matcher : sectionMatcherList) {
      final AccessSection section = matcher.section;
      for (final Permission permission : section.getPermissions()) {
        for (final PermissionRule rule : permission.getRules()) {
          all.add(rule.getGroup());
        }
      }
    }
    return all;
  }

  private Capable verifyActiveContributorAgreement() {
    if (! (user instanceof IdentifiedUser)) {
      return new Capable("Must be logged in to verify Contributor Agreement");
    }
    final IdentifiedUser iUser = (IdentifiedUser) user;

    boolean hasContactInfo = !missing(iUser.getAccount().getFullName())
        && !missing(iUser.getAccount().getPreferredEmail())
        && iUser.getAccount().isContactFiled();

    List<AccountGroup.UUID> okGroupIds = Lists.newArrayList();
    List<AccountGroup.UUID> missingInfoGroupIds = Lists.newArrayList();
    for (ContributorAgreement ca : contributorAgreements) {
      List<AccountGroup.UUID> groupIds;
      if (hasContactInfo || !ca.isRequireContactInformation()) {
        groupIds = okGroupIds;
      } else {
        groupIds = missingInfoGroupIds;
      }

      for (PermissionRule rule : ca.getAccepted()) {
        if ((rule.getAction() == Action.ALLOW) && (rule.getGroup() != null)
            && (rule.getGroup().getUUID() != null)) {
          groupIds.add(new AccountGroup.UUID(rule.getGroup().getUUID().get()));
        }
      }
    }

    if (iUser.getEffectiveGroups().containsAnyOf(okGroupIds)) {
      return Capable.OK;
    }

    if (iUser.getEffectiveGroups().containsAnyOf(missingInfoGroupIds)) {
      final StringBuilder msg = new StringBuilder();
      for (ContributorAgreement ca : contributorAgreements) {
        if (ca.isRequireContactInformation()) {
          msg.append(ca.getName());
          break;
        }
      }
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

  private boolean canPerformOnAllRefs(String permission, Set<String> except) {
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
        } else if (except.contains(pattern)) {
          continue;
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

  private List<SectionMatcher> localAccess() {
    if (localSections == null) {
      localSections = state.getLocalAccessSections();
    }
    return localSections;
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
}

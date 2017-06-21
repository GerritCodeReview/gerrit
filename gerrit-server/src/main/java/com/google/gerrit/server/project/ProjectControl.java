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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.change.IncludedInResolver;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.FailedPermissionBackend;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
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
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Access control management for a user accessing a project's data. */
public class ProjectControl {
  private static final Logger log = LoggerFactory.getLogger(ProjectControl.class);

  public static class GenericFactory {
    private final ProjectCache projectCache;

    @Inject
    GenericFactory(ProjectCache pc) {
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

    public ProjectControl controlFor(Project.NameKey nameKey) throws NoSuchProjectException {
      return userCache.get().get(nameKey);
    }
  }

  public interface AssistedFactory {
    ProjectControl create(CurrentUser who, ProjectState ps);
  }

  @Singleton
  protected static class Metrics {
    final Counter0 claCheckCount;

    @Inject
    Metrics(MetricMaker metricMaker) {
      claCheckCount =
          metricMaker.newCounter(
              "license/cla_check_count",
              new Description("Total number of CLA check requests").setRate().setUnit("requests"));
    }
  }

  private final Set<AccountGroup.UUID> uploadGroups;
  private final Set<AccountGroup.UUID> receiveGroups;

  private final String canonicalWebUrl;
  private final PermissionBackend.WithUser perm;
  private final CurrentUser user;
  private final ProjectState state;
  private final ChangeControl.Factory changeControlFactory;
  private final PermissionCollection.Factory permissionFilter;
  private final VisibleRefFilter.Factory refFilter;
  private final Collection<ContributorAgreement> contributorAgreements;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Metrics metrics;

  private List<SectionMatcher> allSections;
  private List<SectionMatcher> localSections;
  private LabelTypes labelTypes;
  private Map<String, RefControl> refControls;
  private Boolean declaredOwner;

  @Inject
  ProjectControl(
      @GitUploadPackGroups Set<AccountGroup.UUID> uploadGroups,
      @GitReceivePackGroups Set<AccountGroup.UUID> receiveGroups,
      ProjectCache pc,
      PermissionCollection.Factory permissionFilter,
      ChangeControl.Factory changeControlFactory,
      VisibleRefFilter.Factory refFilter,
      Provider<InternalChangeQuery> queryProvider,
      @CanonicalWebUrl @Nullable String canonicalWebUrl,
      PermissionBackend permissionBackend,
      @Assisted CurrentUser who,
      @Assisted ProjectState ps,
      Metrics metrics) {
    this.changeControlFactory = changeControlFactory;
    this.refFilter = refFilter;
    this.uploadGroups = uploadGroups;
    this.receiveGroups = receiveGroups;
    this.permissionFilter = permissionFilter;
    this.contributorAgreements = pc.getAllProjects().getConfig().getContributorAgreements();
    this.canonicalWebUrl = canonicalWebUrl;
    this.queryProvider = queryProvider;
    this.metrics = metrics;
    this.perm = permissionBackend.user(who);
    user = who;
    state = ps;
  }

  public ProjectControl forUser(CurrentUser who) {
    ProjectControl r = state.controlFor(who);
    // Not per-user, and reusing saves lookup time.
    r.allSections = allSections;
    return r;
  }

  public ChangeControl controlFor(ReviewDb db, Change change) throws OrmException {
    return changeControlFactory.create(
        controlForRef(change.getDest()), db, change.getProject(), change.getId());
  }

  /**
   * Create a change control for a change that was loaded from index. This method should only be
   * used when database access is harmful and potentially stale data from the index is acceptable.
   *
   * @param change change loaded from secondary index
   * @return change control
   */
  public ChangeControl controlForIndexedChange(Change change) {
    return changeControlFactory.createForIndexedChange(controlForRef(change.getDest()), change);
  }

  public ChangeControl controlFor(ChangeNotes notes) {
    return changeControlFactory.create(controlForRef(notes.getChange().getDest()), notes);
  }

  public RefControl controlForRef(Branch.NameKey ref) {
    return controlForRef(ref.get());
  }

  public RefControl controlForRef(String refName) {
    if (refControls == null) {
      refControls = new HashMap<>();
    }
    RefControl ctl = refControls.get(refName);
    if (ctl == null) {
      PermissionCollection relevant = permissionFilter.filter(access(), refName, user);
      ctl = new RefControl(this, refName, relevant);
      refControls.put(refName, ctl);
    }
    return ctl;
  }

  public CurrentUser getUser() {
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

  /** Returns whether the project is hidden. */
  private boolean isHidden() {
    return getProject().getState().equals(com.google.gerrit.extensions.client.ProjectState.HIDDEN);
  }

  private boolean canAddRefs() {
    return (canPerformOnAnyRef(Permission.CREATE) || isOwnerAnyRef());
  }

  private boolean canCreateChanges() {
    for (SectionMatcher matcher : access()) {
      AccessSection section = matcher.section;
      if (section.getName().startsWith("refs/for/")) {
        Permission permission = section.getPermission(Permission.PUSH);
        if (permission != null && controlForRef(section.getName()).canPerform(Permission.PUSH)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean allRefsAreVisible(Set<String> ignore) {
    return user.isInternalUser() || canPerformOnAllRefs(Permission.READ, ignore);
  }

  /** Is this user a project owner? */
  public boolean isOwner() {
    return (isDeclaredOwner() && !controlForRef("refs/*").isBlocked(Permission.OWNER)) || isAdmin();
  }

  boolean isAdmin() {
    try {
      perm.check(GlobalPermission.ADMINISTRATE_SERVER);
      return true;
    } catch (AuthException | PermissionBackendException e) {
      return false;
    }
  }

  private boolean isDeclaredOwner() {
    if (declaredOwner == null) {
      GroupMembership effectiveGroups = user.getEffectiveGroups();
      declaredOwner = effectiveGroups.containsAnyOf(state.getAllOwners());
    }
    return declaredOwner;
  }

  /** Does this user have ownership on at least one reference name? */
  public boolean isOwnerAnyRef() {
    return canPerformOnAnyRef(Permission.OWNER) || isAdmin();
  }

  /** @return true if the user can upload to at least one reference */
  public Capable canPushToAtLeastOneRef() {
    if (!canPerformOnAnyRef(Permission.PUSH) && !canPerformOnAnyRef(Permission.CREATE_TAG)) {
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

  private static Set<GroupReference> getGroups(List<SectionMatcher> sectionMatcherList) {
    final Set<GroupReference> all = new HashSet<>();
    for (SectionMatcher matcher : sectionMatcherList) {
      final AccessSection section = matcher.section;
      for (Permission permission : section.getPermissions()) {
        for (PermissionRule rule : permission.getRules()) {
          all.add(rule.getGroup());
        }
      }
    }
    return all;
  }

  private Capable verifyActiveContributorAgreement() {
    metrics.claCheckCount.increment();
    if (!(user.isIdentifiedUser())) {
      return new Capable("Must be logged in to verify Contributor Agreement");
    }
    final IdentifiedUser iUser = user.asIdentifiedUser();

    List<AccountGroup.UUID> okGroupIds = new ArrayList<>();
    for (ContributorAgreement ca : contributorAgreements) {
      List<AccountGroup.UUID> groupIds;
      groupIds = okGroupIds;

      for (PermissionRule rule : ca.getAccepted()) {
        if ((rule.getAction() == Action.ALLOW)
            && (rule.getGroup() != null)
            && (rule.getGroup().getUUID() != null)) {
          groupIds.add(new AccountGroup.UUID(rule.getGroup().getUUID().get()));
        }
      }
    }

    if (iUser.getEffectiveGroups().containsAnyOf(okGroupIds)) {
      return Capable.OK;
    }

    final StringBuilder msg = new StringBuilder();
    msg.append("A Contributor Agreement must be completed before uploading");
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
        }
        break;
      }
    }

    return false;
  }

  private boolean canPerformOnAllRefs(String permission, Set<String> ignore) {
    boolean canPerform = false;
    Set<String> patterns = allRefPatterns(permission);
    if (patterns.contains(AccessSection.ALL)) {
      // Only possible if granted on the pattern that
      // matches every possible reference.  Check all
      // patterns also have the permission.
      //
      for (String pattern : patterns) {
        if (controlForRef(pattern).canPerform(permission)) {
          canPerform = true;
        } else if (ignore.contains(pattern)) {
          continue;
        } else {
          return false;
        }
      }
    }
    return canPerform;
  }

  private Set<String> allRefPatterns(String permissionName) {
    Set<String> all = new HashSet<>();
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

  boolean match(PermissionRule rule, boolean isChangeOwner) {
    return match(rule.getGroup().getUUID(), isChangeOwner);
  }

  boolean match(AccountGroup.UUID uuid) {
    return match(uuid, false);
  }

  boolean match(AccountGroup.UUID uuid, boolean isChangeOwner) {
    if (SystemGroupBackend.PROJECT_OWNERS.equals(uuid)) {
      return isDeclaredOwner();
    } else if (SystemGroupBackend.CHANGE_OWNER.equals(uuid)) {
      return isChangeOwner;
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

  /** @return whether a commit is visible to user. */
  public boolean canReadCommit(ReviewDb db, Repository repo, RevCommit commit) {
    // Look for changes associated with the commit.
    try {
      List<ChangeData> changes =
          queryProvider.get().byProjectCommit(getProject().getNameKey(), commit);
      for (ChangeData change : changes) {
        if (controlFor(db, change.change()).isVisible(db)) {
          return true;
        }
      }
    } catch (OrmException e) {
      log.error(
          "Cannot look up change for commit " + commit.name() + " in " + getProject().getName(), e);
    }
    // Scan all visible refs.
    return canReadCommitFromVisibleRef(repo, commit);
  }

  private boolean canReadCommitFromVisibleRef(Repository repo, RevCommit commit) {
    try (RevWalk rw = new RevWalk(repo)) {
      return isMergedIntoVisibleRef(repo, rw, commit, repo.getAllRefs().values());
    } catch (IOException e) {
      String msg =
          String.format(
              "Cannot verify permissions to commit object %s in repository %s",
              commit.name(), getProject().getNameKey());
      log.error(msg, e);
      return false;
    }
  }

  boolean isMergedIntoVisibleRef(
      Repository repo, RevWalk rw, RevCommit commit, Collection<Ref> unfilteredRefs)
      throws IOException {
    VisibleRefFilter filter = refFilter.create(state, repo);
    Map<String, Ref> m = Maps.newHashMapWithExpectedSize(unfilteredRefs.size());
    for (Ref r : unfilteredRefs) {
      m.put(r.getName(), r);
    }
    Map<String, Ref> refs = filter.filter(m, true);
    return !refs.isEmpty() && IncludedInResolver.includedInOne(repo, rw, commit, refs.values());
  }

  ForProject asForProject() {
    return new ForProjectImpl();
  }

  private class ForProjectImpl extends ForProject {
    @Override
    public ForProject user(CurrentUser user) {
      return forUser(user).asForProject().database(db);
    }

    @Override
    public ForRef ref(String ref) {
      return controlForRef(ref).asForRef().database(db);
    }

    @Override
    public ForChange change(ChangeData cd) {
      try {
        checkProject(cd.change());
        return super.change(cd);
      } catch (OrmException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    @Override
    public ForChange change(ChangeNotes notes) {
      checkProject(notes.getChange());
      return super.change(notes);
    }

    private void checkProject(Change change) {
      Project.NameKey project = getProject().getNameKey();
      checkArgument(
          project.equals(change.getProject()),
          "expected change in project %s, not %s",
          project,
          change.getProject());
    }

    @Override
    public void check(ProjectPermission perm) throws AuthException, PermissionBackendException {
      if (!can(perm)) {
        throw new AuthException(perm.describeForException() + " not permitted");
      }
    }

    @Override
    public Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException {
      EnumSet<ProjectPermission> ok = EnumSet.noneOf(ProjectPermission.class);
      for (ProjectPermission perm : permSet) {
        if (can(perm)) {
          ok.add(perm);
        }
      }
      return ok;
    }

    private boolean can(ProjectPermission perm) throws PermissionBackendException {
      switch (perm) {
        case ACCESS:
          return (!isHidden() && (user.isInternalUser() || canPerformOnAnyRef(Permission.READ)))
              || isOwner();

        case READ:
          return !isHidden() && allRefsAreVisible(Collections.emptySet());

        case CREATE_REF:
          return canAddRefs();
        case CREATE_CHANGE:
          return canCreateChanges();
      }
      throw new PermissionBackendException(perm + " unsupported");
    }
  }
}

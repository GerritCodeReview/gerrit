// Copyright (C) 2008 The Android Open Source Project
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

import static com.google.gerrit.server.project.RefControl.isRE;
import static com.google.gerrit.server.project.RefControl.shortestExample;
import static com.google.gerrit.server.project.RefControl.toRegExp;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cached information on a project. */
public class ProjectState {
  private static final String USERNAME_PARAMETER = "${username}";

  private static final String USERNAME_RE = Account.USER_NAME_PATTERN
      .substring(1, Account.USER_NAME_PATTERN.length() - 1);

  private static final Pattern USERNAME_PATTERN =
      Pattern.compile("^" + USERNAME_RE);

  public interface Factory {
    ProjectState create(ProjectConfig config);
  }

  private final boolean isAllProjects;
  private final ProjectCache projectCache;
  private final ProjectControl.AssistedFactory projectControlFactory;
  private final PrologEnvironment.Factory envFactory;
  private final GitRepositoryManager gitMgr;
  private final RulesCache rulesCache;

  private final ProjectConfig config;
  private final Set<AccountGroup.UUID> localOwners;

  /** Prolog rule state. */
  private transient PrologMachineCopy rulesMachine;

  /** Last system time the configuration's revision was examined. */
  private transient long lastCheckTime;

  @Inject
  protected ProjectState(
      final ProjectCache projectCache,
      final AllProjectsName allProjectsName,
      final ProjectControl.AssistedFactory projectControlFactory,
      final PrologEnvironment.Factory envFactory,
      final GitRepositoryManager gitMgr,
      final RulesCache rulesCache,
      @Assisted final ProjectConfig config) {
    this.projectCache = projectCache;
    this.isAllProjects = config.getProject().getNameKey().equals(allProjectsName);
    this.projectControlFactory = projectControlFactory;
    this.envFactory = envFactory;
    this.gitMgr = gitMgr;
    this.rulesCache = rulesCache;
    this.config = config;

    HashSet<AccountGroup.UUID> groups = new HashSet<AccountGroup.UUID>();
    AccessSection all = config.getAccessSection(AccessSection.ALL);
    if (all != null) {
      Permission owner = all.getPermission(Permission.OWNER);
      if (owner != null) {
        for (PermissionRule rule : owner.getRules()) {
          GroupReference ref = rule.getGroup();
          if (ref.getUUID() != null) {
            groups.add(ref.getUUID());
          }
        }
      }
    }
    localOwners = Collections.unmodifiableSet(groups);
  }

  boolean needsRefresh(long generation) {
    if (generation <= 0) {
      return isRevisionOutOfDate();
    }
    if (lastCheckTime != generation) {
      lastCheckTime = generation;
      return isRevisionOutOfDate();
    }
    return false;
  }

  private boolean isRevisionOutOfDate() {
    try {
      Repository git = gitMgr.openRepository(getProject().getNameKey());
      try {
        Ref ref = git.getRef(GitRepositoryManager.REF_CONFIG);
        if (ref == null || ref.getObjectId() == null) {
          return true;
        }
        return !ref.getObjectId().equals(config.getRevision());
      } finally {
        git.close();
      }
    } catch (IOException gone) {
      return true;
    }
  }

  /** @return Construct a new PrologEnvironment for the calling thread. */
  public PrologEnvironment newPrologEnvironment() throws CompileException {
    PrologMachineCopy pmc = rulesMachine;
    if (pmc == null) {
      pmc = rulesCache.loadMachine(
          getProject().getNameKey(),
          getConfig().getRulesId());
      rulesMachine = pmc;
    }
    return envFactory.create(pmc);
  }

  public Project getProject() {
    return getConfig().getProject();
  }

  public ProjectConfig getConfig() {
    return config;
  }

  /** Get the rights that pertain only to this project. */
  public Collection<AccessSection> getLocalAccessSections() {
    return getConfig().getAccessSections();
  }

  /** Get the rights this project inherits. */
  public Collection<AccessSection> getInheritedAccessSections() {
    if (isAllProjects) {
      return Collections.emptyList();
    }

    List<AccessSection> inherited = new ArrayList<AccessSection>();
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    Project.NameKey parent = getProject().getParent();

    while (parent != null && seen.add(parent)) {
      ProjectState s = projectCache.get(parent);
      if (s != null) {
        inherited.addAll(s.getLocalAccessSections());
        parent = s.getProject().getParent();
      } else {
        break;
      }
    }

    // The root of the tree is the special "All-Projects" case.
    if (parent == null) {
      inherited.addAll(projectCache.getAllProjects().getLocalAccessSections());
    }

    return inherited;
  }

  /** Get both local and inherited access sections. */
  public Collection<AccessSection> getAllAccessSections() {
    List<AccessSection> all = new ArrayList<AccessSection>();
    all.addAll(getLocalAccessSections());
    all.addAll(getInheritedAccessSections());
    return all;
  }

  /**
   * @return all {@link AccountGroup}'s to which the owner privilege for
   *         'refs/*' is assigned for this project (the local owners), if there
   *         are no local owners the local owners of the nearest parent project
   *         that has local owners are returned
   */
  public Set<AccountGroup.UUID> getOwners() {
    Project.NameKey parentName = getProject().getParent();
    if (!localOwners.isEmpty() || parentName == null || isAllProjects) {
      return localOwners;
    }

    ProjectState parent = projectCache.get(parentName);
    if (parent != null) {
      return parent.getOwners();
    }

    return Collections.emptySet();
  }

  /**
   * @return all {@link AccountGroup}'s that are allowed to administrate the
   *         complete project. This includes all groups to which the owner
   *         privilege for 'refs/*' is assigned for this project (the local
   *         owners) and all groups to which the owner privilege for 'refs/*' is
   *         assigned for one of the parent projects (the inherited owners).
   */
  public Set<AccountGroup.UUID> getAllOwners() {
    HashSet<AccountGroup.UUID> owners = new HashSet<AccountGroup.UUID>();
    owners.addAll(localOwners);

    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    Project.NameKey parent = getProject().getParent();

    while (parent != null && seen.add(parent)) {
      ProjectState s = projectCache.get(parent);
      if (s != null) {
        owners.addAll(s.localOwners);
        parent = s.getProject().getParent();
      } else {
        break;
      }
    }

    return Collections.unmodifiableSet(owners);
  }

  /**
   * Get all permissions that apply to a reference.
   *
   * @param ref reference being accessed.
   * @return map of permissions that apply to this reference, keyed by
   *         permission name.
   */
  public Map<String, List<PermissionRule>> getPermissions(String ref) {
    return getPermissions(ref, false, null /* ignore user */);
  }

  /**
   * Get all permissions that apply to a reference.
   *
   * @param ref reference being accessed.
   * @param includeUsername if true sections that contain the "${username}"
   *        placeholder will only be included in the result if {@code username}
   *        matches at that position of {@code ref}.
   * @param username identity of the calling user. If a section reference uses
   *        the placeholder "${username}" syntax permissions in that section
   *        will only be applied if the {@code ref} contains {@code username} at
   *        this position.
   * @return map of permissions that apply to this reference, keyed by
   *         permission name.
   */
  public Map<String, List<PermissionRule>> getPermissions(
      String ref, boolean includeUsername, String username) {
    if (isRE(ref)) {
      ref = RefControl.shortestExample(ref);
    } else if (ref.endsWith("/*")) {
      ref = ref.substring(0, ref.length() - 1);
    }

    List<AccessSection> sections = new ArrayList<AccessSection>();
    for (AccessSection section : getAllAccessSections()) {
      if (appliesToRef(section, ref, includeUsername, username)) {
        sections.add(section);
      }
    }
    Collections.sort(sections, new MostSpecificComparator(ref));

    Set<SeenRule> seen = new HashSet<SeenRule>();
    Set<String> exclusiveGroupPermissions = new HashSet<String>();

    HashMap<String, List<PermissionRule>> permissions =
        new HashMap<String, List<PermissionRule>>();
    for (AccessSection section : sections) {
      for (Permission permission : section.getPermissions()) {
        if (exclusiveGroupPermissions.contains(permission.getName())) {
          continue;
        }

        for (PermissionRule rule : permission.getRules()) {
          SeenRule s = new SeenRule(section, permission, rule);
          if (seen.add(s) && !rule.getDeny()) {
            List<PermissionRule> r = permissions.get(permission.getName());
            if (r == null) {
              r = new ArrayList<PermissionRule>(2);
              permissions.put(permission.getName(), r);
            }
            r.add(rule);
          }
        }

        if (permission.getExclusiveGroup()) {
          exclusiveGroupPermissions.add(permission.getName());
        }
      }
    }

    return Collections.unmodifiableMap(permissions);
  }

  public ProjectControl controlFor(final CurrentUser user) {
    return projectControlFactory.create(user, this);
  }

  private static boolean appliesToRef(AccessSection section,
      String ref, boolean includeUsername, String username) {
    String pattern = section.getName();
    int uPos = pattern.indexOf(USERNAME_PARAMETER);

    if (0 <= uPos) {
      String prefix = pattern.substring(0, uPos);
      String suffix = pattern.substring(uPos + USERNAME_PARAMETER.length());

      if (includeUsername) {
        if (username == null || username.isEmpty()) {
          return false;
        } else if (isRE(pattern)) {
          String re = prefix + username.replace(".","\\.") + suffix;
          return Pattern.matches(re, ref);
        } else {
          String p = prefix + username + suffix;
          if (p.endsWith("/*")) {
            return ref.startsWith(p.substring(0, p.length() - 1));
          } else {
            return ref.equals(p);
          }
        }
      }

      if (isRE(pattern)) {
        String re = prefix + USERNAME_RE + suffix;
        return Pattern.matches(re, ref);

      } else {
        if (!ref.startsWith(prefix)) {
          return false;
        }

        String tail = ref.substring(uPos);
        Matcher uMatcher = USERNAME_PATTERN.matcher(tail);
        if (!uMatcher.find() || uMatcher.start() != 0) {
          return false;
        }

        tail = tail.substring(uMatcher.end());
        if (suffix.endsWith("/*")) {
          return tail.startsWith(suffix.substring(0, suffix.length() -1));
        } else {
          return tail.equals(suffix);
        }
      }

    } else if (isRE(pattern)) {
      return Pattern.matches(pattern, ref);

    } else if (pattern.endsWith("/*")) {
      String prefix = pattern.substring(0, pattern.length() - 1);
      return ref.startsWith(prefix);

    } else {
      return ref.equals(pattern);
    }
  }

  /** Tracks whether or not a permission has been overridden. */
  private static class SeenRule {
    final String refPattern;
    final String permissionName;
    final AccountGroup.UUID group;

    SeenRule(AccessSection section, Permission permission, PermissionRule rule) {
      refPattern = section.getName();
      permissionName = permission.getName();
      group = rule.getGroup().getUUID();
    }

    @Override
    public int hashCode() {
      int hc = refPattern.hashCode();
      hc = hc * 31 + permissionName.hashCode();
      if (group != null) {
        hc = hc * 31 + group.hashCode();
      }
      return hc;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof SeenRule) {
        SeenRule a = this;
        SeenRule b = (SeenRule) other;
        return a.refPattern.equals(b.refPattern) //
            && a.permissionName.equals(b.permissionName) //
            && eq(a.group, b.group);
      }
      return false;
    }

    private boolean eq(AccountGroup.UUID a, AccountGroup.UUID b) {
      return a != null && b != null && a.equals(b);
    }
  }

  /**
   * Order the Ref Pattern by the most specific. This sort is done by:
   * <ul>
   * <li>1 - The minor value of Levenshtein string distance between the branch
   * name and the regex string shortest example. A shorter distance is a more
   * specific match.
   * <li>2 - Finites first, infinities after.
   * <li>3 - Number of transitions.
   * <li>4 - Length of the expression text.
   * </ul>
   *
   * Levenshtein distance is a measure of the similarity between two strings.
   * The distance is the number of deletions, insertions, or substitutions
   * required to transform one string into another.
   *
   * For example, if given refs/heads/m* and refs/heads/*, the distances are 5
   * and 6. It means that refs/heads/m* is more specific because it's closer to
   * refs/heads/master than refs/heads/*.
   *
   * Another example could be refs/heads/* and refs/heads/[a-zA-Z]*, the
   * distances are both 6. Both are infinite, but refs/heads/[a-zA-Z]* has more
   * transitions, which after all turns it more specific.
   */
  private static final class MostSpecificComparator implements
      Comparator<AccessSection> {
    private final String refName;

    MostSpecificComparator(String refName) {
      this.refName = refName;
    }

    public int compare(AccessSection a, AccessSection b) {
      return compare(a.getName(), b.getName());
    }

    private int compare(final String pattern1, final String pattern2) {
      int cmp = distance(pattern1) - distance(pattern2);
      if (cmp == 0) {
        boolean p1_finite = finite(pattern1);
        boolean p2_finite = finite(pattern2);

        if (p1_finite && !p2_finite) {
          cmp = -1;
        } else if (!p1_finite && p2_finite) {
          cmp = 1;
        } else /* if (f1 == f2) */{
          cmp = 0;
        }
      }
      if (cmp == 0) {
        cmp = transitions(pattern1) - transitions(pattern2);
      }
      if (cmp == 0) {
        cmp = pattern2.length() - pattern1.length();
      }
      return cmp;
    }

    private int distance(String pattern) {
      String example;
      if (isRE(pattern)) {
        example = shortestExample(pattern);

      } else if (pattern.endsWith("/*")) {
        example = pattern.substring(0, pattern.length() - 1) + '1';

      } else if (pattern.equals(refName)) {
        return 0;

      } else {
        return Math.max(pattern.length(), refName.length());
      }
      return StringUtils.getLevenshteinDistance(example, refName);
    }

    private boolean finite(String pattern) {
      if (isRE(pattern)) {
        return toRegExp(pattern).toAutomaton().isFinite();

      } else if (pattern.endsWith("/*")) {
        return false;

      } else {
        return true;
      }
    }

    private int transitions(String pattern) {
      if (isRE(pattern)) {
        return toRegExp(pattern).toAutomaton().getNumberOfTransitions();

      } else if (pattern.endsWith("/*")) {
        return pattern.length();

      } else {
        return pattern.length();
      }
    }
  }
}

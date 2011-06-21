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

package com.google.gerrit.server.git;

import static com.google.gerrit.common.data.AccessSection.isAccessSection;
import static com.google.gerrit.common.data.Permission.isPermission;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.account.GroupCache;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectConfig extends VersionedMetaData {
  private static final String PROJECT_CONFIG = "project.config";
  private static final String GROUP_LIST = "groups";

  private static final String PROJECT = "project";
  private static final String KEY_DESCRIPTION = "description";

  private static final String ACCESS = "access";
  private static final String KEY_INHERIT_FROM = "inheritFrom";
  private static final String KEY_GROUP_PERMISSIONS = "exclusiveGroupPermissions";

  private static final String CAPABILITY = "capability";

  private static final String RECEIVE = "receive";
  private static final String KEY_REQUIRE_SIGNED_OFF_BY = "requireSignedOffBy";
  private static final String KEY_REQUIRE_CHANGE_ID = "requireChangeId";
  private static final String KEY_REQUIRE_CONTRIBUTOR_AGREEMENT =
      "requireContributorAgreement";
  private static final String KEY_ALLOW_TOPIC_REVIEW = "allowTopicReview";

  private static final String SUBMIT = "submit";
  private static final String KEY_ACTION = "action";
  private static final String KEY_MERGE_CONTENT = "mergeContent";

  private static final SubmitType defaultSubmitAction =
      SubmitType.MERGE_IF_NECESSARY;

  private Project.NameKey projectName;
  private Project project;
  private Map<AccountGroup.UUID, GroupReference> groupsByUUID;
  private Map<String, AccessSection> accessSections;
  private List<ValidationError> validationErrors;
  private ObjectId rulesId;

  public static ProjectConfig read(MetaDataUpdate update) throws IOException,
      ConfigInvalidException {
    ProjectConfig r = new ProjectConfig(update.getProjectName());
    r.load(update);
    return r;
  }

  public static ProjectConfig read(MetaDataUpdate update, ObjectId id)
      throws IOException, ConfigInvalidException {
    ProjectConfig r = new ProjectConfig(update.getProjectName());
    r.load(update, id);
    return r;
  }

  public ProjectConfig(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  public Project getProject() {
    return project;
  }

  public AccessSection getAccessSection(String name) {
    return getAccessSection(name, false);
  }

  public AccessSection getAccessSection(String name, boolean create) {
    AccessSection as = accessSections.get(name);
    if (as == null && create) {
      as = new AccessSection(name);
      accessSections.put(name, as);
    }
    return as;
  }

  public Collection<AccessSection> getAccessSections() {
    return sort(accessSections.values());
  }

  public void remove(AccessSection section) {
    if (section != null) {
      accessSections.remove(section.getName());
    }
  }

  public void replace(AccessSection section) {
    for (Permission permission : section.getPermissions()) {
      for (PermissionRule rule : permission.getRules()) {
        rule.setGroup(resolve(rule.getGroup()));
      }
    }

    accessSections.put(section.getName(), section);
  }

  public GroupReference resolve(AccountGroup group) {
    return resolve(GroupReference.forGroup(group));
  }

  public GroupReference resolve(GroupReference group) {
    if (group != null) {
      GroupReference ref = groupsByUUID.get(group.getUUID());
      if (ref != null) {
        return ref;
      }
      groupsByUUID.put(group.getUUID(), group);
    }
    return group;
  }

  /** @return the group reference, if the group is used by at least one rule. */
  public GroupReference getGroup(AccountGroup.UUID uuid) {
    return groupsByUUID.get(uuid);
  }

  /**
   * @return the project's rules.pl ObjectId, if present in the branch.
   *    Null if it doesn't exist.
   */
  public ObjectId getRulesId() {
    return rulesId;
  }

  /**
   * Check all GroupReferences use current group name, repairing stale ones.
   *
   * @param groupCache cache to use when looking up group information by UUID.
   * @return true if one or more group names was stale.
   */
  public boolean updateGroupNames(GroupCache groupCache) {
    boolean dirty = false;
    for (GroupReference ref : groupsByUUID.values()) {
      AccountGroup g = groupCache.get(ref.getUUID());
      if (g != null && !g.getName().equals(ref.getName())) {
        dirty = true;
        ref.setName(g.getName());
      }
    }
    return dirty;
  }

  /**
   * Get the validation errors, if any were discovered during load.
   *
   * @return list of errors; empty list if there are no errors.
   */
  public List<ValidationError> getValidationErrors() {
    if (validationErrors != null) {
      return Collections.unmodifiableList(validationErrors);
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  protected String getRefName() {
    return GitRepositoryManager.REF_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    Map<String, GroupReference> groupsByName = readGroupList();

    rulesId = getObjectId("rules.pl");
    Config rc = readConfig(PROJECT_CONFIG);
    project = new Project(projectName);

    Project p = project;
    p.setDescription(rc.getString(PROJECT, null, KEY_DESCRIPTION));
    if (p.getDescription() == null) {
      p.setDescription("");
    }
    p.setParentName(rc.getString(ACCESS, null, KEY_INHERIT_FROM));

    p.setUseContributorAgreements(getBoolean(rc, RECEIVE, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, false));
    p.setUseSignedOffBy(getBoolean(rc, RECEIVE, KEY_REQUIRE_SIGNED_OFF_BY, false));
    p.setRequireChangeID(getBoolean(rc, RECEIVE, KEY_REQUIRE_CHANGE_ID, false));
    p.setAllowTopicReview(getBoolean(rc, RECEIVE, KEY_ALLOW_TOPIC_REVIEW, false));

    p.setSubmitType(getEnum(rc, SUBMIT, null, KEY_ACTION, defaultSubmitAction));
    p.setUseContentMerge(getBoolean(rc, SUBMIT, KEY_MERGE_CONTENT, false));

    accessSections = new HashMap<String, AccessSection>();
    for (String refName : rc.getSubsections(ACCESS)) {
      if (isAccessSection(refName)) {
        AccessSection as = getAccessSection(refName, true);

        for (String varName : rc.getStringList(ACCESS, refName, KEY_GROUP_PERMISSIONS)) {
          for (String n : varName.split("[, \t]{1,}")) {
            if (isPermission(n)) {
              as.getPermission(n, true).setExclusiveGroup(true);
            }
          }
        }

        for (String varName : rc.getNames(ACCESS, refName)) {
          if (isPermission(varName)) {
            Permission perm = as.getPermission(varName, true);
            loadPermissionRules(rc, ACCESS, refName, varName, groupsByName,
                perm, perm.isLabel());
          }
        }
      }
    }

    AccessSection capability = null;
    for (String varName : rc.getNames(CAPABILITY)) {
      if (GlobalCapability.isCapability(varName)) {
        if (capability == null) {
          capability = new AccessSection(AccessSection.GLOBAL_CAPABILITIES);
          accessSections.put(AccessSection.GLOBAL_CAPABILITIES, capability);
        }
        Permission perm = capability.getPermission(varName, true);
        loadPermissionRules(rc, CAPABILITY, null, varName, groupsByName, perm,
            GlobalCapability.hasRange(varName));
      }
    }
  }

  private void loadPermissionRules(Config rc, String section,
      String subsection, String varName,
      Map<String, GroupReference> groupsByName, Permission perm,
      boolean useRange) {
    for (String ruleString : rc.getStringList(section, subsection, varName)) {
      PermissionRule rule;
      try {
        rule = PermissionRule.fromString(ruleString, useRange);
      } catch (IllegalArgumentException notRule) {
        error(new ValidationError(PROJECT_CONFIG, "Invalid rule in "
            + section
            + (subsection != null ? "." + subsection : "")
            + "." + varName + ": "
            + notRule.getMessage()));
        continue;
      }

      GroupReference ref = groupsByName.get(rule.getGroup().getName());
      if (ref == null) {
        // The group wasn't mentioned in the groups table, so there is
        // no valid UUID for it. Pool the reference anyway so at least
        // all rules in the same file share the same GroupReference.
        //
        ref = rule.getGroup();
        groupsByName.put(ref.getName(), ref);
        error(new ValidationError(PROJECT_CONFIG,
            "group \"" + ref.getName() + "\" not in " + GROUP_LIST));
      }

      rule.setGroup(ref);
      perm.add(rule);
    }
  }

  private Map<String, GroupReference> readGroupList() throws IOException {
    groupsByUUID = new HashMap<AccountGroup.UUID, GroupReference>();
    Map<String, GroupReference> groupsByName =
        new HashMap<String, GroupReference>();

    BufferedReader br = new BufferedReader(new StringReader(readUTF8(GROUP_LIST)));
    String s;
    for (int lineNumber = 1; (s = br.readLine()) != null; lineNumber++) {
      if (s.isEmpty() || s.startsWith("#")) {
        continue;
      }

      int tab = s.indexOf('\t');
      if (tab < 0) {
        error(new ValidationError(GROUP_LIST, lineNumber, "missing tab delimiter"));
        continue;
      }

      AccountGroup.UUID uuid = new AccountGroup.UUID(s.substring(0, tab).trim());
      String name = s.substring(tab + 1).trim();
      GroupReference ref = new GroupReference(uuid, name);

      groupsByUUID.put(uuid, ref);
      groupsByName.put(name, ref);
    }
    return groupsByName;
  }

  @Override
  protected void onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    if (commit.getMessage() == null || "".equals(commit.getMessage())) {
      commit.setMessage("Updated project configuration\n");
    }

    Config rc = readConfig(PROJECT_CONFIG);
    Project p = project;

    if (p.getDescription() != null && !p.getDescription().isEmpty()) {
      rc.setString(PROJECT, null, KEY_DESCRIPTION, p.getDescription());
    } else {
      rc.unset(PROJECT, null, KEY_DESCRIPTION);
    }
    set(rc, ACCESS, null, KEY_INHERIT_FROM, p.getParentName());

    set(rc, RECEIVE, null, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, p.isUseContributorAgreements());
    set(rc, RECEIVE, null, KEY_REQUIRE_SIGNED_OFF_BY, p.isUseSignedOffBy());
    set(rc, RECEIVE, null, KEY_REQUIRE_CHANGE_ID, p.isRequireChangeID() || p.isAllowTopicReview());
    set(rc, RECEIVE, null, KEY_ALLOW_TOPIC_REVIEW, p.isAllowTopicReview());

    set(rc, SUBMIT, null, KEY_ACTION, p.getSubmitType(), defaultSubmitAction);
    set(rc, SUBMIT, null, KEY_MERGE_CONTENT, p.isUseContentMerge());

    Set<AccountGroup.UUID> keepGroups = new HashSet<AccountGroup.UUID>();
    AccessSection capability = accessSections.get(AccessSection.GLOBAL_CAPABILITIES);
    if (capability != null) {
      Set<String> have = new HashSet<String>();
      for (Permission permission : sort(capability.getPermissions())) {
        have.add(permission.getName().toLowerCase());

        boolean needRange = GlobalCapability.hasRange(permission.getName());
        List<String> rules = new ArrayList<String>();
        for (PermissionRule rule : sort(permission.getRules())) {
          GroupReference group = rule.getGroup();
          if (group.getUUID() != null) {
            keepGroups.add(group.getUUID());
          }
          rules.add(rule.asString(needRange));
        }
        rc.setStringList(CAPABILITY, null, permission.getName(), rules);
      }
      for (String varName : rc.getNames(CAPABILITY)) {
        if (GlobalCapability.isCapability(varName)
            && !have.contains(varName.toLowerCase())) {
          rc.unset(CAPABILITY, null, varName);
        }
      }
    } else {
      rc.unsetSection(CAPABILITY, null);
    }

    for (AccessSection as : sort(accessSections.values())) {
      String refName = as.getName();
      if (AccessSection.GLOBAL_CAPABILITIES.equals(refName)) {
        continue;
      }

      StringBuilder doNotInherit = new StringBuilder();
      for (Permission perm : sort(as.getPermissions())) {
        if (perm.getExclusiveGroup()) {
          if (0 < doNotInherit.length()) {
            doNotInherit.append(' ');
          }
          doNotInherit.append(perm.getName());
        }
      }
      if (0 < doNotInherit.length()) {
        rc.setString(ACCESS, refName, KEY_GROUP_PERMISSIONS, doNotInherit.toString());
      } else {
        rc.unset(ACCESS, refName, KEY_GROUP_PERMISSIONS);
      }

      Set<String> have = new HashSet<String>();
      for (Permission permission : sort(as.getPermissions())) {
        have.add(permission.getName().toLowerCase());

        boolean needRange = permission.isLabel();
        List<String> rules = new ArrayList<String>();
        for (PermissionRule rule : sort(permission.getRules())) {
          GroupReference group = rule.getGroup();
          if (group.getUUID() != null) {
            keepGroups.add(group.getUUID());
          }
          rules.add(rule.asString(needRange));
        }
        rc.setStringList(ACCESS, refName, permission.getName(), rules);
      }

      for (String varName : rc.getNames(ACCESS, refName)) {
        if (isPermission(varName) && !have.contains(varName.toLowerCase())) {
          rc.unset(ACCESS, refName, varName);
        }
      }
    }

    for (String name : rc.getSubsections(ACCESS)) {
      if (isAccessSection(name) && !accessSections.containsKey(name)) {
        rc.unsetSection(ACCESS, name);
      }
    }
    groupsByUUID.keySet().retainAll(keepGroups);

    saveConfig(PROJECT_CONFIG, rc);
    saveGroupList();
  }

  private void saveGroupList() throws IOException {
    if (groupsByUUID.isEmpty()) {
      saveFile(GROUP_LIST, null);
      return;
    }

    final int uuidLen = 40;
    StringBuilder buf = new StringBuilder();
    buf.append(pad(uuidLen, "# UUID"));
    buf.append('\t');
    buf.append("Group Name");
    buf.append('\n');

    buf.append('#');
    buf.append('\n');

    for (GroupReference g : sort(groupsByUUID.values())) {
      if (g.getUUID() != null && g.getName() != null) {
        buf.append(pad(uuidLen, g.getUUID().get()));
        buf.append('\t');
        buf.append(g.getName());
        buf.append('\n');
      }
    }
    saveUTF8(GROUP_LIST, buf.toString());
  }

  private boolean getBoolean(Config rc, String section, String name,
      boolean defaultValue) {
    try {
      return rc.getBoolean(section, name, defaultValue);
    } catch (IllegalArgumentException err) {
      error(new ValidationError(PROJECT_CONFIG, err.getMessage()));
      return defaultValue;
    }
  }

  private <E extends Enum<?>> E getEnum(Config rc, String section,
      String subsection, String name, E defaultValue) {
    try {
      return rc.getEnum(section, subsection, name, defaultValue);
    } catch (IllegalArgumentException err) {
      error(new ValidationError(PROJECT_CONFIG, err.getMessage()));
      return defaultValue;
    }
  }

  private void error(ValidationError error) {
    if (validationErrors == null) {
      validationErrors = new ArrayList<ValidationError>(4);
    }
    validationErrors.add(error);
  }

  private static String pad(int len, String src) {
    if (len <= src.length()) {
      return src;
    }

    StringBuilder r = new StringBuilder(len);
    r.append(src);
    while (r.length() < len) {
      r.append(' ');
    }
    return r.toString();
  }

  private static <T extends Comparable<? super T>> List<T> sort(Collection<T> m) {
    ArrayList<T> r = new ArrayList<T>(m);
    Collections.sort(r);
    return r;
  }
}

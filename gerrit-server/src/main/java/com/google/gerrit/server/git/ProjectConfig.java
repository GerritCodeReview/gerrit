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
  private static final String KEY_DO_NOT_INHERIT = "doNotInherit";

  private static final String RECEIVE = "receive";
  private static final String KEY_REQUIRE_SIGNED_OFF_BY = "requireSignedOffBy";
  private static final String KEY_REQUIRE_CHANGE_ID = "requireChangeId";
  private static final String KEY_REQUIRE_CONTRIBUTOR_AGREEMENT =
      "requireContributorAgreement";

  private static final String SUBMIT = "submit";
  private static final String KEY_ACTION = "action";
  private static final String KEY_MERGE_CONTENT = "mergeContent";

  private static final SubmitType defaultSubmitAction =
      SubmitType.MERGE_IF_NECESSARY;

  private Project.NameKey projectName;
  private Project project;
  private Map<AccountGroup.UUID, GroupReference> groupsByUUID;
  private Map<String, AccessSection> accessSections;

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
    return accessSections.values();
  }

  public void remove(AccessSection section) {
    accessSections.remove(section.getRefPattern());
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

  @Override
  protected String getRefName() {
    return GitRepositoryManager.REF_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    Map<String,GroupReference> groupsByName = readGroupList();

    Config rc = readConfig(PROJECT_CONFIG);
    project = new Project(projectName);

    Project p = project;
    p.setDescription(rc.getString(PROJECT, null, KEY_DESCRIPTION));
    if (p.getDescription() == null) {
      p.setDescription("");
    }
    p.setParentName(rc.getString(ACCESS, null, KEY_INHERIT_FROM));

    p.setUseContributorAgreements(rc.getBoolean(RECEIVE, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, false));
    p.setUseSignedOffBy(rc.getBoolean(RECEIVE, KEY_REQUIRE_SIGNED_OFF_BY, false));
    p.setRequireChangeID(rc.getBoolean(RECEIVE, KEY_REQUIRE_CHANGE_ID, false));

    p.setSubmitType(rc.getEnum(SUBMIT, null, KEY_ACTION, defaultSubmitAction));
    p.setUseContentMerge(rc.getBoolean(SUBMIT, null, KEY_MERGE_CONTENT, false));

    accessSections = new HashMap<String, AccessSection>();
    for (String refName : rc.getSubsections(ACCESS)) {
      if (isAccessSection(refName)) {
        AccessSection as = getAccessSection(refName, true);

        for (String varName : rc.getStringList(ACCESS, refName, KEY_DO_NOT_INHERIT)) {
          for (String n : varName.split("[, \t]{1,}")) {
            if (isPermission(n)) {
              as.getPermission(n, true).setInherit(false);
            }
          }
        }

        for (String varName : rc.getNames(ACCESS, refName)) {
          if (isPermission(varName)) {
            Permission perm = as.getPermission(varName, true);

            boolean useRange = perm.isLabel();
            for (String ruleString : rc.getStringList(ACCESS, refName, varName)) {
              PermissionRule rule;
              try {
                rule = PermissionRule.fromString(ruleString, useRange);
              } catch (IllegalArgumentException notRule) {
                throw new ConfigInvalidException("Invalid rule in " + ACCESS
                    + "." + refName + "." + varName + ": "
                    + notRule.getMessage(), notRule);
              }

              GroupReference ref = groupsByName.get(rule.getGroup().getName());
              if (ref == null) {
                // The group wasn't mentioned in the groups table, so there is
                // no valid UUID for it. Pool the reference anyway so at least
                // all rules in the same file share the same GroupReference.
                //
                ref = rule.getGroup();
                groupsByName.put(ref.getName(), ref);
              }

              rule.setGroup(ref);
              perm.add(rule);
            }
          }
        }
      }
    }
  }

  private Map<String, GroupReference> readGroupList() throws IOException,
      ConfigInvalidException {
    groupsByUUID = new HashMap<AccountGroup.UUID, GroupReference>();
    Map<String, GroupReference> groupsByName =
        new HashMap<String, GroupReference>();

    BufferedReader br = new BufferedReader(new StringReader(readUTF8(GROUP_LIST)));
    String s;
    while ((s = br.readLine()) != null) {
      if (s.isEmpty() || s.startsWith("#")) {
        continue;
      }

      int tab = s.indexOf('\t');
      if (tab < 0) {
        throw new ConfigInvalidException("Invalid group line: " + s);
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
    set(rc, RECEIVE, null, KEY_REQUIRE_CHANGE_ID, p.isRequireChangeID());

    set(rc, SUBMIT, null, KEY_ACTION, p.getSubmitType(), defaultSubmitAction);
    set(rc, SUBMIT, null, KEY_MERGE_CONTENT, p.isUseContentMerge());

    Set<AccountGroup.UUID> keepGroups = new HashSet<AccountGroup.UUID>();
    for (AccessSection as : sort(accessSections.values())) {
      String refName = as.getRefPattern();

      StringBuilder doNotInherit = new StringBuilder();
      for (Permission perm : sort(as.getPermissions())) {
        if (!perm.getInherit()) {
          if (0 < doNotInherit.length()) {
            doNotInherit.append(' ');
          }
          doNotInherit.append(perm.getName());
        }
      }
      if (0 < doNotInherit.length()) {
        rc.setString(ACCESS, refName, KEY_DO_NOT_INHERIT, doNotInherit.toString());
      } else {
        rc.unset(ACCESS, refName, KEY_DO_NOT_INHERIT);
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

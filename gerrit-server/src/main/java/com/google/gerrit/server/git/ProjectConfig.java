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
import static com.google.gerrit.common.data.AccessSection.Permission.isPermission;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.AccessSection.GroupReference;
import com.google.gerrit.common.data.AccessSection.Permission;
import com.google.gerrit.common.data.AccessSection.Rule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.SubmitType;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

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
  private static final String KEY_NAME = "name";
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

  private Project project;
  private Map<String, GroupReference> groupsByName;
  private Map<String, AccessSection> accessSections;

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
      GroupReference ref = groupsByName.get(group.getName());
      if (ref != null) {
        return ref;
      }
      groupsByName.put(group.getName(), group);
    }
    return group;
  }

  @Override
  protected String getRefName() {
    return GitRepositoryManager.REF_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    readGroupList();

    Config rc = readConfig(PROJECT_CONFIG);
    project = new Project();

    Project p = project;
    p.setName(rc.getString(PROJECT, null, KEY_NAME));
    p.setDescription(rc.getString(PROJECT, null, KEY_DESCRIPTION));
    if (p.getDescription() == null) {
      p.setDescription("");
    }
    p.setParentName(rc.getString(ACCESS, null, KEY_INHERIT_FROM));

    p.setUseContributorAgreements(rc.getBoolean(RECEIVE, null, //
        KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, false));
    p.setUseSignedOffBy(rc.getBoolean(RECEIVE, null, //
        KEY_REQUIRE_SIGNED_OFF_BY, false));
    p.setRequireChangeID(rc.getBoolean(RECEIVE, null, //
        KEY_REQUIRE_CHANGE_ID, false));

    p.setSubmitType(rc.getEnum(SUBMIT, null, KEY_ACTION,
        SubmitType.MERGE_IF_NECESSARY));
    p.setUseContentMerge(rc.getBoolean(SUBMIT, null, KEY_MERGE_CONTENT, false));

    accessSections = new HashMap<String, AccessSection>();
    for (String refName : rc.getSubsections(ACCESS)) {
      if (isAccessSection(refName)) {
        AccessSection as = getAccessSection(refName, true);

        Set<String> doNotInherit = new HashSet<String>();
        for (String varName : rc.getStringList(ACCESS, refName,
            KEY_DO_NOT_INHERIT)) {
          for (String n : varName.split(" \t")) {
            doNotInherit.add(n.toLowerCase());
          }
        }

        for (String varName : rc.getNames(ACCESS, refName)) {
          if (isPermission(varName)) {
            Permission perm = as.getPermission(varName, true);
            perm.setInherit(!doNotInherit.contains(varName.toLowerCase()));

            boolean useRange = perm.isLabel();
            for (String ruleString : rc.getStringList(ACCESS, refName, varName)) {
              Rule rule;
              try {
              rule = Rule.fromString(ruleString, useRange);
              } catch (IllegalArgumentException notRule) {
                throw new ConfigInvalidException("Invalid rule in " + ACCESS
                    + "." + refName + "." + varName + ": "
                    + notRule.getMessage(), notRule);
              }
              rule.setGroup(resolve(rule.getGroup()));
              perm.add(rule);
            }
          }
        }
      }
    }
  }

  private void readGroupList() throws IOException {
    groupsByName = new HashMap<String, GroupReference>();

    BufferedReader br =
        new BufferedReader(new StringReader(readUTF8(GROUP_LIST)));
    String s;
    while ((s = br.readLine()) != null) {
      if (s.isEmpty() || s.startsWith("#")) {
        continue;
      }

      int sp = split(s);
      if (sp < 0) {
        continue;
      }

      AccountGroup.UUID uuid = new AccountGroup.UUID(s.substring(0, sp).trim());
      String name = s.substring(sp + 1).trim();
      groupsByName.put(name, new GroupReference(uuid, name));
    }
  }

  private static int split(String line) {
    int sp = line.indexOf(' ');
    int tab = line.indexOf('\t');

    if (sp < 0 && tab < 0) {
      return -1;
    }

    if (sp < 0 && 0 <= tab) {
      return tab;
    }

    if (0 <= sp && tab < 0) {
      return sp;
    }

    return Math.min(sp, tab);
  }

  @Override
  protected void onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    if (commit.getMessage() == null || "".equals(commit.getMessage())) {
      commit.setMessage("Updated project configuration\n");
    }

    Config rc = readConfig(PROJECT_CONFIG);
    Project p = project;

    set(rc, PROJECT, null, KEY_NAME, p.getName());
    if (p.getDescription() != null && !p.getDescription().isEmpty()) {
      rc.setString(PROJECT, null, KEY_DESCRIPTION, p.getDescription());
    } else {
      rc.unset(PROJECT, null, KEY_DESCRIPTION);
    }
    set(rc, ACCESS, null, KEY_INHERIT_FROM, p.getParentName());

    rc.setBoolean(RECEIVE, null, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, //
        p.isUseContributorAgreements());
    rc.setBoolean(RECEIVE, null, KEY_REQUIRE_SIGNED_OFF_BY, //
        p.isUseSignedOffBy());
    rc.setBoolean(RECEIVE, null, KEY_REQUIRE_CHANGE_ID, p.isRequireChangeID());

    rc.setEnum(SUBMIT, null, KEY_ACTION, p.getSubmitType());
    rc.setBoolean(SUBMIT, null, KEY_MERGE_CONTENT, p.isUseContentMerge());

    Set<String> keepGroups = new HashSet<String>();
    for (AccessSection as : sort(accessSections.values())) {
      String refName = as.getRefPattern();

      StringBuilder doNotInherit = new StringBuilder();
      for (Permission perm : sort(as.getPermissions())) {
        if (!perm.isInherit()) {
          if (0 < doNotInherit.length()) {
            doNotInherit.append(' ');
          }
          doNotInherit.append(perm.getName());
        }
      }
      if (0 < doNotInherit.length()) {
        rc.setString(ACCESS, refName, KEY_DO_NOT_INHERIT, doNotInherit
            .toString());
      } else {
        rc.unset(ACCESS, refName, KEY_DO_NOT_INHERIT);
      }

      Set<String> have = new HashSet<String>();
      for (Permission permission : sort(as.getPermissions())) {
        have.add(permission.getName().toLowerCase());

        boolean needRange = permission.isLabel();
        List<String> rules = new ArrayList<String>();
        for (Rule rule : sort(permission.getRules())) {
          GroupReference group = rule.getGroup();
          keepGroups.add(group.getName());
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
    groupsByName.keySet().retainAll(keepGroups);

    saveConfig(PROJECT_CONFIG, rc);
    saveGroupList();
  }

  private void saveGroupList() throws IOException {
    if (groupsByName.isEmpty()) {
      saveFile(GROUP_LIST, null);
      return;
    }

    StringBuilder buf = new StringBuilder();
    buf.append("# UUID");
    for (int i = buf.length(); i < 40; i++) {
      buf.append(' ');
    }
    buf.append('\t');
    buf.append("Group Name");
    buf.append('\n');

    buf.append('#');
    buf.append('\n');

    for (GroupReference g : sort(groupsByName.values())) {
      if (g.getUUID() != null && g.getName() != null) {
        buf.append(g.getUUID().get());
        buf.append('\t');
        buf.append(g.getName());
        buf.append('\n');
      }
    }
    saveUTF8(GROUP_LIST, buf.toString());
  }

  private static <T extends Comparable<? super T>> List<T> sort(Collection<T> m) {
    ArrayList<T> r = new ArrayList<T>(m);
    Collections.sort(r);
    return r;
  }
}

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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.common.data.Permission.isPermission;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Shorts;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.project.CommentLinkInfo;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ProjectConfig extends VersionedMetaData implements ValidationError.Sink {
  public static final String COMMENTLINK = "commentlink";
  private static final String KEY_MATCH = "match";
  private static final String KEY_HTML = "html";
  private static final String KEY_LINK = "link";
  private static final String KEY_ENABLED = "enabled";

  public static final String PROJECT_CONFIG = "project.config";

  private static final String PROJECT = "project";
  private static final String KEY_DESCRIPTION = "description";

  private static final String ACCESS = "access";
  private static final String KEY_INHERIT_FROM = "inheritFrom";
  private static final String KEY_GROUP_PERMISSIONS = "exclusiveGroupPermissions";

  private static final String ACCOUNTS = "accounts";
  private static final String KEY_SAME_GROUP_VISIBILITY = "sameGroupVisibility";

  private static final String BRANCH_ORDER = "branchOrder";
  private static final String BRANCH = "branch";

  private static final String CONTRIBUTOR_AGREEMENT = "contributor-agreement";
  private static final String KEY_ACCEPTED = "accepted";
  private static final String KEY_REQUIRE_CONTACT_INFORMATION = "requireContactInformation";
  private static final String KEY_AUTO_VERIFY = "autoVerify";
  private static final String KEY_AGREEMENT_URL = "agreementUrl";

  private static final String NOTIFY = "notify";
  private static final String KEY_EMAIL = "email";
  private static final String KEY_FILTER = "filter";
  private static final String KEY_TYPE = "type";
  private static final String KEY_HEADER = "header";

  private static final String CAPABILITY = "capability";

  private static final String RECEIVE = "receive";
  private static final String KEY_REQUIRE_SIGNED_OFF_BY = "requireSignedOffBy";
  private static final String KEY_REQUIRE_CHANGE_ID = "requireChangeId";
  private static final String KEY_USE_ALL_NOT_IN_TARGET =
      "createNewChangeForAllNotInTarget";
  private static final String KEY_MAX_OBJECT_SIZE_LIMIT = "maxObjectSizeLimit";
  private static final String KEY_REQUIRE_CONTRIBUTOR_AGREEMENT =
      "requireContributorAgreement";
  private static final String KEY_CHECK_RECEIVED_OBJECTS = "checkReceivedObjects";

  private static final String SUBMIT = "submit";
  private static final String KEY_ACTION = "action";
  private static final String KEY_MERGE_CONTENT = "mergeContent";
  private static final String KEY_STATE = "state";

  private static final String DASHBOARD = "dashboard";
  private static final String KEY_DEFAULT = "default";
  private static final String KEY_LOCAL_DEFAULT = "local-default";

  private static final String LABEL = "label";
  private static final String KEY_FUNCTION = "function";
  private static final String KEY_DEFAULT_VALUE = "defaultValue";
  private static final String KEY_COPY_MIN_SCORE = "copyMinScore";
  private static final String KEY_COPY_MAX_SCORE = "copyMaxScore";
  private static final String KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE = "copyAllScoresOnTrivialRebase";
  private static final String KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE = "copyAllScoresIfNoCodeChange";
  private static final String KEY_COPY_ALL_SCORES_IF_NO_CHANGE = "copyAllScoresIfNoChange";
  private static final String KEY_VALUE = "value";
  private static final String KEY_CAN_OVERRIDE = "canOverride";
  private static final String KEY_Branch = "branch";
  private static final Set<String> LABEL_FUNCTIONS = ImmutableSet.of(
      "MaxWithBlock", "AnyWithBlock", "MaxNoBlock", "NoBlock", "NoOp");

  private static final String PLUGIN = "plugin";

  private static final SubmitType defaultSubmitAction =
      SubmitType.MERGE_IF_NECESSARY;
  private static final ProjectState defaultStateValue =
      ProjectState.ACTIVE;

  private Project.NameKey projectName;
  private Project project;
  private AccountsSection accountsSection;
  private GroupList groupList;
  private Map<String, AccessSection> accessSections;
  private BranchOrderSection branchOrderSection;
  private Map<String, ContributorAgreement> contributorAgreements;
  private Map<String, NotifyConfig> notifySections;
  private Map<String, LabelType> labelSections;
  private ConfiguredMimeTypes mimeTypes;
  private List<CommentLinkInfo> commentLinkSections;
  private List<ValidationError> validationErrors;
  private ObjectId rulesId;
  private long maxObjectSizeLimit;
  private Map<String, Config> pluginConfigs;
  private boolean checkReceivedObjects;

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

  public static CommentLinkInfo buildCommentLink(Config cfg, String name,
      boolean allowRaw) throws IllegalArgumentException {
    String match = cfg.getString(COMMENTLINK, name, KEY_MATCH);
    if (match != null) {
      // Unfortunately this validation isn't entirely complete. Clients
      // can have exceptions trying to evaluate the pattern if they don't
      // support a token used, even if the server does support the token.
      //
      // At the minimum, we can trap problems related to unmatched groups.
      Pattern.compile(match);
    }

    String link = cfg.getString(COMMENTLINK, name, KEY_LINK);
    String html = cfg.getString(COMMENTLINK, name, KEY_HTML);
    boolean hasHtml = !Strings.isNullOrEmpty(html);

    String rawEnabled = cfg.getString(COMMENTLINK, name, KEY_ENABLED);
    Boolean enabled;
    if (rawEnabled != null) {
      enabled = cfg.getBoolean(COMMENTLINK, name, KEY_ENABLED, true);
    } else {
      enabled = null;
    }
    checkArgument(allowRaw || !hasHtml, "Raw html replacement not allowed");

    if (Strings.isNullOrEmpty(match) && Strings.isNullOrEmpty(link) && !hasHtml
        && enabled != null) {
      if (enabled) {
        return new CommentLinkInfo.Enabled(name);
      } else {
        return new CommentLinkInfo.Disabled(name);
      }
    }
    return new CommentLinkInfo(name, match, link, html, enabled);
  }

  public ProjectConfig(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  public Project.NameKey getName() {
    return projectName;
  }

  public Project getProject() {
    return project;
  }

  public AccountsSection getAccountsSection() {
    return accountsSection;
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

  public BranchOrderSection getBranchOrderSection() {
    return branchOrderSection;
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

  public ContributorAgreement getContributorAgreement(String name) {
    return getContributorAgreement(name, false);
  }

  public ContributorAgreement getContributorAgreement(String name, boolean create) {
    ContributorAgreement ca = contributorAgreements.get(name);
    if (ca == null && create) {
      ca = new ContributorAgreement(name);
      contributorAgreements.put(name, ca);
    }
    return ca;
  }

  public Collection<ContributorAgreement> getContributorAgreements() {
    return sort(contributorAgreements.values());
  }

  public void remove(ContributorAgreement section) {
    if (section != null) {
      accessSections.remove(section.getName());
    }
  }

  public void replace(ContributorAgreement section) {
    section.setAutoVerify(resolve(section.getAutoVerify()));
    for (PermissionRule rule : section.getAccepted()) {
      rule.setGroup(resolve(rule.getGroup()));
    }

    contributorAgreements.put(section.getName(), section);
  }

  public Collection<NotifyConfig> getNotifyConfigs() {
    return notifySections.values();
  }

  public void putNotifyConfig(String name, NotifyConfig nc) {
    notifySections.put(name, nc);
  }

  public Map<String, LabelType> getLabelSections() {
    return labelSections;
  }

  public Collection<CommentLinkInfo> getCommentLinkSections() {
    return commentLinkSections;
  }

  public ConfiguredMimeTypes getMimeTypes() {
    return mimeTypes;
  }

  public GroupReference resolve(AccountGroup group) {
    return resolve(GroupReference.forGroup(group));
  }

  public GroupReference resolve(GroupReference group) {
    return groupList.resolve(group);
  }

  /** @return the group reference, if the group is used by at least one rule. */
  public GroupReference getGroup(AccountGroup.UUID uuid) {
    return groupList.byUUID(uuid);
  }

  /** @return set of all groups used by this configuration. */
  public Set<AccountGroup.UUID> getAllGroupUUIDs() {
    return groupList.uuids();
  }

  /**
   * @return the project's rules.pl ObjectId, if present in the branch.
   *    Null if it doesn't exist.
   */
  public ObjectId getRulesId() {
    return rulesId;
  }

  /**
   * @return the maxObjectSizeLimit for this project, if set. Zero if this
   *         project doesn't define own maxObjectSizeLimit.
   */
  public long getMaxObjectSizeLimit() {
    return maxObjectSizeLimit;
  }

  /**
   * @return the checkReceivedObjects for this project, default is true.
   */
  public boolean getCheckReceivedObjects() {
    return checkReceivedObjects;
  }

  /**
   * Check all GroupReferences use current group name, repairing stale ones.
   *
   * @param groupBackend cache to use when looking up group information by UUID.
   * @return true if one or more group names was stale.
   */
  public boolean updateGroupNames(GroupBackend groupBackend) {
    boolean dirty = false;
    for (GroupReference ref : groupList.references()) {
      GroupDescription.Basic g = groupBackend.get(ref.getUUID());
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
    return RefNames.REFS_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    readGroupList();
    Map<String, GroupReference> groupsByName = mapGroupReferences();

    rulesId = getObjectId("rules.pl");
    Config rc = readConfig(PROJECT_CONFIG);
    project = new Project(projectName);

    Project p = project;
    p.setDescription(rc.getString(PROJECT, null, KEY_DESCRIPTION));
    if (p.getDescription() == null) {
      p.setDescription("");
    }
    p.setParentName(rc.getString(ACCESS, null, KEY_INHERIT_FROM));

    p.setUseContributorAgreements(getEnum(rc, RECEIVE, null, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, InheritableBoolean.INHERIT));
    p.setUseSignedOffBy(getEnum(rc, RECEIVE, null, KEY_REQUIRE_SIGNED_OFF_BY, InheritableBoolean.INHERIT));
    p.setRequireChangeID(getEnum(rc, RECEIVE, null, KEY_REQUIRE_CHANGE_ID, InheritableBoolean.INHERIT));
    p.setCreateNewChangeForAllNotInTarget(getEnum(rc, RECEIVE, null, KEY_USE_ALL_NOT_IN_TARGET, InheritableBoolean.INHERIT));
    p.setMaxObjectSizeLimit(rc.getString(RECEIVE, null, KEY_MAX_OBJECT_SIZE_LIMIT));

    p.setSubmitType(getEnum(rc, SUBMIT, null, KEY_ACTION, defaultSubmitAction));
    p.setUseContentMerge(getEnum(rc, SUBMIT, null, KEY_MERGE_CONTENT, InheritableBoolean.INHERIT));
    p.setState(getEnum(rc, PROJECT, null, KEY_STATE, defaultStateValue));

    p.setDefaultDashboard(rc.getString(DASHBOARD, null, KEY_DEFAULT));
    p.setLocalDefaultDashboard(rc.getString(DASHBOARD, null, KEY_LOCAL_DEFAULT));

    loadAccountsSection(rc, groupsByName);
    loadContributorAgreements(rc, groupsByName);
    loadAccessSections(rc, groupsByName);
    loadBranchOrderSection(rc);
    loadNotifySections(rc, groupsByName);
    loadLabelSections(rc);
    loadCommentLinkSections(rc);
    mimeTypes = new ConfiguredMimeTypes(projectName.get(), rc);
    loadPluginSections(rc);
    loadReceiveSection(rc);
  }

  private void loadAccountsSection(
      Config rc, Map<String, GroupReference> groupsByName) {
    accountsSection = new AccountsSection();
    accountsSection.setSameGroupVisibility(loadPermissionRules(
        rc, ACCOUNTS, null, KEY_SAME_GROUP_VISIBILITY, groupsByName, false));
  }

  private void loadContributorAgreements(
      Config rc, Map<String, GroupReference> groupsByName) {
    contributorAgreements = new HashMap<>();
    for (String name : rc.getSubsections(CONTRIBUTOR_AGREEMENT)) {
      ContributorAgreement ca = getContributorAgreement(name, true);
      ca.setDescription(rc.getString(CONTRIBUTOR_AGREEMENT, name, KEY_DESCRIPTION));
      ca.setRequireContactInformation(
          rc.getBoolean(CONTRIBUTOR_AGREEMENT, name, KEY_REQUIRE_CONTACT_INFORMATION, false));
      ca.setAgreementUrl(rc.getString(CONTRIBUTOR_AGREEMENT, name, KEY_AGREEMENT_URL));
      ca.setAccepted(loadPermissionRules(
          rc, CONTRIBUTOR_AGREEMENT, name, KEY_ACCEPTED, groupsByName, false));

      List<PermissionRule> rules = loadPermissionRules(
          rc, CONTRIBUTOR_AGREEMENT, name, KEY_AUTO_VERIFY, groupsByName, false);
      if (rules.isEmpty()) {
        ca.setAutoVerify(null);
      } else if (rules.size() > 1) {
        error(new ValidationError(PROJECT_CONFIG, "Invalid rule in "
            + CONTRIBUTOR_AGREEMENT
            + "." + name
            + "." + KEY_AUTO_VERIFY
            + ": at most one group may be set"));
      } else if (rules.get(0).getAction() != Action.ALLOW) {
        error(new ValidationError(PROJECT_CONFIG, "Invalid rule in "
            + CONTRIBUTOR_AGREEMENT
            + "." + name
            + "." + KEY_AUTO_VERIFY
            + ": the group must be allowed"));
      } else {
        ca.setAutoVerify(rules.get(0).getGroup());
      }
    }
  }

  /**
   * Parses the [notify] sections out of the configuration file.
   *
   * <pre>
   *   [notify "reviewers"]
   *     email = group Reviewers
   *     type = new_changes
   *
   *   [notify "dev-team"]
   *     email = dev-team@example.com
   *     filter = branch:master
   *
   *   [notify "qa"]
   *     email = qa@example.com
   *     filter = branch:\"^(maint|stable)-.*\"
   *     type = submitted_changes
   * </pre>
   */
  private void loadNotifySections(
      Config rc, Map<String, GroupReference> groupsByName) {
    notifySections = Maps.newHashMap();
    for (String sectionName : rc.getSubsections(NOTIFY)) {
      NotifyConfig n = new NotifyConfig();
      n.setName(sectionName);
      n.setFilter(rc.getString(NOTIFY, sectionName, KEY_FILTER));

      EnumSet<NotifyType> types = EnumSet.noneOf(NotifyType.class);
      types.addAll(ConfigUtil.getEnumList(rc,
          NOTIFY, sectionName, KEY_TYPE,
          NotifyType.ALL));
      n.setTypes(types);
      n.setHeader(rc.getEnum(NOTIFY, sectionName, KEY_HEADER,
          NotifyConfig.Header.BCC));

      for (String dst : rc.getStringList(NOTIFY, sectionName, KEY_EMAIL)) {
        if (dst.startsWith("group ")) {
          String groupName = dst.substring(6).trim();
          GroupReference ref = groupsByName.get(groupName);
          if (ref == null) {
            ref = new GroupReference(null, groupName);
            groupsByName.put(ref.getName(), ref);
          }
          if (ref.getUUID() != null) {
            n.addEmail(ref);
          } else {
            error(new ValidationError(PROJECT_CONFIG,
                "group \"" + ref.getName() + "\" not in " + GroupList.FILE_NAME));
          }
        } else if (dst.startsWith("user ")) {
          error(new ValidationError(PROJECT_CONFIG, dst + " not supported"));
        } else {
          try {
            n.addEmail(Address.parse(dst));
          } catch (IllegalArgumentException err) {
            error(new ValidationError(PROJECT_CONFIG,
                "notify section \"" + sectionName + "\" has invalid email \"" + dst + "\""));
          }
        }
      }
      notifySections.put(sectionName, n);
    }
  }

  private void loadAccessSections(
      Config rc, Map<String, GroupReference> groupsByName) {
    accessSections = new HashMap<>();
    for (String refName : rc.getSubsections(ACCESS)) {
      if (RefConfigSection.isValid(refName)) {
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
                perm, Permission.hasRange(varName));
          }
        }
      }
    }

    AccessSection capability = null;
    for (String varName : rc.getNames(CAPABILITY)) {
      if (capability == null) {
        capability = new AccessSection(AccessSection.GLOBAL_CAPABILITIES);
        accessSections.put(AccessSection.GLOBAL_CAPABILITIES, capability);
      }
      Permission perm = capability.getPermission(varName, true);
      loadPermissionRules(rc, CAPABILITY, null, varName, groupsByName, perm,
          GlobalCapability.hasRange(varName));
    }
  }

  private void loadBranchOrderSection(Config rc) {
    if (rc.getSections().contains(BRANCH_ORDER)) {
      branchOrderSection = new BranchOrderSection(
          rc.getStringList(BRANCH_ORDER, null, BRANCH));
    }
  }

  private List<PermissionRule> loadPermissionRules(Config rc, String section,
      String subsection, String varName,
      Map<String, GroupReference> groupsByName,
      boolean useRange) {
    Permission perm = new Permission(varName);
    loadPermissionRules(rc, section, subsection, varName, groupsByName, perm, useRange);
    return perm.getRules();
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
            "group \"" + ref.getName() + "\" not in " + GroupList.FILE_NAME));
      }

      rule.setGroup(ref);
      perm.add(rule);
    }
  }

  private static LabelValue parseLabelValue(String src) {
    List<String> parts = ImmutableList.copyOf(
        Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().limit(2)
        .split(src));
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("empty value");
    }
    String valueText = parts.size() > 1 ? parts.get(1) : "";
    return new LabelValue(
        Shorts.checkedCast(PermissionRule.parseInt(parts.get(0))),
        valueText);
  }

  private void loadLabelSections(Config rc) {
    Map<String, String> lowerNames = Maps.newHashMapWithExpectedSize(2);
    labelSections = Maps.newLinkedHashMap();
    for (String name : rc.getSubsections(LABEL)) {
      String lower = name.toLowerCase();
      if (lowerNames.containsKey(lower)) {
        error(new ValidationError(PROJECT_CONFIG, String.format(
            "Label \"%s\" conflicts with \"%s\"",
            name, lowerNames.get(lower))));
      }
      lowerNames.put(lower, name);

      List<LabelValue> values = Lists.newArrayList();
      for (String value : rc.getStringList(LABEL, name, KEY_VALUE)) {
        try {
          values.add(parseLabelValue(value));
        } catch (IllegalArgumentException notValue) {
          error(new ValidationError(PROJECT_CONFIG, String.format(
              "Invalid %s \"%s\" for label \"%s\": %s",
              KEY_VALUE, value, name, notValue.getMessage())));
        }
      }

      LabelType label;
      try {
        label = new LabelType(name, values);
      } catch (IllegalArgumentException badName) {
        error(new ValidationError(PROJECT_CONFIG, String.format(
            "Invalid label \"%s\"", name)));
        continue;
      }

      String functionName = MoreObjects.firstNonNull(
          rc.getString(LABEL, name, KEY_FUNCTION), "MaxWithBlock");
      if (LABEL_FUNCTIONS.contains(functionName)) {
        label.setFunctionName(functionName);
      } else {
        error(new ValidationError(PROJECT_CONFIG, String.format(
            "Invalid %s for label \"%s\". Valid names are: %s",
            KEY_FUNCTION, name, Joiner.on(", ").join(LABEL_FUNCTIONS))));
        label.setFunctionName(null);
      }

      short dv = (short) rc.getInt(LABEL, name, KEY_DEFAULT_VALUE, 0);
      if (isInRange(dv, values)) {
        label.setDefaultValue(dv);
      } else {
        error(new ValidationError(PROJECT_CONFIG, String.format(
            "Invalid %s \"%s\" for label \"%s\"",
            KEY_DEFAULT_VALUE, dv, name)));
      }
      label.setCopyMinScore(
          rc.getBoolean(LABEL, name, KEY_COPY_MIN_SCORE,
              LabelType.DEF_COPY_MIN_SCORE));
      label.setCopyMaxScore(
          rc.getBoolean(LABEL, name, KEY_COPY_MAX_SCORE,
              LabelType.DEF_COPY_MAX_SCORE));
      label.setCopyAllScoresOnTrivialRebase(
          rc.getBoolean(LABEL, name, KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
              LabelType.DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE));
      label.setCopyAllScoresIfNoCodeChange(
          rc.getBoolean(LABEL, name, KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
              LabelType.DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE));
      label.setCopyAllScoresIfNoChange(
          rc.getBoolean(LABEL, name, KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
              LabelType.DEF_COPY_ALL_SCORES_IF_NO_CHANGE));
      label.setCanOverride(
          rc.getBoolean(LABEL, name, KEY_CAN_OVERRIDE,
              LabelType.DEF_CAN_OVERRIDE));
      label.setRefPatterns(getStringListOrNull(rc, LABEL, name, KEY_Branch));
      labelSections.put(name, label);
    }
  }

  private boolean isInRange(short value, List<LabelValue> labelValues) {
    for (LabelValue lv : labelValues) {
      if (lv.getValue() == value) {
        return true;
      }
    }
    return false;
  }

  private List<String> getStringListOrNull(Config rc, String section,
      String subSection, String name) {
    String[] ac = rc.getStringList(section, subSection, name);
    return ac.length == 0 ? null : Arrays.asList(ac);
  }

  private void loadCommentLinkSections(Config rc) {
    Set<String> subsections = rc.getSubsections(COMMENTLINK);
    commentLinkSections = Lists.newArrayListWithCapacity(subsections.size());
    for (String name : subsections) {
      try {
        commentLinkSections.add(buildCommentLink(rc, name, false));
      } catch (PatternSyntaxException e) {
        error(new ValidationError(PROJECT_CONFIG, String.format(
            "Invalid pattern \"%s\" in commentlink.%s.match: %s",
            rc.getString(COMMENTLINK, name, KEY_MATCH), name, e.getMessage())));
      } catch (IllegalArgumentException e) {
        error(new ValidationError(PROJECT_CONFIG, String.format(
            "Error in pattern \"%s\" in commentlink.%s.match: %s",
            rc.getString(COMMENTLINK, name, KEY_MATCH), name, e.getMessage())));
      }
    }
    commentLinkSections = ImmutableList.copyOf(commentLinkSections);
  }

  private void loadReceiveSection(Config rc) {
    checkReceivedObjects = rc.getBoolean(RECEIVE, KEY_CHECK_RECEIVED_OBJECTS, true);
    maxObjectSizeLimit = rc.getLong(RECEIVE, null, KEY_MAX_OBJECT_SIZE_LIMIT, 0);
  }

  private void loadPluginSections(Config rc) {
    pluginConfigs = Maps.newHashMap();
    for (String plugin : rc.getSubsections(PLUGIN)) {
      Config pluginConfig = new Config();
      pluginConfigs.put(plugin, pluginConfig);
      for (String name : rc.getNames(PLUGIN, plugin)) {
        pluginConfig.setStringList(PLUGIN, plugin, name,
            Arrays.asList(rc.getStringList(PLUGIN, plugin, name)));
      }
    }
  }

  public PluginConfig getPluginConfig(String pluginName) {
    Config pluginConfig = pluginConfigs.get(pluginName);
    if (pluginConfig == null) {
      pluginConfig = new Config();
      pluginConfigs.put(pluginName, pluginConfig);
    }
    return new PluginConfig(pluginName, pluginConfig, this);
  }

  private void readGroupList() throws IOException {
    groupList = GroupList.parse(readUTF8(GroupList.FILE_NAME), this);
  }

  private Map<String, GroupReference> mapGroupReferences() {
    Collection<GroupReference> references = groupList.references();
    Map<String, GroupReference> result = new HashMap<>(references.size());
    for (GroupReference ref : references) {
      result.put(ref.getName(), ref);
    }

    return result;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException,
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

    set(rc, RECEIVE, null, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, p.getUseContributorAgreements(), InheritableBoolean.INHERIT);
    set(rc, RECEIVE, null, KEY_REQUIRE_SIGNED_OFF_BY, p.getUseSignedOffBy(), InheritableBoolean.INHERIT);
    set(rc, RECEIVE, null, KEY_REQUIRE_CHANGE_ID, p.getRequireChangeID(), InheritableBoolean.INHERIT);
    set(rc, RECEIVE, null, KEY_USE_ALL_NOT_IN_TARGET, p.getCreateNewChangeForAllNotInTarget(), InheritableBoolean.INHERIT);
    set(rc, RECEIVE, null, KEY_MAX_OBJECT_SIZE_LIMIT, validMaxObjectSizeLimit(p.getMaxObjectSizeLimit()));

    set(rc, SUBMIT, null, KEY_ACTION, p.getSubmitType(), defaultSubmitAction);
    set(rc, SUBMIT, null, KEY_MERGE_CONTENT, p.getUseContentMerge(), InheritableBoolean.INHERIT);

    set(rc, PROJECT, null, KEY_STATE, p.getState(), defaultStateValue);

    set(rc, DASHBOARD, null, KEY_DEFAULT, p.getDefaultDashboard());
    set(rc, DASHBOARD, null, KEY_LOCAL_DEFAULT, p.getLocalDefaultDashboard());

    Set<AccountGroup.UUID> keepGroups = new HashSet<>();
    saveAccountsSection(rc, keepGroups);
    saveContributorAgreements(rc, keepGroups);
    saveAccessSections(rc, keepGroups);
    saveNotifySections(rc, keepGroups);
    groupList.retainUUIDs(keepGroups);
    saveLabelSections(rc);
    savePluginSections(rc);

    saveConfig(PROJECT_CONFIG, rc);
    saveGroupList();
    return true;
  }

  public static final String validMaxObjectSizeLimit(String value)
      throws ConfigInvalidException {
    if (value == null) {
      return null;
    }
    value = value.trim();
    if (value.isEmpty()) {
      return null;
    }
    Config cfg = new Config();
    cfg.fromText("[s]\nn=" + value);
    try {
      long s = cfg.getLong("s", "n", 0);
      if (s < 0) {
        throw new ConfigInvalidException(String.format(
            "Negative value '%s' not allowed as %s", value,
            KEY_MAX_OBJECT_SIZE_LIMIT));
      }
      if (s == 0) {
        // return null for the default so that it is not persisted
        return null;
      }
      return value;
    } catch (IllegalArgumentException e) {
      throw new ConfigInvalidException(
          String.format("Value '%s' not parseable as a Long", value), e);
    }
  }

  private void saveAccountsSection(Config rc, Set<AccountGroup.UUID> keepGroups) {
    if (accountsSection != null) {
      rc.setStringList(ACCOUNTS, null, KEY_SAME_GROUP_VISIBILITY,
          ruleToStringList(accountsSection.getSameGroupVisibility(), keepGroups));
    }
  }

  private void saveContributorAgreements(
      Config rc, Set<AccountGroup.UUID> keepGroups) {
    for (ContributorAgreement ca : sort(contributorAgreements.values())) {
      set(rc, CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_DESCRIPTION, ca.getDescription());
      set(rc, CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_REQUIRE_CONTACT_INFORMATION, ca.isRequireContactInformation());
      set(rc, CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_AGREEMENT_URL, ca.getAgreementUrl());

      if (ca.getAutoVerify() != null) {
        if (ca.getAutoVerify().getUUID() != null) {
          keepGroups.add(ca.getAutoVerify().getUUID());
        }
        String autoVerify = new PermissionRule(ca.getAutoVerify()).asString(false);
        set(rc, CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_AUTO_VERIFY, autoVerify);
      } else {
        rc.unset(CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_AUTO_VERIFY);
      }

      rc.setStringList(CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_ACCEPTED,
          ruleToStringList(ca.getAccepted(), keepGroups));
    }
  }

  private void saveNotifySections(
      Config rc, Set<AccountGroup.UUID> keepGroups) {
    for (NotifyConfig nc : sort(notifySections.values())) {
      List<String> email = Lists.newArrayList();
      for (GroupReference gr : nc.getGroups()) {
        if (gr.getUUID() != null) {
          keepGroups.add(gr.getUUID());
        }
        email.add(new PermissionRule(gr).asString(false));
      }
      Collections.sort(email);

      List<String> addrs = Lists.newArrayList();
      for (Address addr : nc.getAddresses()) {
        addrs.add(addr.toString());
      }
      Collections.sort(addrs);
      email.addAll(addrs);

      set(rc, NOTIFY, nc.getName(), KEY_HEADER,
          nc.getHeader(), NotifyConfig.Header.BCC);
      if (email.isEmpty()) {
        rc.unset(NOTIFY, nc.getName(), KEY_EMAIL);
      } else {
        rc.setStringList(NOTIFY, nc.getName(), KEY_EMAIL, email);
      }

      if (nc.getNotify().equals(EnumSet.of(NotifyType.ALL))) {
        rc.unset(NOTIFY, nc.getName(), KEY_TYPE);
      } else {
        List<String> types = Lists.newArrayListWithCapacity(4);
        for (NotifyType t : NotifyType.values()) {
          if (nc.isNotify(t)) {
            types.add(StringUtils.toLowerCase(t.name()));
          }
        }
        rc.setStringList(NOTIFY, nc.getName(), KEY_TYPE, types);
      }

      set(rc, NOTIFY, nc.getName(), KEY_FILTER, nc.getFilter());
    }
  }

  private List<String> ruleToStringList(
      List<PermissionRule> list, Set<AccountGroup.UUID> keepGroups) {
    List<String> rules = new ArrayList<>();
    for (PermissionRule rule : sort(list)) {
      if (rule.getGroup().getUUID() != null) {
        keepGroups.add(rule.getGroup().getUUID());
      }
      rules.add(rule.asString(false));
    }
    return rules;
  }

  private void saveAccessSections(
      Config rc, Set<AccountGroup.UUID> keepGroups) {
    AccessSection capability = accessSections.get(AccessSection.GLOBAL_CAPABILITIES);
    if (capability != null) {
      Set<String> have = new HashSet<>();
      for (Permission permission : sort(capability.getPermissions())) {
        have.add(permission.getName().toLowerCase());

        boolean needRange = GlobalCapability.hasRange(permission.getName());
        List<String> rules = new ArrayList<>();
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
        if (!have.contains(varName.toLowerCase())) {
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

      Set<String> have = new HashSet<>();
      for (Permission permission : sort(as.getPermissions())) {
        have.add(permission.getName().toLowerCase());

        boolean needRange = Permission.hasRange(permission.getName());
        List<String> rules = new ArrayList<>();
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
      if (RefConfigSection.isValid(name) && !accessSections.containsKey(name)) {
        rc.unsetSection(ACCESS, name);
      }
    }
  }

  private void saveLabelSections(Config rc) {
    List<String> existing = Lists.newArrayList(rc.getSubsections(LABEL));
    if (!Lists.newArrayList(labelSections.keySet()).equals(existing)) {
      // Order of sections changed, remove and rewrite them all.
      for (String name : existing) {
        rc.unsetSection(LABEL, name);
      }
    }

    Set<String> toUnset = Sets.newHashSet(existing);
    for (Map.Entry<String, LabelType> e : labelSections.entrySet()) {
      String name = e.getKey();
      LabelType label = e.getValue();
      toUnset.remove(name);
      rc.setString(LABEL, name, KEY_FUNCTION, label.getFunctionName());
      rc.setInt(LABEL, name, KEY_DEFAULT_VALUE, label.getDefaultValue());

      setBooleanConfigKey(rc, name, KEY_COPY_MIN_SCORE, label.isCopyMinScore(),
          LabelType.DEF_COPY_MIN_SCORE);
      setBooleanConfigKey(rc, name, KEY_COPY_MAX_SCORE, label.isCopyMaxScore(),
          LabelType.DEF_COPY_MAX_SCORE);
      setBooleanConfigKey(rc, name, KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
          label.isCopyAllScoresOnTrivialRebase(),
          LabelType.DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
      setBooleanConfigKey(rc, name, KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
          label.isCopyAllScoresIfNoCodeChange(),
          LabelType.DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE);
      setBooleanConfigKey(rc, name, KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
          label.isCopyAllScoresIfNoChange(),
          LabelType.DEF_COPY_ALL_SCORES_IF_NO_CHANGE);
      setBooleanConfigKey(rc, name, KEY_CAN_OVERRIDE, label.canOverride(),
          LabelType.DEF_CAN_OVERRIDE);
      List<String> values =
          Lists.newArrayListWithCapacity(label.getValues().size());
      for (LabelValue value : label.getValues()) {
        values.add(value.format());
      }
      rc.setStringList(LABEL, name, KEY_VALUE, values);
    }

    for (String name : toUnset) {
      rc.unsetSection(LABEL, name);
    }
  }

  private static void setBooleanConfigKey(
      Config rc, String name, String key, boolean value, boolean defaultValue) {
    if (value == defaultValue) {
      rc.unset(LABEL, name, key);
    } else {
      rc.setBoolean(LABEL, name, key, value);
    }
  }

  private void savePluginSections(Config rc) {
    List<String> existing = Lists.newArrayList(rc.getSubsections(PLUGIN));
    for (String name : existing) {
      rc.unsetSection(PLUGIN, name);
    }

    for (Entry<String, Config> e : pluginConfigs.entrySet()) {
      String plugin = e.getKey();
      Config pluginConfig = e.getValue();
      for (String name : pluginConfig.getNames(PLUGIN, plugin)) {
        rc.setStringList(PLUGIN, plugin, name,
            Arrays.asList(pluginConfig.getStringList(PLUGIN, plugin, name)));
      }
    }
  }

  private void saveGroupList() throws IOException {
    saveUTF8(GroupList.FILE_NAME, groupList.asText());
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

  @Override
  public void error(ValidationError error) {
    if (validationErrors == null) {
      validationErrors = new ArrayList<>(4);
    }
    validationErrors.add(error);
  }

  private static <T extends Comparable<? super T>> List<T> sort(Collection<T> m) {
    ArrayList<T> r = new ArrayList<>(m);
    Collections.sort(r);
    return r;
  }
}

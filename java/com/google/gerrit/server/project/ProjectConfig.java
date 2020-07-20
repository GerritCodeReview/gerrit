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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.entities.Permission.isPermission;
import static com.google.gerrit.entities.Project.DEFAULT_SUBMIT_TYPE;
import static com.google.gerrit.server.permissions.PluginPermissionsUtil.isValidPluginPermission;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Shorts;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountsSection;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchOrderSection;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.ConfiguredMimeTypes;
import com.google.gerrit.entities.ContributorAgreement;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.NotifyConfig;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.PermissionRule.Action;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.StoredCommentLinkInfo;
import com.google.gerrit.entities.SubscribeSection;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class ProjectConfig extends VersionedMetaData implements ValidationError.Sink {
  public static final String COMMENTLINK = "commentlink";
  public static final String LABEL = "label";
  public static final String KEY_FUNCTION = "function";
  public static final String KEY_DEFAULT_VALUE = "defaultValue";
  public static final String KEY_COPY_MIN_SCORE = "copyMinScore";
  public static final String KEY_ALLOW_POST_SUBMIT = "allowPostSubmit";
  public static final String KEY_IGNORE_SELF_APPROVAL = "ignoreSelfApproval";
  public static final String KEY_COPY_ANY_SCORE = "copyAnyScore";
  public static final String KEY_COPY_MAX_SCORE = "copyMaxScore";
  public static final String KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE =
      "copyAllScoresOnMergeFirstParentUpdate";
  public static final String KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE = "copyAllScoresOnTrivialRebase";
  public static final String KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE = "copyAllScoresIfNoCodeChange";
  public static final String KEY_COPY_ALL_SCORES_IF_NO_CHANGE = "copyAllScoresIfNoChange";
  public static final String KEY_COPY_VALUE = "copyValue";
  public static final String KEY_VALUE = "value";
  public static final String KEY_CAN_OVERRIDE = "canOverride";
  public static final String KEY_BRANCH = "branch";

  public static final String KEY_MATCH = "match";
  private static final String KEY_HTML = "html";
  public static final String KEY_LINK = "link";
  public static final String KEY_ENABLED = "enabled";

  public static final String PROJECT_CONFIG = "project.config";

  private static final String PROJECT = "project";
  private static final String KEY_DESCRIPTION = "description";

  public static final String ACCESS = "access";
  private static final String KEY_INHERIT_FROM = "inheritFrom";
  private static final String KEY_GROUP_PERMISSIONS = "exclusiveGroupPermissions";

  private static final String ACCOUNTS = "accounts";
  private static final String KEY_SAME_GROUP_VISIBILITY = "sameGroupVisibility";

  private static final String BRANCH_ORDER = "branchOrder";
  private static final String BRANCH = "branch";

  private static final String CONTRIBUTOR_AGREEMENT = "contributor-agreement";
  private static final String KEY_ACCEPTED = "accepted";
  private static final String KEY_AUTO_VERIFY = "autoVerify";
  private static final String KEY_AGREEMENT_URL = "agreementUrl";
  private static final String KEY_MATCH_PROJECTS = "matchProjects";
  private static final String KEY_EXCLUDE_PROJECTS = "excludeProjects";

  private static final String NOTIFY = "notify";
  private static final String KEY_EMAIL = "email";
  private static final String KEY_FILTER = "filter";
  private static final String KEY_TYPE = "type";
  private static final String KEY_HEADER = "header";

  private static final String CAPABILITY = "capability";

  private static final String RECEIVE = "receive";
  private static final String KEY_CHECK_RECEIVED_OBJECTS = "checkReceivedObjects";

  private static final String SUBMIT = "submit";
  private static final String KEY_ACTION = "action";
  private static final String KEY_STATE = "state";

  private static final String KEY_MAX_OBJECT_SIZE_LIMIT = "maxObjectSizeLimit";

  private static final String SUBSCRIBE_SECTION = "allowSuperproject";
  private static final String SUBSCRIBE_MATCH_REFS = "matching";
  private static final String SUBSCRIBE_MULTI_MATCH_REFS = "all";

  private static final String DASHBOARD = "dashboard";
  private static final String KEY_DEFAULT = "default";
  private static final String KEY_LOCAL_DEFAULT = "local-default";

  private static final String LEGACY_PERMISSION_PUSH_TAG = "pushTag";
  private static final String LEGACY_PERMISSION_PUSH_SIGNED_TAG = "pushSignedTag";

  private static final String PLUGIN = "plugin";

  private static final ProjectState DEFAULT_STATE_VALUE = ProjectState.ACTIVE;

  private static final String EXTENSION_PANELS = "extension-panels";
  private static final String KEY_PANEL = "panel";

  private static final Pattern EXCLUSIVE_PERMISSIONS_SPLIT_PATTERN = Pattern.compile("[, \t]{1,}");

  // Don't use an assisted factory, since instances created by an assisted factory retain references
  // to their enclosing injector. Instances of ProjectConfig are cached for a long time in the
  // ProjectCache, so this would retain lots more memory.
  @Singleton
  public static class Factory {
    @Nullable
    public static StoredConfig getBaseConfig(
        SitePaths sitePaths, AllProjectsName allProjects, Project.NameKey projectName) {
      return projectName.equals(allProjects)
          // Delay loading till onLoad method.
          ? new FileBasedConfig(
              sitePaths.etc_dir.resolve(allProjects.get()).resolve(PROJECT_CONFIG).toFile(),
              FS.DETECTED)
          : null;
    }

    private final SitePaths sitePaths;
    private final AllProjectsName allProjects;

    @Inject
    Factory(SitePaths sitePaths, AllProjectsName allProjects) {
      this.sitePaths = sitePaths;
      this.allProjects = allProjects;
    }

    public ProjectConfig create(Project.NameKey projectName) {
      return new ProjectConfig(projectName, getBaseConfig(sitePaths, allProjects, projectName));
    }

    public ProjectConfig read(MetaDataUpdate update) throws IOException, ConfigInvalidException {
      ProjectConfig r = create(update.getProjectName());
      r.load(update);
      return r;
    }

    public ProjectConfig read(MetaDataUpdate update, ObjectId id)
        throws IOException, ConfigInvalidException {
      ProjectConfig r = create(update.getProjectName());
      r.load(update, id);
      return r;
    }

    @UsedAt(UsedAt.Project.COLLABNET)
    public ProjectConfig read(Repository repo, Project.NameKey name)
        throws IOException, ConfigInvalidException {
      ProjectConfig r = create(name);
      r.load(repo);
      return r;
    }
  }

  private final StoredConfig baseConfig;

  private Project project;
  private AccountsSection accountsSection;
  private GroupList groupList;
  private Map<String, AccessSection> accessSections;
  private BranchOrderSection branchOrderSection;
  private Map<String, ContributorAgreement> contributorAgreements;
  private Map<String, NotifyConfig> notifySections;
  private Map<String, LabelType> labelSections;
  private ConfiguredMimeTypes mimeTypes;
  private Map<Project.NameKey, SubscribeSection> subscribeSections;
  private Map<String, StoredCommentLinkInfo> commentLinkSections;
  private List<ValidationError> validationErrors;
  private ObjectId rulesId;
  private long maxObjectSizeLimit;
  private Map<String, Config> pluginConfigs;
  private boolean checkReceivedObjects;
  private Set<String> sectionsWithUnknownPermissions;
  private boolean hasLegacyPermissions;
  private Map<String, List<String>> extensionPanelSections;

  /** Returns an immutable, thread-safe representation of this object that can be cached. */
  public CachedProjectConfig getCacheable() {
    CachedProjectConfig.Builder builder =
        CachedProjectConfig.builder()
            .setProject(project)
            .setAccountsSection(accountsSection)
            .setBranchOrderSection(Optional.ofNullable(branchOrderSection))
            .setMimeTypes(mimeTypes)
            .setRulesId(Optional.ofNullable(rulesId))
            .setRevision(Optional.ofNullable(getRevision()))
            .setMaxObjectSizeLimit(maxObjectSizeLimit)
            .setCheckReceivedObjects(checkReceivedObjects)
            .setExtensionPanelSections(extensionPanelSections);
    groupList.byUUID().values().forEach(g -> builder.addGroup(g));
    accessSections.values().forEach(a -> builder.addAccessSection(a));
    contributorAgreements.values().forEach(c -> builder.addContributorAgreement(c));
    notifySections.values().forEach(n -> builder.addNotifySection(n));
    subscribeSections.values().forEach(s -> builder.addSubscribeSection(s));
    commentLinkSections.values().forEach(c -> builder.addCommentLinkSection(c));
    labelSections.values().forEach(l -> builder.addLabelSection(l));
    pluginConfigs
        .entrySet()
        .forEach(c -> builder.addPluginConfig(c.getKey(), c.getValue().toText()));
    return builder.build();
  }

  public static StoredCommentLinkInfo buildCommentLink(Config cfg, String name, boolean allowRaw)
      throws IllegalArgumentException {
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

    if (Strings.isNullOrEmpty(match)
        && Strings.isNullOrEmpty(link)
        && !hasHtml
        && enabled != null) {
      if (enabled) {
        return StoredCommentLinkInfo.enabled(name);
      }
      return StoredCommentLinkInfo.disabled(name);
    }
    return StoredCommentLinkInfo.builder(name)
        .setMatch(match)
        .setLink(link)
        .setHtml(html)
        .setEnabled(enabled)
        .setOverrideOnly(false)
        .build();
  }

  public void addCommentLinkSection(StoredCommentLinkInfo commentLink) {
    commentLinkSections.put(commentLink.getName(), commentLink);
  }

  public void removeCommentLinkSection(String name) {
    requireNonNull(name);
    requireNonNull(commentLinkSections.remove(name));
  }

  private ProjectConfig(Project.NameKey projectName, @Nullable StoredConfig baseConfig) {
    this.projectName = projectName;
    this.baseConfig = baseConfig;
  }

  public void load(Repository repo) throws IOException, ConfigInvalidException {
    super.load(projectName, repo);
  }

  public void load(Repository repo, @Nullable ObjectId revision)
      throws IOException, ConfigInvalidException {
    super.load(projectName, repo, revision);
  }

  public void load(RevWalk rw, @Nullable ObjectId revision)
      throws IOException, ConfigInvalidException {
    super.load(projectName, rw, revision);
  }

  public Project.NameKey getName() {
    return projectName;
  }

  public Project getProject() {
    return project;
  }

  public void setProject(Project.Builder project) {
    this.project = project.build();
  }

  public void updateProject(Consumer<Project.Builder> update) {
    Project.Builder builder = project.toBuilder();
    update.accept(builder);
    project = builder.build();
  }

  public AccountsSection getAccountsSection() {
    return accountsSection;
  }

  public void setAccountsSection(AccountsSection accountsSection) {
    this.accountsSection = accountsSection;
  }

  public AccessSection getAccessSection(String name) {
    return accessSections.get(name);
  }

  public void upsertAccessSection(String name, Consumer<AccessSection.Builder> update) {
    AccessSection.Builder accessSectionBuilder =
        accessSections.containsKey(name)
            ? accessSections.get(name).toBuilder()
            : AccessSection.builder(name);
    update.accept(accessSectionBuilder);
    accessSections.put(name, accessSectionBuilder.build());
  }

  public Collection<AccessSection> getAccessSections() {
    return sort(accessSections.values());
  }

  public BranchOrderSection getBranchOrderSection() {
    return branchOrderSection;
  }

  public void setBranchOrderSection(BranchOrderSection branchOrderSection) {
    this.branchOrderSection = branchOrderSection;
  }

  public Map<Project.NameKey, SubscribeSection> getSubscribeSections() {
    return subscribeSections;
  }

  public void addSubscribeSection(SubscribeSection s) {
    subscribeSections.put(s.project(), s);
  }

  public void remove(AccessSection section) {
    if (section != null) {
      String name = section.getName();
      if (sectionsWithUnknownPermissions.contains(name)) {
        AccessSection.Builder a = accessSections.get(name).toBuilder();
        a.modifyPermissions(List::clear);
        accessSections.put(name, a.build());
      } else {
        accessSections.remove(name);
      }
    }
  }

  public void remove(AccessSection section, Permission permission) {
    if (permission == null) {
      remove(section);
    } else if (section != null) {
      AccessSection a =
          accessSections.get(section.getName()).toBuilder().remove(permission.toBuilder()).build();
      accessSections.put(section.getName(), a);
      if (a.getPermissions().isEmpty()) {
        remove(a);
      }
    }
  }

  public void remove(AccessSection section, Permission permission, PermissionRule rule) {
    if (rule == null) {
      remove(section, permission);
    } else if (section != null && permission != null) {
      AccessSection a = accessSections.get(section.getName());
      if (a == null) {
        return;
      }
      Permission p = a.getPermission(permission.getName());
      if (p == null) {
        return;
      }
      AccessSection.Builder accessSectionBuilder = a.toBuilder();
      Permission.Builder permissionBuilder =
          accessSectionBuilder.upsertPermission(permission.getName());
      permissionBuilder.remove(rule);
      if (permissionBuilder.build().getRules().isEmpty()) {
        accessSectionBuilder.remove(permissionBuilder);
      }
      a = accessSectionBuilder.build();
      accessSections.put(section.getName(), a);
      if (a.getPermissions().isEmpty()) {
        remove(a);
      }
    }
  }

  public ContributorAgreement getContributorAgreement(String name) {
    return contributorAgreements.get(name);
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
    ContributorAgreement.Builder ca = section.toBuilder();
    ca.setAutoVerify(resolve(section.getAutoVerify()));
    ImmutableList.Builder<PermissionRule> newRules = ImmutableList.builder();
    for (PermissionRule rule : section.getAccepted()) {
      newRules.add(rule.toBuilder().setGroup(resolve(rule.getGroup())).build());
    }
    ca.setAccepted(newRules.build());

    contributorAgreements.put(section.getName(), ca.build());
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

  /** Adds or replaces the given {@link LabelType} in this config. */
  public void upsertLabelType(LabelType labelType) {
    labelSections.put(labelType.getName(), labelType);
  }

  /** Allows a mutation of an existing {@link LabelType}. */
  public void updateLabelType(String name, Consumer<LabelType.Builder> update) {
    LabelType labelType = labelSections.get(name);
    checkState(labelType != null, "labelType must not be null");
    LabelType.Builder builder = labelSections.get(name).toBuilder();
    update.accept(builder);
    upsertLabelType(builder.build());
  }

  /** Adds or replaces the given {@link ContributorAgreement} in this config. */
  public void upsertContributorAgreement(ContributorAgreement ca) {
    contributorAgreements.remove(ca.getName());
    contributorAgreements.put(ca.getName(), ca);
  }

  public Collection<StoredCommentLinkInfo> getCommentLinkSections() {
    return commentLinkSections.values();
  }

  public ConfiguredMimeTypes getMimeTypes() {
    return mimeTypes;
  }

  public GroupReference resolve(GroupReference group) {
    return groupList.resolve(group);
  }

  public void renameGroup(AccountGroup.UUID uuid, String newName) {
    groupList.renameGroup(uuid, newName);
  }

  /** @return the group reference, if the group is used by at least one rule. */
  public GroupReference getGroup(AccountGroup.UUID uuid) {
    return groupList.byUUID(uuid);
  }

  /**
   * @return the group reference corresponding to the specified group name if the group is used by
   *     at least one rule or plugin value.
   */
  public GroupReference getGroup(String groupName) {
    return groupList.byName(groupName);
  }

  /**
   * @return the project's rules.pl ObjectId, if present in the branch. Null if it doesn't exist.
   */
  public ObjectId getRulesId() {
    return rulesId;
  }

  /** @return the maxObjectSizeLimit configured on this project, or zero if not configured. */
  public long getMaxObjectSizeLimit() {
    return maxObjectSizeLimit;
  }

  /** @return the checkReceivedObjects for this project, default is true. */
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
        groupList.renameGroup(ref.getUUID(), g.getName());
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
    }
    return Collections.emptyList();
  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (baseConfig != null) {
      baseConfig.load();
    }
    readGroupList();

    rulesId = getObjectId("rules.pl");
    Config rc = readConfig(PROJECT_CONFIG, baseConfig);
    Project.Builder p = Project.builder(projectName);
    p.setDescription(Strings.nullToEmpty(rc.getString(PROJECT, null, KEY_DESCRIPTION)));
    if (revision != null) {
      p.setConfigRefState(revision.toObjectId().name());
    }

    if (rc.getStringList(ACCESS, null, KEY_INHERIT_FROM).length > 1) {
      // The config must not contain more than one parent to inherit from
      // as there is no guarantee which of the parents would be used then.
      error(ValidationError.create(PROJECT_CONFIG, "Cannot inherit from multiple projects"));
    }
    p.setParent(rc.getString(ACCESS, null, KEY_INHERIT_FROM));

    for (BooleanProjectConfig config : BooleanProjectConfig.values()) {
      p.setBooleanConfig(
          config,
          getEnum(
              rc,
              config.getSection(),
              config.getSubSection(),
              config.getName(),
              InheritableBoolean.INHERIT));
    }

    p.setMaxObjectSizeLimit(rc.getString(RECEIVE, null, KEY_MAX_OBJECT_SIZE_LIMIT));

    p.setSubmitType(getEnum(rc, SUBMIT, null, KEY_ACTION, DEFAULT_SUBMIT_TYPE));
    p.setState(getEnum(rc, PROJECT, null, KEY_STATE, DEFAULT_STATE_VALUE));

    p.setDefaultDashboard(rc.getString(DASHBOARD, null, KEY_DEFAULT));
    p.setLocalDefaultDashboard(rc.getString(DASHBOARD, null, KEY_LOCAL_DEFAULT));
    this.project = p.build();

    loadAccountsSection(rc);
    loadContributorAgreements(rc);
    loadAccessSections(rc);
    loadBranchOrderSection(rc);
    loadNotifySections(rc);
    loadLabelSections(rc);
    loadCommentLinkSections(rc);
    loadSubscribeSections(rc);
    mimeTypes = ConfiguredMimeTypes.create(projectName.get(), rc);
    loadPluginSections(rc);
    loadReceiveSection(rc);
    loadExtensionPanelSections(rc);
  }

  private void loadAccountsSection(Config rc) {
    accountsSection =
        AccountsSection.create(
            loadPermissionRules(rc, ACCOUNTS, null, KEY_SAME_GROUP_VISIBILITY, false));
  }

  private void loadExtensionPanelSections(Config rc) {
    Map<String, String> lowerNames = Maps.newHashMapWithExpectedSize(2);
    extensionPanelSections = new LinkedHashMap<>();
    for (String name : rc.getSubsections(EXTENSION_PANELS)) {
      String lower = name.toLowerCase();
      if (lowerNames.containsKey(lower)) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                String.format(
                    "Extension Panels \"%s\" conflicts with \"%s\"", name, lowerNames.get(lower))));
      }
      lowerNames.put(lower, name);
      extensionPanelSections.put(
          name,
          new ArrayList<>(Arrays.asList(rc.getStringList(EXTENSION_PANELS, name, KEY_PANEL))));
    }
  }

  private void loadContributorAgreements(Config rc) {
    contributorAgreements = new HashMap<>();
    for (String name : rc.getSubsections(CONTRIBUTOR_AGREEMENT)) {
      ContributorAgreement.Builder ca = ContributorAgreement.builder(name);
      ca.setDescription(rc.getString(CONTRIBUTOR_AGREEMENT, name, KEY_DESCRIPTION));
      ca.setAgreementUrl(rc.getString(CONTRIBUTOR_AGREEMENT, name, KEY_AGREEMENT_URL));
      ca.setAccepted(loadPermissionRules(rc, CONTRIBUTOR_AGREEMENT, name, KEY_ACCEPTED, false));
      ca.setExcludeProjectsRegexes(
          loadPatterns(rc, CONTRIBUTOR_AGREEMENT, name, KEY_EXCLUDE_PROJECTS));
      ca.setMatchProjectsRegexes(loadPatterns(rc, CONTRIBUTOR_AGREEMENT, name, KEY_MATCH_PROJECTS));

      List<PermissionRule> rules =
          loadPermissionRules(rc, CONTRIBUTOR_AGREEMENT, name, KEY_AUTO_VERIFY, false);
      if (rules.isEmpty()) {
        ca.setAutoVerify(null);
      } else if (rules.size() > 1) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                "Invalid rule in "
                    + CONTRIBUTOR_AGREEMENT
                    + "."
                    + name
                    + "."
                    + KEY_AUTO_VERIFY
                    + ": at most one group may be set"));
      } else if (rules.get(0).getAction() != Action.ALLOW) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                "Invalid rule in "
                    + CONTRIBUTOR_AGREEMENT
                    + "."
                    + name
                    + "."
                    + KEY_AUTO_VERIFY
                    + ": the group must be allowed"));
      } else {
        ca.setAutoVerify(rules.get(0).getGroup());
      }
      contributorAgreements.put(name, ca.build());
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
  private void loadNotifySections(Config rc) {
    notifySections = new HashMap<>();
    for (String sectionName : rc.getSubsections(NOTIFY)) {
      NotifyConfig.Builder n = NotifyConfig.builder();
      n.setName(sectionName);
      n.setFilter(rc.getString(NOTIFY, sectionName, KEY_FILTER));

      EnumSet<NotifyType> types = EnumSet.noneOf(NotifyType.class);
      types.addAll(ConfigUtil.getEnumList(rc, NOTIFY, sectionName, KEY_TYPE, NotifyType.ALL));
      n.setNotify(types);
      n.setHeader(rc.getEnum(NOTIFY, sectionName, KEY_HEADER, NotifyConfig.Header.BCC));

      for (String dst : rc.getStringList(NOTIFY, sectionName, KEY_EMAIL)) {
        String groupName = GroupReference.extractGroupName(dst);
        if (groupName != null) {
          GroupReference ref = groupList.byName(groupName);
          if (ref == null) {
            ref = groupList.resolve(GroupReference.create(groupName));
          }
          if (ref.getUUID() != null) {
            n.addGroup(ref);
          } else {
            error(
                ValidationError.create(
                    PROJECT_CONFIG,
                    "group \"" + ref.getName() + "\" not in " + GroupList.FILE_NAME));
          }
        } else if (dst.startsWith("user ")) {
          error(ValidationError.create(PROJECT_CONFIG, dst + " not supported"));
        } else {
          try {
            n.addAddress(Address.parse(dst));
          } catch (IllegalArgumentException err) {
            error(
                ValidationError.create(
                    PROJECT_CONFIG,
                    "notify section \"" + sectionName + "\" has invalid email \"" + dst + "\""));
          }
        }
      }
      notifySections.put(sectionName, n.build());
    }
  }

  private void loadAccessSections(Config rc) {
    accessSections = new HashMap<>();
    sectionsWithUnknownPermissions = new HashSet<>();
    for (String refName : rc.getSubsections(ACCESS)) {
      if (AccessSection.isValidRefSectionName(refName) && isValidRegex(refName)) {
        upsertAccessSection(
            refName,
            as -> {
              for (String varName : rc.getStringList(ACCESS, refName, KEY_GROUP_PERMISSIONS)) {
                for (String n : Splitter.on(EXCLUSIVE_PERMISSIONS_SPLIT_PATTERN).split(varName)) {
                  n = convertLegacyPermission(n);
                  if (isCoreOrPluginPermission(n)) {
                    as.upsertPermission(n).setExclusiveGroup(true);
                  }
                }
              }

              for (String varName : rc.getNames(ACCESS, refName)) {
                String convertedName = convertLegacyPermission(varName);
                if (isCoreOrPluginPermission(convertedName)) {
                  Permission.Builder perm = as.upsertPermission(convertedName);
                  loadPermissionRules(
                      rc, ACCESS, refName, varName, perm, Permission.hasRange(convertedName));
                } else {
                  sectionsWithUnknownPermissions.add(as.getName());
                }
              }
            });
      }
    }

    AccessSection.Builder capability = null;
    for (String varName : rc.getNames(CAPABILITY)) {
      if (capability == null) {
        capability = AccessSection.builder(AccessSection.GLOBAL_CAPABILITIES);
        accessSections.put(AccessSection.GLOBAL_CAPABILITIES, capability.build());
      }
      Permission.Builder perm = capability.upsertPermission(varName);
      loadPermissionRules(rc, CAPABILITY, null, varName, perm, GlobalCapability.hasRange(varName));
      accessSections.put(AccessSection.GLOBAL_CAPABILITIES, capability.build());
    }
  }

  private boolean isCoreOrPluginPermission(String permission) {
    // Since plugins are loaded dynamically, here we can't load all plugin permissions and verify
    // their existence.
    return isPermission(permission) || isValidPluginPermission(permission);
  }

  private boolean isValidRegex(String refPattern) {
    try {
      RefPattern.validateRegExp(refPattern);
    } catch (InvalidNameException e) {
      error(ValidationError.create(PROJECT_CONFIG, "Invalid ref name: " + e.getMessage()));
      return false;
    }
    return true;
  }

  private void loadBranchOrderSection(Config rc) {
    if (rc.getSections().contains(BRANCH_ORDER)) {
      branchOrderSection =
          BranchOrderSection.create(Arrays.asList(rc.getStringList(BRANCH_ORDER, null, BRANCH)));
    }
  }

  private void saveBranchOrderSection(Config rc) {
    if (branchOrderSection != null) {
      rc.setStringList(BRANCH_ORDER, null, BRANCH, branchOrderSection.order());
    }
  }

  private ImmutableList<String> loadPatterns(
      Config rc, String section, String subsection, String varName) {
    ImmutableList.Builder<String> patterns = ImmutableList.builder();
    for (String patternString : rc.getStringList(section, subsection, varName)) {
      try {
        // While one could just use getStringList directly, compiling first will cause the server
        // to fail fast if any of the patterns are invalid.
        patterns.add(Pattern.compile(patternString).pattern());
      } catch (PatternSyntaxException e) {
        error(
            ValidationError.create(
                PROJECT_CONFIG, "Invalid regular expression: " + e.getMessage()));
        continue;
      }
    }
    return patterns.build();
  }

  private ImmutableList<PermissionRule> loadPermissionRules(
      Config rc, String section, String subsection, String varName, boolean useRange) {
    Permission.Builder perm = Permission.builder(varName);
    loadPermissionRules(rc, section, subsection, varName, perm, useRange);
    return ImmutableList.copyOf(perm.build().getRules());
  }

  private void loadPermissionRules(
      Config rc,
      String section,
      String subsection,
      String varName,
      Permission.Builder perm,
      boolean useRange) {
    for (String ruleString : rc.getStringList(section, subsection, varName)) {
      PermissionRule rule;
      try {
        rule = PermissionRule.fromString(ruleString, useRange);
      } catch (IllegalArgumentException notRule) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                "Invalid rule in "
                    + section
                    + (subsection != null ? "." + subsection : "")
                    + "."
                    + varName
                    + ": "
                    + notRule.getMessage()));
        continue;
      }

      GroupReference ref = groupList.byName(rule.getGroup().getName());
      if (ref == null) {
        // The group wasn't mentioned in the groups table, so there is
        // no valid UUID for it. Pool the reference anyway so at least
        // all rules in the same file share the same GroupReference.
        //
        ref = groupList.resolve(rule.getGroup());
        error(
            ValidationError.create(
                PROJECT_CONFIG, "group \"" + ref.getName() + "\" not in " + GroupList.FILE_NAME));
      }

      perm.add(rule.toBuilder().setGroup(ref));
    }
  }

  private static LabelValue parseLabelValue(String src) {
    List<String> parts =
        ImmutableList.copyOf(
            Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().limit(2).split(src));
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("empty value");
    }
    String valueText = parts.size() > 1 ? parts.get(1) : "";
    return LabelValue.create(Shorts.checkedCast(PermissionRule.parseInt(parts.get(0))), valueText);
  }

  private void loadLabelSections(Config rc) {
    Map<String, String> lowerNames = Maps.newHashMapWithExpectedSize(2);
    labelSections = new LinkedHashMap<>();
    for (String name : rc.getSubsections(LABEL)) {
      String lower = name.toLowerCase();
      if (lowerNames.containsKey(lower)) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                String.format("Label \"%s\" conflicts with \"%s\"", name, lowerNames.get(lower))));
      }
      lowerNames.put(lower, name);

      List<LabelValue> values = new ArrayList<>();
      Set<Short> allValues = new HashSet<>();
      for (String value : rc.getStringList(LABEL, name, KEY_VALUE)) {
        try {
          LabelValue labelValue = parseLabelValue(value);
          if (allValues.add(labelValue.getValue())) {
            values.add(labelValue);
          } else {
            error(
                ValidationError.create(
                    PROJECT_CONFIG,
                    String.format("Duplicate %s \"%s\" for label \"%s\"", KEY_VALUE, value, name)));
          }
        } catch (IllegalArgumentException notValue) {
          error(
              ValidationError.create(
                  PROJECT_CONFIG,
                  String.format(
                      "Invalid %s \"%s\" for label \"%s\": %s",
                      KEY_VALUE, value, name, notValue.getMessage())));
        }
      }

      LabelType.Builder label;
      try {
        label = LabelType.builder(name, values);
      } catch (IllegalArgumentException badName) {
        error(ValidationError.create(PROJECT_CONFIG, String.format("Invalid label \"%s\"", name)));
        continue;
      }

      String functionName = rc.getString(LABEL, name, KEY_FUNCTION);
      Optional<LabelFunction> function =
          functionName != null
              ? LabelFunction.parse(functionName)
              : Optional.of(LabelFunction.MAX_WITH_BLOCK);
      if (!function.isPresent()) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                String.format(
                    "Invalid %s for label \"%s\". Valid names are: %s",
                    KEY_FUNCTION, name, Joiner.on(", ").join(LabelFunction.ALL.keySet()))));
      }
      label.setFunction(function.orElse(null));

      if (!values.isEmpty()) {
        short dv = (short) rc.getInt(LABEL, name, KEY_DEFAULT_VALUE, 0);
        if (isInRange(dv, values)) {
          label.setDefaultValue(dv);
        } else {
          error(
              ValidationError.create(
                  PROJECT_CONFIG,
                  String.format(
                      "Invalid %s \"%s\" for label \"%s\"", KEY_DEFAULT_VALUE, dv, name)));
        }
      }
      label.setAllowPostSubmit(
          rc.getBoolean(LABEL, name, KEY_ALLOW_POST_SUBMIT, LabelType.DEF_ALLOW_POST_SUBMIT));
      label.setIgnoreSelfApproval(
          rc.getBoolean(LABEL, name, KEY_IGNORE_SELF_APPROVAL, LabelType.DEF_IGNORE_SELF_APPROVAL));
      label.setCopyAnyScore(
          rc.getBoolean(LABEL, name, KEY_COPY_ANY_SCORE, LabelType.DEF_COPY_ANY_SCORE));
      label.setCopyMinScore(
          rc.getBoolean(LABEL, name, KEY_COPY_MIN_SCORE, LabelType.DEF_COPY_MIN_SCORE));
      label.setCopyMaxScore(
          rc.getBoolean(LABEL, name, KEY_COPY_MAX_SCORE, LabelType.DEF_COPY_MAX_SCORE));
      label.setCopyAllScoresOnMergeFirstParentUpdate(
          rc.getBoolean(
              LABEL,
              name,
              KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
              LabelType.DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE));
      label.setCopyAllScoresOnTrivialRebase(
          rc.getBoolean(
              LABEL,
              name,
              KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
              LabelType.DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE));
      label.setCopyAllScoresIfNoCodeChange(
          rc.getBoolean(
              LABEL,
              name,
              KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
              LabelType.DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE));
      label.setCopyAllScoresIfNoChange(
          rc.getBoolean(
              LABEL,
              name,
              KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
              LabelType.DEF_COPY_ALL_SCORES_IF_NO_CHANGE));
      Set<Short> copyValues = new HashSet<>();
      for (String value : rc.getStringList(LABEL, name, KEY_COPY_VALUE)) {
        try {
          short copyValue = Shorts.checkedCast(PermissionRule.parseInt(value));
          if (!copyValues.add(copyValue)) {
            error(
                ValidationError.create(
                    PROJECT_CONFIG,
                    String.format(
                        "Duplicate %s \"%s\" for label \"%s\"", KEY_COPY_VALUE, value, name)));
          }
        } catch (IllegalArgumentException notValue) {
          error(
              ValidationError.create(
                  PROJECT_CONFIG,
                  String.format(
                      "Invalid %s \"%s\" for label \"%s\": %s",
                      KEY_COPY_VALUE, value, name, notValue.getMessage())));
        }
      }
      label.setCopyValues(copyValues);
      label.setCanOverride(
          rc.getBoolean(LABEL, name, KEY_CAN_OVERRIDE, LabelType.DEF_CAN_OVERRIDE));
      List<String> refPatterns = getStringListOrNull(rc, LABEL, name, KEY_BRANCH);
      label.setRefPatterns(refPatterns == null ? null : ImmutableList.copyOf(refPatterns));
      labelSections.put(name, label.build());
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

  private List<String> getStringListOrNull(
      Config rc, String section, String subSection, String name) {
    String[] ac = rc.getStringList(section, subSection, name);
    return ac.length == 0 ? null : Arrays.asList(ac);
  }

  private void loadCommentLinkSections(Config rc) {
    Set<String> subsections = rc.getSubsections(COMMENTLINK);
    commentLinkSections = new LinkedHashMap<>(subsections.size());
    for (String name : subsections) {
      try {
        commentLinkSections.put(name, buildCommentLink(rc, name, false));
      } catch (PatternSyntaxException e) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                String.format(
                    "Invalid pattern \"%s\" in commentlink.%s.match: %s",
                    rc.getString(COMMENTLINK, name, KEY_MATCH), name, e.getMessage())));
      } catch (IllegalArgumentException e) {
        error(
            ValidationError.create(
                PROJECT_CONFIG,
                String.format(
                    "Error in pattern \"%s\" in commentlink.%s.match: %s",
                    rc.getString(COMMENTLINK, name, KEY_MATCH), name, e.getMessage())));
      }
    }
  }

  private void loadSubscribeSections(Config rc) throws ConfigInvalidException {
    Set<String> subsections = rc.getSubsections(SUBSCRIBE_SECTION);
    subscribeSections = new HashMap<>();
    try {
      for (String projectName : subsections) {
        Project.NameKey p = Project.nameKey(projectName);
        SubscribeSection.Builder ss = SubscribeSection.builder(p);
        for (String s :
            rc.getStringList(SUBSCRIBE_SECTION, projectName, SUBSCRIBE_MULTI_MATCH_REFS)) {
          ss.addMultiMatchRefSpec(s);
        }
        for (String s : rc.getStringList(SUBSCRIBE_SECTION, projectName, SUBSCRIBE_MATCH_REFS)) {
          ss.addMatchingRefSpec(s);
        }
        subscribeSections.put(p, ss.build());
      }
    } catch (IllegalArgumentException e) {
      throw new ConfigInvalidException(e.getMessage());
    }
  }

  private void loadReceiveSection(Config rc) {
    checkReceivedObjects = rc.getBoolean(RECEIVE, KEY_CHECK_RECEIVED_OBJECTS, true);
    maxObjectSizeLimit = rc.getLong(RECEIVE, null, KEY_MAX_OBJECT_SIZE_LIMIT, 0);
  }

  private void loadPluginSections(Config rc) {
    pluginConfigs = new HashMap<>();
    for (String plugin : rc.getSubsections(PLUGIN)) {
      Config pluginConfig = new Config();
      pluginConfigs.put(plugin, pluginConfig);
      for (String name : rc.getNames(PLUGIN, plugin)) {
        String value = rc.getString(PLUGIN, plugin, name);
        String groupName = GroupReference.extractGroupName(value);
        if (groupName != null) {
          GroupReference ref = groupList.byName(groupName);
          if (ref == null) {
            error(
                ValidationError.create(
                    PROJECT_CONFIG, "group \"" + groupName + "\" not in " + GroupList.FILE_NAME));
          }
          rc.setString(PLUGIN, plugin, name, value);
        }
        pluginConfig.setStringList(
            PLUGIN, plugin, name, Arrays.asList(rc.getStringList(PLUGIN, plugin, name)));
      }
    }
  }

  public void updatePluginConfig(
      String pluginName, Consumer<PluginConfig.Update> pluginConfigUpdate) {
    Config pluginConfig = pluginConfigs.get(pluginName);
    if (pluginConfig == null) {
      pluginConfig = new Config();
      pluginConfigs.put(pluginName, pluginConfig);
    }
    pluginConfigUpdate.accept(new PluginConfig.Update(pluginName, pluginConfig, Optional.of(this)));
  }

  public PluginConfig getPluginConfig(String pluginName) {
    Config pluginConfig = pluginConfigs.getOrDefault(pluginName, new Config());
    return PluginConfig.create(pluginName, pluginConfig, getCacheable());
  }

  private void readGroupList() throws IOException {
    groupList = GroupList.parse(projectName, readUTF8(GroupList.FILE_NAME), this);
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
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

    for (BooleanProjectConfig config : BooleanProjectConfig.values()) {
      set(
          rc,
          config.getSection(),
          config.getSubSection(),
          config.getName(),
          p.getBooleanConfig(config),
          InheritableBoolean.INHERIT);
    }

    set(
        rc,
        RECEIVE,
        null,
        KEY_MAX_OBJECT_SIZE_LIMIT,
        validMaxObjectSizeLimit(p.getMaxObjectSizeLimit()));

    set(rc, SUBMIT, null, KEY_ACTION, p.getSubmitType(), DEFAULT_SUBMIT_TYPE);

    set(rc, PROJECT, null, KEY_STATE, p.getState(), DEFAULT_STATE_VALUE);

    set(rc, DASHBOARD, null, KEY_DEFAULT, p.getDefaultDashboard());
    set(rc, DASHBOARD, null, KEY_LOCAL_DEFAULT, p.getLocalDefaultDashboard());

    Set<AccountGroup.UUID> keepGroups = new HashSet<>();
    saveAccountsSection(rc, keepGroups);
    saveContributorAgreements(rc, keepGroups);
    saveAccessSections(rc, keepGroups);
    saveNotifySections(rc, keepGroups);
    savePluginSections(rc, keepGroups);
    groupList.retainUUIDs(keepGroups);
    saveLabelSections(rc);
    saveCommentLinkSections(rc);
    saveSubscribeSections(rc);
    saveBranchOrderSection(rc);

    saveConfig(PROJECT_CONFIG, rc);
    saveGroupList();
    return true;
  }

  public static String validMaxObjectSizeLimit(String value) throws ConfigInvalidException {
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
        throw new ConfigInvalidException(
            String.format(
                "Negative value '%s' not allowed as %s", value, KEY_MAX_OBJECT_SIZE_LIMIT));
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
    unsetSection(rc, ACCOUNTS);
    if (accountsSection != null) {
      rc.setStringList(
          ACCOUNTS,
          null,
          KEY_SAME_GROUP_VISIBILITY,
          ruleToStringList(accountsSection.getSameGroupVisibility(), keepGroups));
    }
  }

  private void saveCommentLinkSections(Config rc) {
    unsetSection(rc, COMMENTLINK);
    if (commentLinkSections != null) {
      for (StoredCommentLinkInfo cm : commentLinkSections.values()) {
        rc.setString(COMMENTLINK, cm.getName(), KEY_MATCH, cm.getMatch());
        if (!Strings.isNullOrEmpty(cm.getHtml())) {
          rc.setString(COMMENTLINK, cm.getName(), KEY_HTML, cm.getHtml());
        }
        if (!Strings.isNullOrEmpty(cm.getLink())) {
          rc.setString(COMMENTLINK, cm.getName(), KEY_LINK, cm.getLink());
        }
        if (cm.getEnabled() != null && !cm.getEnabled()) {
          rc.setBoolean(COMMENTLINK, cm.getName(), KEY_ENABLED, cm.getEnabled());
        }
      }
    }
  }

  private void saveContributorAgreements(Config rc, Set<AccountGroup.UUID> keepGroups) {
    unsetSection(rc, CONTRIBUTOR_AGREEMENT);
    for (ContributorAgreement ca : sort(contributorAgreements.values())) {
      set(rc, CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_DESCRIPTION, ca.getDescription());
      set(rc, CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_AGREEMENT_URL, ca.getAgreementUrl());

      if (ca.getAutoVerify() != null) {
        if (ca.getAutoVerify().getUUID() != null) {
          keepGroups.add(ca.getAutoVerify().getUUID());
        }
        String autoVerify = PermissionRule.create(ca.getAutoVerify()).asString(false);
        set(rc, CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_AUTO_VERIFY, autoVerify);
      } else {
        rc.unset(CONTRIBUTOR_AGREEMENT, ca.getName(), KEY_AUTO_VERIFY);
      }

      rc.setStringList(
          CONTRIBUTOR_AGREEMENT,
          ca.getName(),
          KEY_ACCEPTED,
          ruleToStringList(ca.getAccepted(), keepGroups));
      rc.setStringList(
          CONTRIBUTOR_AGREEMENT,
          ca.getName(),
          KEY_EXCLUDE_PROJECTS,
          patternToStringList(ca.getExcludeProjectsRegexes()));
      rc.setStringList(
          CONTRIBUTOR_AGREEMENT,
          ca.getName(),
          KEY_MATCH_PROJECTS,
          patternToStringList(ca.getMatchProjectsRegexes()));
    }
  }

  private void saveNotifySections(Config rc, Set<AccountGroup.UUID> keepGroups) {
    unsetSection(rc, NOTIFY);
    for (NotifyConfig nc : sort(notifySections.values())) {
      nc.getGroups().stream()
          .map(GroupReference::getUUID)
          .filter(Objects::nonNull)
          .forEach(keepGroups::add);
      List<String> email =
          nc.getGroups().stream()
              .map(gr -> PermissionRule.create(gr).asString(false))
              .sorted()
              .collect(toList());

      // Separate stream operation so that emails list contains 2 sorted sub-lists.
      nc.getAddresses().stream().map(Address::toString).sorted().forEach(email::add);

      set(rc, NOTIFY, nc.getName(), KEY_HEADER, nc.getHeader(), NotifyConfig.Header.BCC);
      if (email.isEmpty()) {
        rc.unset(NOTIFY, nc.getName(), KEY_EMAIL);
      } else {
        rc.setStringList(NOTIFY, nc.getName(), KEY_EMAIL, email);
      }

      if (nc.getNotify().equals(Sets.immutableEnumSet(NotifyType.ALL))) {
        rc.unset(NOTIFY, nc.getName(), KEY_TYPE);
      } else {
        List<String> types = new ArrayList<>(4);
        for (NotifyType t : NotifyType.values()) {
          if (nc.isNotify(t)) {
            types.add(t.name().toLowerCase(Locale.US));
          }
        }
        rc.setStringList(NOTIFY, nc.getName(), KEY_TYPE, types);
      }

      set(rc, NOTIFY, nc.getName(), KEY_FILTER, nc.getFilter());
    }
  }

  private List<String> patternToStringList(List<String> list) {
    return list;
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

  private void saveAccessSections(Config rc, Set<AccountGroup.UUID> keepGroups) {
    unsetSection(rc, CAPABILITY);
    AccessSection capability = accessSections.get(AccessSection.GLOBAL_CAPABILITIES);
    if (capability != null) {
      Set<String> have = new HashSet<>();
      for (Permission permission : sort(capability.getPermissions())) {
        have.add(permission.getName().toLowerCase());

        boolean needRange = GlobalCapability.hasRange(permission.getName());
        List<String> rules = new ArrayList<>();
        for (PermissionRule rule : sort(permission.getRules())) {
          GroupReference group = resolve(rule.getGroup());
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
          GroupReference group = resolve(rule.getGroup());
          if (group.getUUID() != null) {
            keepGroups.add(group.getUUID());
          }
          rules.add(rule.asString(needRange));
        }
        rc.setStringList(ACCESS, refName, permission.getName(), rules);
      }

      for (String varName : rc.getNames(ACCESS, refName)) {
        if (isCoreOrPluginPermission(convertLegacyPermission(varName))
            && !have.contains(varName.toLowerCase())) {
          rc.unset(ACCESS, refName, varName);
        }
      }
    }

    for (String name : rc.getSubsections(ACCESS)) {
      if (AccessSection.isValidRefSectionName(name) && !accessSections.containsKey(name)) {
        rc.unsetSection(ACCESS, name);
      }
    }
  }

  private void saveLabelSections(Config rc) {
    List<String> existing = new ArrayList<>(rc.getSubsections(LABEL));
    if (!new ArrayList<>(labelSections.keySet()).equals(existing)) {
      // Order of sections changed, remove and rewrite them all.
      unsetSection(rc, LABEL);
    }

    Set<String> toUnset = new HashSet<>(existing);
    for (Map.Entry<String, LabelType> e : labelSections.entrySet()) {
      String name = e.getKey();
      LabelType label = e.getValue();
      toUnset.remove(name);
      rc.setString(LABEL, name, KEY_FUNCTION, label.getFunction().getFunctionName());
      rc.setInt(LABEL, name, KEY_DEFAULT_VALUE, label.getDefaultValue());

      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_ALLOW_POST_SUBMIT,
          label.isAllowPostSubmit(),
          LabelType.DEF_ALLOW_POST_SUBMIT);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_IGNORE_SELF_APPROVAL,
          label.isIgnoreSelfApproval(),
          LabelType.DEF_IGNORE_SELF_APPROVAL);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_COPY_ANY_SCORE,
          label.isCopyAnyScore(),
          LabelType.DEF_COPY_ANY_SCORE);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_COPY_MIN_SCORE,
          label.isCopyMinScore(),
          LabelType.DEF_COPY_MIN_SCORE);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_COPY_MAX_SCORE,
          label.isCopyMaxScore(),
          LabelType.DEF_COPY_MAX_SCORE);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
          label.isCopyAllScoresOnTrivialRebase(),
          LabelType.DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
          label.isCopyAllScoresIfNoCodeChange(),
          LabelType.DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_COPY_ALL_SCORES_IF_NO_CHANGE,
          label.isCopyAllScoresIfNoChange(),
          LabelType.DEF_COPY_ALL_SCORES_IF_NO_CHANGE);
      setBooleanConfigKey(
          rc,
          LABEL,
          name,
          KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
          label.isCopyAllScoresOnMergeFirstParentUpdate(),
          LabelType.DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE);
      rc.setStringList(
          LABEL,
          name,
          KEY_COPY_VALUE,
          label.getCopyValues().stream().map(LabelValue::formatValue).collect(toList()));
      setBooleanConfigKey(
          rc, LABEL, name, KEY_CAN_OVERRIDE, label.isCanOverride(), LabelType.DEF_CAN_OVERRIDE);
      List<String> values = new ArrayList<>(label.getValues().size());
      for (LabelValue value : label.getValues()) {
        values.add(value.format().trim());
      }
      rc.setStringList(LABEL, name, KEY_VALUE, values);

      List<String> refPatterns = label.getRefPatterns();
      if (refPatterns != null && !refPatterns.isEmpty()) {
        rc.setStringList(LABEL, name, KEY_BRANCH, refPatterns);
      } else {
        rc.unset(LABEL, name, KEY_BRANCH);
      }
    }

    for (String name : toUnset) {
      rc.unsetSection(LABEL, name);
    }
  }

  private static void setBooleanConfigKey(
      Config rc, String section, String name, String key, boolean value, boolean defaultValue) {
    if (value == defaultValue) {
      rc.unset(section, name, key);
    } else {
      rc.setBoolean(section, name, key, value);
    }
  }

  private void savePluginSections(Config rc, Set<AccountGroup.UUID> keepGroups) {
    unsetSection(rc, PLUGIN);
    for (Map.Entry<String, Config> e : pluginConfigs.entrySet()) {
      String plugin = e.getKey();
      Config pluginConfig = e.getValue();
      for (String name : pluginConfig.getNames(PLUGIN, plugin)) {
        String value = pluginConfig.getString(PLUGIN, plugin, name);
        String groupName = GroupReference.extractGroupName(value);
        if (groupName != null) {
          GroupReference ref = groupList.byName(groupName);
          if (ref != null && ref.getUUID() != null) {
            keepGroups.add(ref.getUUID());
            pluginConfig.setString(PLUGIN, plugin, name, "group " + ref.getName());
          }
        }
        rc.setStringList(
            PLUGIN, plugin, name, Arrays.asList(pluginConfig.getStringList(PLUGIN, plugin, name)));
      }
    }
  }

  private void saveGroupList() throws IOException {
    saveUTF8(GroupList.FILE_NAME, groupList.asText());
  }

  private void saveSubscribeSections(Config rc) {
    for (Project.NameKey p : subscribeSections.keySet()) {
      SubscribeSection s = subscribeSections.get(p);
      List<String> matchings = new ArrayList<>();
      for (String r : s.matchingRefSpecsAsString()) {
        matchings.add(r);
      }
      rc.setStringList(SUBSCRIBE_SECTION, p.get(), SUBSCRIBE_MATCH_REFS, matchings);

      List<String> multimatchs = new ArrayList<>();
      for (String r : s.multiMatchRefSpecsAsString()) {
        multimatchs.add(r);
      }
      rc.setStringList(SUBSCRIBE_SECTION, p.get(), SUBSCRIBE_MULTI_MATCH_REFS, multimatchs);
    }
  }

  private void unsetSection(Config rc, String sectionName) {
    for (String subSectionName : rc.getSubsections(sectionName)) {
      rc.unsetSection(sectionName, subSectionName);
    }
    rc.unsetSection(sectionName, null);
  }

  private <E extends Enum<?>> E getEnum(
      Config rc, String section, String subsection, String name, E defaultValue) {
    try {
      return rc.getEnum(section, subsection, name, defaultValue);
    } catch (IllegalArgumentException err) {
      error(ValidationError.create(PROJECT_CONFIG, err.getMessage()));
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

  private static <T extends Comparable<? super T>> ImmutableList<T> sort(Collection<T> m) {
    return m.stream().sorted().collect(toImmutableList());
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public boolean hasLegacyPermissions() {
    return hasLegacyPermissions;
  }

  private String convertLegacyPermission(String permissionName) {
    switch (permissionName) {
      case LEGACY_PERMISSION_PUSH_TAG:
        hasLegacyPermissions = true;
        return Permission.CREATE_TAG;
      case LEGACY_PERMISSION_PUSH_SIGNED_TAG:
        hasLegacyPermissions = true;
        return Permission.CREATE_SIGNED_TAG;
      default:
        return permissionName;
    }
  }
}

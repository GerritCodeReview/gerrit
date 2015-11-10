// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GitwebType;
import com.google.gerrit.extensions.config.CloneCommand;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.change.ArchiveFormat;
import com.google.gerrit.server.change.GetArchive;
import com.google.gerrit.server.change.Submit;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GetServerInfo implements RestReadView<ConfigResource> {
  private static final String URL_ALIAS = "urlAlias";
  private static final String KEY_MATCH = "match";
  private static final String KEY_TOKEN = "token";

  private final Config config;
  private final AuthConfig authConfig;
  private final Realm realm;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final DynamicMap<CloneCommand> cloneCommands;
  private final GetArchive.AllowedFormats archiveFormats;
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final String anonymousCowardName;
  private final GitwebConfig gitwebConfig;
  private final DynamicItem<AvatarProvider> avatar;
  private final boolean enableSignedPush;

  @Inject
  public GetServerInfo(
      @GerritServerConfig Config config,
      AuthConfig authConfig,
      Realm realm,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicMap<DownloadCommand> downloadCommands,
      DynamicMap<CloneCommand> cloneCommands,
      GetArchive.AllowedFormats archiveFormats,
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      @AnonymousCowardName String anonymousCowardName,
      GitwebConfig gitwebConfig,
      DynamicItem<AvatarProvider> avatar,
      @EnableSignedPush boolean enableSignedPush) {
    this.config = config;
    this.authConfig = authConfig;
    this.realm = realm;
    this.downloadSchemes = downloadSchemes;
    this.downloadCommands = downloadCommands;
    this.cloneCommands = cloneCommands;
    this.archiveFormats = archiveFormats;
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.anonymousCowardName = anonymousCowardName;
    this.gitwebConfig = gitwebConfig;
    this.avatar = avatar;
    this.enableSignedPush = enableSignedPush;
  }

  @Override
  public ServerInfo apply(ConfigResource rsrc) throws MalformedURLException {
    ServerInfo info = new ServerInfo();
    info.auth = getAuthInfo(authConfig, realm);
    info.change = getChangeInfo(config);
    info.download =
        getDownloadInfo(downloadSchemes, downloadCommands, cloneCommands,
            archiveFormats);
    info.gerrit = getGerritInfo(config, allProjectsName, allUsersName);
    info.gitweb = getGitwebInfo(gitwebConfig);
    info.plugin = getPluginInfo();
    info.sshd = getSshdInfo(config);
    info.suggest = getSuggestInfo(config);

    Map<String, String> urlAliases = getUrlAliasesInfo(config);
    info.urlAliases = !urlAliases.isEmpty() ? urlAliases : null;

    info.user = getUserInfo(anonymousCowardName);
    info.receive = getReceiveInfo();
    return info;
  }

  private AuthInfo getAuthInfo(AuthConfig cfg, Realm realm) {
    AuthInfo info = new AuthInfo();
    info.authType = cfg.getAuthType();
    info.useContributorAgreements = toBoolean(cfg.isUseContributorAgreements());
    info.editableAccountFields = new ArrayList<>(realm.getEditableFields());
    info.switchAccountUrl = cfg.getSwitchAccountUrl();

    switch (info.authType) {
      case LDAP:
      case LDAP_BIND:
        info.registerUrl = cfg.getRegisterUrl();
        info.registerText = cfg.getRegisterText();
        info.editFullNameUrl = cfg.getEditFullNameUrl();
        info.isGitBasicAuth = toBoolean(cfg.isGitBasicAuth());
        break;

      case CUSTOM_EXTENSION:
        info.registerUrl = cfg.getRegisterUrl();
        info.registerText = cfg.getRegisterText();
        info.editFullNameUrl = cfg.getEditFullNameUrl();
        info.httpPasswordUrl = cfg.getHttpPasswordUrl();
        break;

      case HTTP:
      case HTTP_LDAP:
        info.loginUrl = cfg.getLoginUrl();
        info.loginText = cfg.getLoginText();
        break;

      case CLIENT_SSL_CERT_LDAP:
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case OAUTH:
      case OPENID:
      case OPENID_SSO:
        break;
    }
    return info;
  }

  private ChangeConfigInfo getChangeInfo(Config cfg) {
    ChangeConfigInfo info = new ChangeConfigInfo();
    info.allowDrafts = toBoolean(cfg.getBoolean("change", "allowDrafts", true));
    info.largeChange = cfg.getInt("change", "largeChange", 500);
    info.replyTooltip =
        Optional.fromNullable(cfg.getString("change", null, "replyTooltip"))
            .or("Reply and score") + " (Shortcut: a)";
    info.replyLabel =
        Optional.fromNullable(cfg.getString("change", null, "replyLabel"))
            .or("Reply") + "\u2026";
    info.updateDelay = (int) ConfigUtil.getTimeUnit(
        cfg, "change", null, "updateDelay", 30, TimeUnit.SECONDS);
    info.submitWholeTopicMode = Submit.wholeTopic(cfg).name();
    return info;
  }

  private DownloadInfo getDownloadInfo(
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicMap<DownloadCommand> downloadCommands,
      DynamicMap<CloneCommand> cloneCommands,
      GetArchive.AllowedFormats archiveFormats) {
    DownloadInfo info = new DownloadInfo();
    info.schemes = new HashMap<>();
    for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
      DownloadScheme scheme = e.getProvider().get();
      if (scheme.isEnabled() && scheme.getUrl("${project}") != null) {
        info.schemes.put(e.getExportName(),
            getDownloadSchemeInfo(scheme, downloadCommands, cloneCommands));
      }
    }
    info.archives = Lists.newArrayList(Iterables.transform(
        archiveFormats.getAllowed(),
        new Function<ArchiveFormat, String>() {
          @Override
          public String apply(ArchiveFormat in) {
            return in.getShortName();
          }
        }));
    return info;
  }

  private DownloadSchemeInfo getDownloadSchemeInfo(DownloadScheme scheme,
      DynamicMap<DownloadCommand> downloadCommands,
      DynamicMap<CloneCommand> cloneCommands) {
    DownloadSchemeInfo info = new DownloadSchemeInfo();
    info.url = scheme.getUrl("${project}");
    info.isAuthRequired = toBoolean(scheme.isAuthRequired());
    info.isAuthSupported = toBoolean(scheme.isAuthSupported());

    info.commands = new HashMap<>();
    for (DynamicMap.Entry<DownloadCommand> e : downloadCommands) {
      String commandName = e.getExportName();
      DownloadCommand command = e.getProvider().get();
      String c = command.getCommand(scheme, "${project}", "${ref}");
      if (c != null) {
        info.commands.put(commandName, c);
      }
    }

    info.cloneCommands = new HashMap<>();
    for (DynamicMap.Entry<CloneCommand> e : cloneCommands) {
      String commandName = e.getExportName();
      CloneCommand command = e.getProvider().get();
      String c = command.getCommand(scheme, "${project-path}/${project-base-name}");
      if (c != null) {
        c = c.replaceAll("\\$\\{project-path\\}/\\$\\{project-base-name\\}",
            "\\$\\{project\\}");
        info.cloneCommands.put(commandName, c);
      }
    }

    return info;
  }

  private GerritInfo getGerritInfo(Config cfg, AllProjectsName allProjectsName,
      AllUsersName allUsersName) {
    GerritInfo info = new GerritInfo();
    info.allProjects = allProjectsName.get();
    info.allUsers = allUsersName.get();
    info.reportBugUrl = cfg.getString("gerrit", null, "reportBugUrl");
    info.reportBugText = cfg.getString("gerrit", null, "reportBugText");
    info.docUrl = getDocUrl(cfg);
    info.editGpgKeys = toBoolean(enableSignedPush
        && cfg.getBoolean("gerrit", null, "editGpgKeys", true));
    return info;
  }

  private String getDocUrl(Config cfg) {
    String docUrl = cfg.getString("gerrit", null, "docUrl");
    if (Strings.isNullOrEmpty(docUrl)) {
      return null;
    }
    return CharMatcher.is('/').trimTrailingFrom(docUrl) + '/';
  }

  private GitwebInfo getGitwebInfo(GitwebConfig cfg) {
    if (cfg.getUrl() == null || cfg.getGitwebType() == null) {
      return null;
    }

    GitwebInfo info = new GitwebInfo();
    info.url = cfg.getUrl();
    info.type = cfg.getGitwebType();
    return info;
  }

  private PluginConfigInfo getPluginInfo() {
    PluginConfigInfo info = new PluginConfigInfo();
    info.hasAvatars = toBoolean(avatar.get() != null);
    return info;
  }

  private Map<String, String> getUrlAliasesInfo(Config cfg) {
    Map<String, String> urlAliases = new HashMap<>();
    for (String subsection : cfg.getSubsections(URL_ALIAS)) {
      urlAliases.put(cfg.getString(URL_ALIAS, subsection, KEY_MATCH),
         cfg.getString(URL_ALIAS, subsection, KEY_TOKEN));
    }
    return urlAliases;
  }

  private SshdInfo getSshdInfo(Config cfg) {
    String[] addr = cfg.getStringList("sshd", null, "listenAddress");
    if (addr.length == 1 && isOff(addr[0])) {
      return null;
    }
    return new SshdInfo();
  }

  private static boolean isOff(String listenHostname) {
    return "off".equalsIgnoreCase(listenHostname)
        || "none".equalsIgnoreCase(listenHostname)
        || "no".equalsIgnoreCase(listenHostname);
  }

  private SuggestInfo getSuggestInfo(Config cfg) {
    SuggestInfo info = new SuggestInfo();
    info.from = cfg.getInt("suggest", "from", 0);
    return info;
  }

  private UserConfigInfo getUserInfo(String anonymousCowardName) {
    UserConfigInfo info = new UserConfigInfo();
    info.anonymousCowardName = anonymousCowardName;
    return info;
  }

  private ReceiveInfo getReceiveInfo() {
    ReceiveInfo info = new ReceiveInfo();
    info.enableSignedPush = enableSignedPush;
    return info;
  }

  private static Boolean toBoolean(boolean v) {
    return v ? v : null;
  }

  public static class ServerInfo {
    public AuthInfo auth;
    public ChangeConfigInfo change;
    public DownloadInfo download;
    public GerritInfo gerrit;
    public GitwebInfo gitweb;
    public PluginConfigInfo plugin;
    public SshdInfo sshd;
    public SuggestInfo suggest;
    public Map<String, String> urlAliases;
    public UserConfigInfo user;
    public ReceiveInfo receive;
  }

  public static class AuthInfo {
    public AuthType authType;
    public Boolean useContributorAgreements;
    public List<Account.FieldName> editableAccountFields;
    public String loginUrl;
    public String loginText;
    public String switchAccountUrl;
    public String registerUrl;
    public String registerText;
    public String editFullNameUrl;
    public String httpPasswordUrl;
    public Boolean isGitBasicAuth;
  }

  public static class ChangeConfigInfo {
    public Boolean allowDrafts;
    public int largeChange;
    public String replyLabel;
    public String replyTooltip;
    public int updateDelay;
    public String submitWholeTopicMode;
  }

  public static class DownloadInfo {
    public Map<String, DownloadSchemeInfo> schemes;
    public List<String> archives;
  }

  public static class DownloadSchemeInfo {
    public String url;
    public Boolean isAuthRequired;
    public Boolean isAuthSupported;
    public Map<String, String> commands;
    public Map<String, String> cloneCommands;
  }

  public static class GerritInfo {
    public String allProjects;
    public String allUsers;
    public String docUrl;
    public String reportBugUrl;
    public String reportBugText;
    public Boolean editGpgKeys;
  }

  public static class GitwebInfo {
    public String url;
    public GitwebType type;
  }

  public static class PluginConfigInfo {
    public Boolean hasAvatars;
  }

  public static class SshdInfo {
  }

  public static class SuggestInfo {
    public int from;
  }

  public static class UserConfigInfo {
    public String anonymousCowardName;
  }

  public static class ReceiveInfo {
    public Boolean enableSignedPush;
  }
}

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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GitWebType;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.change.ArchiveFormat;
import com.google.gerrit.server.change.GetArchive;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GetServerInfo implements RestReadView<ConfigResource> {
  private final Config config;
  private final AuthConfig authConfig;
  private final Realm realm;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final GetArchive.AllowedFormats archiveFormats;
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final String anonymousCowardName;
  private final GitWebConfig gitWebConfig;

  @Inject
  public GetServerInfo(
      @GerritServerConfig Config config,
      AuthConfig authConfig,
      Realm realm,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicMap<DownloadCommand> downloadCommands,
      GetArchive.AllowedFormats archiveFormats,
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      @AnonymousCowardName String anonymousCowardName,
      GitWebConfig gitWebConfig) {
    this.config = config;
    this.authConfig = authConfig;
    this.realm = realm;
    this.downloadSchemes = downloadSchemes;
    this.downloadCommands = downloadCommands;
    this.archiveFormats = archiveFormats;
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.anonymousCowardName = anonymousCowardName;
    this.gitWebConfig = gitWebConfig;
  }

  @Override
  public ServerInfo apply(ConfigResource rsrc) throws MalformedURLException {
    ServerInfo info = new ServerInfo();
    info.auth = new AuthInfo(authConfig, realm);
    info.change = getChangeInfo(config);
    info.contactStore = getContactStoreInfo();
    info.download =
        new DownloadInfo(downloadSchemes, downloadCommands, archiveFormats);
    info.gerrit = getGerritInfo(config, allProjectsName, allUsersName);
    info.gitWeb = getGitWebInfo(gitWebConfig);
    info.suggest = getSuggestInfo(config);
    info.user = getUserInfo(anonymousCowardName);
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
    return info;
  }

  private ContactStoreInfo getContactStoreInfo() {
    String url = config.getString("contactstore", null, "url");
    if (url == null) {
      return null;
    }

    ContactStoreInfo contactStore = new ContactStoreInfo();
    contactStore.url = url;
    return contactStore;
  }

  private GerritInfo getGerritInfo(Config cfg, AllProjectsName allProjectsName,
      AllUsersName allUsersName) {
    GerritInfo info = new GerritInfo();
    info.allProjects = allProjectsName.get();
    info.allUsers = allUsersName.get();
    info.reportBugUrl = cfg.getString("gerrit", null, "reportBugUrl");
    info.reportBugText = cfg.getString("gerrit", null, "reportBugText");
    return info;
  }

  private GitWebInfo getGitWebInfo(GitWebConfig cfg) {
    if (cfg.getUrl() == null || cfg.getGitWebType() == null) {
      return null;
    }

    GitWebInfo info = new GitWebInfo();
    info.url = cfg.getUrl();
    info.type = cfg.getGitWebType();
    return info;
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

  private static Boolean toBoolean(boolean v) {
    return v ? v : null;
  }

  public static class ServerInfo {
    public AuthInfo auth;
    public ChangeConfigInfo change;
    public ContactStoreInfo contactStore;
    public DownloadInfo download;
    public GerritInfo gerrit;
    public GitWebInfo gitWeb;
    public SuggestInfo suggest;
    public UserConfigInfo user;
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

    public AuthInfo(AuthConfig cfg, Realm realm) {
      authType = cfg.getAuthType();
      useContributorAgreements = toBoolean(cfg.isUseContributorAgreements());
      editableAccountFields = new ArrayList<>(realm.getEditableFields());
      switchAccountUrl = cfg.getSwitchAccountUrl();

      switch (authType) {
        case LDAP:
        case LDAP_BIND:
          registerUrl = cfg.getRegisterUrl();
          registerText = cfg.getRegisterText();
          editFullNameUrl = cfg.getEditFullNameUrl();
          isGitBasicAuth = toBoolean(cfg.isGitBasicAuth());
          break;

        case CUSTOM_EXTENSION:
          registerUrl = cfg.getRegisterUrl();
          registerText = cfg.getRegisterText();
          editFullNameUrl = cfg.getEditFullNameUrl();
          httpPasswordUrl = cfg.getHttpPasswordUrl();
          break;

        case HTTP:
        case HTTP_LDAP:
          loginUrl = cfg.getLoginUrl();
          loginText = cfg.getLoginText();
          break;

        case CLIENT_SSL_CERT_LDAP:
        case DEVELOPMENT_BECOME_ANY_ACCOUNT:
        case OAUTH:
        case OPENID:
        case OPENID_SSO:
          break;
      }
    }
  }

  public static class ChangeConfigInfo {
    public Boolean allowDrafts;
    public int largeChange;
    public String replyLabel;
    public String replyTooltip;
    public int updateDelay;
  }

  public static class ContactStoreInfo {
    public String url;
  }

  public static class DownloadInfo {
    public Map<String, DownloadSchemeInfo> schemes;
    public List<String> archives;

    public DownloadInfo(DynamicMap<DownloadScheme> downloadSchemes,
        DynamicMap<DownloadCommand> downloadCommands,
        GetArchive.AllowedFormats archiveFormats) {
      schemes = new HashMap<>();
      for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
        DownloadScheme scheme = e.getProvider().get();
        if (scheme.isEnabled() && scheme.getUrl("${project}") != null) {
          schemes.put(e.getExportName(),
              new DownloadSchemeInfo(scheme, downloadCommands));
        }
      }
      archives = Lists.newArrayList(Iterables.transform(
          archiveFormats.getAllowed(),
          new Function<ArchiveFormat, String>() {
            @Override
            public String apply(ArchiveFormat in) {
              return in.getShortName();
            }
          }));
    }
  }

  public static class DownloadSchemeInfo {
    public String url;
    public Boolean isAuthRequired;
    public Boolean isAuthSupported;
    public Map<String, String> commands;

    public DownloadSchemeInfo(DownloadScheme scheme,
        DynamicMap<DownloadCommand> downloadCommands) {
      url = scheme.getUrl("${project}");
      isAuthRequired = toBoolean(scheme.isAuthRequired());
      isAuthSupported = toBoolean(scheme.isAuthSupported());

      commands = new HashMap<>();
      for (DynamicMap.Entry<DownloadCommand> e : downloadCommands) {
        String commandName = e.getExportName();
        DownloadCommand command = e.getProvider().get();
        String c = command.getCommand(scheme, "${project}", "${ref}");
        if (c != null) {
          commands.put(commandName, c);
        }
      }
    }
  }

  public static class GerritInfo {
    public String allProjects;
    public String allUsers;
    public String reportBugUrl;
    public String reportBugText;
  }

  public static class GitWebInfo {
    public String url;
    public GitWebType type;
  }

  public static class SuggestInfo {
    public int from;
  }

  public static class UserConfigInfo {
    public String anonymousCowardName;
  }
}

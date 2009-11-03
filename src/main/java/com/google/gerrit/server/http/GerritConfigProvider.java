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

package com.google.gerrit.server.http;

import com.google.gerrit.client.data.ApprovalTypes;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.GitwebLink;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.mail.EmailSender;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtexpui.safehtml.client.RegexFindReplace;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class GerritConfigProvider implements Provider<GerritConfig> {
  private final Realm realm;
  private final Config cfg;
  private final String canonicalWebUrl;
  private final AuthConfig authConfig;
  private final Project.NameKey wildProject;
  private final SshInfo sshInfo;
  private final ApprovalTypes approvalTypes;

  private EmailSender emailSender;
  private final ContactStore contactStore;

  @Inject
  GerritConfigProvider(final Realm r, @GerritServerConfig final Config gsc,
      @CanonicalWebUrl @Nullable final String cwu, final AuthConfig ac,
      @WildProjectName final Project.NameKey wp, final SshInfo si,
      final ApprovalTypes at, final ContactStore cs) {
    realm = r;
    cfg = gsc;
    canonicalWebUrl = cwu;
    authConfig = ac;
    sshInfo = si;
    wildProject = wp;
    approvalTypes = at;
    contactStore = cs;
  }

  @Inject(optional = true)
  void setEmailSender(final EmailSender d) {
    emailSender = d;
  }

  private GerritConfig create() {
    final GerritConfig config = new GerritConfig();
    config.setCanonicalUrl(canonicalWebUrl);
    config.setUseContributorAgreements(cfg.getBoolean("auth",
        "contributoragreements", false));
    config.setGitDaemonUrl(cfg.getString("gerrit", null, "canonicalgiturl"));
    config.setUseRepoDownload(cfg.getBoolean("repo", null,
        "showdownloadcommand", false));
    config.setUseContactInfo(contactStore != null && contactStore.isEnabled());
    config.setAuthType(authConfig.getAuthType());
    config.setWildProject(wildProject);
    config.setApprovalTypes(approvalTypes);

    final Set<Account.FieldName> fields = new HashSet<Account.FieldName>();
    for (final Account.FieldName n : Account.FieldName.values()) {
      if (realm.allowsEdit(n)) {
        fields.add(n);
      }
    }
    if (emailSender != null && emailSender.isEnabled()) {
      fields.add(Account.FieldName.REGISTER_NEW_EMAIL);
    }
    config.setEditableAccountFields(fields);

    final String gitwebUrl = cfg.getString("gitweb", null, "url");
    if (gitwebUrl != null) {
      config.setGitwebLink(new GitwebLink(gitwebUrl));
    }

    config.setSshdAddress(sshInfo != null ? sshInfo.getSshdAddress() : null);

    ArrayList<String> commentLinkNames =
        new ArrayList<String>(cfg.getSubsections("CommentLink"));
    ArrayList<RegexFindReplace> commentLinks =
        new ArrayList<RegexFindReplace>(commentLinkNames.size());
    for (String commentLinkName : commentLinkNames) {
      String match = cfg.getString("CommentLink", commentLinkName, "match");
      String link =
          "<a href=\"" + cfg.getString("CommentLink", commentLinkName, "link")
              + "\">$&</a>";
      commentLinks.add(new RegexFindReplace(match, link));
    }
    config.setCommentLinks(commentLinks);

    return config;
  }

  @Override
  public GerritConfig get() {
    return create();
  }
}

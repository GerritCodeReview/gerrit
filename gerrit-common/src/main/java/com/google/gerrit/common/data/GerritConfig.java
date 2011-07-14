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

package com.google.gerrit.common.data;

import com.google.gerrit.common.auth.openid.OpenIdProviderPattern;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AuthType;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadScheme;
import com.google.gwtexpui.safehtml.client.RegexFindReplace;

import java.util.List;
import java.util.Set;

public class GerritConfig implements Cloneable {
  protected String registerUrl;
  protected List<OpenIdProviderPattern> allowedOpenIDs;

  protected GitwebLink gitweb;
  protected boolean useContributorAgreements;
  protected boolean useContactInfo;
  protected boolean allowRegisterNewEmail;
  protected AuthType authType;
  protected Set<DownloadScheme> downloadSchemes;
  protected String gitDaemonUrl;
  protected String sshdAddress;
  protected Project.NameKey wildProject;
  protected ApprovalTypes approvalTypes;
  protected Set<Account.FieldName> editableAccountFields;
  protected List<RegexFindReplace> commentLinks;
  protected boolean documentationAvailable;
  protected boolean testChangeMerge;

  public String getRegisterUrl() {
    return registerUrl;
  }

  public void setRegisterUrl(final String u) {
    registerUrl = u;
  }

  public List<OpenIdProviderPattern> getAllowedOpenIDs() {
    return allowedOpenIDs;
  }

  public void setAllowedOpenIDs(List<OpenIdProviderPattern> l) {
    allowedOpenIDs = l;
  }

  public AuthType getAuthType() {
    return authType;
  }

  public void setAuthType(final AuthType t) {
    authType = t;
  }

  public Set<DownloadScheme> getDownloadSchemes() {
    return downloadSchemes;
  }

  public void setDownloadSchemes(final Set<DownloadScheme> s) {
    downloadSchemes = s;
  }

  public GitwebLink getGitwebLink() {
    return gitweb;
  }

  public void setGitwebLink(final GitwebLink w) {
    gitweb = w;
  }

  public boolean isUseContributorAgreements() {
    return useContributorAgreements;
  }

  public void setUseContributorAgreements(final boolean r) {
    useContributorAgreements = r;
  }

  public boolean isUseContactInfo() {
    return useContactInfo;
  }

  public void setUseContactInfo(final boolean r) {
    useContactInfo = r;
  }

  public String getGitDaemonUrl() {
    return gitDaemonUrl;
  }

  public void setGitDaemonUrl(String url) {
    if (url != null && !url.endsWith("/")) {
      url += "/";
    }
    gitDaemonUrl = url;
  }

  public String getSshdAddress() {
    return sshdAddress;
  }

  public void setSshdAddress(final String addr) {
    sshdAddress = addr;
  }

  public Project.NameKey getWildProject() {
    return wildProject;
  }

  public void setWildProject(final Project.NameKey wp) {
    wildProject = wp;
  }

  public ApprovalTypes getApprovalTypes() {
    return approvalTypes;
  }

  public void setApprovalTypes(final ApprovalTypes at) {
    approvalTypes = at;
  }

  public boolean canEdit(final Account.FieldName f) {
    return editableAccountFields.contains(f);
  }

  public Set<Account.FieldName> getEditableAccountFields() {
    return editableAccountFields;
  }

  public void setEditableAccountFields(final Set<Account.FieldName> af) {
    editableAccountFields = af;
  }

  public List<RegexFindReplace> getCommentLinks() {
    return commentLinks;
  }

  public void setCommentLinks(final List<RegexFindReplace> cl) {
    commentLinks = cl;
  }

  public boolean isDocumentationAvailable() {
    return documentationAvailable;
  }

  public void setDocumentationAvailable(final boolean available) {
    documentationAvailable = available;
  }

  public boolean testChangeMerge() {
    return testChangeMerge;
  }

  public void setTestChangeMerge(final boolean test) {
    testChangeMerge = test;
  }
}

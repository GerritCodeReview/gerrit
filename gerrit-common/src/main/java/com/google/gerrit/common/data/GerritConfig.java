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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.client.AuthType;

import java.util.List;
import java.util.Set;

public class GerritConfig implements Cloneable {
  protected String registerUrl;
  protected String registerText;
  protected String loginUrl;
  protected String loginText;
  protected String switchAccountUrl;
  protected String httpPasswordUrl;
  protected String reportBugUrl;
  protected String reportBugText;
  protected boolean httpPasswordSettingsEnabled = true;

  protected GitwebConfig gitweb;
  protected AuthType authType;
  protected String gitDaemonUrl;
  protected String gitHttpUrl;
  protected String sshdAddress;
  protected String editFullNameUrl;
  protected Set<Account.FieldName> editableAccountFields;
  protected boolean documentationAvailable;
  protected String anonymousCowardName;
  protected int suggestFrom;
  protected int changeUpdateDelay;
  protected List<String> archiveFormats;
  protected int largeChangeSize;
  protected String replyLabel;
  protected String replyTitle;
  protected boolean allowDraftChanges;

  public String getLoginUrl() {
    return loginUrl;
  }

  public void setLoginUrl(final String u) {
    loginUrl = u;
  }

  public String getLoginText() {
    return loginText;
  }

  public void setLoginText(String signinText) {
    this.loginText = signinText;
  }

  public String getRegisterUrl() {
    return registerUrl;
  }

  public void setRegisterUrl(final String u) {
    registerUrl = u;
  }

  public String getSwitchAccountUrl() {
    return switchAccountUrl;
  }

  public void setSwitchAccountUrl(String u) {
    switchAccountUrl = u;
  }

  public String getRegisterText() {
    return registerText;
  }

  public void setRegisterText(final String t) {
    registerText = t;
  }

  public String getReportBugUrl() {
    return reportBugUrl;
  }

  public void setReportBugUrl(String u) {
    reportBugUrl = u;
  }

  public String getReportBugText() {
    return reportBugText;
  }

  public void setReportBugText(String t) {
    reportBugText = t;
  }

  public boolean isHttpPasswordSettingsEnabled() {
    return httpPasswordSettingsEnabled;
  }

  public void setHttpPasswordSettingsEnabled(boolean httpPasswordSettingsEnabled) {
    this.httpPasswordSettingsEnabled = httpPasswordSettingsEnabled;
  }

  public String getEditFullNameUrl() {
    return editFullNameUrl;
  }

  public void setEditFullNameUrl(String u) {
    editFullNameUrl = u;
  }

  public String getHttpPasswordUrl() {
    return httpPasswordUrl;
  }

  public void setHttpPasswordUrl(String url) {
    httpPasswordUrl = url;
  }

  public void setAuthType(final AuthType t) {
    authType = t;
  }

  public GitwebConfig getGitwebLink() {
    return gitweb;
  }

  public void setGitwebLink(final GitwebConfig w) {
    gitweb = w;
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

  public String getGitHttpUrl() {
    return gitHttpUrl;
  }

  public void setGitHttpUrl(String url) {
    if (url != null && !url.endsWith("/")) {
      url += "/";
    }
    gitHttpUrl = url;
  }

  public String getSshdAddress() {
    return sshdAddress;
  }

  public void setSshdAddress(final String addr) {
    sshdAddress = addr;
  }

  public void setEditableAccountFields(final Set<Account.FieldName> af) {
    editableAccountFields = af;
  }

  public boolean isDocumentationAvailable() {
    return documentationAvailable;
  }

  public void setDocumentationAvailable(final boolean available) {
    documentationAvailable = available;
  }

  public String getAnonymousCowardName() {
    return anonymousCowardName;
  }

  public void setAnonymousCowardName(final String anonymousCowardName) {
    this.anonymousCowardName = anonymousCowardName;
  }

  public int getSuggestFrom() {
    return suggestFrom;
  }

  public void setSuggestFrom(final int suggestFrom) {
    this.suggestFrom = suggestFrom;
  }

  public boolean siteHasUsernames() {
    if (authType == AuthType.CUSTOM_EXTENSION
        && getHttpPasswordUrl() != null
        && !editableAccountFields.contains(FieldName.USER_NAME)) {
      return false;
    }
    return true;
  }

  public int getChangeUpdateDelay() {
    return changeUpdateDelay;
  }

  public void setChangeUpdateDelay(int seconds) {
    changeUpdateDelay = seconds;
  }

  public int getLargeChangeSize() {
    return largeChangeSize;
  }

  public void setLargeChangeSize(int largeChangeSize) {
    this.largeChangeSize = largeChangeSize;
  }

  public List<String> getArchiveFormats() {
    return archiveFormats;
  }

  public void setArchiveFormats(List<String> formats) {
    archiveFormats = formats;
  }

  public String getReplyTitle() {
    return replyTitle;
  }

  public void setReplyTitle(String r) {
    replyTitle = r;
  }

  public String getReplyLabel() {
    return replyLabel;
  }

  public void setReplyLabel(String r) {
    replyLabel = r;
  }

  public boolean isAllowDraftChanges() {
    return allowDraftChanges;
  }

  public void setAllowDraftChanges(boolean b) {
    allowDraftChanges = b;
  }
}

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


public class GerritConfig implements Cloneable {
  protected String reportBugUrl;
  protected String reportBugText;

  protected GitwebConfig gitweb;
  protected String gitDaemonUrl;
  protected String sshdAddress;
  protected boolean documentationAvailable;
  protected String anonymousCowardName;
  protected int suggestFrom;
  protected int changeUpdateDelay;
  protected int largeChangeSize;
  protected String replyLabel;
  protected String replyTitle;
  protected boolean allowDraftChanges;

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

  public String getSshdAddress() {
    return sshdAddress;
  }

  public void setSshdAddress(final String addr) {
    sshdAddress = addr;
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

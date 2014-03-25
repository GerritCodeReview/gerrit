// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.common.data.GerritConfig;

public class JsGerritConfig {

  JsGerritConfig(GerritConfig myConfig) {
    setAnonymousCowardName(myConfig.getAnonymousCowardName());
    setChangeUpdateDelay(myConfig.getChangeUpdateDelay());
    setEditFullNameUrl(myConfig.getEditFullNameUrl());
    setGitDaemonUrl(myConfig.getGitDaemonUrl());
    setGitHttpUrl(myConfig.getGitHttpUrl());
    setHttpPasswordUrl(myConfig.getHttpPasswordUrl());
    setLargeChangeSize(myConfig.getLargeChangeSize());
    setLoginUrl(myConfig.getLoginUrl());
    setLoginText(myConfig.getLoginText());
    setNewFeatures(myConfig.getNewFeatures());
    setRegisterText(myConfig.getRegisterText());
    setRegisterUrl(myConfig.getRegisterUrl());
    setReportBugText(myConfig.getReportBugText());
    setReportBugUrl(myConfig.getReportBugUrl());
    setSshdAddress(myConfig.getSshdAddress());
    setSuggestFrom(myConfig.getSuggestFrom());
    setSwitchAccountUrl(myConfig.getSwitchAccountUrl());
  }

  public native String getLoginUrl() /*-{
    return this.loginUrl;
  }-*/;

  private native void setLoginUrl(final String u) /*-{
    this.loginUrl = u;
  }-*/;

  public native String getLoginText() /*-{
    return this.loginText;
  }-*/;

  private native void setLoginText(String signinText) /*-{
    this.loginText = signinText;
  }-*/;

  public native String getRegisterUrl() /*-{
    return this.registerUrl;
  }-*/;

  private native void setRegisterUrl(final String u) /*-{
    this.registerUrl = u;
  }-*/;

  public native String getSwitchAccountUrl() /*-{
    return this.switchAccountUrl;
  }-*/;

  private native void setSwitchAccountUrl(String u) /*-{
    this.switchAccountUrl = u;
  }-*/;

  public native String getRegisterText() /*-{
    return this.registerText;
  }-*/;

  private native void setRegisterText(final String t) /*-{
    this.registerText = t;
  }-*/;

  public native String getReportBugUrl() /*-{
    return this.reportBugUrl;
  }-*/;

  private native void setReportBugUrl(String u) /*-{
    this.reportBugUrl = u;
  }-*/;

  public native String getReportBugText() /*-{
    return this.reportBugText;
  }-*/;

  private native void setReportBugText(String t) /*-{
    this.reportBugText = t;
  }-*/;

  public native boolean isGitBasicAuth() /*-{
    return this.gitBasicAuth;
  }-*/;

  private native void setGitBasicAuth(boolean gba) /*-{
    this.gitBasicAuth = gba;
  }-*/;

  public native String getEditFullNameUrl() /*-{
    return this.editFullNameUrl;
  }-*/;

  private native void setEditFullNameUrl(String u) /*-{
    this.editFullNameUrl = u;
  }-*/;

  public native String getHttpPasswordUrl() /*-{
    return this.httpPasswordUrl;
  }-*/;

  private native void setHttpPasswordUrl(String url) /*-{
    this.httpPasswordUrl = url;
  }-*/;

  public native boolean isUseContributorAgreements() /*-{
    return this.useContributorAgreements;
  }-*/;

  private native void setUseContributorAgreements(final boolean r) /*-{
    this.useContributorAgreements = r;
  }-*/;

  public native boolean isUseContactInfo() /*-{
    return this.useContactInfo;
  }-*/;

  private native void setUseContactInfo(final boolean r) /*-{
    this.useContactInfo = r;
  }-*/;

  public native String getGitDaemonUrl() /*-{
    return this.gitDaemonUrl;
  }-*/;

  private native void setGitDaemonUrl(String url) /*-{
    this.gitDaemonUrl = @com.google.gerrit.client.JsGerritConfig::addjustUrl(Ljava/lang/String;)(url);
  }-*/;

  public native String getGitHttpUrl() /*-{
    return this.gitHttpUrl;
  }-*/;

  private native void setGitHttpUrl(String url) /*-{
    this.gitHttpUrl = @com.google.gerrit.client.JsGerritConfig::addjustUrl(Ljava/lang/String;)(url);
  }-*/;

  public native String getSshdAddress() /*-{
    return this.sshdAddress;
  }-*/;

  private native void setSshdAddress(final String addr) /*-{
    this.sshdAddress = addr;
  }-*/;

  public native boolean isDocumentationAvailable() /*-{
    return this.documentationAvailable;
  }-*/;

  private native void setDocumentationAvailable(final boolean available) /*-{
    this.documentationAvailable = available;
  }-*/;

  public native String getAnonymousCowardName() /*-{
    return this.anonymousCowardName;
  }-*/;

  private native void setAnonymousCowardName(final String anonymousCowardName) /*-{
    this.anonymousCowardName = anonymousCowardName;
  }-*/;

  public native int getSuggestFrom() /*-{
    return this.suggestFrom;
  }-*/;

  private native void setSuggestFrom(final int suggestFrom) /*-{
    this.suggestFrom = suggestFrom;
  }-*/;

  public native int getChangeUpdateDelay() /*-{
    return this.changeUpdateDelay;
  }-*/;

  private native void setChangeUpdateDelay(int seconds) /*-{
    this.changeUpdateDelay = seconds;
  }-*/;

  public native int getLargeChangeSize() /*-{
    return this.largeChangeSize;
  }-*/;

  private native void setLargeChangeSize(int largeChangeSize) /*-{
    this.largeChangeSize = largeChangeSize;
  }-*/;

  public native boolean getNewFeatures() /*-{
    return this.newFeatures;
  }-*/;

  private native void setNewFeatures(boolean n) /*-{
    this.newFeatures = n;
  }-*/;

  private static String addjustUrl(String url) {
    if (url != null && !url.endsWith("/")) {
      return url + "/";
    }
    return url;
  }
}

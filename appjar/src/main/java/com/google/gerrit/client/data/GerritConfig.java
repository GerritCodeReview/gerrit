// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.SystemConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GerritConfig {
  protected String canonicalUrl;
  protected GitwebLink gitweb;
  protected List<ApprovalType> approvalTypes;
  protected List<ApprovalType> actionTypes;
  protected int sshdPort;
  protected boolean useContributorAgreements;
  protected SystemConfig.LoginType loginType;
  private transient Map<ApprovalCategory.Id, ApprovalType> byCategoryId;

  public GerritConfig() {
  }

  public String getCanonicalUrl() {
    return canonicalUrl;
  }

  public void setCanonicalUrl(final String u) {
    canonicalUrl = u;
  }

  public SystemConfig.LoginType getLoginType() {
    return loginType;
  }

  public void setLoginType(final SystemConfig.LoginType t) {
    loginType = t;
  }

  public GitwebLink getGitwebLink() {
    return gitweb;
  }

  public void setGitwebLink(final GitwebLink w) {
    gitweb = w;
  }

  public void add(final ApprovalType t) {
    if (t.getCategory().isAction()) {
      initActionTypes();
      actionTypes.add(t);
    } else {
      initApprovalTypes();
      approvalTypes.add(t);
    }
  }

  public List<ApprovalType> getApprovalTypes() {
    initApprovalTypes();
    return approvalTypes;
  }

  private void initApprovalTypes() {
    if (approvalTypes == null) {
      approvalTypes = new ArrayList<ApprovalType>();
    }
  }

  public List<ApprovalType> getActionTypes() {
    initActionTypes();
    return actionTypes;
  }

  private void initActionTypes() {
    if (actionTypes == null) {
      actionTypes = new ArrayList<ApprovalType>();
    }
  }

  public int getSshdPort() {
    return sshdPort;
  }

  public void setSshdPort(final int p) {
    sshdPort = p;
  }

  public boolean isUseContributorAgreements() {
    return useContributorAgreements;
  }

  public void setUseContributorAgreements(final boolean r) {
    useContributorAgreements = r;
  }

  public ApprovalType getApprovalType(final ApprovalCategory.Id id) {
    if (byCategoryId == null) {
      byCategoryId = new HashMap<ApprovalCategory.Id, ApprovalType>();
      if (actionTypes != null) {
        for (final ApprovalType t : actionTypes) {
          byCategoryId.put(t.getCategory().getId(), t);
        }
      }

      if (approvalTypes != null) {
        for (final ApprovalType t : approvalTypes) {
          byCategoryId.put(t.getCategory().getId(), t);
        }
      }
    }
    return byCategoryId.get(id);
  }
}

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

import java.util.ArrayList;
import java.util.List;

public class GerritConfig {
  private String canonicalUrl;
  private GitwebLink gitweb;
  private List<ApprovalType> approvalTypes;

  public GerritConfig() {
  }

  public String getCanonicalUrl() {
    return canonicalUrl;
  }

  public void setCanonicalUrl(final String u) {
    canonicalUrl = u;
  }

  public GitwebLink getGitwebLink() {
    return gitweb;
  }

  public void setGitwebLink(final GitwebLink w) {
    gitweb = w;
  }

  public void add(final ApprovalType t) {
    initApprovalTypes();
    approvalTypes.add(t);
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
}

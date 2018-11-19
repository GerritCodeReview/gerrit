// Copyright (C) 2018 The Android Open Source Project
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

// WARNING: NoteDbUpdateManager cares about the package name RestApiServlet lives in.

package com.google.gerrit.httpd.restapi;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaException;
import com.google.gerrit.util.http.RequestUtil;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * Enforces quota on specific REST API endpoints.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>/a/accounts/self/detail => /restapi/accounts/detail:GET
 *   <li>/changes/123/revisions/current/detail => /restapi/changes/revisions/detail:GET
 *   <li>/changes/ => /changes:PUT
 * </ul>
 *
 * <p>Adds context (change, project, account) to the quota check if the call is for an existing
 * entity that was successfully parsed. This quota check is generally enforced after the resource
 * was parsed, but before the view is executed. If a quota enforcer desires to throttle earlier,
 * they should consider quota groups in the {@code /http/*} space.
 */
public class RestApiQuotaEnforcer {
  private final QuotaBackend quotaBackend;

  @Inject
  RestApiQuotaEnforcer(QuotaBackend quotaBackend) {
    this.quotaBackend = quotaBackend;
  }

  /** Enforce quota on a request not tied to any {@code RestResource}. */
  void enforce(HttpServletRequest req) throws QuotaException {
    String pathForQuotaReporting = RequestUtil.getRestPathWithoutIds(req);
    quotaBackend.currentUser().requestThrowing(quotaGroup(pathForQuotaReporting, req.getMethod()));
  }

  /** Enforce quota on a request for a given resource. */
  void enforce(RestResource rsrc, HttpServletRequest req) throws QuotaException {
    String pathForQuotaReporting = RequestUtil.getRestPathWithoutIds(req);
    // Enrich the quota request we we are operating on an interesting collection
    QuotaBackend.WithResource report = quotaBackend.currentUser();
    if (rsrc instanceof ChangeResource) {
      ChangeResource changeResource = (ChangeResource) rsrc;
      report =
          quotaBackend.currentUser().change(changeResource.getId(), changeResource.getProject());
    } else if (rsrc instanceof AccountResource) {
      AccountResource accountResource = (AccountResource) rsrc;
      report = quotaBackend.currentUser().account(accountResource.getUser().getAccountId());
    } else if (rsrc instanceof ProjectResource) {
      ProjectResource accountResource = (ProjectResource) rsrc;
      report = quotaBackend.currentUser().account(accountResource.getUser().getAccountId());
    }

    report.requestThrowing(quotaGroup(pathForQuotaReporting, req.getMethod()));
  }

  private static String quotaGroup(String path, String method) {
    return "/restapi/" + path + ":" + method;
  }
}

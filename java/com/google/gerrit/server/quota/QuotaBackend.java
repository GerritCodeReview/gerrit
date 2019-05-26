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

package com.google.gerrit.server.quota;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.ImplementedBy;

/**
 * Backend interface to perform quota requests on. By default, this interface is backed by {@link
 * DefaultQuotaBackend} which calls all plugins that implement {@link QuotaEnforcer}. A different
 * implementation might be bound in tests. Plugins are not supposed to implement this interface, but
 * bind a {@link QuotaEnforcer} implementation instead.
 *
 * <p>All quota requests require a quota group and a user. Enriching them with a top-level entity
 * {@code Change, Project, Account} is optional but should be done if the request is targeted.
 *
 * <p>Example usage:
 *
 * <pre>
 *   quotaBackend.currentUser().project(projectName).requestToken("/projects/create").throwOnError();
 *   quotaBackend.user(user).requestToken("/restapi/config/put").throwOnError();
 *   QuotaResponse.Aggregated result = quotaBackend.currentUser().account(accountId).requestToken("/restapi/accounts/emails/validate");
 *   QuotaResponse.Aggregated result = quotaBackend.currentUser().project(projectName).requestTokens("/projects/git/upload", numBytesInPush);
 * </pre>
 *
 * <p>All quota groups must be documented in {@code quota.txt} and detail the metadata that is
 * provided (i.e. the parameters used to scope down the quota request).
 */
@ImplementedBy(DefaultQuotaBackend.class)
public interface QuotaBackend {
  /** Constructs a request for the current user. */
  WithUser currentUser();

  /**
   * See {@link #currentUser()}. Use this method only if you can't guarantee that the request is for
   * the current user (e.g. impersonation).
   */
  WithUser user(CurrentUser user);

  /**
   * An interface capable of issuing quota requests. Scope can be futher reduced by providing a
   * top-level entity.
   */
  interface WithUser extends WithResource {
    /** Scope the request down to an account. */
    WithResource account(Account.Id account);

    /** Scope the request down to a project. */
    WithResource project(Project.NameKey project);

    /** Scope the request down to a change. */
    WithResource change(Change.Id change, Project.NameKey project);
  }

  /** An interface capable of issuing quota requests. */
  interface WithResource {
    /** Issues a single quota request for {@code 1} token. */
    default QuotaResponse.Aggregated requestToken(String quotaGroup) {
      return requestTokens(quotaGroup, 1);
    }

    /** Issues a single quota request for {@code numTokens} tokens. */
    QuotaResponse.Aggregated requestTokens(String quotaGroup, long numTokens);

    /**
     * Issues a single quota request for {@code numTokens} tokens but signals the implementations
     * not to deduct any quota yet. Can be used to do pre-flight requests where necessary
     */
    QuotaResponse.Aggregated dryRun(String quotaGroup, long tokens);

    /**
     * Requests a minimum number of tokens available in implementations. This is a pre-flight check
     * for the exceptional case when the requested number of tokens is not known in advance but
     * boundary can be specified. For instance, when the commit is received its size is not known
     * until the transfer happens however one can specify how many bytes can be accepted to meet the
     * repository size quota.
     *
     * <p>By definition, this is not an allocating request, therefore, it should be followed by the
     * call to {@link #requestTokens(String, long)} when the size gets determined so that quota
     * could be properly adjusted. It is in developer discretion to ensure that it gets called.
     * There might be a case when particular quota gets temporarily overbooked when multiple
     * requests are performed but the following calls to {@link #requestTokens(String, long)} will
     * fail at the moment when a quota is exhausted. It is not a subject of quota backend to reclaim
     * tokens that were used due to overbooking.
     */
    QuotaResponse.Aggregated availableTokens(String quotaGroup);
  }
}

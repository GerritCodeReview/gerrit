// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.client.GitBasicAuthPolicy;
import java.util.List;

/**
 * Representation of auth-related server configuration in the REST API.
 *
 * <p>This class determines the JSON format of auth-related server configuration in the REST API.
 *
 * <p>The contained values come from the {@code auth} section of {@code gerrit.config}.
 */
public class AuthInfo {
  /**
   * The authentication type that is configured on the server.
   *
   * <p>The value of the {@code auth.type} parameter in {@code gerrit.config}.
   */
  public AuthType authType;

  /**
   * Whether contributor agreements are required.
   *
   * <p>The value of the {@code auth.contributorAgreements} parameter in {@code gerrit.config}.
   */
  public Boolean useContributorAgreements;

  /** List of contributor agreements that have been configured on the server. */
  public List<AgreementInfo> contributorAgreements;

  /** List of account fields that are editable. */
  public List<AccountFieldName> editableAccountFields;

  /**
   * The login URL.
   *
   * <p>The value of the {@code auth.loginUrl} parameter in {@code gerrit.config}.
   *
   * <p>Only set if authentication type is {@code HTTP} or {@code HTTP_LDAP}.
   */
  public String loginUrl;

  /**
   * The login text.
   *
   * <p>The value of the {@code auth.loginText} parameter in {@code gerrit.config}.
   *
   * <p>Only set if authentication type is {@code HTTP} or {@code HTTP_LDAP}.
   */
  public String loginText;

  /**
   * The URL to switch accounts.
   *
   * <p>The value of the {@code auth.switchAccountUrl} parameter in {@code gerrit.config}.
   */
  public String switchAccountUrl;

  /**
   * The register URL.
   *
   * <p>The value of the {@code auth.registerUrl} parameter in {@code gerrit.config}.
   *
   * <p>Only set if authentication type is {@code LDAP}, {@code LDAP_BIND} or {@code
   * CUSTOM_EXTENSION}.
   */
  public String registerUrl;

  /**
   * The register text.
   *
   * <p>The value of the {@code auth.registerText} parameter in {@code gerrit.config}.
   *
   * <p>Only set if authentication type is {@code LDAP}, {@code LDAP_BIND} or {@code
   * CUSTOM_EXTENSION}.
   */
  public String registerText;

  /**
   * The URL to edit the full name.
   *
   * <p>The value of the {@code auth.editFullNameUrl} parameter in {@code gerrit.config}.
   *
   * <p>Only set if authentication type is {@code LDAP}, {@code LDAP_BIND} or {@code
   * CUSTOM_EXTENSION}.
   */
  public String editFullNameUrl;

  /**
   * The URL to obtain an HTTP password.
   *
   * <p>The value of the {@code auth.httpPasswordUrl} parameter in {@code gerrit.config}.
   *
   * <p>Only set if authentication type is {@code CUSTOM_EXTENSION}.
   */
  public String httpPasswordUrl;

  /**
   * The policy to authenticate Git over HTTP and REST API requests.
   *
   * <p>The value of the {@code auth.gitBasicAuthPolicy} parameter in {@code gerrit.config}.
   *
   * <p>Only set if authentication type is {@code LDAP}, {@code LDAP_BIND} or {@code OAUTH}.
   */
  public GitBasicAuthPolicy gitBasicAuthPolicy;

  /**
   * The maximum lifetime allowed for authentication tokens in minutes.
   *
   * <p>The value of the {@code auth.maxAuthTokenLifetime} parameter in {@code gerrit.config}.
   */
  public long maxTokenLifetime;
}

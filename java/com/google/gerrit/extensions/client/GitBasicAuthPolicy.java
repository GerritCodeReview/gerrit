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

package com.google.gerrit.extensions.client;

public enum GitBasicAuthPolicy {
  /** Only the HTTP password is accepted when doing Git over HTTP and REST API requests. */
  HTTP,

  /** Only the LDAP password is allowed when doing Git over HTTP and REST API requests. */
  LDAP,

  /**
   * The password in the request is first checked against the HTTP password and, if it does not
   * match, it is then validated against the LDAP password.
   */
  HTTP_LDAP,

  /** Only the `OAUTH` authentication is allowed when doing Git over HTTP and REST API requests. */
  OAUTH
}

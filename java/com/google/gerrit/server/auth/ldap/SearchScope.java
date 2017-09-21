// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.auth.ldap;

import javax.naming.directory.SearchControls;

public enum SearchScope {
  // Search only the base DN
  //
  OBJECT(SearchControls.OBJECT_SCOPE), //
  BASE(SearchControls.OBJECT_SCOPE),

  // Search all entries one level under the base DN
  //
  // Does not include the base DN, and does not include items below items
  // under the base DN.
  //
  ONE(SearchControls.ONELEVEL_SCOPE),

  // Search all entries under the base DN, including the base DN.
  //
  SUBTREE(SearchControls.SUBTREE_SCOPE), //
  SUB(SearchControls.SUBTREE_SCOPE);

  private final int scope;

  SearchScope(int scope) {
    this.scope = scope;
  }

  int scope() {
    return scope;
  }
}

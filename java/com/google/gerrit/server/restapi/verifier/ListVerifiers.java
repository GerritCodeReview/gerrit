// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.restapi.verifier;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.verifier.VerifierJson;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Singleton
public class ListVerifiers implements RestReadView<TopLevelResource> {
  private final GlobalVerifierConfig globalVerifierConfig;
  private final PermissionBackend permissionBackend;
  private final Verifiers verifiers;
  private final VerifierJson verifierJson;

  @Inject
  public ListVerifiers(
      GlobalVerifierConfig globalVerifierConfig,
      PermissionBackend permissionBackend,
      Verifiers verifiers,
      VerifierJson verifierJson) {
    this.globalVerifierConfig = globalVerifierConfig;
    this.permissionBackend = permissionBackend;
    this.verifiers = verifiers;
    this.verifierJson = verifierJson;
  }

  @Override
  public List<VerifierInfo> apply(TopLevelResource resource)
      throws RestApiException, PermissionBackendException, IOException {
    globalVerifierConfig.checkThatApiIsEnabled();
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_VERIFIERS);

    return verifiers.listVerifiers().stream().map(verifierJson::format).collect(toList());
  }
}

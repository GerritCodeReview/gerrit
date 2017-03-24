// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.CheckAccountExternalIdsResultInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalIdsConsistencyChecker;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class CheckConsistency implements RestModifyView<ConfigResource, ConsistencyCheckInput> {
  private final Provider<IdentifiedUser> userProvider;
  private final ExternalIdsConsistencyChecker externalIdsConsistencyChecker;

  @Inject
  CheckConsistency(
      Provider<IdentifiedUser> currentUser,
      ExternalIdsConsistencyChecker externalIdsConsistencyChecker) {
    this.userProvider = currentUser;
    this.externalIdsConsistencyChecker = externalIdsConsistencyChecker;
  }

  @Override
  public ConsistencyCheckInfo apply(ConfigResource resource, ConsistencyCheckInput input)
      throws RestApiException, IOException {
    IdentifiedUser user = userProvider.get();
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    if (!user.getCapabilities().canAccessDatabase()) {
      throw new AuthException("not allowed to run consistency checks");
    }

    if (input == null || input.checkAccountExternalIds == null) {
      throw new BadRequestException("input required");
    }

    ConsistencyCheckInfo consistencyCheckInfo = new ConsistencyCheckInfo();
    if (input.checkAccountExternalIds != null) {
      consistencyCheckInfo.checkAccountExternalIdsResult =
          new CheckAccountExternalIdsResultInfo(externalIdsConsistencyChecker.check());
    }

    return consistencyCheckInfo;
  }
}

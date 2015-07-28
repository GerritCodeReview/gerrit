// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.api.accounts;

import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.GpgKeys;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.IOException;

class GpgKeyApiImpl implements GpgKeyApi {
  interface Factory {
    GpgKeyApiImpl create(AccountResource.GpgKey rsrc);
  }

  private final GpgKeys.Get get;
  private final AccountResource.GpgKey rsrc;

  @AssistedInject
  GpgKeyApiImpl(
      GpgKeys.Get get,
      @Assisted AccountResource.GpgKey rsrc) {
    this.get = get;
    this.rsrc = rsrc;
  }

  @Override
  public GpgKeyInfo get() throws RestApiException {
    try {
      return get.apply(rsrc);
    } catch (IOException e) {
      throw new RestApiException("Cannot get GPG key", e);
    }
  }
}

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

package com.google.gerrit.gpg.api;

import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.gpg.server.GpgKeys;
import com.google.gerrit.gpg.server.PostGpgKeys;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.api.accounts.GpgApiAdapter;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.bouncycastle.openpgp.PGPException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GpgApiAdapterImpl implements GpgApiAdapter {
  private final PostGpgKeys postGpgKeys;
  private final GpgKeys gpgKeys;
  private final GpgKeyApiImpl.Factory gpgKeyApiFactory;

  @Inject
  GpgApiAdapterImpl(
      PostGpgKeys postGpgKeys,
      GpgKeys gpgKeys,
      GpgKeyApiImpl.Factory gpgKeyApiFactory) {
    this.postGpgKeys = postGpgKeys;
    this.gpgKeys = gpgKeys;
    this.gpgKeyApiFactory = gpgKeyApiFactory;
  }

  @Override
  public Map<String, GpgKeyInfo> listGpgKeys(AccountResource account)
      throws RestApiException, GpgException {
    try {
      return gpgKeys.list().apply(account);
    } catch (OrmException | PGPException | IOException e) {
      throw new GpgException(e);
    }
  }

  @Override
  public Map<String, GpgKeyInfo> putGpgKeys(AccountResource account,
      List<String> add, List<String> delete)
      throws RestApiException, GpgException {
    PostGpgKeys.Input in = new PostGpgKeys.Input();
    in.add = add;
    in.delete = delete;
    try {
      return postGpgKeys.apply(account, in);
    } catch (PGPException | OrmException | IOException e) {
      throw new GpgException(e);
    }
  }

  @Override
  public GpgKeyApi gpgKey(AccountResource account, IdString idStr)
      throws RestApiException, GpgException {
    try {
      return gpgKeyApiFactory.create(gpgKeys.parse(account, idStr));
    } catch (PGPException | OrmException | IOException e) {
      throw new GpgException(e);
    }
  }
}

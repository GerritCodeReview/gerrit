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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.gpg.server.DeleteGpgKey;
import com.google.gerrit.gpg.server.GpgKey;
import com.google.gerrit.gpg.server.GpgKeys;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import org.bouncycastle.openpgp.PGPException;

public class GpgKeyApiImpl implements GpgKeyApi {
  public interface Factory {
    GpgKeyApiImpl create(GpgKey rsrc);
  }

  private final GpgKeys.Get get;
  private final DeleteGpgKey delete;
  private final GpgKey rsrc;

  @AssistedInject
  GpgKeyApiImpl(GpgKeys.Get get, DeleteGpgKey delete, @Assisted GpgKey rsrc) {
    this.get = get;
    this.delete = delete;
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

  @Override
  public void delete() throws RestApiException {
    try {
      delete.apply(rsrc, new DeleteGpgKey.Input());
    } catch (PGPException | OrmException | IOException e) {
      throw new RestApiException("Cannot delete GPG key", e);
    }
  }
}

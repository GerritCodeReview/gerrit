// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.api2.changes;

import com.google.gerrit.extensions.api2.changes.ChangeApi;
import com.google.gerrit.extensions.api2.changes.RevisionApi;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.Revisions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class ChangeApiImpl implements ChangeApi {
  interface Factory {
    ChangeApiImpl create(ChangeResource change);
  }

  private final Revisions revisions;
  private final RevisionApiImpl.Factory revisionApi;
  private final ChangeResource change;

  @Inject
  ChangeApiImpl(Revisions revisions,
      RevisionApiImpl.Factory api,
      @Assisted ChangeResource change) {
    this.revisions = revisions;
    this.revisionApi = api;
    this.change = change;
  }

  @Override
  public RevisionApi revision(int id) throws RestApiException {
    return revision(String.valueOf(id));
  }

  @Override
  public RevisionApi revision(String id) throws RestApiException {
    try {
      return revisionApi.create(
          revisions.parse(change, IdString.fromDecoded(id)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot parse revision", e);
    }
  }
}

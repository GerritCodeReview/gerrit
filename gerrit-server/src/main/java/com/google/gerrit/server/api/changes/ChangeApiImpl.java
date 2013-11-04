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

package com.google.gerrit.server.api.changes;

import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.Abandon;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.Restore;
import com.google.gerrit.server.change.Revisions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

class ChangeApiImpl implements ChangeApi {
  interface Factory {
    ChangeApiImpl create(ChangeResource change);
  }

  private final Revisions revisions;
  private final RevisionApiImpl.Factory revisionApi;
  private final ChangeResource change;
  private final Provider<Abandon> abandon;
  private final Provider<Restore> restore;

  @Inject
  ChangeApiImpl(Revisions revisions,
      RevisionApiImpl.Factory api,
      Provider<Abandon> abandon,
      Provider<Restore> restore,
      @Assisted ChangeResource change) {
    this.revisions = revisions;
    this.revisionApi = api;
    this.abandon = abandon;
    this.restore = restore;
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

  @Override
  public void abandon(AbandonInput in) throws RestApiException {
    try {
      abandon.get().apply(change, in);
    } catch (OrmException e) {
      throw new RestApiException("Cannot abandon change", e);
    } catch (IOException e) {
      throw new RestApiException("Cannot abandon review", e);
    }
  }

  @Override
  public void restore(RestoreInput in) throws RestApiException {
    try {
      restore.get().apply(change, in);
    } catch (OrmException e) {
      throw new RestApiException("Cannot restore review", e);
    } catch (IOException e) {
      throw new RestApiException("Cannot restore review", e);
    }
  }
}

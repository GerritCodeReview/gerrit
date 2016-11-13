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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.Index.Input;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class Index implements RestModifyView<ChangeResource, Input> {
  public static class Input {}

  private final Provider<ReviewDb> db;
  private final ChangeIndexer indexer;

  @Inject
  Index(Provider<ReviewDb> db, ChangeIndexer indexer) {
    this.db = db;
    this.indexer = indexer;
  }

  @Override
  public Response<?> apply(ChangeResource rsrc, Input input)
      throws IOException, AuthException, OrmException {
    ChangeControl ctl = rsrc.getControl();
    if (!ctl.isOwner() && !ctl.getUser().getCapabilities().canMaintainServer()) {
      throw new AuthException("Only change owner or server maintainer can reindex");
    }
    indexer.index(db.get(), rsrc.getChange());
    return Response.none();
  }
}

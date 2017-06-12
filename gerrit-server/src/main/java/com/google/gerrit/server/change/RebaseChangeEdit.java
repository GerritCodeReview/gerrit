// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class RebaseChangeEdit
    implements ChildCollection<ChangeResource, ChangeEditResource>, AcceptsPost<ChangeResource> {

  private final Rebase rebase;

  @Inject
  RebaseChangeEdit(Rebase rebase) {
    this.rebase = rebase;
  }

  @Override
  public DynamicMap<RestView<ChangeEditResource>> views() {
    throw new NotImplementedException();
  }

  @Override
  public RestView<ChangeResource> list() {
    throw new NotImplementedException();
  }

  @Override
  public ChangeEditResource parse(ChangeResource parent, IdString id) {
    throw new NotImplementedException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Rebase post(ChangeResource parent) throws RestApiException {
    return rebase;
  }

  @Singleton
  public static class Rebase implements RestModifyView<ChangeResource, Rebase.Input> {
    public static class Input {}

    private final ChangeEditModifier editModifier;
    private final ChangeEditUtil editUtil;
    private final PatchSetUtil psUtil;
    private final Provider<ReviewDb> db;

    @Inject
    Rebase(
        ChangeEditModifier editModifier,
        ChangeEditUtil editUtil,
        PatchSetUtil psUtil,
        Provider<ReviewDb> db) {
      this.editModifier = editModifier;
      this.editUtil = editUtil;
      this.psUtil = psUtil;
      this.db = db;
    }

    @Override
    public Response<?> apply(ChangeResource rsrc, Rebase.Input in)
        throws AuthException, ResourceConflictException, IOException,
            InvalidChangeOperationException, OrmException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (!edit.isPresent()) {
        throw new ResourceConflictException(
            String.format("no edit exists for change %s", rsrc.getChange().getChangeId()));
      }

      PatchSet current = psUtil.current(db.get(), rsrc.getNotes());
      if (current.getId().equals(edit.get().getBasePatchSet().getId())) {
        throw new ResourceConflictException(
            String.format(
                "edit for change %s is already on latest patch set: %s",
                rsrc.getChange().getChangeId(), current.getId()));
      }
      editModifier.rebaseEdit(edit.get(), current);
      return Response.none();
    }
  }
}

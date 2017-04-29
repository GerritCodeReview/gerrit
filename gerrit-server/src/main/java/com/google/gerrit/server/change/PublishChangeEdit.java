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

import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class PublishChangeEdit
    implements ChildCollection<ChangeResource, ChangeEditResource>, AcceptsPost<ChangeResource> {

  private final Publish publish;

  @Inject
  PublishChangeEdit(Publish publish) {
    this.publish = publish;
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
  public Publish post(ChangeResource parent) throws RestApiException {
    return publish;
  }

  @Singleton
  public static class Publish
      extends RetryingRestModifyView<ChangeResource, PublishChangeEditInput, Response<?>> {

    private final ChangeEditUtil editUtil;
    private final NotifyUtil notifyUtil;

    @Inject
    Publish(RetryHelper retryHelper, ChangeEditUtil editUtil, NotifyUtil notifyUtil) {
      super(retryHelper);
      this.editUtil = editUtil;
      this.notifyUtil = notifyUtil;
    }

    @Override
    protected Response<?> applyImpl(
        BatchUpdate.Factory updateFactory, ChangeResource rsrc, PublishChangeEditInput in)
        throws IOException, OrmException, RestApiException, UpdateException {
      CreateChange.checkValidCLA(rsrc.getControl().getProjectControl());
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (!edit.isPresent()) {
        throw new ResourceConflictException(
            String.format("no edit exists for change %s", rsrc.getChange().getChangeId()));
      }
      if (in == null) {
        in = new PublishChangeEditInput();
      }
      editUtil.publish(
          updateFactory,
          rsrc.getControl(),
          edit.get(),
          in.notify,
          notifyUtil.resolveAccounts(in.notifyDetails));
      return Response.none();
    }
  }
}

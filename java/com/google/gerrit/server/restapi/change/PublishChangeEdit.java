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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.change.ChangeEditResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestCollectionView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PublishChangeEdit
    implements ChildCollection<ChangeResource, ChangeEditResource.Publish> {

  private final DynamicMap<RestView<ChangeEditResource.Publish>> views;

  @Inject
  PublishChangeEdit(DynamicMap<RestView<ChangeEditResource.Publish>> views) {
    this.views = views;
  }

  @Override
  public DynamicMap<RestView<ChangeEditResource.Publish>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public ChangeEditResource.Publish parse(ChangeResource parent, IdString id)
      throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Singleton
  public static class Publish
      extends RetryingRestCollectionView<
          ChangeResource, ChangeEditResource.Publish, PublishChangeEditInput, Response<?>> {

    private final ChangeEditUtil editUtil;
    private final NotifyUtil notifyUtil;
    private final ContributorAgreementsChecker contributorAgreementsChecker;

    @Inject
    Publish(
        RetryHelper retryHelper,
        ChangeEditUtil editUtil,
        NotifyUtil notifyUtil,
        ContributorAgreementsChecker contributorAgreementsChecker) {
      super(retryHelper);
      this.editUtil = editUtil;
      this.notifyUtil = notifyUtil;
      this.contributorAgreementsChecker = contributorAgreementsChecker;
    }

    @Override
    protected Response<?> applyImpl(
        BatchUpdate.Factory updateFactory, ChangeResource rsrc, PublishChangeEditInput in)
        throws IOException, OrmException, RestApiException, UpdateException, ConfigInvalidException,
            NoSuchProjectException {
      contributorAgreementsChecker.check(rsrc.getProject(), rsrc.getUser());
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getNotes(), rsrc.getUser());
      if (!edit.isPresent()) {
        throw new ResourceConflictException(
            String.format("no edit exists for change %s", rsrc.getChange().getChangeId()));
      }
      if (in == null) {
        in = new PublishChangeEditInput();
      }
      editUtil.publish(
          updateFactory,
          rsrc.getNotes(),
          rsrc.getUser(),
          edit.get(),
          in.notify,
          notifyUtil.resolveAccounts(in.notifyDetails));
      return Response.none();
    }
  }
}

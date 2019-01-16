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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PublishChangeEdit
    extends RetryingRestModifyView<ChangeResource, PublishChangeEditInput, Response<?>> {
  private final ChangeEditUtil editUtil;
  private final NotifyResolver notifyResolver;
  private final ContributorAgreementsChecker contributorAgreementsChecker;

  @Inject
  PublishChangeEdit(
      RetryHelper retryHelper,
      ChangeEditUtil editUtil,
      NotifyResolver notifyResolver,
      ContributorAgreementsChecker contributorAgreementsChecker) {
    super(retryHelper);
    this.editUtil = editUtil;
    this.notifyResolver = notifyResolver;
    this.contributorAgreementsChecker = contributorAgreementsChecker;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, PublishChangeEditInput in)
      throws IOException, StorageException, RestApiException, UpdateException,
          ConfigInvalidException, NoSuchProjectException {
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
        notifyResolver.resolve(firstNonNull(in.notify, NotifyHandling.ALL), in.notifyDetails));
    return Response.none();
  }
}

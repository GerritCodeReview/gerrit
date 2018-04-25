// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.ChangeMessageApi;
import com.google.gerrit.extensions.api.changes.DeleteChangeMessageInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ChangeMessageResource;
import com.google.gerrit.server.restapi.change.DeleteChangeMessage;
import com.google.gerrit.server.restapi.change.GetChangeMessage;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class ChangeMessageApiImpl implements ChangeMessageApi {
  interface Factory {
    ChangeMessageApiImpl create(ChangeMessageResource changeMessageResource);
  }

  private final GetChangeMessage getChangeMessage;
  private final DeleteChangeMessage deleteChangeMessage;
  private final ChangeMessageResource changeMessageResource;

  @Inject
  ChangeMessageApiImpl(
      GetChangeMessage getChangeMessage,
      DeleteChangeMessage deleteChangeMessage,
      @Assisted ChangeMessageResource changeMessageResource) {
    this.getChangeMessage = getChangeMessage;
    this.deleteChangeMessage = deleteChangeMessage;
    this.changeMessageResource = changeMessageResource;
  }

  @Override
  public ChangeMessageInfo get() throws RestApiException {
    try {
      return getChangeMessage.apply(changeMessageResource);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change message", e);
    }
  }

  @Override
  public ChangeMessageInfo delete(DeleteChangeMessageInput input) throws RestApiException {
    try {
      return deleteChangeMessage.apply(changeMessageResource, input).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot delete change message", e);
    }
  }
}

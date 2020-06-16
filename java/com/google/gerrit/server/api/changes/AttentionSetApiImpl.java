// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.AttentionSetApi;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.AttentionSetEntryResource;
import com.google.gerrit.server.restapi.change.RemoveFromAttentionSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AttentionSetApiImpl implements AttentionSetApi {
  interface Factory {
    AttentionSetApiImpl create(AttentionSetEntryResource attentionSetEntryResource);
  }

  private final RemoveFromAttentionSet removeFromAttentionSet;
  private final AttentionSetEntryResource attentionSetEntryResource;

  @Inject
  AttentionSetApiImpl(
      RemoveFromAttentionSet removeFromAttentionSet,
      @Assisted AttentionSetEntryResource attentionSetEntryResource) {
    this.removeFromAttentionSet = removeFromAttentionSet;
    this.attentionSetEntryResource = attentionSetEntryResource;
  }

  @Override
  public void remove(AttentionSetInput input) throws RestApiException {
    try {
      removeFromAttentionSet.apply(attentionSetEntryResource, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot remove from attention set", e);
    }
  }
}

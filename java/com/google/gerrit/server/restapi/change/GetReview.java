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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetReview implements RestReadView<RevisionResource> {
  private final GetChange delegate;

  @Inject
  GetReview(GetChange delegate) {
    this.delegate = delegate;
    delegate.addOption(ListChangesOption.DETAILED_LABELS);
    delegate.addOption(ListChangesOption.DETAILED_ACCOUNTS);
  }

  @Override
  public Response<ChangeInfo> apply(RevisionResource rsrc) throws StorageException {
    return delegate.apply(rsrc);
  }
}

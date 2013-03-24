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

import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

class GetComment implements RestReadView<CommentResource> {

  private final AccountInfo.Loader.Factory accountLoaderFactory;

  @Inject
  GetComment(AccountInfo.Loader.Factory accountLoaderFactory) {
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Object apply(CommentResource rsrc) throws OrmException {
    AccountInfo.Loader accountLoader = accountLoaderFactory.create(true);
    CommentInfo ci = new CommentInfo(rsrc.getComment(), accountLoader);
    accountLoader.fill();
    return ci;
  }
}

// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.PatchDetailService;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Provider;

class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final ChangeControl.GenericFactory changeControlFactory;

  @Inject
  PatchDetailServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final PatchScriptFactory.Factory patchScriptFactoryFactory,
      final ChangeControl.GenericFactory changeControlFactory) {
    super(schema, currentUser);

    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.changeControlFactory = changeControlFactory;
  }

  @Override
  public void patchScript(final Patch.Key patchKey, final PatchSet.Id psa,
      final PatchSet.Id psb, final AccountDiffPreference dp,
      final AsyncCallback<PatchScript> callback) {
    if (psb == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    new Handler<PatchScript>() {
      @Override
      public PatchScript call() throws Exception {
        ChangeControl control = changeControlFactory.validateFor(
            patchKey.getParentKey().getParentKey(),
            getCurrentUser());
        return patchScriptFactoryFactory.create(
            control, patchKey.getFileName(), psa, psb, dp).call();
      }
    }.to(callback);
  }
}

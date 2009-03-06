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

package com.google.gerrit.server;

import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.SystemInfoService;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;

import java.util.List;

public class SystemInfoServiceImpl implements SystemInfoService {
  public void loadGerritConfig(final AsyncCallback<GerritConfig> callback) {
    callback.onSuccess(Common.getGerritConfig());
  }

  public void contributorAgreements(
      final AsyncCallback<List<ContributorAgreement>> callback) {
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        callback.onSuccess(db.contributorAgreements().active().toList());
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }
}

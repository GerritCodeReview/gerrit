// Copyright 2008 Google Inc.
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

package com.google.gerrit.server.patch;

import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.patches.PatchDetailService;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.SchemaFactory;

public class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  public PatchDetailServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    super(rdf);
  }

  public void unifiedPatchDetail(final Patch.Id key,
      final AsyncCallback<UnifiedPatchDetail> callback) {
    run(callback, new UnifiedPatchDetailAction(key));
  }
}

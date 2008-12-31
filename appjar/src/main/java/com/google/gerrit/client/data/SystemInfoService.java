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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.rpc.SignInRequired;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.AllowCrossSiteRequest;
import com.google.gwtjsonrpc.client.HostPageCache;
import com.google.gwtjsonrpc.client.RemoteJsonService;

import java.util.List;

public interface SystemInfoService extends RemoteJsonService {
  @AllowCrossSiteRequest
  @HostPageCache(name = "gerrit_gerritconfig_obj", once = true)
  void loadGerritConfig(AsyncCallback<GerritConfig> callback);

  @SignInRequired
  @AllowCrossSiteRequest
  void contributorAgreements(AsyncCallback<List<ContributorAgreement>> callback);
}

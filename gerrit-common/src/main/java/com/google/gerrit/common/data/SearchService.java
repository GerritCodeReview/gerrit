// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.reviewdb.Search;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.RpcImpl;
import com.google.gwtjsonrpc.client.RpcImpl.Version;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface SearchService extends RemoteJsonService {

  @SignInRequired
  void createSearch(Search search, AsyncCallback<String> callback);

  @SignInRequired
  void changeSearch(Search.Key key, Search search, AsyncCallback<String> callback);

  @SignInRequired
  void deleteSearches(Set<Search.Key> keys, AsyncCallback<VoidResult> callback);
}

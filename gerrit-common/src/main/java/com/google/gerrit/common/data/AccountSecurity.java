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

package com.google.gerrit.common.data;

import com.google.gerrit.common.audit.Audit;
import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.RpcImpl;
import com.google.gwtjsonrpc.common.RpcImpl.Version;
import java.util.List;
import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface AccountSecurity extends RemoteJsonService {
  @SignInRequired
  void myExternalIds(AsyncCallback<List<AccountExternalId>> callback);

  @Audit
  @SignInRequired
  void deleteExternalIds(
      Set<AccountExternalId.Key> keys, AsyncCallback<Set<AccountExternalId.Key>> callback);
}

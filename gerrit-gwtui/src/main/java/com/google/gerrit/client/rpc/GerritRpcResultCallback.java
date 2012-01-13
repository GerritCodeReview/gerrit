// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.rpc;

import com.google.gerrit.common.data.RpcError;
import com.google.gerrit.common.data.RpcResult;

public abstract class GerritRpcResultCallback<R, E extends RpcError<?>> extends
    GerritCallback<RpcResult<R, E>> {

  @Override
  public final void onSuccess(final RpcResult<R, E> r) {
    if (r.result != null) {
      onResult(r.result);
    }
    if (r.error != null) {
      onError(r.error);
    }
  }

  public abstract void onResult(R result);
  public abstract void onError(E error);
}

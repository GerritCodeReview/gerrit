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

package com.google.gerrit.common.data;

public class RpcResult<R, E extends RpcError<?>> {

  public static <R, E extends RpcError<?>> RpcResult<R, E> result(final R result) {
    final RpcResult<R, E> r = new RpcResult<R, E>();
    r.result = result;
    return r;
  }

  public static <R, E extends RpcError<?>> RpcResult<R, E> error(final E error) {
    final RpcResult<R, E> r = new RpcResult<R, E>();
    r.error = error;
    return r;
  }

  public R result;
  public E error;

  protected RpcResult() {
  }
}

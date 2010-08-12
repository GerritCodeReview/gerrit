// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ConcatFuture<V> extends AbstractListenableFuture<List<V>> {
  private final AtomicInteger pending;
  private final List<ListenableFuture<List<V>>> sources;

  ConcatFuture(List<ListenableFuture<List<V>>> all) {
    sources = all;
    pending = new AtomicInteger(sources.size());

    Runnable done = new Runnable() {
      @Override
      public void run() {
        finish();
      }
    };
    for (ListenableFuture<List<V>> future : sources) {
      future.addListener(done, MoreExecutors.sameThreadExecutor());
    }
  }

  private void finish() {
    if (pending.decrementAndGet() == 0) {
      List<V> all = Lists.newArrayList();
      for (ListenableFuture<List<V>> f : sources) {
        all.addAll(FutureUtil.get(f));
      }
      set(all);
    }
  }
}

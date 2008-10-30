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

package com.google.codereview.manager;

import com.google.codereview.internal.Admin.AdminService;
import com.google.codereview.internal.Build.BuildService;
import com.google.codereview.internal.BundleStore.BundleStoreService;
import com.google.codereview.internal.Change.ChangeService;
import com.google.codereview.internal.Merge.MergeService;
import com.google.protobuf.RpcChannel;

import org.spearce.jgit.lib.PersonIdent;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Configuration and state related to a single Gerrit backend process.
 */
public class Backend {
  private final RepositoryCache repoCache;
  private final RpcChannel rpc;
  private final ScheduledExecutorService executor;
  private final PersonIdent mergeIdent;

  private final AdminService adminSvc;
  private final BuildService buildSvc;
  private final BundleStoreService bundleStoreSvc;
  private final ChangeService changeSvc;
  private final MergeService mergeSvc;

  public Backend(final RepositoryCache cache, final RpcChannel api,
      final ScheduledExecutorService threadPool,
      final PersonIdent performMergsAs) {
    repoCache = cache;
    rpc = api;
    executor = threadPool;
    mergeIdent = performMergsAs;

    adminSvc = AdminService.newStub(rpc);
    buildSvc = BuildService.newStub(rpc);
    bundleStoreSvc = BundleStoreService.newStub(rpc);
    changeSvc = ChangeService.newStub(rpc);
    mergeSvc = MergeService.newStub(rpc);
  }

  public RepositoryCache getRepositoryCache() {
    return repoCache;
  }

  public RpcChannel getRpcChannel() {
    return rpc;
  }

  public PersonIdent getMergeIdentity() {
    return mergeIdent;
  }

  /**
   * @return a copy of {@link #getMergeIdentity()} modified to use the current
   *         system clock as the time, in the GMT/UTC time zone.
   */
  public PersonIdent newMergeIdentity() {
    return new PersonIdent(getMergeIdentity(), System.currentTimeMillis(), 0);
  }

  /**
   * Schedule a task for execution on a background thread.
   * 
   * @param task runnable to perform the task. The task will be executed once,
   *        as soon as a thread is available.
   */
  public void asyncExec(final Runnable task) {
    executor.submit(task);
  }

  public AdminService getAdminService() {
    return adminSvc;
  }

  public BuildService getBuildService() {
    return buildSvc;
  }

  public BundleStoreService getBundleStoreService() {
    return bundleStoreSvc;
  }

  public ChangeService getChangeService() {
    return changeSvc;
  }

  public MergeService getMergeService() {
    return mergeSvc;
  }
}

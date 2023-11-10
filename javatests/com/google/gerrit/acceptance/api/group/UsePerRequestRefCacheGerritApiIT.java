// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.group;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static junit.framework.TestCase.assertTrue;

@UseLocalDisk
public class UsePerRequestRefCacheGerritApiIT extends AbstractDaemonTest {

  @Inject
  private RequestScopeOperations requestScopeOperations;


  private GroupApi createGroup(String name) throws RestApiException {
    requestScopeOperations.setApiUser(admin.id());
    return gApi.groups().create(name);
  }


  //Operation: create Group
  // 1. open repository `allUsersName` (snapshot representation)
  // 2. create GroupNameNotes instance
  // 3. create GroupConfig instance
  // 4. commit

  @Test
//  @GerritConfig(name = "core.usePerRequestRefCache", value = "false")
  @GerritConfig(name = "core.usePerRequestRefCache", value = "true")
  public void shouldAAAA() throws Exception{
    for (int i = 0; i < 400; i++) {
      run2CreateGroupsOperation("group-" + i);
    }

  }


  private void run2CreateGroupsOperation(String name) {
    // Create an ExecutorService with two threads
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    // Define a Callable task for the API call
    Callable<GroupApi> task = () -> createGroup(name);
    // Submit the tasks for execution
    Future<GroupApi> future1 = executorService.submit(task);
    Future<GroupApi> future2 = executorService.submit(task);

    GroupApi result1 = null;
    GroupApi result2 = null;

    // Wait for the tasks to complete and get the results
    try {
      result1 = future1.get();
    } catch (Exception e) {
      System.out.println(">>>>> exception first try:" + e.getCause());
    }

    try {
      result2 = future2.get();
    } catch (Exception e) {
      System.out.println(">>>>> exception second try:" + e.getCause());
    }

    // Check if at least one of the tasks succeeded
    boolean onlyOneSucceeded = (result1 != null && result2 == null) ||
        (result1 == null && result2 != null);

    // Assert that at least one task succeeded
    assertTrue(onlyOneSucceeded);
    // Shutdown the executor service
    executorService.shutdown();
  }

}

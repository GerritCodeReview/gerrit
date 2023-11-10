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
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static junit.framework.TestCase.assertTrue;


@UseLocalDisk
public class UsePerRequestRefCacheGroupsUpdateIT extends AbstractDaemonTest {
  @Inject @ServerInitiated
  private GroupsUpdate groupsUpdate;

  @Inject
  private RequestScopeOperations requestScopeOperations;


  @Inject private Sequences seq;


  private InternalGroup createGroup(String name) throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    AccountGroup.UUID groupUuid = AccountGroup.uuid(name + "-UUID");
    return testRefAction(
        () ->
            groupsUpdate.createGroup(
                InternalGroupCreation.builder()
                    .setGroupUUID(groupUuid)
                    .setNameKey(AccountGroup.nameKey(name))
                    .setId(AccountGroup.id(seq.nextGroupId()))
                    .build(),
                GroupDelta.builder().build()));
  }


  //Operation: create Group
  // 1. open repository `allUsersName` (snapshot representation)
  // 2. create GroupNameNotes instance
  // 3. create GroupConfig instance
  // 4. commit

  @Test
  @GerritConfig(name = "core.usePerRequestRefCache", value = "true") // inconsistency
//  @GerritConfig(name = "core.usePerRequestRefCache", value = "false") // duplicate key
  public void shouldAAAA() throws Exception{
      run2CreateGroupsOperation("foo");
  }


  private void run2CreateGroupsOperation(String name) {

    // Create an ExecutorService with two threads
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    // Define a Callable task for the API call
    Callable<InternalGroup> task = () -> createGroup(name);
    // Submit the tasks for execution
    Future<InternalGroup> future1 = executorService.submit(task);
    Future<InternalGroup> future2 = executorService.submit(task);

    InternalGroup result1 = null;
    InternalGroup result2 = null;

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

    System.out.println("result1 " + result1);
    System.out.println("result2 " + result2);
//     Check if at least one of the tasks succeeded
    boolean onlyOneSucceeded = (result1 != null && result2 == null) ||
        (result1 == null && result2 != null);
//
    // Assert that at least one task succeeded
//    assertTrue(result1 != null);
    assertTrue(onlyOneSucceeded);
    // Shutdown the executor service
    executorService.shutdown();
  }

}

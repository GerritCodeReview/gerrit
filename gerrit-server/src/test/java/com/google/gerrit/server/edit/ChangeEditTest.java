// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;

import org.junit.Test;

public class ChangeEditTest {
  @Test
  public void changeEditRef() throws Exception {
    Change.Id changeId = new Change.Id(1);
    Account.Id accountId = new Account.Id(1);
    String refName = ChangeEditUtil.editRefName(accountId, changeId);
    assertEquals("refs/users/01/1/edit-1", refName);
  }
}

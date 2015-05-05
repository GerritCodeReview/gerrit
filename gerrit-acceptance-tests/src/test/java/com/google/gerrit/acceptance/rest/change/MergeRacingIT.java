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

package com.google.gerrit.acceptance.rest.change;

import com.google.gerrit.server.git.ChangeMergeQueue;
import com.google.gerrit.server.git.ChangeSetMerger;
import com.google.gerrit.server.git.MergeQueue;
import com.google.inject.Inject;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;

public class MergeRacingIT {

  private MergeQueue mergeQueue;
  private IMocksControl mockMaker;


  @Inject
  MergeRacingIT(MergeQueue mergeQueue) {
    this.mergeQueue = mergeQueue;
  }

  @Before
  void setUp() {
    mockMaker = EasyMock.createStrictControl();
    ChangeSetMerger changeSetMerger = mockMaker.createMock(ChangeSetMerger.class);
    ((ChangeMergeQueue)mergeQueue).setMergeBackend(changeSetMerger);
  }




}

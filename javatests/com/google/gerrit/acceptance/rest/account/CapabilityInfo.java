// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

class CapabilityInfo {
  public boolean accessDatabase;
  public boolean administrateServer;
  public BatchChangesLimit batchChangesLimit;
  public boolean createAccount;
  public boolean createGroup;
  public boolean createProject;
  public boolean emailReviewers;
  public boolean flushCaches;
  public boolean killTask;
  public boolean maintainServer;
  public boolean modifyAccount;
  public boolean priority;
  public QueryLimit queryLimit;
  public boolean runAs;
  public boolean runGC;
  public boolean streamEvents;
  public boolean viewAllAccounts;
  public boolean viewCaches;
  public boolean viewConnections;
  public boolean viewPlugins;
  public boolean viewQueue;
  public boolean viewAccess;

  static class QueryLimit {
    short min;
    short max;
  }

  static class BatchChangesLimit {
    short min;
    short max;
  }
}

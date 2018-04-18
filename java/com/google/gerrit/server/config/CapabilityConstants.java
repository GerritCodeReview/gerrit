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

package com.google.gerrit.server.config;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

public class CapabilityConstants extends TranslationBundle {
  public static CapabilityConstants get() {
    return NLS.getBundleFor(CapabilityConstants.class);
  }

  public String accessDatabase;
  public String administrateServer;
  public String batchChangesLimit;
  public String createAccount;
  public String createGroup;
  public String createProject;
  public String emailReviewers;
  public String flushCaches;
  public String killTask;
  public String maintainServer;
  public String modifyAccount;
  public String priority;
  public String queryLimit;
  public String runAs;
  public String runGC;
  public String streamEvents;
  public String viewAllAccounts;
  public String viewCaches;
  public String viewConnections;
  public String viewPlugins;
  public String viewQueue;
  public String viewAccess;
}

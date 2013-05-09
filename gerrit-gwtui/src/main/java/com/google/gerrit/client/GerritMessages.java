// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gwt.i18n.client.Messages;

public interface GerritMessages extends Messages {
  String windowTitle1(String hostname);
  String windowTitle2(String section, String hostname);
  String poweredBy(String version);

  String noSuchAccountMessage(String who);
  String noSuchGroupMessage(String who);
  String nameAlreadyUsedBody(String alreadyUsedName);

  String branchCreationFailed(String branchName, String error);
  String invalidBranchName(String branchName);
  String invalidRevision(String revision);
  String branchCreationNotAllowedUnderRefnamePrefix(String refnamePrefix);
  String branchAlreadyExists(String branchName);
  String branchCreationConflict(String branchName, String existingBranchName);

  String pluginFailed(String scriptPath);
  String cannotDownloadlPlugin(String scriptPath);
}

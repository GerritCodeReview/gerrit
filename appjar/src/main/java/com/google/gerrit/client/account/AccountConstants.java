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

package com.google.gerrit.client.account;

import com.google.gwt.i18n.client.Constants;

public interface AccountConstants extends Constants {
  String accountSettingsHeading();

  String fullName();
  String preferredEmail();
  String registeredOn();
  String defaultContext();

  String tabPreferences();
  String tabSshKeys();
  String tabAgreements();

  String buttonDeleteSshKey();
  String buttonAddSshKey();

  String sshKeyAlgorithm();
  String sshKeyKey();
  String sshKeyComment();
  String sshKeyLastUsed();
  String sshKeyStored();

  String addSshKeyPanelHeader();
}

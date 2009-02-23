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
  String sshUserName();
  String registeredOn();
  String accountId();
  String defaultContext();
  String showSiteHeader();

  String tabPreferences();
  String tabContactInformation();
  String tabSshKeys();
  String tabWebIdentities();
  String tabAgreements();

  String buttonDeleteSshKey();
  String buttonClearSshKeyInput();
  String buttonOpenSshKey();
  String buttonAddSshKey();

  String sshKeyInvalid();
  String sshKeyAlgorithm();
  String sshKeyKey();
  String sshKeyComment();
  String sshKeyLastUsed();
  String sshKeyStored();

  String addSshKeyPanelHeader();
  String addSshKeyHelp();
  String sshJavaAppletNotAvailable();
  String invalidSshKeyError();

  String webIdLastUsed();
  String webIdEmail();
  String webIdIdentity();
  String buttonLinkIdentity();

  String watchedProjects();
  String buttonWatchProject();
  String defaultProjectName();
  String watchedProjectColumnEmailNotifications();
  String watchedProjectColumnNewChanges();
  String watchedProjectColumnAllComments();

  String contactFieldFullName();
  String contactFieldEmail();
  String contactPrivacyDetailsHtml();
  String contactFieldAddress();
  String contactFieldCountry();
  String contactFieldPhone();
  String contactFieldFax();
  String buttonSaveContact();
  String buttonOpenRegisterNewEmail();
  String buttonSendRegisterNewEmail();
  String titleRegisterNewEmail();
  String descRegisterNewEmail();

  String newAgreement();
  String agreementStatus();
  String agreementName();
  String agreementDescription();
  String agreementAccepted();
  String agreementStatus_EXPIRED();
  String agreementStatus_NEW();
  String agreementStatus_REJECTED();
  String agreementStatus_VERIFIED();

  String newAgreementSelectTypeHeading();
  String newAgreementNoneAvailable();
  String newAgreementReviewLegalHeading();
  String newAgreementReviewContactHeading();
  String newAgreementCompleteHeading();
  String newAgreementIAGREE();
  String newAgreementAlreadySubmitted();
  String buttonSubmitNewAgreement();
}

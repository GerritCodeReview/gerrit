// Copyright (C) 2008 The Android Open Source Project
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
  String settingsHeading();

  String fullName();
  String preferredEmail();
  String registeredOn();
  String accountId();

  String maximumPageSizeFieldLabel();
  String dateFormatLabel();
  String contextWholeFile();
  String showSiteHeader();
  String useFlashClipboard();
  String copySelfOnEmails();
  String reversePatchSetOrder();
  String showUsernameInReviewCategory();
  String buttonSaveChanges();
  String showRelativeDateInChangeTable();

  String tabAccountSummary();
  String tabPreferences();
  String tabWatchedProjects();
  String tabContactInformation();
  String tabSshKeys();
  String tabHttpAccess();
  String tabWebIdentities();
  String tabMyGroups();
  String tabAgreements();

  String buttonShowAddSshKey();
  String buttonCloseAddSshKey();
  String buttonDeleteSshKey();
  String buttonClearSshKeyInput();
  String buttonAddSshKey();

  String userName();
  String password();
  String buttonSetUserName();
  String buttonChangeUserName();
  String buttonClearPassword();
  String buttonGeneratePassword();
  String linkObtainPassword();
  String linkEditFullName();
  String linkReloadContact();
  String invalidUserName();
  String invalidUserEmail();

  String sshKeyInvalid();
  String sshKeyAlgorithm();
  String sshKeyKey();
  String sshKeyComment();
  String sshKeyStatus();

  String addSshKeyPanelHeader();
  String addSshKeyHelpTitle();
  String addSshKeyHelp();
  String sshJavaAppletNotAvailable();
  String invalidSshKeyError();

  String sshHostKeyTitle();
  String sshHostKeyFingerprint();
  String sshHostKeyKnownHostEntry();

  String webIdStatus();
  String webIdEmail();
  String webIdIdentity();
  String untrustedProvider();
  String buttonDeleteIdentity();
  String buttonLinkIdentity();

  String buttonWatchProject();
  String defaultProjectName();
  String defaultFilter();
  String buttonBrowseProjects();
  String projects();
  String projectsClose();
  String projectListOpen();
  String watchedProjectName();
  String watchedProjectFilter();
  String watchedProjectColumnEmailNotifications();
  String watchedProjectColumnNewChanges();
  String watchedProjectColumnNewPatchSets();
  String watchedProjectColumnAllComments();
  String watchedProjectColumnSubmittedChanges();
  String watchedProjectColumnAbandonedChanges();

  String contactFieldFullName();
  String contactFieldEmail();
  String contactPrivacyDetailsHtml();
  String contactFieldAddress();
  String contactFieldCountry();
  String contactFieldPhone();
  String contactFieldFax();
  String buttonOpenRegisterNewEmail();
  String buttonSendRegisterNewEmail();
  String buttonCancel();
  String titleRegisterNewEmail();
  String descRegisterNewEmail();
  String errorDialogTitleRegisterNewEmail();

  String newAgreement();
  String agreementStatus();
  String agreementName();
  String agreementDescription();
  String agreementStatus_EXPIRED();
  String agreementStatus_VERIFIED();

  String newAgreementSelectTypeHeading();
  String newAgreementNoneAvailable();
  String newAgreementReviewLegalHeading();
  String newAgreementReviewContactHeading();
  String newAgreementCompleteHeading();
  String newAgreementIAGREE();
  String newAgreementAlreadySubmitted();
  String buttonSubmitNewAgreement();

  String welcomeToGerritCodeReview();
  String welcomeReviewContact();
  String welcomeContactFrom();
  String welcomeUsernameHeading();
  String welcomeSshKeyHeading();
  String welcomeSshKeyText();
  String welcomeAgreementHeading();
  String welcomeAgreementText();
  String welcomeAgreementLater();
  String welcomeContinue();
}

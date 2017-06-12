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

  String changeAvatar();

  String fullName();

  String preferredEmail();

  String registeredOn();

  String accountId();

  String diffViewLabel();

  String maximumPageSizeFieldLabel();

  String dateFormatLabel();

  String contextWholeFile();

  String showSiteHeader();

  String useFlashClipboard();

  String reviewCategoryLabel();

  String messageShowInReviewCategoryNone();

  String messageShowInReviewCategoryName();

  String messageShowInReviewCategoryEmail();

  String messageShowInReviewCategoryUsername();

  String messageShowInReviewCategoryAbbrev();

  String buttonSaveChanges();

  String highlightAssigneeInChangeTable();

  String showRelativeDateInChangeTable();

  String showSizeBarInChangeTable();

  String showLegacycidInChangeTable();

  String muteCommonPathPrefixes();

  String signedOffBy();

  String myMenu();

  String myMenuInfo();

  String myMenuName();

  String myMenuUrl();

  String myMenuReset();

  String tabAccountSummary();

  String tabAgreements();

  String tabContactInformation();

  String tabDiffPreferences();

  String tabEditPreferences();

  String tabGpgKeys();

  String tabHttpAccess();

  String tabMyGroups();

  String tabOAuthToken();

  String tabPreferences();

  String tabSshKeys();

  String tabWatchedProjects();

  String tabWebIdentities();

  String buttonShowAddSshKey();

  String buttonCloseAddSshKey();

  String buttonDeleteSshKey();

  String buttonClearSshKeyInput();

  String buttonAddSshKey();

  String userName();

  String password();

  String buttonSetUserName();

  String confirmSetUserNameTitle();

  String confirmSetUserName();

  String buttonClearPassword();

  String buttonGeneratePassword();

  String linkObtainPassword();

  String linkEditFullName();

  String linkReloadContact();

  String invalidUserName();

  String invalidUserEmail();

  String labelOAuthToken();

  String labelOAuthExpires();

  String labelOAuthNetRCEntry();

  String labelOAuthGitCookie();

  String labelOAuthExpired();

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

  String gpgKeyId();

  String gpgKeyFingerprint();

  String gpgKeyUserIds();

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

  String buttonOpenRegisterNewEmail();

  String buttonSendRegisterNewEmail();

  String buttonCancel();

  String titleRegisterNewEmail();

  String descRegisterNewEmail();

  String errorDialogTitleRegisterNewEmail();

  String newAgreement();

  String agreementName();

  String agreementDescription();

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

  String messageEnabled();

  String messageCCMeOnMyComments();

  String messageDisabled();

  String emailFieldLabel();

  String defaultBaseForMerges();

  String autoMerge();

  String firstParent();
}

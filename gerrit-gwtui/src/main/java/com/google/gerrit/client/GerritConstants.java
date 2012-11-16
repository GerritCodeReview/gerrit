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

package com.google.gerrit.client;

import com.google.gwt.i18n.client.Constants;

public interface GerritConstants extends Constants {
  String menuSignIn();
  String menuSignOut();
  String menuRegister();
  String menuSettings();
  String reportBug();

  String buttonSignIn();

  String signInDialogTitle();
  String signInDialogClose();
  String signInFailed();

  String linkIdentityDialogTitle();
  String registerDialogTitle();
  String loginTypeUnsupported();

  String errorDialogTitle();
  String errorDialogContinue();

  String confirmationDialogOk();
  String confirmationDialogCancel();

  String branchDeletionDialogTitle();
  String branchDeletionConfirmationMessage();

  String notSignedInTitle();
  String notSignedInBody();

  String notFoundTitle();
  String notFoundBody();
  String noSuchAccountTitle();

  String noSuchGroupTitle();

  String inactiveAccountBody();

  String menuAll();
  String menuAllOpen();
  String menuAllMerged();
  String menuAllAbandoned();

  String menuMine();
  String menuMyChanges();
  String menuMyDrafts();
  String menuMyWatchedChanges();
  String menuMyStarredChanges();
  String menuMyDraftComments();

  String menuDiff();
  String menuDiffCommit();
  String menuDiffPreferences();
  String menuDiffPatchSets();
  String menuDiffFiles();

  String menuProjects();
  String menuProjectsList();
  String menuProjectsInfo();
  String menuProjectsBranches();
  String menuProjectsAccess();
  String menuProjectsDashboards();
  String menuProjectsCreate();

  String menuPeople();
  String menuPeopleGroupsList();
  String menuPeopleGroupsCreate();

  String menuPlugins();
  String menuPluginsInstalled();

  String menuDocumentation();
  String menuDocumentationIndex();
  String menuDocumentationSearch();
  String menuDocumentationUpload();
  String menuDocumentationAccess();

  String searchHint();
  String searchButton();

  String rpcStatusWorking();

  String sectionNavigation();
  String sectionActions();
  String keySearch();
  String keyHelp();

  String sectionJumping();
  String jumpAllOpen();
  String jumpAllMerged();
  String jumpAllAbandoned();
  String jumpMine();
  String jumpMineDrafts();
  String jumpMineWatched();
  String jumpMineStarred();
  String jumpMineDraftComments();

  String projectAccessError();
  String projectAccessProposeForReviewHint();

  String userCannotVoteToolTip();
}

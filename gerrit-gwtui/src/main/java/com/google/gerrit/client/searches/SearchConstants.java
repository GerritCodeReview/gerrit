// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.searches;

import com.google.gwt.i18n.client.Constants;

public interface SearchConstants extends Constants {
  String defaultSearchName();

  String buttonDeleteSearches();
  String buttonCancelSearch();
  String buttonInsertNewSearch();
  String buttonInsertSearch();
  String buttonSaveSearch();

  String columnSearchName();
  String columnSearchDescription();
  String columnSearchQuery();

  String columnSearchEditName();
  String columnSearchEditDescription();
  String columnSearchEditQuery();
  String columnSearchEditType();
  String columnSearchEditGroup();
  String columnSearchEditProject();

  String ownerTypeUser();
  String ownerTypeGroup();
  String ownerTypeProject();
  String ownerTypeSite();

  String searchListPrev();
  String searchListNext();
  String searchListOpen();

  String titleSearchUser();
  String titleSearchGroup();
  String titleSearchProject();
  String titleSearchSite();

  String searchTableNone();
}

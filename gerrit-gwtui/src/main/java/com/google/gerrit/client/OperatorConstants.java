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

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.Constants;

import java.util.Map;

public interface OperatorConstants extends Constants{
  OperatorConstants O = GWT.create(OperatorConstants.class);

  String format();
  String constraints();
  String searchOperators();
  String close();

  String ageTab();
  String timeTab();
  String changeTab();
  String idTab();
  String userTab();
  String projectTab();
  String hasTab();
  String isTab();
  String statusTab();
  String relationTab();
  String magicalTab();
  String othersTab();
  String elementsTab();
  String booleansTab();

  Map <String, String> ages();
  Map <String, String> befores();
  Map <String, String> untils();
  Map <String, String> afters();
  Map <String, String> sinces();
  Map <String, String> changes();
  Map <String, String> conflictss();
  Map <String, String> owners();
  Map <String, String> ownerins();
  Map <String, String> reviewers();
  Map <String, String> reviewerins();
  Map <String, String> commits();
  Map <String, String> projects();
  Map <String, String> projectss();
  Map <String, String> parentprojects();
  Map <String, String> branchs();
  Map <String, String> topics();
  Map <String, String> refs();
  Map <String, String> trs();
  Map <String, String> bugs();
  Map <String, String> labels();
  Map <String, String> messages();
  Map <String, String> comments();
  Map <String, String> paths();
  Map <String, String> files();
  Map <String, String> drafts();
  Map <String, String> stars();

  Map <String, String> isstarreds();
  Map <String, String> iswatcheds();
  Map <String, String> isrevieweds();
  Map <String, String> isowners();
  Map <String, String> isreviewers();
  Map <String, String> isopens();
  Map <String, String> ispendings();
  Map <String, String> isdrafts();
  Map <String, String> iscloseds();
  Map <String, String> issubmitteds();
  Map <String, String> ismergeds();
  Map <String, String> isabandoneds();
  Map <String, String> ismergeables();
  Map <String, String> statusopens();
  Map <String, String> statuspendings();
  Map <String, String> statusrevieweds();
  Map <String, String> statussubmitteds();
  Map <String, String> statuscloseds();
  Map <String, String> statusmergeds();
  Map <String, String> statusabandoneds();
  Map <String, String> addeds();
  Map <String, String> deleteds();
  Map <String, String> deltas();
  Map <String, String> combys();
  Map <String, String> froms();
  Map <String, String> reviewedbys();
  Map <String, String> authors();
  Map <String, String> committers();

  Map <String, String> negations();
  Map <String, String> ands();
  Map <String, String> ors();
  Map <String, String> visibletos();
  Map <String, String> isvisibles();
  Map <String, String> starredbys();
  Map <String, String> watchedbys();
  Map <String, String> draftbys();
  Map <String, String> limits();
}

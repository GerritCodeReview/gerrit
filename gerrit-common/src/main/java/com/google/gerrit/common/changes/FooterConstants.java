// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.common.changes;

import org.eclipse.jgit.revwalk.FooterKey;

public class FooterConstants {
  //public static final String CHANGE_ID_FOOTER = "ChangeId";

  /** What we're looking for, when parsing the dependencies. */
  //public static final String DEPENDS_ON_FOOTER = "Depends-On";

  public static final FooterKey CHANGE_ID_FOOTER = new FooterKey("Change-Id");
  public static final FooterKey DEPENDS_ON_FOOTER = new FooterKey("Depends-On");
}
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.reviewdb.Project;

/** Summary information about an {@link Owner}. */
public class OwnerInfo extends Owner {
  private String forDisplay = "";
  private String forLink = "";

  protected OwnerInfo(Owner.Type type, String owner) {
    super(type, owner);
  }

  public OwnerInfo(Owner.Type type) {
    this(type, "");
  }

  public OwnerInfo(final Account o) {
    this(Owner.Type.USER, o.getId().toString());
    try {
      forLink = notNull(o.getUserName());
      if ("".equals(forLink)) {
        forLink = notNull(o.getId().toString());
      }
    } catch (Exception e) {}

    try {
      forDisplay = notNull(o.getFullName());
      if ("".equals(forDisplay)) {
        forDisplay = forLink;
      }
    } catch (Exception e) {}
  }

  public OwnerInfo(final AccountGroup o) {
    this(Owner.Type.GROUP, o.getId().toString());
    try {
      forLink = notNull(o.getName());
      if ("".equals(forLink)) {
        forLink = notNull(o.getId().toString());
      }
      if (forLink.matches(".* .*")) {
        forDisplay = forLink;
        forLink = notNull(o.getId().toString());
        return;
      }

    } catch (Exception e) {}

    forDisplay = forLink;
  }

  public OwnerInfo(final Project o) {
    this(Owner.Type.PROJECT, o.getName());
    forLink = notNull(o.getName());
    forDisplay = forLink;
  }

  public OwnerInfo() {
    this(Owner.Type.SITE, "");
  }

  public String getOwnerForDisplay() {
    return forDisplay;
  }

  public String getOwnerForLink() {
    return forLink;
  }

  public static String notNull(String s) {
    return s == null ? "" : s;
  }
}

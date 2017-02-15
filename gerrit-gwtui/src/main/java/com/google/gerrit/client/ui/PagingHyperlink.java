// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.AdminConstants;

public class PagingHyperlink extends Hyperlink {

  public static PagingHyperlink createPrev() {
    return new PagingHyperlink(AdminConstants.I.pagedListPrev());
  }

  public static PagingHyperlink createNext() {
    return new PagingHyperlink(AdminConstants.I.pagedListNext());
  }

  private PagingHyperlink(String text) {
    super(text, true, "");
    setStyleName(Gerrit.RESOURCES.css().pagingLink());
  }
}

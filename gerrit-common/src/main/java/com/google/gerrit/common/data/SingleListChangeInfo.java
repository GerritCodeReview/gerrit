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

package com.google.gerrit.common.data;

import java.util.List;

/** Summary information needed for screens showing a single list of changes}. */
public class SingleListChangeInfo {
  protected AccountInfoCache accounts;
  protected List<ChangeInfo> changes;
  protected boolean atEnd;

  public SingleListChangeInfo() {
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public void setAccounts(final AccountInfoCache ac) {
    accounts = ac;
  }

  public List<ChangeInfo> getChanges() {
    return changes;
  }

  public boolean isAtEnd() {
    return atEnd;
  }

  public void setChanges(List<ChangeInfo> c) {
    setChanges(c, true);
  }

  public void setChanges(List<ChangeInfo> c, boolean end) {
    changes = c;
    atEnd = end;
  }
}

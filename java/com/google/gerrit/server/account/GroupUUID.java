// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.reviewdb.client.AccountGroup;
import java.security.MessageDigest;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

public class GroupUUID {
  public static AccountGroup.UUID make(String groupName, PersonIdent creator) {
    MessageDigest md = Constants.newMessageDigest();
    md.update(Constants.encode("group " + groupName + "\n"));
    md.update(Constants.encode("creator " + creator.toExternalString() + "\n"));
    return new AccountGroup.UUID(ObjectId.fromRaw(md.digest()).name());
  }

  private GroupUUID() {}
}

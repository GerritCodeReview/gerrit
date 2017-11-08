// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Account;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;

public class NoteDbUtil {
  public static Optional<Account.Id> parseIdent(PersonIdent ident, String serverId) {
    String email = ident.getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      String host = email.substring(at + 1, email.length());
      if (host.equals(serverId)) {
        Integer id = Ints.tryParse(email.substring(0, at));
        if (id != null) {
          return Optional.of(new Account.Id(id));
        }
      }
    }
    return Optional.empty();
  }

  private NoteDbUtil() {}
}

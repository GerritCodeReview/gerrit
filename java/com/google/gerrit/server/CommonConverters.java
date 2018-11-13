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

package com.google.gerrit.server;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Set;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Converters to classes in {@code com.google.gerrit.extensions.common}.
 *
 * <p>The server frequently needs to convert internal types to types exposed in the extension API,
 * but the converters themselves are not part of this API. This class contains such converters as
 * static utility methods.
 */
public class CommonConverters {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static GitPerson toGitPerson(PersonIdent ident, @Nullable ExternalIds externalIds) {
    GitPerson result = new GitPerson();
    result.name = ident.getName();
    result.email = ident.getEmailAddress();
    result.date = new Timestamp(ident.getWhen().getTime());
    result.tz = ident.getTimeZoneOffset();

    if (externalIds == null) {
      return result;
    }

    try {
      Set<ExternalId> externalIdsByEmail = externalIds.byEmail(ident.getEmailAddress());
      if (externalIdsByEmail.size() == 1) {
        result._accountId = Iterables.getOnlyElement(externalIdsByEmail).accountId().get();
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Can't retrieve account by email");
    }
    return result;
  }

  private CommonConverters() {}
}

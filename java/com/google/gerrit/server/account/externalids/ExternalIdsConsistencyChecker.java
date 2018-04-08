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

package com.google.gerrit.server.account.externalids;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.HashedPassword;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class ExternalIdsConsistencyChecker {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final AccountCache accountCache;
  private final OutgoingEmailValidator validator;

  @Inject
  ExternalIdsConsistencyChecker(
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      AccountCache accountCache,
      OutgoingEmailValidator validator) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.accountCache = accountCache;
    this.validator = validator;
  }

  public List<ConsistencyProblemInfo> check() throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return check(ExternalIdNotes.loadReadOnly(repo));
    }
  }

  public List<ConsistencyProblemInfo> check(ObjectId rev)
      throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return check(ExternalIdNotes.loadReadOnly(repo, rev));
    }
  }

  private List<ConsistencyProblemInfo> check(ExternalIdNotes extIdNotes) throws IOException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    ListMultimap<String, ExternalId.Key> emails =
        MultimapBuilder.hashKeys().arrayListValues().build();

    try (RevWalk rw = new RevWalk(extIdNotes.getRepository())) {
      NoteMap noteMap = extIdNotes.getNoteMap();
      for (Note note : noteMap) {
        byte[] raw = ExternalIdNotes.readNoteData(rw, note.getData());
        try {
          ExternalId extId = ExternalId.parse(note.getName(), raw, note.getData());
          problems.addAll(validateExternalId(extId));

          if (extId.email() != null) {
            emails.put(extId.email(), extId.key());
          }
        } catch (ConfigInvalidException e) {
          addError(String.format(e.getMessage()), problems);
        }
      }
    }

    emails
        .asMap()
        .entrySet()
        .stream()
        .filter(e -> e.getValue().size() > 1)
        .forEach(
            e ->
                addError(
                    String.format(
                        "Email '%s' is not unique, it's used by the following external IDs: %s",
                        e.getKey(),
                        e.getValue()
                            .stream()
                            .map(k -> "'" + k.get() + "'")
                            .sorted()
                            .collect(joining(", "))),
                    problems));

    return problems;
  }

  private List<ConsistencyProblemInfo> validateExternalId(ExternalId extId) {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    if (!accountCache.get(extId.accountId()).isPresent()) {
      addError(
          String.format(
              "External ID '%s' belongs to account that doesn't exist: %s",
              extId.key().get(), extId.accountId().get()),
          problems);
    }

    if (extId.email() != null && !validator.isValid(extId.email())) {
      addError(
          String.format(
              "External ID '%s' has an invalid email: %s", extId.key().get(), extId.email()),
          problems);
    }

    if (extId.password() != null && extId.isScheme(SCHEME_USERNAME)) {
      try {
        HashedPassword.decode(extId.password());
      } catch (DecoderException e) {
        addError(
            String.format(
                "External ID '%s' has an invalid password: %s", extId.key().get(), e.getMessage()),
            problems);
      }
    }

    return problems;
  }

  private static void addError(String error, List<ConsistencyProblemInfo> problems) {
    problems.add(new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.ERROR, error));
  }
}

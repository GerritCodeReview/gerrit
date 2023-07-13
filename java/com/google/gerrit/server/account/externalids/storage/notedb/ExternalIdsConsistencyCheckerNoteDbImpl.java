// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.HashedPassword;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdsConsistencyChecker;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class ExternalIdsConsistencyCheckerNoteDbImpl implements ExternalIdsConsistencyChecker {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final OutgoingEmailValidator validator;
  private final ExternalIdFactory externalIdFactory;

  @Inject
  ExternalIdsConsistencyCheckerNoteDbImpl(
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      OutgoingEmailValidator validator,
      ExternalIdFactory externalIdFactory) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.validator = validator;
    this.externalIdFactory = externalIdFactory;
  }

  @Override
  public List<ConsistencyProblemInfo> check(AccountCache accountCache)
      throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return check(
          accountCache,
          ExternalIdNotes.loadReadOnly(allUsers, repo, null, externalIdFactory, false));
    }
  }

  @Override
  public List<ConsistencyProblemInfo> check(AccountCache accountCache, ObjectId rev)
      throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return check(
          accountCache,
          ExternalIdNotes.loadReadOnly(allUsers, repo, rev, externalIdFactory, false));
    }
  }

  private List<ConsistencyProblemInfo> check(AccountCache accountCache, ExternalIdNotes extIdNotes)
      throws IOException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    ListMultimap<String, ExternalId> emails = MultimapBuilder.hashKeys().arrayListValues().build();

    try (RevWalk rw = new RevWalk(extIdNotes.getRepository())) {
      NoteMap noteMap = extIdNotes.getNoteMap();
      for (Note note : noteMap) {
        byte[] raw = ExternalIdNotes.readNoteData(rw, note.getData());
        try {
          ExternalId extId = externalIdFactory.parse(note.getName(), raw, note.getData());
          problems.addAll(validateExternalId(accountCache, extId));

          if (extId.email() != null) {
            String email = extId.email();
            if (emails.get(email).stream()
                .noneMatch(e -> e.accountId().get() == extId.accountId().get())) {
              emails.put(email, extId);
            }
          }
        } catch (ConfigInvalidException e) {
          addError(String.format(e.getMessage()), problems);
        }
      }
    }

    emails.asMap().entrySet().stream()
        .filter(e -> e.getValue().size() > 1)
        .forEach(
            e ->
                addError(
                    String.format(
                        "Email '%s' is not unique, it's used by the following external IDs: %s",
                        e.getKey(),
                        e.getValue().stream()
                            .map(k -> "'" + k.key().get() + "'")
                            .sorted()
                            .collect(joining(", "))),
                    problems));

    return problems;
  }

  private List<ConsistencyProblemInfo> validateExternalId(
      AccountCache accountCache, ExternalId extId) {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    if (accountCache.get(extId.accountId()).isEmpty()) {
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
      } catch (HashedPassword.DecoderException e) {
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

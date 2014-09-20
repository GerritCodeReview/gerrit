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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.validators.HashtagValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Singleton
public class HashtagsUtil {
  private static final Logger log = LoggerFactory
      .getLogger(HashtagsUtil.class);

  public class ParsedResult {
    public boolean update;
    public Set<String> parsedHashtags;
    public Set<String> toAdd;
    public Set<String> toRemove;
  }

  static Set<String> detectCommitMessageHashtags(RevCommit commit) {
    List<String> hashtagsLines = commit.getFooterLines(FOOTER_HASHTAGS);
    if (hashtagsLines.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> result = Sets.newHashSet();
    for (String hashtagsLine : hashtagsLines) {
      result.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings()
          .trimResults().split(hashtagsLine)));
    }
    return result;
  }

  private final ChangeUpdate.Factory updateFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeIndexer indexer;
  private final ChangeHooks hooks;
  private final DynamicSet<HashtagValidationListener> hashtagValidationListeners;

  @Inject
  HashtagsUtil(ChangeUpdate.Factory updateFactory,
      Provider<ReviewDb> dbProvider, ChangeIndexer indexer,
      ChangeHooks hooks,
      DynamicSet<HashtagValidationListener> hashtagValidationListeners) {
    this.updateFactory = updateFactory;
    this.dbProvider = dbProvider;
    this.indexer = indexer;
    this.hooks = hooks;
    this.hashtagValidationListeners = hashtagValidationListeners;
  }

  public ParsedResult parseCommitMessageHashtags(RevCommit commit,
      ChangeControl ctl) {
    ParsedResult r = new ParsedResult();
    r.parsedHashtags = detectCommitMessageHashtags(commit);
    try {
      if (!r.parsedHashtags.isEmpty()) {
        Set<String> existingHashtags = ctl.getNotes().load().getHashtags();
        if (existingHashtags.isEmpty()) {
          r.toAdd = r.parsedHashtags;
          r.toRemove = Collections.emptySet();
        } else {
          r.toAdd = Sets.difference(r.parsedHashtags, existingHashtags);
          r.toRemove = Sets.difference(existingHashtags, r.parsedHashtags);
        }

        if (r.toAdd.size() > 0 || r.toRemove.size() > 0) {
          for (HashtagValidationListener validator : hashtagValidationListeners) {
            validator.validateHashtags(ctl.getChange(), r.toAdd, r.toRemove);
          }
          r.update = true;
          return r;
        }
      }
    } catch (OrmException err) {
      log.error("Can not get existing hashtags", err);
    } catch (ValidationException e) {
      log.error("Invalid hashtags ", e);
    }
    return r;
  }

  private Set<String> extractTags(Set<String> input)
      throws IllegalArgumentException {
    if (input == null) {
      return Collections.emptySet();
    } else {
      HashSet<String> result = new HashSet<>();
      for (String hashtag : input) {
        if (hashtag.contains(",")) {
          throw new IllegalArgumentException("Hashtags may not contain commas");
        }
        if (!hashtag.trim().isEmpty()) {
          result.add(hashtag.trim());
        }
      }
      return result;
    }
  }

  public TreeSet<String> setHashtags(ChangeControl control,
      HashtagsInput input, boolean runHooks, boolean index)
          throws IllegalArgumentException, IOException,
          ValidationException, AuthException, OrmException {
    if (input == null
        || (input.add == null && input.remove == null)) {
      throw new IllegalArgumentException("Hashtags are required");
    }

    if (!control.canEditHashtags()) {
      throw new AuthException("Editing hashtags not permitted");
    }
    ChangeUpdate update = updateFactory.create(control);
    ChangeNotes notes = control.getNotes().load();

    Set<String> existingHashtags = notes.getHashtags();
    Set<String> updatedHashtags = new HashSet<>();
    Set<String> toAdd = new HashSet<>(extractTags(input.add));
    Set<String> toRemove = new HashSet<>(extractTags(input.remove));

    for (HashtagValidationListener validator : hashtagValidationListeners) {
      validator.validateHashtags(update.getChange(), toAdd, toRemove);
    }

    if (existingHashtags != null && !existingHashtags.isEmpty()) {
      updatedHashtags.addAll(existingHashtags);
      toAdd.removeAll(existingHashtags);
      toRemove.retainAll(existingHashtags);
    }

    if (toAdd.size() > 0 || toRemove.size() > 0) {
      updatedHashtags.addAll(toAdd);
      updatedHashtags.removeAll(toRemove);
      update.setHashtags(updatedHashtags);
      update.commit();

      if (index) {
        indexer.index(dbProvider.get(), update.getChange());
      }

      if (runHooks) {
        IdentifiedUser currentUser = ((IdentifiedUser) control.getCurrentUser());
        hooks.doHashtagsChangedHook(
            update.getChange(), currentUser.getAccount(),
            toAdd, toRemove, updatedHashtags,
            dbProvider.get());
      }
    }
    return new TreeSet<String>(updatedHashtags);
  }
}

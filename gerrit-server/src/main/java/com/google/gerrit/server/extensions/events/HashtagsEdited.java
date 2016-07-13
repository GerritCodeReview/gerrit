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

package com.google.gerrit.server.extensions.events;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.HashtagsEditedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Set;

public class HashtagsEdited {
  private static final Logger log =
      LoggerFactory.getLogger(HashtagsEdited.class);

  private final DynamicSet<HashtagsEditedListener> listeners;
  private final EventUtil util;

  @Inject
  public HashtagsEdited(DynamicSet<HashtagsEditedListener> listeners,
      EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, AccountInfo editor,
      Collection<String> hashtags, Collection<String> added,
      Collection<String> removed, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    Event event = new Event(change, editor, hashtags, added, removed, when);
    for (HashtagsEditedListener l : listeners) {
      try {
        l.onHashtagsEdited(event);
      } catch (Exception e) {
        log.warn("Error in event listener", e);
      }
    }
  }

  public void fire(Change change, Id accountId,
      ImmutableSortedSet<String> hashtags, Set<String> added,
      Set<String> removed, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      fire(util.changeInfo(change),
          util.accountInfo(accountId),
          hashtags, added, removed,
          when);
    } catch (OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractChangeEvent
      implements HashtagsEditedListener.Event {

    private AccountInfo editor;
    private Collection<String> updatedHashtags;
    private Collection<String> addedHashtags;
    private Collection<String> removedHashtags;

    Event(ChangeInfo change, AccountInfo editor, Collection<String> updated,
        Collection<String> added, Collection<String> removed, Timestamp when) {
      super(change, editor, when, NotifyHandling.ALL);
      this.editor = editor;
      this.updatedHashtags = updated;
      this.addedHashtags = added;
      this.removedHashtags = removed;
    }

    @Override
    public AccountInfo getEditor() {
      return editor;
    }

    @Override
    public Collection<String> getHashtags() {
      return updatedHashtags;
    }

    @Override
    public Collection<String> getAddedHashtags() {
      return addedHashtags;
    }

    @Override
    public Collection<String> getRemovedHashtags() {
      return removedHashtags;
    }
  }
}

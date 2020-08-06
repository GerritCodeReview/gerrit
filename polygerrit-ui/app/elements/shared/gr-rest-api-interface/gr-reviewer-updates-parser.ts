/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {parseDate} from '../../../utils/date-util.js';
import {MessageTag} from '../../../constants/constants.js';

const MESSAGE_REVIEWERS_THRESHOLD_MILLIS = 500;
const REVIEWER_UPDATE_THRESHOLD_MILLIS = 6000;

export class GrReviewerUpdatesParser {
  constructor(change) {
    this.result = {...change};
    this._lastState = {};
    this._batch = null;
    this._updateItems = null;
  }

  /**
   * Removes messages that describe removed reviewers, since reviewer_updates
   * are used.
   */
  _filterRemovedMessages() {
    this.result.messages = this.result.messages.filter(
        message => message.tag !== MessageTag.TAG_DELETE_REVIEWER
    );
  }

  /**
   * Is a part of _groupUpdates(). Creates a new batch of updates.
   *
   * @param {Object} update instance of ReviewerUpdateInfo
   */
  _startBatch(update) {
    this._updateItems = [];
    return {
      author: update.updated_by,
      date: update.updated,
      type: 'REVIEWER_UPDATE',
      tag: MessageTag.TAG_REVIEWER_UPDATE,
    };
  }

  /**
   * Is a part of _groupUpdates(). Validates current batch:
   * - filters out updates that don't change reviewer state.
   * - updates current reviewer state.
   *
   * @param {Object} update instance of ReviewerUpdateInfo
   */
  _completeBatch(update) {
    const items = [];
    for (const accountId in this._updateItems) {
      if (!this._updateItems.hasOwnProperty(accountId)) continue;
      const updateItem = this._updateItems[accountId];
      if (this._lastState[accountId] !== updateItem.state) {
        this._lastState[accountId] = updateItem.state;
        items.push(updateItem);
      }
    }
    if (items.length) {
      this._batch.updates = items;
    }
  }

  /**
   * Groups reviewer updates. Sequential updates are grouped if:
   * - They were performed within short timeframe (6 seconds)
   * - Made by the same person
   * - Non-change updates are discarded within a group
   * - Groups with no-change updates are discarded (eg CC -> CC)
   */
  _groupUpdates() {
    const updates = this.result.reviewer_updates;
    const newUpdates = updates.reduce((newUpdates, update) => {
      if (!this._batch) {
        this._batch = this._startBatch(update);
      }
      const updateDate = parseDate(update.updated).getTime();
      const batchUpdateDate = parseDate(this._batch.date).getTime();
      const reviewerId = update.reviewer._account_id.toString();
      if (
        updateDate - batchUpdateDate >
          REVIEWER_UPDATE_THRESHOLD_MILLIS ||
        update.updated_by._account_id !== this._batch.author._account_id
      ) {
        // Next sequential update should form new group.
        this._completeBatch();
        if (this._batch.updates && this._batch.updates.length) {
          newUpdates.push(this._batch);
        }
        this._batch = this._startBatch(update);
      }
      this._updateItems[reviewerId] = {
        reviewer: update.reviewer,
        state: update.state,
      };
      if (this._lastState[reviewerId]) {
        this._updateItems[reviewerId].prev_state = this._lastState[reviewerId];
      }
      return newUpdates;
    }, []);
    this._completeBatch();
    if (this._batch.updates && this._batch.updates.length) {
      newUpdates.push(this._batch);
    }
    this.result.reviewer_updates = newUpdates;
  }

  /**
   * Generates update message for reviewer state change.
   *
   * @param {string} prev previous reviewer state.
   * @param {string} state current reviewer state.
   * @return {string}
   */
  _getUpdateMessage(prev, state) {
    if (prev === 'REMOVED' || !prev) {
      return 'Added to ' + state.toLowerCase() + ': ';
    } else if (state === 'REMOVED') {
      if (prev) {
        return 'Removed from ' + prev.toLowerCase() + ': ';
      } else {
        return 'Removed : ';
      }
    } else {
      return (
        'Moved from ' + prev.toLowerCase() + ' to ' + state.toLowerCase() + ': '
      );
    }
  }

  /**
   * Groups updates for same category (eg CC->CC) into a hash arrays of
   * reviewers.
   *
   * @param {!Array<!Object>} updates Array of ReviewerUpdateItemInfo.
   * @return {!Object} Hash of arrays of AccountInfo, message as key.
   */
  _groupUpdatesByMessage(updates) {
    return updates.reduce((result, item) => {
      const message = this._getUpdateMessage(item.prev_state, item.state);
      if (!result[message]) {
        result[message] = [];
      }
      result[message].push(item.reviewer);
      return result;
    }, {});
  }

  /**
   * Generates text messages for grouped reviewer updates.
   * Formats reviewer updates to a (not yet implemented) EventInfo instance.
   *
   * @see https://gerrit-review.googlesource.com/c/94490/
   */
  _formatUpdates() {
    for (const update of this.result.reviewer_updates) {
      const grouppedReviewers = this._groupUpdatesByMessage(update.updates);
      const newUpdates = [];
      for (const message in grouppedReviewers) {
        if (grouppedReviewers.hasOwnProperty(message)) {
          newUpdates.push({
            message,
            reviewers: grouppedReviewers[message],
          });
        }
      }
      update.updates = newUpdates;
    }
  }

  /**
   * Moves reviewer updates that are within short time frame of change messages
   * back in time so they would come before change messages.
   * TODO(viktard): Remove when server-side serves reviewer updates like so.
   */
  _advanceUpdates() {
    const updates = this.result.reviewer_updates;
    const messages = this.result.messages;
    messages.forEach((message, index) => {
      const messageDate = parseDate(message.date).getTime();
      const nextMessageDate =
        index === messages.length - 1
          ? null
          : parseDate(messages[index + 1].date).getTime();
      for (const update of updates) {
        const date = parseDate(update.date).getTime();
        if (
          date >= messageDate &&
          (!nextMessageDate || date < nextMessageDate)
        ) {
          const timestamp =
            parseDate(update.date).getTime() -
            MESSAGE_REVIEWERS_THRESHOLD_MILLIS;
          update.date = new Date(timestamp)
              .toISOString()
              .replace('T', ' ')
              .replace('Z', '000000');
        }
        if (nextMessageDate && date > nextMessageDate) {
          break;
        }
      }
    });
  }

  static parse(change) {
    if (
      !change ||
    !change.messages ||
    !change.reviewer_updates ||
    !change.reviewer_updates.length
    ) {
      return change;
    }
    const parser = new GrReviewerUpdatesParser(change);
    parser._filterRemovedMessages();
    parser._groupUpdates();
    parser._formatUpdates();
    parser._advanceUpdates();
    return parser.result;
  }
}
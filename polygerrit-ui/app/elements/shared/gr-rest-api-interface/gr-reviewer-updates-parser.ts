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

import {parseDate} from '../../../utils/date-util';
import {MessageTag, ReviewerState} from '../../../constants/constants';
import {
  AccountInfo,
  ChangeInfo,
  ChangeMessageInfo,
  ReviewerUpdateInfo,
  Timestamp,
} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';

const MESSAGE_REVIEWERS_THRESHOLD_MILLIS = 500;
const REVIEWER_UPDATE_THRESHOLD_MILLIS = 6000;

interface ChangeInfoParserInput extends ChangeInfo {
  messages: ChangeMessageInfo[];
  reviewer_updates: ReviewerUpdateInfo[]; // Always has at least 1 item
}

function isChangeInfoParserInput(
  change: ChangeInfo
): change is ChangeInfoParserInput {
  return !!(
    change.messages &&
    change.reviewer_updates &&
    change.reviewer_updates.length
  );
}

interface ParserBatch {
  author: AccountInfo;
  date: Timestamp;
  type: 'REVIEWER_UPDATE';
  tag: MessageTag.TAG_REVIEWER_UPDATE;
  updates?: UpdateItem[];
}

interface ParserBatchWithNonEmptyUpdates extends ParserBatch {
  updates: UpdateItem[]; // Always has at least 1 items
}

export interface FormattedReviewerUpdateInfo {
  author: AccountInfo;
  date: Timestamp;
  type: 'REVIEWER_UPDATE';
  tag: MessageTag.TAG_REVIEWER_UPDATE;
  updates: {message: string; reviewers: AccountInfo[]}[];
}

function isParserBatchWithNonEmptyUpdates(
  x: ParserBatch
): x is ParserBatchWithNonEmptyUpdates {
  return !!(x.updates && x.updates.length);
}

interface UpdateItem {
  reviewer: AccountInfo;
  state: ReviewerState;
  prev_state?: ReviewerState;
}

export interface ParsedChangeInfo extends Omit<ChangeInfo, 'reviewer_updates'> {
  reviewer_updates?: ReviewerUpdateInfo[] | FormattedReviewerUpdateInfo[];
}

type ReviewersGroupByMessage = {[message: string]: AccountInfo[]};

export class GrReviewerUpdatesParser {
  // TODO(TS): The parser several times reassigns different types to
  // reviewer_updates. After parse complete, the result has ParsedChangeInfo
  // type. This class should be refactored to avoid reassignment.
  private readonly result: ChangeInfoParserInput;

  private _batch: ParserBatch | null = null;

  private _updateItems: {[accountId: string]: UpdateItem} | null = null;

  private readonly _lastState: {[accountId: string]: ReviewerState} = {};

  constructor(change: ChangeInfoParserInput) {
    this.result = {...change};
  }

  /**
   * Removes messages that describe removed reviewers, since reviewer_updates
   * are used.
   */
  private _filterRemovedMessages() {
    this.result.messages = this.result.messages.filter(
      message => message.tag !== MessageTag.TAG_DELETE_REVIEWER
    );
  }

  /**
   * Is a part of _groupUpdates(). Creates a new batch of updates.
   */
  private _startBatch(update: ReviewerUpdateInfo): ParserBatch {
    this._updateItems = {};
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
   */
  private _completeBatch(batch: ParserBatch) {
    const items = [];
    for (const accountId in this._updateItems) {
      if (!hasOwnProperty(this._updateItems, accountId)) continue;
      const updateItem = this._updateItems[accountId];
      if (this._lastState[accountId] !== updateItem.state) {
        this._lastState[accountId] = updateItem.state;
        items.push(updateItem);
      }
    }
    if (items.length) {
      batch.updates = items;
    }
  }

  /**
   * Groups reviewer updates. Sequential updates are grouped if:
   * - They were performed within short timeframe (6 seconds)
   * - Made by the same person
   * - Non-change updates are discarded within a group
   * - Groups with no-change updates are discarded (eg CC -> CC)
   */
  _groupUpdates(): ParserBatchWithNonEmptyUpdates[] {
    const updates = this.result.reviewer_updates;
    const newUpdates = updates.reduce((newUpdates, update) => {
      if (!this._batch) {
        this._batch = this._startBatch(update);
      }
      const updateDate = parseDate(update.updated).getTime();
      const batchUpdateDate = parseDate(this._batch.date).getTime();
      const reviewerId = update.reviewer._account_id.toString();
      if (
        updateDate - batchUpdateDate > REVIEWER_UPDATE_THRESHOLD_MILLIS ||
        update.updated_by._account_id !== this._batch.author._account_id
      ) {
        // Next sequential update should form new group.
        this._completeBatch(this._batch);
        if (isParserBatchWithNonEmptyUpdates(this._batch)) {
          newUpdates.push(this._batch);
        }
        this._batch = this._startBatch(update);
      }
      // _startBatch assigns _updateItems. When _groupUpdates is calling,
      // _batch and _updateItems are not set => _startBatch is called. The
      // _startBatch method assigns _updateItems
      const updateItems = this._updateItems!;
      updateItems[reviewerId] = {
        reviewer: update.reviewer,
        state: update.state,
      };
      if (this._lastState[reviewerId]) {
        updateItems[reviewerId].prev_state = this._lastState[reviewerId];
      }
      return newUpdates;
    }, [] as ParserBatchWithNonEmptyUpdates[]);
    // reviewer_updates always has at least 1 item
    // (otherwise parse is not created) => updates.reduce calls callback
    // at least once and callback assigns this._batch
    const batch = this._batch!;
    this._completeBatch(batch);
    if (isParserBatchWithNonEmptyUpdates(batch)) {
      newUpdates.push(batch);
    }
    ((this.result
      .reviewer_updates as unknown) as ParserBatchWithNonEmptyUpdates[]) = newUpdates;
    return newUpdates;
  }

  /**
   * Generates update message for reviewer state change.
   */
  private _getUpdateMessage(
    prevReviewerState: string | undefined,
    currentReviewerState: string
  ): string {
    if (prevReviewerState === 'REMOVED' || !prevReviewerState) {
      return `Added to ${currentReviewerState.toLowerCase()}: `;
    } else if (currentReviewerState === 'REMOVED') {
      if (prevReviewerState) {
        return `Removed from ${prevReviewerState.toLowerCase()}: `;
      } else {
        return 'Removed : ';
      }
    } else {
      return `Moved from ${prevReviewerState.toLowerCase()} to ${currentReviewerState.toLowerCase()}: `;
    }
  }

  /**
   * Groups updates for same category (eg CC->CC) into a hash arrays of
   * reviewers.
   */
  _groupUpdatesByMessage(updates: UpdateItem[]): ReviewersGroupByMessage {
    return updates.reduce((result, item) => {
      const message = this._getUpdateMessage(item.prev_state, item.state);
      if (!result[message]) {
        result[message] = [];
      }
      result[message].push(item.reviewer);
      return result;
    }, {} as ReviewersGroupByMessage);
  }

  /**
   * Generates text messages for grouped reviewer updates.
   * Formats reviewer updates to a (not yet implemented) EventInfo instance.
   *
   * @see https://gerrit-review.googlesource.com/c/94490/
   */
  _formatUpdates() {
    const reviewerUpdates = (this.result
      .reviewer_updates as unknown) as ParserBatchWithNonEmptyUpdates[];
    for (const update of reviewerUpdates) {
      const grouppedReviewers = this._groupUpdatesByMessage(update.updates);
      const newUpdates: {message: string; reviewers: AccountInfo[]}[] = [];
      for (const message in grouppedReviewers) {
        if (hasOwnProperty(grouppedReviewers, message)) {
          newUpdates.push({
            message,
            reviewers: grouppedReviewers[message],
          });
        }
      }
      ((update as unknown) as FormattedReviewerUpdateInfo).updates = newUpdates;
    }
  }

  /**
   * Moves reviewer updates that are within short time frame of change messages
   * back in time so they would come before change messages.
   * TODO(viktard): Remove when server-side serves reviewer updates like so.
   */
  _advanceUpdates() {
    const updates = (this.result
      .reviewer_updates as unknown) as FormattedReviewerUpdateInfo[];
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
            .replace('Z', '000000') as Timestamp;
        }
        if (nextMessageDate && date > nextMessageDate) {
          break;
        }
      }
    });
  }

  static parse(change: ChangeInfo): ParsedChangeInfo {
    if (!change || !isChangeInfoParserInput(change)) {
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

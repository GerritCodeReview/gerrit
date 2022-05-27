/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {parseDate} from '../../../utils/date-util';
import {MessageTag, ReviewerState} from '../../../constants/constants';
import {
  AccountInfo,
  ChangeInfo,
  ChangeMessageInfo,
  ChangeViewChangeInfo,
  ReviewerUpdateInfo,
  Timestamp,
} from '../../../types/common';
import {accountKey} from '../../../utils/account-util';
import {
  FormattedReviewerUpdateInfo,
  ParsedChangeInfo,
} from '../../../types/types';

const MESSAGE_REVIEWERS_THRESHOLD_MILLIS = 500;
const REVIEWER_UPDATE_THRESHOLD_MILLIS = 6000;

interface ChangeInfoParserInput extends ChangeViewChangeInfo {
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

type ReviewersGroupByMessage = {[message: string]: AccountInfo[]};

export class GrReviewerUpdatesParser {
  // TODO(TS): The parser several times reassigns different types to
  // reviewer_updates. After parse complete, the result has ParsedChangeInfo
  // type. This class should be refactored to avoid reassignment.
  private readonly result: ChangeInfoParserInput;

  private batch: ParserBatch | null = null;

  private updateItems: {[accountId: string]: UpdateItem} | null = null;

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
    this.updateItems = {};
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
    for (const [accountId, item] of Object.entries(this.updateItems ?? {})) {
      if (this._lastState[accountId] !== item.state) {
        this._lastState[accountId] = item.state;
        items.push(item);
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
      if (!this.batch) {
        this.batch = this._startBatch(update);
      }
      const updateDate = parseDate(update.updated).getTime();
      const batchUpdateDate = parseDate(this.batch.date).getTime();
      const reviewerId = accountKey(update.reviewer);
      if (
        updateDate - batchUpdateDate > REVIEWER_UPDATE_THRESHOLD_MILLIS ||
        update.updated_by._account_id !== this.batch.author._account_id
      ) {
        // Next sequential update should form new group.
        this._completeBatch(this.batch);
        if (isParserBatchWithNonEmptyUpdates(this.batch)) {
          newUpdates.push(this.batch);
        }
        this.batch = this._startBatch(update);
      }
      // _startBatch assigns updateItems. When _groupUpdates is calling,
      // batch and updateItems are not set => _startBatch is called. The
      // _startBatch method assigns updateItems
      const updateItems = this.updateItems!;
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
    // at least once and callback assigns this.batch
    const batch = this.batch!;
    this._completeBatch(batch);
    if (isParserBatchWithNonEmptyUpdates(batch)) {
      newUpdates.push(batch);
    }
    (this.result
      .reviewer_updates as unknown as ParserBatchWithNonEmptyUpdates[]) = newUpdates;
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
    const reviewerUpdates = this.result
      .reviewer_updates as unknown as ParserBatchWithNonEmptyUpdates[];
    for (const update of reviewerUpdates) {
      const groupedReviewers = this._groupUpdatesByMessage(update.updates);
      const newUpdates: {message: string; reviewers: AccountInfo[]}[] = [];
      for (const [message, reviewers] of Object.entries(groupedReviewers)) {
        newUpdates.push({message, reviewers});
      }
      (update as unknown as FormattedReviewerUpdateInfo).updates = newUpdates;
    }
  }

  /**
   * Moves reviewer updates that are within short time frame of change messages
   * back in time so they would come before change messages.
   * TODO(viktard): Remove when server-side serves reviewer updates like so.
   */
  _advanceUpdates() {
    const updates = this.result
      .reviewer_updates as unknown as FormattedReviewerUpdateInfo[];
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

  static parse(
    change: ChangeViewChangeInfo | undefined
  ): ParsedChangeInfo | undefined {
    // TODO(TS): The !change condition should be removed when all files are converted to TS
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

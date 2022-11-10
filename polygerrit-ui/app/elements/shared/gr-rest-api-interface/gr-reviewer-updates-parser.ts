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

export interface ChangeInfoParserInput extends ChangeViewChangeInfo {
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

  // visible for testing
  readonly result: ChangeInfoParserInput;

  private batch: FormattedReviewerUpdateInfo | null = null;

  private updateItems: {[accountId: string]: UpdateItem} | null = null;

  private readonly lastState: {[accountId: string]: ReviewerState} = {};

  constructor(change: ChangeInfoParserInput) {
    this.result = {...change};
  }

  /**
   * Removes messages that describe removed reviewers, since reviewer_updates
   * are used.
   */
  // visible for testing
  filterRemovedMessages() {
    this.result.messages = this.result.messages.filter(
      message => message.tag !== MessageTag.TAG_DELETE_REVIEWER
    );
  }

  /**
   * Is a part of groupUpdates(). Creates a new batch of updates.
   */
  private startBatch(update: ReviewerUpdateInfo): FormattedReviewerUpdateInfo {
    this.updateItems = {};
    return {
      author: update.updated_by,
      date: update.updated,
      type: 'REVIEWER_UPDATE',
      tag: MessageTag.TAG_REVIEWER_UPDATE,
      updates: [],
    };
  }

  /**
   * Is a part of groupUpdates(). Validates current batch:
   * - filters out updates that don't change reviewer state.
   * - updates current reviewer state.
   */
  private completeBatch(batch: FormattedReviewerUpdateInfo) {
    const items = [];
    for (const [accountId, item] of Object.entries(this.updateItems ?? {})) {
      if (this.lastState[accountId] !== item.state) {
        this.lastState[accountId] = item.state;
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
  groupUpdates(): FormattedReviewerUpdateInfo[] {
    const updates = this.result.reviewer_updates;
    const newUpdates = updates.reduce((newUpdates, update) => {
      if (!this.batch) {
        this.batch = this.startBatch(update);
      }
      const updateDate = parseDate(update.updated).getTime();
      const batchUpdateDate = parseDate(this.batch.date).getTime();
      const reviewerId = accountKey(update.reviewer);
      if (
        updateDate - batchUpdateDate > REVIEWER_UPDATE_THRESHOLD_MILLIS ||
        update.updated_by._account_id !== this.batch.author._account_id
      ) {
        // Next sequential update should form new group.
        this.completeBatch(this.batch);
        if (isParserBatchWithNonEmptyUpdates(this.batch)) {
          newUpdates.push(this.batch);
        }
        this.batch = this.startBatch(update);
      }
      // startBatch() assigns updateItems. When groupUpdates() is calling,
      // batch and updateItems are not set => startBatch() is called. The
      // startBatch() method assigns updateItems
      const updateItems = this.updateItems!;
      updateItems[reviewerId] = {
        reviewer: update.reviewer,
        state: update.state,
      };
      if (this.lastState[reviewerId]) {
        updateItems[reviewerId].prev_state = this.lastState[reviewerId];
      }
      return newUpdates;
    }, [] as ParserBatchWithNonEmptyUpdates[]);
    // reviewer_updates always has at least 1 item
    // (otherwise parse is not created) => updates.reduce calls callback
    // at least once and callback assigns this.batch
    const batch = this.batch;
    this.completeBatch(batch);
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
  private getUpdateMessage(
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
  groupUpdatesByMessage(updates: UpdateItem[]): ReviewersGroupByMessage {
    return updates.reduce((result, item) => {
      const message = this.getUpdateMessage(item.prev_state, item.state);
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
  formatUpdates() {
    const reviewerUpdates = this.result
      .reviewer_updates as unknown as ParserBatchWithNonEmptyUpdates[];
    for (const update of reviewerUpdates) {
      const groupedReviewers = this.groupUpdatesByMessage(update.updates);
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
  advanceUpdates() {
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
    parser.filterRemovedMessages();
    parser.groupUpdates();
    parser.formatUpdates();
    parser.advanceUpdates();
    return parser.result;
  }
}

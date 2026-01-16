/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {
  getCodeReviewVotesFromMessage,
  getRevertCreatedChangeIds,
} from './message-util';
import {assert} from '@open-wc/testing';
import {MessageTag} from '../constants/constants';
import {
  AccountInfo,
  ChangeId,
  ChangeInfo,
  ChangeMessageInfo,
  LabelNameToInfoMap,
  PatchSetNum,
  ReviewInputTag,
} from '../api/rest-api';
import {
  createAccountWithId,
  createChange,
  createChangeMessage,
  createDetailedLabelInfo,
} from '../test/test-data-generators';

suite('message-util tests', () => {
  suite('getRevertCreatedChangeIds', () => {
    test('getRevertCreatedChangeIds', () => {
      const messages = [
        {
          ...createChangeMessage(),
          message:
            'Created a revert of this change as If02ca1cd494579d6bb92a157bf1819e3689cd6b1',
          tag: MessageTag.TAG_REVERT as ReviewInputTag,
        },
        {
          ...createChangeMessage(),
          message: 'Created a revert of this change as abc',
          tag: undefined,
        },
      ];

      assert.deepEqual(getRevertCreatedChangeIds(messages), [
        'If02ca1cd494579d6bb92a157bf1819e3689cd6b1' as ChangeId,
      ]);
    });

    test('getRevertCreatedChangeIds with extra spam', () => {
      const messages = [
        {
          ...createChangeMessage(),
          message:
            'Created a revert of this change as IIf02ca1cd494579d6bb92a157bf1819e3689cd6b1',
          tag: MessageTag.TAG_REVERT as ReviewInputTag,
        },
        {
          ...createChangeMessage(),
          message: 'Created a revert of this change as abc',
          tag: undefined,
        },
      ];

      assert.deepEqual(getRevertCreatedChangeIds(messages), [
        'If02ca1cd494579d6bb92a157bf1819e3689cd6b1' as ChangeId,
      ]);
    });
  });

  suite('getCodeReviewVotesFromMessage', () => {
    const account1: AccountInfo = {
      ...createAccountWithId(1),
    };
    const account2: AccountInfo = {
      ...createAccountWithId(2),
    };

    const labels: LabelNameToInfoMap = {
      'Code-Review': createDetailedLabelInfo(),
    };

    function createMessage(
      author: AccountInfo,
      message: string,
      ps: number
    ): ChangeMessageInfo {
      return {
        ...createChangeMessage(),
        author,
        message,
        _revision_number: ps as PatchSetNum,
      };
    }

    test('no messages', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [],
        labels,
      };
      const actual = getCodeReviewVotesFromMessage(change, account1);
      assert.equal(actual.size, 0);
    });

    test('no messages from account', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [createMessage(account2, 'Patch Set 1: Code-Review+1', 1)],
        labels,
      };
      const actual = getCodeReviewVotesFromMessage(change, account1);
      assert.equal(actual.size, 0);
    });

    test('one message with code review vote', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [createMessage(account1, 'Patch Set 1: Code-Review+1', 1)],
        labels,
      };
      const actual = getCodeReviewVotesFromMessage(change, account1);
      assert.deepEqual(
        actual,
        new Map([[1 as PatchSetNum, {label: 'Code-Review', value: '+1'}]])
      );
    });

    test('vote reset', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [createMessage(account1, 'Patch Set 1: Code-Review-1', 1)],
        labels,
      };
      const actual = getCodeReviewVotesFromMessage(change, account1);
      assert.deepEqual(
        actual,
        new Map([[1 as PatchSetNum, {label: 'Code-Review', value: '-1'}]])
      );
    });

    test('latest message wins for same patchset', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [
          createMessage(account1, 'Patch Set 1: Code-Review-1', 1),
          createMessage(account1, 'Patch Set 1: Code-Review+1', 1),
        ],
        labels,
      };
      const actual = getCodeReviewVotesFromMessage(change, account1);
      assert.deepEqual(
        actual,
        new Map([[1 as PatchSetNum, {label: 'Code-Review', value: '+1'}]])
      );
    });

    test('messages from different users', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [
          createMessage(account1, 'Patch Set 1: Code-Review+1', 1),
          createMessage(account2, 'Patch Set 1: Code-Review-1', 1),
        ],
        labels,
      };
      const actual = getCodeReviewVotesFromMessage(change, account1);
      assert.deepEqual(
        actual,
        new Map([[1 as PatchSetNum, {label: 'Code-Review', value: '+1'}]])
      );
    });

    test('messages for different patchsets', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [
          createMessage(account1, 'Patch Set 1: Code-Review-1', 1),
          createMessage(account1, 'Patch Set 2: Code-Review+1', 2),
        ],
        labels,
      };
      const actual = getCodeReviewVotesFromMessage(change, account1);
      assert.deepEqual(
        actual,
        new Map([
          [1 as PatchSetNum, {label: 'Code-Review', value: '-1'}],
          [2 as PatchSetNum, {label: 'Code-Review', value: '+1'}],
        ])
      );
    });
  });
});

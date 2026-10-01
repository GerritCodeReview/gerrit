/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {
  getCodeReviewVotesFromMessage,
  getLastReviewedPatchSet,
  getPatchSetsWithUserVotes,
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
import {SavingState} from '../types/common';
import {
  createAccountWithId,
  createChange,
  createChangeMessage,
  createCommentThread,
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

  suite('getPatchSetsWithUserVotes', () => {
    const account: AccountInfo = createAccountWithId(1);
    const labels: LabelNameToInfoMap = {
      'Code-Review': createDetailedLabelInfo(),
    };

    function createMessage(message: string, ps: number): ChangeMessageInfo {
      return {
        ...createChangeMessage(),
        author: account,
        message,
        _revision_number: ps as PatchSetNum,
      };
    }

    test('returns patch sets with explicit user votes', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [createMessage('Patch Set 1: Code-Review+1', 1)],
        labels,
      };

      assert.deepEqual([...getPatchSetsWithUserVotes(change, account)], [1]);
    });

    test('does not treat copied votes in an upload message as user votes', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [
          createMessage(
            'Uploaded patch set 2.\n\nCopied Votes:\n* Code-Review+1',
            2
          ),
        ],
        labels,
      };

      assert.deepEqual([...getPatchSetsWithUserVotes(change, account)], []);
    });
  });

  suite('getLastReviewedPatchSet', () => {
    const account: AccountInfo = createAccountWithId(1);
    const labels: LabelNameToInfoMap = {
      'Code-Review': createDetailedLabelInfo(),
    };

    test('returns the latest prior patch set with a comment or vote', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [
          {
            ...createChangeMessage(),
            author: account,
            message: 'Patch Set 2: Code-Review+1',
            _revision_number: 2 as PatchSetNum,
          },
        ],
        labels,
      };
      const threads = [
        createCommentThread([
          {author: account, patch_set: 1 as PatchSetNum},
        ]),
      ];

      assert.equal(getLastReviewedPatchSet(change, threads, account, 4), 2);
    });

    test('does not inspect review activity for the first patch set', () => {
      const change = {
        get messages() {
          throw new Error('messages should not be accessed');
        },
      } as ChangeInfo;

      assert.isUndefined(getLastReviewedPatchSet(change, [], account, 1));
    });

    test('does not use activity on the target patch set', () => {
      const change: ChangeInfo = {
        ...createChange(),
        messages: [
          {
            ...createChangeMessage(),
            author: account,
            message: 'Patch Set 3: Code-Review+1',
            _revision_number: 3 as PatchSetNum,
          },
        ],
        labels,
      };
      const threads = [
        createCommentThread([
          {author: account, patch_set: 3 as PatchSetNum},
        ]),
      ];

      assert.isUndefined(getLastReviewedPatchSet(change, threads, account, 3));
    });

    test('ignores draft comments', () => {
      const threads = [
        createCommentThread([
          {
            author: account,
            patch_set: 1 as PatchSetNum,
            savingState: SavingState.OK,
          },
        ]),
      ];

      assert.isUndefined(
        getLastReviewedPatchSet(undefined, threads, account, 3)
      );
    });
  });
});

/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getRevertCreatedChangeIds} from './message-util';
import {assert} from '@open-wc/testing';
import {MessageTag} from '../constants/constants';
import {ChangeId, ReviewInputTag} from '../api/rest-api';
import {createChangeMessage} from '../test/test-data-generators';

suite('message-util tests', () => {
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

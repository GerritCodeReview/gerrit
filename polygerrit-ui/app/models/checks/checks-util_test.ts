/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './checks-model';
import {assert} from '@open-wc/testing';
import {
  ALL_ATTEMPTS,
  AttemptChoice,
  LATEST_ATTEMPT,
  computeIsExpandable,
  rectifyFix,
  sortAttemptChoices,
  stringToAttemptChoice,
} from './checks-util';
import {Fix, Replacement} from '../../api/checks';
import {PROVIDED_FIX_ID} from '../../utils/comment-util';
import {CommentRange} from '../../api/rest-api';
import {
  createCheckFix,
  createCheckLink,
  createCheckResult,
  createRange,
} from '../../test/test-data-generators';

suite('checks-util tests', () => {
  setup(() => {});

  teardown(() => {});

  test('stringToAttemptChoice', () => {
    assert.equal(stringToAttemptChoice('0'), 0);
    assert.equal(stringToAttemptChoice('1'), 1);
    assert.equal(stringToAttemptChoice('999'), 999);
    assert.equal(stringToAttemptChoice('latest'), 'latest');
    assert.equal(stringToAttemptChoice('all'), 'all');

    assert.equal(stringToAttemptChoice(undefined), undefined);
    assert.equal(stringToAttemptChoice(''), undefined);
    assert.equal(stringToAttemptChoice('asdf'), undefined);
    assert.equal(stringToAttemptChoice('-1'), undefined);
    assert.equal(stringToAttemptChoice('1x'), undefined);
  });

  test('rectifyFix', () => {
    assert.isUndefined(rectifyFix(undefined, 'name'));
    assert.isUndefined(rectifyFix({} as Fix, 'name'));
    assert.isUndefined(
      rectifyFix({description: 'asdf', replacements: []}, 'name')
    );
    assert.isUndefined(
      rectifyFix(
        {description: 'asdf', replacements: [{} as Replacement]},
        'test-check-name'
      )
    );
    assert.isUndefined(
      rectifyFix(
        {
          description: 'asdf',
          replacements: [
            {
              path: 'test-path',
              range: {} as CommentRange,
              replacement: 'test-replacement-string',
            },
          ],
        },
        'test-check-name'
      )
    );
    const rectified = rectifyFix(
      {
        replacements: [
          {
            path: 'test-path',
            range: {
              start_line: 1,
              end_line: 1,
              start_character: 0,
              end_character: 1,
            } as CommentRange,
            replacement: 'test-replacement-string',
          },
        ],
      },
      'test-check-name'
    );
    assert.isDefined(rectified);
    assert.equal(rectified?.description, 'Fix provided by test-check-name');
    assert.equal(rectified?.fix_id, PROVIDED_FIX_ID);
  });

  test('sortAttemptChoices', () => {
    const unsorted: (AttemptChoice | undefined)[] = [
      3,
      1,
      LATEST_ATTEMPT,
      ALL_ATTEMPTS,
      undefined,
      0,
      999,
    ];
    const sortedExpected: (AttemptChoice | undefined)[] = [
      LATEST_ATTEMPT,
      ALL_ATTEMPTS,
      0,
      1,
      3,
      999,
      undefined,
    ];
    assert.deepEqual(unsorted.sort(sortAttemptChoices), sortedExpected);
  });

  test('computeIsExpandable', () => {
    assert.isFalse(computeIsExpandable(createCheckResult()));
    assert.isTrue(
      computeIsExpandable({...createCheckResult(), message: 'asdf'})
    );
    assert.isFalse(
      computeIsExpandable({
        ...createCheckResult(),
        message: 'asdf',
        summary: undefined as unknown as string,
      })
    );
    assert.isTrue(
      computeIsExpandable({...createCheckResult(), message: 'asdf'})
    );
    assert.isTrue(
      computeIsExpandable({
        ...createCheckResult(),
        links: [createCheckLink(), createCheckLink()],
      })
    );
    assert.isTrue(
      computeIsExpandable({
        ...createCheckResult(),
        codePointers: [{path: 'asdf', range: createRange()}],
      })
    );
    assert.isTrue(
      computeIsExpandable({
        ...createCheckResult(),
        fixes: [createCheckFix()],
      })
    );
  });
});

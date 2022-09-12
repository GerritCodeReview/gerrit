/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {assert} from '@open-wc/testing';
import {SubmitRequirementExpressionInfo} from '../api/rest-api';
import '../test/common-test-setup';
import {
  atomizeExpression,
  SubmitRequirementExpressionAtomStatus,
} from './submit-requirement-util';

suite('submit-requirement-util', () => {
  test('atomizeExpression no evaluted atoms', () => {
    const expression: SubmitRequirementExpressionInfo = {
      expression:
        'label:Code-Review=MAX,user=non_uploader AND -label:Code-Review=MIN',
    };

    assert.deepStrictEqual(atomizeExpression(expression), [
      {
        value:
          'label:Code-Review=MAX,user=non_uploader AND -label:Code-Review=MIN',
        isAtom: false,
      },
    ]);
  });

  test('atomizeExpression normal', () => {
    const expression: SubmitRequirementExpressionInfo = {
      expression: 'has:unresolved AND hashtag:allow-unresolved-comments',
      passing_atoms: ['has:unresolved'],
      failing_atoms: ['hashtag:allow-unresolved-comments'],
    };

    assert.deepStrictEqual(atomizeExpression(expression), [
      {
        value: 'has:unresolved',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.PASSING,
      },
      {
        value: ' AND ',
        isAtom: false,
      },
      {
        value: 'hashtag:allow-unresolved-comments',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
      },
    ]);
  });

  test('atomizeExpression unary negation', () => {
    const expression: SubmitRequirementExpressionInfo = {
      expression: '-has:unresolved AND hashtag:allow-unresolved-comments',
      passing_atoms: ['has:unresolved'],
      failing_atoms: ['hashtag:allow-unresolved-comments'],
    };

    assert.deepStrictEqual(atomizeExpression(expression), [
      {
        value: '-has:unresolved',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
      },
      {
        value: ' AND ',
        isAtom: false,
      },
      {
        value: 'hashtag:allow-unresolved-comments',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
      },
    ]);
  });

  test('atomizeExpression partially unmatched', () => {
    const expression: SubmitRequirementExpressionInfo = {
      expression:
        'NOT (-has:unresolved AND hashtag:allow-unresolved-comments) OR tested:no',
      passing_atoms: ['has:unresolved'],
      failing_atoms: ['hashtag:allow-unresolved-comments'],
    };

    // All that is not part of passing or failing atoms is considered
    // "not an atom".
    assert.deepStrictEqual(atomizeExpression(expression), [
      {
        value: 'NOT (',
        isAtom: false,
      },
      {
        value: '-has:unresolved',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
      },
      {
        value: ' AND ',
        isAtom: false,
      },
      {
        value: 'hashtag:allow-unresolved-comments',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
      },
      {
        value: ') OR tested:no',
        isAtom: false,
      },
    ]);
  });
});

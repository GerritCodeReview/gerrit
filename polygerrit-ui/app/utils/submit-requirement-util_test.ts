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
        atomExplanation: '',
      },
      {
        value: ' AND ',
        isAtom: false,
      },
      {
        value: 'hashtag:allow-unresolved-comments',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
        atomExplanation: '',
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
        atomExplanation: '',
      },
      {
        value: ' AND ',
        isAtom: false,
      },
      {
        value: 'hashtag:allow-unresolved-comments',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
        atomExplanation: '',
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
        atomExplanation: '',
      },
      {
        value: ' AND ',
        isAtom: false,
      },
      {
        value: 'hashtag:allow-unresolved-comments',
        isAtom: true,
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
        atomExplanation: '',
      },
      {
        value: ') OR tested:no',
        isAtom: false,
      },
    ]);
  });

  test('atomizeExpression b/370742469', () => {
    const expression: SubmitRequirementExpressionInfo = {
      expression:
        '-is:android-cherry-pick_exemptedusers OR is:android-cherry-pick_exemptedusers',
      passing_atoms: [
        'is:android-cherry-pick_exemptedusers',
        'is:android-cherry-pick_exemptedusers',
        'project:platform/frameworks/support',
      ],
      failing_atoms: [
        'label:Code-Review=MIN',
        'label:Code-Review=MAX,user=non_uploader',
        'label:Code-Review=MAX,count>=2',
        'label:Code-Review=MAX',
        'label:Exempt=+1',
        'uploader:1474732',
        'project:platform/developers/docs',
      ],
    };

    assert.deepStrictEqual(atomizeExpression(expression), [
      {
        atomStatus: SubmitRequirementExpressionAtomStatus.FAILING,
        atomExplanation: '',
        isAtom: true,
        value: '-is:android-cherry-pick_exemptedusers',
      },
      {
        value: ' OR ',
        isAtom: false,
      },
      {
        atomStatus: SubmitRequirementExpressionAtomStatus.PASSING,
        atomExplanation: '',
        isAtom: true,
        value: 'is:android-cherry-pick_exemptedusers',
      },
    ]);
  });
});

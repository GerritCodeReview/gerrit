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

import '../../../test/common-test-setup-karma';
import './gr-label-scores';
import {isHidden, queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {GrLabelScores} from './gr-label-scores';
import {AccountId} from '../../../types/common';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {
  createAccountWithId,
  createChange,
} from '../../../test/test-data-generators';
import {ChangeStatus} from '../../../constants/constants';

const basicFixture = fixtureFromElement('gr-label-scores');

suite('gr-label-scores tests', () => {
  const accountId = 123 as AccountId;
  let element: GrLabelScores;

  setup(async () => {
    stubRestApi('getLoggedIn').resolves(false);
    element = basicFixture.instantiate();
    element.change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
          value: 1,
          all: [
            {
              _account_id: accountId,
              value: 1,
            },
          ],
        },
        Verified: {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
          value: 1,
          all: [
            {
              _account_id: accountId,
              value: 1,
            },
          ],
        },
      },
    };

    element.account = createAccountWithId(accountId);

    element.permittedLabels = {
      'Code-Review': ['-2', '-1', ' 0', '+1', '+2'],
      Verified: ['-1', ' 0', '+1'],
    };
    await flush();
  });

  test('get and set label scores', () => {
    for (const label of Object.keys(element.permittedLabels!)) {
      const row = queryAndAssert<GrLabelScoreRow>(
        element,
        'gr-label-score-row[name="' + label + '"]'
      );
      row.setSelectedValue('-1');
    }
    assert.deepEqual(element.getLabelValues(), {
      'Code-Review': -1,
      Verified: -1,
    });
  });

  test('getLabelValues includeDefaults', async () => {
    element.change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          values: {'0': 'meh', '+1': 'good', '-1': 'bad'},
          default_value: 0,
        },
      },
    };
    await flush();

    assert.deepEqual(element.getLabelValues(true), {'Code-Review': 0});
    assert.deepEqual(element.getLabelValues(false), {});
  });

  test('_getVoteForAccount', () => {
    const labelName = 'Code-Review';
    assert.strictEqual(element._getVoteForAccount(labelName), '+1');
  });

  test('_computeColumns', () => {
    const labelValues = element._computeColumns();
    assert.deepEqual(labelValues, {
      '-2': 0,
      '-1': 1,
      '0': 2,
      '1': 3,
      '2': 4,
    });
  });

  test('changes in label score are reflected in _labels', async () => {
    const change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
        },
        Verified: {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
        },
      },
    };
    element.change = change;
    await flush();
    let labels = element._computeLabels();
    assert.deepEqual(labels, [
      {name: 'Code-Review', value: null},
      {name: 'Verified', value: null},
    ]);
    element.change = {
      ...change,
      labels: {
        ...change.labels,
        Verified: {
          ...change.labels.Verified,
          all: [
            {
              _account_id: accountId,
              value: 1,
            },
          ],
        },
      },
    };
    await flush();
    labels = element._computeLabels();
    assert.deepEqual(labels, [
      {name: 'Code-Review', value: null},
      {name: 'Verified', value: '+1'},
    ]);
  });
  suite('message', () => {
    test('shown when change is abandoned', async () => {
      element.change = {
        ...createChange(),
        status: ChangeStatus.ABANDONED,
      };
      await flush();
      assert.isFalse(isHidden(queryAndAssert(element, '.abandonedMessage')));
      assert.isTrue(isHidden(queryAndAssert(element, '.mergedMessage')));
    });
    test('shown when change is merged', async () => {
      element.change = {
        ...createChange(),
        status: ChangeStatus.MERGED,
      };
      await flush();
      assert.isFalse(isHidden(queryAndAssert(element, '.mergedMessage')));
      assert.isTrue(isHidden(queryAndAssert(element, '.abandonedMessage')));
    });
    test('do not show for new', async () => {
      element.change = {
        ...createChange(),
        status: ChangeStatus.NEW,
      };
      await flush();
      assert.isTrue(isHidden(queryAndAssert(element, '.mergedMessage')));
      assert.isTrue(isHidden(queryAndAssert(element, '.abandonedMessage')));
    });
  });
});

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

import '../../../test/common-test-setup-karma.js';
import './gr-label-scores.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-label-scores');

suite('gr-label-scores tests', () => {
  let element;

  setup(async () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    element = basicFixture.instantiate();
    element.change = {
      _number: '123',
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
          all: [{
            _account_id: 123,
            value: 1,
          }],
        },
        'Verified': {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
          value: 1,
          all: [{
            _account_id: 123,
            value: 1,
          }],
        },
      },
    };

    element.account = {
      _account_id: 123,
    };

    element.permittedLabels = {
      'Code-Review': [
        '-2',
        '-1',
        ' 0',
        '+1',
        '+2',
      ],
      'Verified': [
        '-1',
        ' 0',
        '+1',
      ],
    };
    await flush();
  });

  test('get and set label scores', () => {
    for (const label of Object.keys(element.permittedLabels)) {
      const row = element.shadowRoot
          .querySelector('gr-label-score-row[name="' + label + '"]');
      row.setSelectedValue(-1);
    }
    assert.deepEqual(element.getLabelValues(), {
      'Code-Review': -1,
      'Verified': -1,
    });
  });

  test('getLabelValues includeDefaults', async () => {
    element.change = {
      _number: '123',
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
    assert.strictEqual(element._getVoteForAccount(
        element.change.labels, labelName, element.account),
    '+1');
  });

  test('_computeColumns', () => {
    element._computeColumns(element.permittedLabels);
    assert.deepEqual(element._labelValues, {
      '-2': 0,
      '-1': 1,
      '0': 2,
      '1': 3,
      '2': 4,
    });
  });

  test('_computeLabelAccessClass undefined case', () => {
    assert.strictEqual(
        element._computeLabelAccessClass(undefined, undefined), '');
    assert.strictEqual(
        element._computeLabelAccessClass('', undefined), '');
    assert.strictEqual(
        element._computeLabelAccessClass(undefined, {}), '');
  });

  test('_computeLabelAccessClass has access', () => {
    assert.strictEqual(
        element._computeLabelAccessClass('foo', {foo: ['']}), 'access');
  });

  test('_computeLabelAccessClass no access', () => {
    assert.strictEqual(
        element._computeLabelAccessClass('zap', {foo: ['']}), 'no-access');
  });

  test('changes in label score are reflected in _labels', () => {
    element.change = {
      _number: '123',
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
        'Verified': {
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
    assert.deepEqual(element._labels [
        ({name: 'Code-Review', value: null}, {name: 'Verified', value: null})
    ]);
    element.set(['change', 'labels', 'Verified', 'all'],
        [{_account_id: 123, value: 1}]);
    assert.deepEqual(element._labels, [
      {name: 'Code-Review', value: null},
      {name: 'Verified', value: '+1'},
    ]);
  });
});


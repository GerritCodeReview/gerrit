/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import './gr-diff-preferences-dialog';
import {GrDiffPreferencesDialog} from './gr-diff-preferences-dialog';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {DiffPreferencesInfo} from '../../../api/diff';
import {ParsedJSON} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-diff-preferences-dialog');

suite('gr-diff-preferences-dialog', () => {
  let element: GrDiffPreferencesDialog;
  let originalDiffPrefs: DiffPreferencesInfo;

  setup(() => {
    originalDiffPrefs = {
      ...createDefaultDiffPrefs(),
      line_wrapping: true,
    };

    stubRestApi('getDiffPreferences').returns(
      Promise.resolve(originalDiffPrefs)
    );

    element = basicFixture.instantiate();
  });

  test('changes applies only on save', async () => {
    await flush();
    element.open();
    await flush();
    assert.isUndefined(element._diffPrefsChanged);
    assert.isTrue(
      queryAndAssert<HTMLInputElement>(
        queryAndAssert(element, '#diffPreferences'),
        '#lineWrappingInput'
      ).checked
    );

    MockInteractions.tap(
      queryAndAssert<HTMLInputElement>(
        queryAndAssert(element, '#diffPreferences'),
        '#lineWrappingInput'
      )
    );
    await flush();
    assert.isFalse(
      queryAndAssert<HTMLInputElement>(
        queryAndAssert(element, '#diffPreferences'),
        '#lineWrappingInput'
      ).checked
    );
    assert.isTrue(element._diffPrefsChanged);
    assert.isTrue(originalDiffPrefs.line_wrapping);

    stubRestApi('getResponseObject').returns(
      Promise.resolve({
        ...originalDiffPrefs,
        line_wrapping: false,
      } as unknown as ParsedJSON)
    );

    MockInteractions.tap(element.$.saveButton);
    await flush();
    // Original prefs must remains unchanged, dialog must expose a new object
    assert.isTrue(originalDiffPrefs.line_wrapping);
    assert.isFalse(element._diffPrefsChanged);
  });
});

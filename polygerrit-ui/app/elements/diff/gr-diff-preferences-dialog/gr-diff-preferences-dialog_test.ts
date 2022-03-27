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
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {DiffPreferencesInfo} from '../../../api/diff';
import {ParsedJSON} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';

suite('gr-diff-preferences-dialog', () => {
  let element: GrDiffPreferencesDialog;
  let originalDiffPrefs: DiffPreferencesInfo;

  setup(async () => {
    originalDiffPrefs = {
      ...createDefaultDiffPrefs(),
      line_wrapping: true,
    };

    stubRestApi('getDiffPreferences').returns(
      Promise.resolve(originalDiffPrefs)
    );

    element = await fixture<GrDiffPreferencesDialog>(html`
      <gr-diff-preferences-dialog></gr-diff-preferences-dialog>
    `);
  });

  test('changes applies only on save', async () => {
    await element.updateComplete;
    element.open();
    await element.updateComplete;
    assert.isUndefined(element.diffPrefsChanged);
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
    await element.updateComplete;
    assert.isFalse(
      queryAndAssert<HTMLInputElement>(
        queryAndAssert(element, '#diffPreferences'),
        '#lineWrappingInput'
      ).checked
    );
    assert.isTrue(element.diffPrefsChanged);
    assert.isTrue(originalDiffPrefs.line_wrapping);

    stubRestApi('getResponseObject').returns(
      Promise.resolve({
        ...originalDiffPrefs,
        line_wrapping: false,
      } as unknown as ParsedJSON)
    );

    // Because MockInteractions.tap only fires events it doesn't wait on a
    // function load including if it's async. We have to manually do this.
    const promise = mockPromise();
    queryAndAssert<GrDiffPreferences>(
      element,
      '#diffPreferences'
    ).addEventListener('has-unsaved-changes-changed', () => {
      assert.isFalse(element.diffPrefsChanged);
      promise.resolve();
    });

    MockInteractions.tap(queryAndAssert<GrButton>(element, '#saveButton'));
    await element.updateComplete;
    // Original prefs must remains unchanged, dialog must expose a new object
    assert.isTrue(originalDiffPrefs.line_wrapping);
    await promise;
  });
});

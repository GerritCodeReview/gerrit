/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-diff-preferences-dialog';
import {GrDiffPreferencesDialog} from './gr-diff-preferences-dialog';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {queryAndAssert, stubRestApi, waitUntil} from '../../../test/test-utils';
import {DiffPreferencesInfo} from '../../../api/diff';
import {ParsedJSON} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html} from '@open-wc/testing-helpers';

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
    element.open();
    await element.updateComplete;
    assert.isUndefined(element.diffPrefsChanged);
    assert.isTrue(
      queryAndAssert<HTMLInputElement>(
        queryAndAssert(element, '#diffPreferences'),
        '#lineWrappingInput'
      ).checked
    );

    queryAndAssert<HTMLInputElement>(
      queryAndAssert(element, '#diffPreferences'),
      '#lineWrappingInput'
    ).click();
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

    queryAndAssert<GrButton>(element, '#saveButton').click();
    await element.updateComplete;
    // Original prefs must remains unchanged, dialog must expose a new object
    assert.isTrue(originalDiffPrefs.line_wrapping);
    await waitUntil(() => element.diffPrefsChanged === false);
  });
});

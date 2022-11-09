/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-preferences-dialog';
import {GrDiffPreferencesDialog} from './gr-diff-preferences-dialog';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {queryAndAssert, stubRestApi, waitUntil} from '../../../test/test-utils';
import {DiffPreferencesInfo} from '../../../api/diff';
import {ParsedJSON} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';

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

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog id="diffPrefsModal" tabindex="-1">
          <div aria-labelledby="diffPreferencesTitle" role="dialog">
            <h3 class="diffHeader heading-3" id="diffPreferencesTitle">
              Diff Preferences
            </h3>
            <gr-diff-preferences id="diffPreferences"> </gr-diff-preferences>
            <div class="diffActions">
              <gr-button
                aria-disabled="false"
                id="cancelButton"
                link=""
                role="button"
                tabindex="0"
              >
                Cancel
              </gr-button>
              <gr-button
                aria-disabled="true"
                disabled=""
                id="saveButton"
                link=""
                primary=""
                role="button"
                tabindex="-1"
              >
                Save
              </gr-button>
            </div>
          </div>
        </dialog>
      `
    );
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

/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-edit-preferences-dialog';
import {GrEditPreferencesDialog} from './gr-edit-preferences-dialog';
import {createDefaultEditPrefs} from '../../../constants/constants';
import {
  makePrefixedJSON,
  queryAndAssert,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';
import {EditPreferencesInfo} from '../../../types/common';

suite('gr-edit-preferences-dialog', () => {
  let element: GrEditPreferencesDialog;
  let originalEditPrefs: EditPreferencesInfo;

  setup(async () => {
    originalEditPrefs = {
      ...createDefaultEditPrefs(),
      line_wrapping: true,
    };

    stubRestApi('getEditPreferences').returns(
      Promise.resolve(originalEditPrefs)
    );

    element = await fixture<GrEditPreferencesDialog>(html`
      <gr-edit-preferences-dialog></gr-edit-preferences-dialog>
    `);
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog id="editPrefsModal" tabindex="-1">
          <div aria-labelledby="editPreferencesTitle" role="dialog">
            <h3 class="editHeader heading-3" id="editPreferencesTitle">
              Edit Preferences
            </h3>
            <gr-edit-preferences id="editPreferences"> </gr-edit-preferences>
            <div class="editActions">
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
    assert.isUndefined(element.editPrefsChanged);
    const editShowLineWrapping = queryAndAssert<HTMLInputElement>(
      queryAndAssert(element, '#editPreferences'),
      '#editShowLineWrapping'
    );
    assert.isTrue(editShowLineWrapping.checked);

    editShowLineWrapping.click();
    await element.updateComplete;
    assert.isFalse(editShowLineWrapping.checked);
    assert.isTrue(element.editPrefsChanged);
    assert.isTrue(originalEditPrefs.line_wrapping);

    stubRestApi('saveEditPreferences').resolves(
      new Response(
        makePrefixedJSON({
          ...originalEditPrefs,
          line_wrapping: false,
        })
      )
    );

    queryAndAssert<GrButton>(element, '#saveButton').click();
    await element.updateComplete;
    // Original prefs must remains unchanged, dialog must expose a new object
    assert.isTrue(originalEditPrefs.line_wrapping);
    await waitUntil(() => element.editPrefsChanged === false);
  });
});

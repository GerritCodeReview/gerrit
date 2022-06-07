/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-diff-mode-selector';
import {GrDiffModeSelector} from './gr-diff-mode-selector';
import {DiffViewMode} from '../../../constants/constants';
import {
  queryAndAssert,
  stubUsers,
  waitUntilObserved,
} from '../../../test/test-utils';
import {fixture, html} from '@open-wc/testing-helpers';
import {wrapInProvider} from '../../../models/di-provider-element';
import {
  BrowserModel,
  browserModelToken,
} from '../../../models/browser/browser-model';
import {getAppContext} from '../../../services/app-context';
import {UserModel} from '../../../models/user/user-model';
import {createPreferences} from '../../../test/test-data-generators';
import {GrButton} from '../../../elements/shared/gr-button/gr-button';

suite('gr-diff-mode-selector tests', () => {
  let element: GrDiffModeSelector;
  let browserModel: BrowserModel;
  let userModel: UserModel;

  setup(async () => {
    userModel = getAppContext().userModel;
    browserModel = new BrowserModel(userModel);
    element = (
      await fixture(
        wrapInProvider(
          html`<gr-diff-mode-selector></gr-diff-mode-selector>`,
          browserModelToken,
          browserModel
        )
      )
    ).querySelector('gr-diff-mode-selector')!;
  });

  test('renders side-by-side selected', async () => {
    userModel.setPreferences({
      ...createPreferences(),
      diff_view: DiffViewMode.SIDE_BY_SIDE,
    });
    await waitUntilObserved(
      browserModel.diffViewMode$,
      mode => mode === DiffViewMode.SIDE_BY_SIDE
    );

    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-tooltip-content has-tooltip="" title="Side-by-side diff">
        <gr-button
          id="sideBySideBtn"
          link=""
          class="selected"
          aria-disabled="false"
          aria-pressed="true"
          role="button"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:side-by-side"></iron-icon>
        </gr-button>
      </gr-tooltip-content>
      <gr-tooltip-content has-tooltip title="Unified diff">
        <gr-button
          id="unifiedBtn"
          link=""
          role="button"
          aria-disabled="false"
          aria-pressed="false"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:unified"></iron-icon>
        </gr-button>
      </gr-tooltip-content>
    `);
  });

  test('renders unified selected', async () => {
    userModel.setPreferences({
      ...createPreferences(),
      diff_view: DiffViewMode.UNIFIED,
    });
    await waitUntilObserved(
      browserModel.diffViewMode$,
      mode => mode === DiffViewMode.UNIFIED
    );

    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-tooltip-content has-tooltip="" title="Side-by-side diff">
        <gr-button
          id="sideBySideBtn"
          link=""
          class=""
          aria-disabled="false"
          aria-pressed="false"
          role="button"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:side-by-side"></iron-icon>
        </gr-button>
      </gr-tooltip-content>
      <gr-tooltip-content has-tooltip title="Unified diff">
        <gr-button
          id="unifiedBtn"
          link=""
          class="selected"
          role="button"
          aria-disabled="false"
          aria-pressed="true"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:unified"></iron-icon>
        </gr-button>
      </gr-tooltip-content>
    `);
  });

  test('set mode', async () => {
    browserModel.setScreenWidth(0);
    const saveStub = stubUsers('updatePreferences');

    // Setting the mode initially does not save prefs.
    element.saveOnChange = true;
    queryAndAssert<GrButton>(element, 'gr-button#sideBySideBtn').click();
    await element.updateComplete;

    assert.isFalse(saveStub.called);

    // Setting the mode to itself does not save prefs.
    queryAndAssert<GrButton>(element, 'gr-button#sideBySideBtn').click();
    await element.updateComplete;

    assert.isFalse(saveStub.called);

    // Setting the mode to something else does not save prefs if saveOnChange
    // is false.
    element.saveOnChange = false;
    queryAndAssert<GrButton>(element, 'gr-button#unifiedBtn').click();
    await element.updateComplete;

    assert.isFalse(saveStub.called);

    // Setting the mode to something else does not save prefs if saveOnChange
    // is false.
    element.saveOnChange = true;
    queryAndAssert<GrButton>(element, 'gr-button#sideBySideBtn').click();
    await element.updateComplete;

    assert.isTrue(saveStub.calledOnce);
  });
});

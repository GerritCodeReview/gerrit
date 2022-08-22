/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {queryAndAssert} from '../../../test/test-utils';
import {GrChangeStar} from './gr-change-star';
import './gr-change-star';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {createChange} from '../../../test/test-data-generators';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-change-star tests', () => {
  let element: GrChangeStar;

  setup(async () => {
    element = await fixture(html`<gr-change-star></gr-change-star>`);
    element.change = {
      ...createChange(),
      starred: true,
    };
    await element.updateComplete;
  });

  test('renders starred', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <button
          aria-label="Unstar this change"
          role="checkbox"
          title="Star/unstar change (shortcut: s)"
        >
          <gr-icon icon="star_rate" filled class="active"></gr-icon>
        </button>
      `
    );
  });

  test('renders unstarred', async () => {
    element.change!.starred = false;
    element.requestUpdate('change');
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <button
          aria-label="Star this change"
          role="checkbox"
          title="Star/unstar change (shortcut: s)"
        >
          <gr-icon icon="star_rate"></gr-icon>
        </button>
      `
    );
  });

  test('starring', async () => {
    element.change!.starred = false;
    await element.updateComplete;
    assert.equal(element.change!.starred, false);

    MockInteractions.tap(queryAndAssert<HTMLButtonElement>(element, 'button'));
    await element.updateComplete;
    assert.equal(element.change!.starred, true);
  });

  test('unstarring', async () => {
    element.change!.starred = true;
    await element.updateComplete;
    assert.equal(element.change!.starred, true);

    MockInteractions.tap(queryAndAssert<HTMLButtonElement>(element, 'button'));
    await element.updateComplete;
    assert.equal(element.change!.starred, false);
  });
});

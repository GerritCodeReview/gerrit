/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html} from '@open-wc/testing-helpers';
import '../../../test/common-test-setup-karma';
import './gr-change-list-action-bar';
import type {GrChangeListActionBar} from './gr-change-list-action-bar';

suite('gr-change-list-action-bar tests', () => {
  let element: GrChangeListActionBar;
  setup(async () => {
    element = await fixture(
      html`<gr-change-list-action-bar></gr-change-list-action-bar>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ ` <div>ACTION BAR</div>`);
  });
});

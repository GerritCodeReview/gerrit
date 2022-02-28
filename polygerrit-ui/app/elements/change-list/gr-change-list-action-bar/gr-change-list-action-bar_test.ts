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

  test('renders all buttons', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <td>
        <div class="container">
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >submit</gr-button
          >
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >create topic</gr-button
          >
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >add to topic</gr-button
          >
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >add changes</gr-button
          >
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >remove changes</gr-button
          >
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >abandon</gr-button
          >
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >add reviewer/cc</gr-button
          >
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >vote</gr-button
          >
        </div>
      </td>
    `);
  });
});

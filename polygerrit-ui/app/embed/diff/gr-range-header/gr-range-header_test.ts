/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-range-header';
import {GrRangeHeader} from './gr-range-header';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-range-header test', () => {
  let element: GrRangeHeader;

  setup(async () => {
    element = await fixture<GrRangeHeader>(
      html`<gr-range-header></gr-range-header>`
    );
    await element.updateComplete;
  });

  test('renders', async () => {
    element.filled = true;
    element.icon = 'test-icon';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="row">
          <gr-icon
            aria-hidden="true"
            class="icon"
            filled
            icon="test-icon"
          ></gr-icon>
          <slot></slot>
        </div>
      `
    );
  });
});

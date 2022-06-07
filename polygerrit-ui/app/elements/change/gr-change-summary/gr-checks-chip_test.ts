/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrChecksChip} from './gr-checks-chip';
import {Category} from '../../../api/checks';

suite('gr-checks-chip test', () => {
  let element: GrChecksChip;
  setup(async () => {
    element = await fixture(html`<gr-checks-chip
      .statusOrCategory=${Category.SUCCESS}
      .text=${'0'}
    ></gr-checks-chip>`);
  });

  test('is defined', () => {
    const el = document.createElement('gr-checks-chip');
    assert.instanceOf(el, GrChecksChip);
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div
      aria-label="0 success result"
      class="check-circle-outline checksChip font-small"
      role="link"
      tabindex="0"
    >
      <iron-icon icon="gr-icons:check-circle-outline"> </iron-icon>
      <div class="text">0</div>
    </div>`);
  });
});

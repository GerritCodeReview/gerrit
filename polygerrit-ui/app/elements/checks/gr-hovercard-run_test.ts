/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup-karma';
import './gr-hovercard-run';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrHovercardRun} from './gr-hovercard-run';

suite('gr-hovercard-run tests', () => {
  let element: GrHovercardRun;

  setup(async () => {
    element = await fixture<GrHovercardRun>(html`
      <gr-hovercard-run class="hovered"></gr-hovercard-run>
    `);
    await flush();
  });

  teardown(() => {
    element.mouseHide(new MouseEvent('click'));
  });

  test('hovercard is shown', () => {
    assert.equal(element.computeIcon(), '');
  });
});

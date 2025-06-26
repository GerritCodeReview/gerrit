/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-selector';
import {assert, fixture, html} from '@open-wc/testing';
import {GrSelector} from './gr-selector';

suite('gr-selector tests', () => {
  let element: GrSelector;

  setup(async () => {
    element = await fixture<GrSelector>(html`<gr-selector></gr-selector>`);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(element, /* HTML */ ' <slot> </slot> ');
  });
});

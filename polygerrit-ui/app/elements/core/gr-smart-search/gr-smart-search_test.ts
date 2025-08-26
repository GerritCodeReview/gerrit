/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-smart-search';
import {GrSmartSearch} from './gr-smart-search';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-smart-search tests', () => {
  let element: GrSmartSearch;

  setup(async () => {
    element = await fixture(html`<gr-smart-search></gr-smart-search>`);
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ ' <gr-search-bar id="search"> </gr-search-bar> '
    );
  });
});

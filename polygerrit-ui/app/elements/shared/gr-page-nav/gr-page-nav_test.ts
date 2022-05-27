/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-page-nav';
import {GrPageNav} from './gr-page-nav';
import {fixture, html} from '@open-wc/testing-helpers';
import {queryAndAssert} from '../../../test/test-utils';

suite('gr-page-nav tests', () => {
  let element: GrPageNav;

  setup(async () => {
    element = await fixture<GrPageNav>(html`
      <gr-page-nav>
        <ul>
          <li>item</li>
        </ul>
      </gr-page-nav>
    `);
    await element.updateComplete;
  });

  test('header is not pinned just below top', () => {
    sinon.stub(element, 'getOffsetTop').callsFake(() => 10);
    sinon.stub(element, 'getScrollY').callsFake(() => 5);
    element.handleBodyScroll();
    assert.isFalse(queryAndAssert(element, 'nav').classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page', () => {
    sinon.stub(element, 'getOffsetTop').callsFake(() => 10);
    sinon.stub(element, 'getScrollY').callsFake(() => 25);
    element.handleBodyScroll();
    assert.isTrue(queryAndAssert(element, 'nav').classList.contains('pinned'));
  });

  test('header is not pinned just below top with header set', () => {
    element.headerHeight = 20;
    sinon.stub(element, 'getScrollY').callsFake(() => 15);
    element.handleBodyScroll();
    assert.isFalse(queryAndAssert(element, 'nav').classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page with header set', () => {
    element.headerHeight = 20;
    sinon.stub(element, 'getScrollY').callsFake(() => 25);
    element.handleBodyScroll();
    assert.isTrue(queryAndAssert(element, 'nav').classList.contains('pinned'));
  });
});

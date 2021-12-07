/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import './gr-page-nav';
import {GrPageNav} from './gr-page-nav';
import {html} from '@polymer/polymer/lib/utils/html-tag';

const basicFixture = fixtureFromTemplate(html`
  <gr-page-nav>
    <ul>
      <li>item</li>
    </ul>
  </gr-page-nav>
`);

suite('gr-page-nav tests', () => {
  let element: GrPageNav;

  setup(async () => {
    element = basicFixture.instantiate() as GrPageNav;
    await flush();
  });

  test('header is not pinned just below top', () => {
    sinon.stub(element, '_getOffsetTop').callsFake(() => 10);
    sinon.stub(element, '_getScrollY').callsFake(() => 5);
    element._handleBodyScroll();
    assert.isFalse(element.$.nav.classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page', () => {
    sinon.stub(element, '_getOffsetTop').callsFake(() => 10);
    sinon.stub(element, '_getScrollY').callsFake(() => 25);
    element._handleBodyScroll();
    assert.isTrue(element.$.nav.classList.contains('pinned'));
  });

  test('header is not pinned just below top with header set', () => {
    element._headerHeight = 20;
    sinon.stub(element, '_getScrollY').callsFake(() => 15);
    element._handleBodyScroll();
    assert.isFalse(element.$.nav.classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page with header set', () => {
    element._headerHeight = 20;
    sinon.stub(element, '_getScrollY').callsFake(() => 25);
    element._handleBodyScroll();
    assert.isTrue(element.$.nav.classList.contains('pinned'));
  });
});

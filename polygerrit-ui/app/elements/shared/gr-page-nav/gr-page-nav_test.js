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

import '../../../test/common-test-setup-karma.js';
import './gr-page-nav.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-page-nav>
      <ul>
        <li>item</li>
      </ul>
    </gr-page-nav>
`);

suite('gr-page-nav tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
    flush();
  });

  test('header is not pinned just below top', () => {
    sinon.stub(element, '_getOffsetParent').callsFake(() => 0);
    sinon.stub(element, '_getOffsetTop').callsFake(() => 10);
    sinon.stub(element, '_getScrollY').callsFake(() => 5);
    element._handleBodyScroll();
    assert.isFalse(element.$.nav.classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page', () => {
    sinon.stub(element, '_getOffsetParent').callsFake(() => 0);
    sinon.stub(element, '_getOffsetTop').callsFake(() => 10);
    sinon.stub(element, '_getScrollY').callsFake(() => 25);
    window.scrollY = 100;
    element._handleBodyScroll();
    assert.isTrue(element.$.nav.classList.contains('pinned'));
  });

  test('header is not pinned just below top with header set', () => {
    element._headerHeight = 20;
    sinon.stub(element, '_getScrollY').callsFake(() => 15);
    window.scrollY = 100;
    element._handleBodyScroll();
    assert.isFalse(element.$.nav.classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page with header set', () => {
    element._headerHeight = 20;
    sinon.stub(element, '_getScrollY').callsFake(() => 25);
    window.scrollY = 100;
    element._handleBodyScroll();
    assert.isTrue(element.$.nav.classList.contains('pinned'));
  });
});


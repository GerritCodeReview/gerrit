
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


<meta charset="utf-8">




<script src="/node_modules/page/page.js"></script>



<test-fixture id="basic">
  <template>
    <gr-page-nav>
      <ul>
        <li>item</li>
      </ul>
    </gr-page-nav>
  </template>
</test-fixture>


import '../../../test/common-test-setup.js';
import './gr-page-nav.js';
suite('gr-page-nav tests', () => {
  let element;
  let sandbox;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = fixture('basic');
    flushAsynchronousOperations();
  });

  teardown(() => {
    sandbox.restore();
  });

  test('header is not pinned just below top', () => {
    sandbox.stub(element, '_getOffsetParent', () => 0);
    sandbox.stub(element, '_getOffsetTop', () => 10);
    sandbox.stub(element, '_getScrollY', () => 5);
    element._handleBodyScroll();
    assert.isFalse(element.$.nav.classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page', () => {
    sandbox.stub(element, '_getOffsetParent', () => 0);
    sandbox.stub(element, '_getOffsetTop', () => 10);
    sandbox.stub(element, '_getScrollY', () => 25);
    window.scrollY = 100;
    element._handleBodyScroll();
    assert.isTrue(element.$.nav.classList.contains('pinned'));
  });

  test('header is not pinned just below top with header set', () => {
    element._headerHeight = 20;
    sandbox.stub(element, '_getScrollY', () => 15);
    window.scrollY = 100;
    element._handleBodyScroll();
    assert.isFalse(element.$.nav.classList.contains('pinned'));
  });

  test('header is pinned when scroll down the page with header set', () => {
    element._headerHeight = 20;
    sandbox.stub(element, '_getScrollY', () => 25);
    window.scrollY = 100;
    element._handleBodyScroll();
    assert.isTrue(element.$.nav.classList.contains('pinned'));
  });
});


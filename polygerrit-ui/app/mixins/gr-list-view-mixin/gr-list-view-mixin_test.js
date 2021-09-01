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
import '../../test/common-test-setup-karma.js';
import {ListViewMixin} from './gr-list-view-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';

const basicFixture = fixtureFromElement(
    'gr-list-view-mixin-test-element');

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = ListViewMixin(PolymerElement);

class GrListViewMixinTestElement extends base {
  static get is() { return 'gr-list-view-mixin-test-element'; }
}

customElements.define(GrListViewMixinTestElement.is,
    GrListViewMixinTestElement);

suite('gr-list-view-mixin tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('computeLoadingClass', () => {
    assert.equal(element.computeLoadingClass(true), 'loading');
    assert.equal(element.computeLoadingClass(false), '');
  });

  test('computeShownItems', () => {
    const myArr = new Array(26);
    assert.equal(element.computeShownItems(myArr).length, 25);
  });

  test('getUrl', () => {
    assert.equal(element.getUrl('/path/to/something/', 'item'),
        '/path/to/something/item');
    assert.equal(element.getUrl('/path/to/something/', 'item%test'),
        '/path/to/something/item%2525test');
  });

  test('getFilterValue', () => {
    let params;
    assert.equal(element.getFilterValue(params), '');

    params = {filter: null};
    assert.equal(element.getFilterValue(params), '');

    params = {filter: 'test'};
    assert.equal(element.getFilterValue(params), 'test');
  });

  test('getOffsetValue', () => {
    let params;
    assert.equal(element.getOffsetValue(params), 0);

    params = {offset: null};
    assert.equal(element.getOffsetValue(params), 0);

    params = {offset: 1};
    assert.equal(element.getOffsetValue(params), 1);
  });
});


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
import '../test/common-test-setup-karma.js';
import {
  getFilterValue,
  getOffsetValue,
  getUrl,
  computeLoadingClass,
  computeShownItems,
} from './list-util.js';

suite('list-util tests', () => {
  test('computeLoadingClass', () => {
    assert.equal(computeLoadingClass(true), 'loading');
    assert.equal(computeLoadingClass(false), '');
  });

  test('computeShownItems', () => {
    const myArr = new Array(26);
    assert.equal(computeShownItems(myArr).length, 25);
  });

  test('getUrl', () => {
    assert.equal(
        getUrl('/path/to/something/', 'item'),
        '/path/to/something/item'
    );
    assert.equal(
        getUrl('/path/to/something/', 'item%test'),
        '/path/to/something/item%2525test'
    );
  });

  test('getFilterValue', () => {
    let params;
    assert.equal(getFilterValue(params), '');

    params = {filter: null};
    assert.equal(getFilterValue(params), '');

    params = {filter: 'test'};
    assert.equal(getFilterValue(params), 'test');
  });

  test('getOffsetValue', () => {
    let params;
    assert.equal(getOffsetValue(params), 0);

    params = {offset: null};
    assert.equal(getOffsetValue(params), 0);

    params = {offset: 1};
    assert.equal(getOffsetValue(params), 1);
  });
});

/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import {GrStorageService} from './gr-storage_impl.js';

suite('gr-storage tests', () => {
  let grStorage;

  function mockStorage(opt_quotaExceeded) {
    return {
      getItem(key) { return this[key]; },
      removeItem(key) { delete this[key]; },
      setItem(key, value) {
        // eslint-disable-next-line no-throw-literal
        if (opt_quotaExceeded) { throw {code: 22}; /* Quota exceeded */ }
        this[key] = value;
      },
    };
  }

  setup(() => {
    grStorage = new GrStorageService();
    grStorage.storage = mockStorage();
  });

  test('storing, retrieving and erasing drafts', () => {
    const changeNum = 1234;
    const patchNum = 5;
    const path = 'my_source_file.js';
    const line = 123;
    const location = {
      changeNum,
      patchNum,
      path,
      line,
    };

    // The key is in the expected format.
    const key = grStorage.getDraftKey(location);
    assert.equal(key, ['draft', changeNum, patchNum, path, line].join(':'));

    // There should be no draft initially.
    const draft = grStorage.getDraftComment(location);
    assert.isNotOk(draft);

    // Setting the draft stores it under the expected key.
    grStorage.setDraftComment(location, 'my comment');
    assert.isOk(grStorage.storage.getItem(key));
    assert.equal(JSON.parse(grStorage.storage.getItem(key)).message,
        'my comment');
    assert.isOk(JSON.parse(grStorage.storage.getItem(key)).updated);

    // Erasing the draft removes the key.
    grStorage.eraseDraftComment(location);
    assert.isNotOk(grStorage.storage.getItem(key));
  });

  test('automatically removes old drafts', () => {
    const changeNum = 1234;
    const patchNum = 5;
    const path = 'my_source_file.js';
    const line = 123;
    const location = {
      changeNum,
      patchNum,
      path,
      line,
    };

    const key = grStorage.getDraftKey(location);

    // Make sure that the call to cleanup doesn't get throttled.
    grStorage.lastCleanup = 0;

    const cleanupSpy = sinon.spy(grStorage, 'cleanupItems');

    // Create a message with a timestamp that is a second behind the max age.
    grStorage.storage.setItem(key, JSON.stringify({
      message: 'old message',
      updated: Date.now() - 24 * 60 * 60 * 1000 - 1000,
    }));

    // Getting the draft should cause it to be removed.
    const draft = grStorage.getDraftComment(location);

    assert.isTrue(cleanupSpy.called);
    assert.isNotOk(draft);
    assert.isNotOk(grStorage.storage.getItem(key));
  });

  test('getDraftKey', () => {
    const changeNum = 1234;
    const patchNum = 5;
    const path = 'my_source_file.js';
    const line = 123;
    const location = {
      changeNum,
      patchNum,
      path,
      line,
    };
    let expectedResult = 'draft:1234:5:my_source_file.js:123';
    assert.equal(grStorage.getDraftKey(location), expectedResult);
    location.range = {
      start_character: 1,
      start_line: 1,
      end_character: 1,
      end_line: 2,
    };
    expectedResult = 'draft:1234:5:my_source_file.js:123:1-1-1-2';
    assert.equal(grStorage.getDraftKey(location), expectedResult);
  });

  test('exceeded quota disables storage', () => {
    grStorage.storage = mockStorage(true);
    assert.isFalse(grStorage.exceededQuota);

    const changeNum = 1234;
    const patchNum = 5;
    const path = 'my_source_file.js';
    const line = 123;
    const location = {
      changeNum,
      patchNum,
      path,
      line,
    };
    const key = grStorage.getDraftKey(location);
    grStorage.setDraftComment(location, 'my comment');
    assert.isTrue(grStorage.exceededQuota);
    assert.isNotOk(grStorage.storage.getItem(key));
  });

  test('editable content items', () => {
    const cleanupStub = sinon.stub(grStorage, 'cleanupItems');
    const key = 'testKey';
    const computedKey = grStorage.getEditableContentKey(key);
    // Key correctly computed.
    assert.equal(computedKey, 'editablecontent:testKey');

    grStorage.setEditableContentItem(key, 'my content');

    // Setting the draft stores it under the expected key.
    let item = grStorage.storage.getItem(computedKey);
    assert.isOk(item);
    assert.equal(JSON.parse(item).message, 'my content');
    assert.isOk(JSON.parse(item).updated);

    // getEditableContentItem performs as expected.
    item = grStorage.getEditableContentItem(key);
    assert.isOk(item);
    assert.equal(item.message, 'my content');
    assert.isOk(item.updated);
    assert.isTrue(cleanupStub.called);

    // eraseEditableContentItem performs as expected.
    grStorage.eraseEditableContentItem(key);
    assert.isNotOk(grStorage.storage.getItem(computedKey));
  });
});


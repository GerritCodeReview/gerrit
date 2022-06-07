/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {PatchSetNum} from '../../types/common';
import {StorageLocation} from './gr-storage';
import {GrStorageService} from './gr-storage_impl';

suite('gr-storage tests', () => {
  // We have to type as any because we access private methods
  // for testing
  let grStorage: any;

  function mockStorage(opt_quotaExceeded: boolean) {
    return {
      getItem(key: string) {
        return (this as any)[key];
      },
      removeItem(key: string) {
        delete (this as any)[key];
      },
      setItem(key: string, value: string) {
        if (opt_quotaExceeded) {
          throw new DOMException('error', 'QuotaExceededError');
        }
        (this as any)[key] = value;
      },
    };
  }

  setup(() => {
    grStorage = new GrStorageService();
    grStorage.storage = mockStorage(false);
  });

  test('storing, retrieving and erasing drafts', () => {
    const changeNum = 1234;
    const patchNum = 5 as PatchSetNum;
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
    assert.equal(
      JSON.parse(grStorage.storage.getItem(key)).message,
      'my comment'
    );
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
    grStorage.storage.setItem(
      key,
      JSON.stringify({
        message: 'old message',
        updated: Date.now() - 24 * 60 * 60 * 1000 - 1000,
      })
    );

    // Getting the draft should cause it to be removed.
    const draft = grStorage.getDraftComment(location);

    assert.isTrue(cleanupSpy.called);
    assert.isNotOk(draft);
    assert.isNotOk(grStorage.storage.getItem(key));
  });

  test('getDraftKey', () => {
    const changeNum = 1234;
    const patchNum = 5 as PatchSetNum;
    const path = 'my_source_file.js';
    const line = 123;
    const location: StorageLocation = {
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

  test('editable content items eraseEditableContentItemsForChangeEdit', () => {
    grStorage.setEditableContentItem('testKey', 'my content');
    grStorage.setEditableContentItem(
      'c50_psedit_index.php',
      'my content test 1'
    );
    grStorage.setEditableContentItem('c50_ps3_index.php', 'my content test 2');

    const item = grStorage.storage.getItem(
      'editablecontent:c50_psedit_index.php'
    );
    assert.isOk(item);
    assert.equal(JSON.parse(item).message, 'my content test 1');
    assert.isOk(JSON.parse(item).updated);

    // We have to add getItem, removeItem and setItem to the array.
    // Typically these functions don't get outputed in .storage,
    // but we're mocking the storage so they are being outputed.
    // This doesn't invalidate the test.
    assert.deepEqual(Object.keys(grStorage.storage), [
      'getItem',
      'removeItem',
      'setItem',
      'editablecontent:testKey',
      'editablecontent:c50_psedit_index.php',
      'editablecontent:c50_ps3_index.php',
    ]);

    grStorage.eraseEditableContentItemsForChangeEdit(50);

    // We have to add getItem, removeItem and setItem to the array.
    // Typically these functions don't get outputed in .storage,
    // but we're mocking the storage so they are being outputed.
    // This doesn't invalidate the test.
    assert.deepEqual(Object.keys(grStorage.storage), [
      'getItem',
      'removeItem',
      'setItem',
      'editablecontent:testKey',
    ]);
  });
});

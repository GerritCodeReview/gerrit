/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {NumericChangeId} from '../../api/rest-api';
import '../../test/common-test-setup';
import {GrStorageService} from './gr-storage_impl';

suite('gr-storage tests', () => {
  let grStorage: GrStorageService;

  function mockStorage(opt_quotaExceeded: boolean): Storage {
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
    } as Storage;
  }

  setup(() => {
    grStorage = new GrStorageService();
    grStorage.storage = mockStorage(false);
  });

  test('exceeded quota disables storage', () => {
    grStorage.storage = mockStorage(true);
    assert.isFalse(grStorage.exceededQuota);

    const key = grStorage.getEditableContentKey('test-key');
    grStorage.setEditableContentItem(key, 'test message');
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
    const item = grStorage.storage.getItem(computedKey);
    assert.isOk(item);
    assert.equal(JSON.parse(item!).message, 'my content');
    assert.isOk(JSON.parse(item!).updated);

    // getEditableContentItem performs as expected.
    const obj = grStorage.getEditableContentItem(key);
    assert.isOk(obj);
    assert.equal(obj!.message, 'my content');
    assert.isOk(obj!.updated);
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
    assert.equal(JSON.parse(item!).message, 'my content test 1');
    assert.isOk(JSON.parse(item!).updated);

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

    grStorage.eraseEditableContentItemsForChangeEdit(50 as NumericChangeId);

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

/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert} from '@open-wc/testing';
import * as sinon from 'sinon';
import {BrowserPreferencesModel} from './browser-preferences-model';
import {getBaseUrl} from '../../utils/url-util';

suite('browser preferences', () => {
  let model: BrowserPreferencesModel;
  let storage: Storage;
  const key = () => `gerrit:${getBaseUrl()}:threaded-changes`;
  setup(() => {
    storage = window.localStorage;
    storage.removeItem(key());
    model = new BrowserPreferencesModel();
  });
  teardown(() => {
    model.finalize();
    storage.removeItem(key());
  });

  test('defaults to threads and persists false across model lifetimes', () => {
    assert.isTrue(model.getState().threadedChanges);
    model.setThreadedChanges(false);
    const next = new BrowserPreferencesModel();
    assert.isFalse(next.getState().threadedChanges);
    next.finalize();
  });

  test('reflects changes and clearing storage from another tab', () => {
    window.localStorage.setItem(key(), 'false');
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: key(),
        storageArea: window.localStorage,
      })
    );
    assert.isFalse(model.getState().threadedChanges);
    window.localStorage.removeItem(key());
    window.dispatchEvent(
      new StorageEvent('storage', {key: null, storageArea: window.localStorage})
    );
    assert.isTrue(model.getState().threadedChanges);
  });

  test('ignores unrelated keys', () => {
    model.setThreadedChanges(false);
    window.dispatchEvent(new StorageEvent('storage', {key: 'other'}));
    assert.isFalse(model.getState().threadedChanges);
  });

  test('uses memory if storage throws', () => {
    sinon
      .stub(Storage.prototype, 'setItem')
      .throws(new DOMException('Denied', 'SecurityError'));
    model.setThreadedChanges(false);
    assert.isFalse(model.getState().threadedChanges);
  });

  test('can start when localStorage access is forbidden', () => {
    sinon.stub(window, 'localStorage').get(() => {
      throw new DOMException('Denied', 'SecurityError');
    });
    const next = new BrowserPreferencesModel();
    assert.isTrue(next.getState().threadedChanges);
    next.setThreadedChanges(false);
    assert.isFalse(next.getState().threadedChanges);
    next.finalize();
  });
});

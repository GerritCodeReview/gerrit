/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../test/common-test-setup';
import {PeriodicUpdateManager} from './periodic-update-util';
import {assert} from '@open-wc/testing';

suite('periodic-update-util tests', () => {
  let manager: PeriodicUpdateManager;

  setup(() => {
    manager = new PeriodicUpdateManager(60 * 60 * 1000);
  });

  teardown(() => {
    if (manager._testOnly_getRefreshTimer() !== null) {
      clearInterval(manager._testOnly_getRefreshTimer()!);
      manager.refreshTimer = null;
    }
    manager.components.clear();
  });

  test('register and unregister', () => {
    const component = {requestUpdate: sinon.stub()};
    assert.equal(manager.components.size, 0);
    assert.isNull(manager._testOnly_getRefreshTimer());

    manager.register(component);
    assert.equal(manager.components.size, 1);
    assert.isNotNull(manager._testOnly_getRefreshTimer());

    manager.unregister(component);
    assert.equal(manager.components.size, 0);
    assert.isNull(manager._testOnly_getRefreshTimer());
  });

  test('timer calls requestUpdate', () => {
    const component = {requestUpdate: sinon.stub()};
    const clock = sinon.useFakeTimers();
    manager.register(component);

    assert.isFalse(component.requestUpdate.called);

    clock.tick(60 * 60 * 1000);
    assert.isTrue(component.requestUpdate.calledOnce);

    clock.tick(60 * 60 * 1000);
    assert.isTrue(component.requestUpdate.calledTwice);

    clock.restore();
  });
});

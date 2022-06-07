/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma.js';
import '../../change/gr-change-actions/gr-change-actions.js';

suite('gr-annotation-actions-js-api tests', () => {
  let annotationActions;

  let plugin;

  setup(() => {
    window.Gerrit.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    annotationActions = plugin.annotationApi();
  });

  teardown(() => {
    annotationActions = null;
  });

  test('add notifier', () => {
    const path1 = '/dummy/path1';
    const path2 = '/dummy/path2';
    const annotationLayer1 = annotationActions.createLayer(path1, 1);
    const annotationLayer2 = annotationActions.createLayer(path2, 1);
    const layer1Spy = sinon.spy(annotationLayer1, 'notifyListeners');
    const layer2Spy = sinon.spy(annotationLayer2, 'notifyListeners');

    // Assert that no layers are invoked with a different path.
    annotationActions.notify('/dummy/path3', 0, 10, 'right');
    assert.isFalse(layer1Spy.called);
    assert.isFalse(layer2Spy.called);

    // Assert that only the 1st layer is invoked with path1.
    annotationActions.notify(path1, 0, 10, 'right');
    assert.isTrue(layer1Spy.called);
    assert.isFalse(layer2Spy.called);

    // Reset spies.
    layer1Spy.resetHistory();
    layer2Spy.resetHistory();

    // Assert that only the 2nd layer is invoked with path2.
    annotationActions.notify(path2, 0, 20, 'left');
    assert.isFalse(layer1Spy.called);
    assert.isTrue(layer2Spy.called);
  });

  test('layer notify listeners', () => {
    const annotationLayer = annotationActions.createLayer('/dummy/path', 1);
    let listenerCalledTimes = 0;
    const startRange = 10;
    const endRange = 20;
    const side = 'right';
    const listener = (st, end, s) => {
      listenerCalledTimes++;
      assert.equal(st, startRange);
      assert.equal(end, endRange);
      assert.equal(s, side);
    };

    // Notify with 0 listeners added.
    annotationLayer.notifyListeners(startRange, endRange, side);
    assert.equal(listenerCalledTimes, 0);

    // Add 1 listener.
    annotationLayer.addListener(listener);
    annotationLayer.notifyListeners(startRange, endRange, side);
    assert.equal(listenerCalledTimes, 1);

    // Add 1 more listener. Total 2 listeners.
    annotationLayer.addListener(listener);
    annotationLayer.notifyListeners(startRange, endRange, side);
    assert.equal(listenerCalledTimes, 3);
  });
});


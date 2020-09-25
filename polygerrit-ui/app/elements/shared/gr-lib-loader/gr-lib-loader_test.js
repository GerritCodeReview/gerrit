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

import '../../../test/common-test-setup-karma.js';
import './gr-lib-loader.js';

const basicFixture = fixtureFromElement('gr-lib-loader');

suite('gr-lib-loader tests', () => {
  let element;
  let resolveLoad;
  let loadStub;

  setup(() => {
    element = basicFixture.instantiate();

    loadStub = sinon.stub(element, '_loadScript').callsFake(() =>
      new Promise(resolve => resolveLoad = resolve)
    );

    // Assert preconditions:
    assert.isFalse(element._hljsState.loading);
  });

  teardown(() => {
    if (window.hljs) {
      delete window.hljs;
    }

    // Because the element state is a singleton, clean it up.
    element._hljsState.configured = false;
    element._hljsState.loading = false;
    element._hljsState.callbacks = [];
  });

  test('only load once', async () => {
    sinon.stub(element, '_getHLJSUrl').returns('');
    const firstCallHandler = sinon.stub();
    element.getHLJS().then(firstCallHandler);

    // It should now be in the loading state.
    assert.isTrue(loadStub.called);
    assert.isTrue(element._hljsState.loading);
    assert.isFalse(firstCallHandler.called);

    const secondCallHandler = sinon.stub();
    element.getHLJS().then(secondCallHandler);

    // No change in state.
    assert.isTrue(element._hljsState.loading);
    assert.isFalse(firstCallHandler.called);
    assert.isFalse(secondCallHandler.called);

    // Now load the library.
    resolveLoad();
    await flush();
    // The state should be loaded and both handlers called.
    assert.isFalse(element._hljsState.loading);
    assert.isTrue(firstCallHandler.called);
    assert.isTrue(secondCallHandler.called);
  });

  suite('preloaded', () => {
    let hljsStub;

    setup(() => {
      hljsStub = {
        configure: sinon.stub(),
      };
      window.hljs = hljsStub;
    });

    teardown(() => {
      delete window.hljs;
    });

    test('returns hljs', async () => {
      const firstCallHandler = sinon.stub();
      element.getHLJS().then(firstCallHandler);
      await flush();
      assert.isTrue(firstCallHandler.called);
      assert.isTrue(firstCallHandler.calledWith(hljsStub));
    });

    test('configures hljs', () => element.getHLJS().then(() => {
      assert.isTrue(window.hljs.configure.calledOnce);
    }));
  });

  suite('_getHLJSUrl', () => {
    suite('checking _getLibRoot', () => {
      let root;

      setup(() => {
        sinon.stub(element, '_getLibRoot').callsFake(() => root);
      });

      test('with no root', () => {
        assert.isNull(element._getHLJSUrl());
      });

      test('with root', () => {
        root = 'test-root.com/';
        assert.equal(element._getHLJSUrl(),
            'test-root.com/bower_components/highlightjs/highlight.min.js');
      });
    });
  });
});

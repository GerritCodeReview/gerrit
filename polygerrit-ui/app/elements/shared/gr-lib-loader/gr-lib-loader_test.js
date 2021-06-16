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
import {GrLibLoader} from './gr-lib-loader.js';

suite('gr-lib-loader tests', () => {
  let grLibLoader;
  let resolveLoad;
  let rejectLoad;
  let loadStub;

  setup(() => {
    grLibLoader = new GrLibLoader();

    loadStub = sinon.stub(grLibLoader, '_loadScript').callsFake(() =>
      new Promise((resolve, reject) => {
        resolveLoad = resolve;
        rejectLoad = reject;
      })
    );
  });

  test('notifies all callers when loaded', async () => {
    const libraryConfig = {src: 'foo.js'};

    const loaded1 = sinon.stub();
    const loaded2 = sinon.stub();

    grLibLoader.getLibrary(libraryConfig).then(loaded1);
    grLibLoader.getLibrary(libraryConfig).then(loaded2);

    resolveLoad();
    await flush();

    const lateLoaded = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

    await flush();

    assert.isTrue(loaded1.calledOnce);
    assert.isTrue(loaded2.calledOnce);
    assert.isTrue(lateLoaded.calledOnce);
  });

  test('notifies all callers when failed', async () => {
    const libraryConfig = {src: 'foo.js'};

    const failed1 = sinon.stub();
    const failed2 = sinon.stub();

    grLibLoader.getLibrary(libraryConfig).catch(failed1);
    grLibLoader.getLibrary(libraryConfig).catch(failed2);

    rejectLoad();
    await flush();

    const lateFailed = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).catch(lateFailed);

    await flush();

    assert.isTrue(failed1.calledOnce);
    assert.isTrue(failed2.calledOnce);
    assert.isTrue(lateFailed.calledOnce);
  });

  test('runs library configuration only once', async () => {
    const configureCallback = sinon.stub();
    const libraryConfig = {
      src: 'foo.js',
      configureCallback,
    };

    const loaded1 = sinon.stub();
    const loaded2 = sinon.stub();

    grLibLoader.getLibrary(libraryConfig).then(loaded1);
    grLibLoader.getLibrary(libraryConfig).then(loaded2);

    resolveLoad();
    await flush();

    const lateLoaded = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

    await flush();

    assert.isTrue(configureCallback.calledOnce);
  });

  test('resolves to result of configureCallback, if any', async () => {
    const library = {someFunction: () => 'foobar'};

    const libraryConfig = {
      src: 'foo.js',
      configureCallback: () => window.library,
    };

    const loaded1 = sinon.stub();
    const loaded2 = sinon.stub();

    grLibLoader.getLibrary(libraryConfig).then(loaded1);
    grLibLoader.getLibrary(libraryConfig).then(loaded2);

    window.library = library;
    resolveLoad();
    await flush();

    assert.isTrue(loaded1.calledWith(library));
    assert.isTrue(loaded2.calledWith(library));

    const lateLoaded = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

    await flush();

    assert.isTrue(lateLoaded.calledWith(library));
  });

  suite('preloaded', () => {
    setup(() => {
      window.library = {
        initialize: sinon.stub(),
      };
    });

    teardown(() => {
      delete window.library;
    });

    test('does not load library again if detected present', async () => {
      const libraryConfig = {
        src: 'foo.js',
        checkPresent: () => window.library !== undefined,
      };

      const loaded1 = sinon.stub();
      const loaded2 = sinon.stub();

      grLibLoader.getLibrary(libraryConfig).then(loaded1);
      grLibLoader.getLibrary(libraryConfig).then(loaded2);

      resolveLoad();
      await flush();

      const lateLoaded = sinon.stub();
      grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

      await flush();

      assert.isFalse(loadStub.called);
      assert.isTrue(loaded1.called);
      assert.isTrue(loaded2.called);
      assert.isTrue(lateLoaded.called);
    });

    test('runs configuration for externally loaded library', async () => {
      const libraryConfig = {
        src: 'foo.js',
        checkPresent: () => window.library !== undefined,
        configureCallback: () => window.library.initialize(),
      };

      grLibLoader.getLibrary(libraryConfig);

      resolveLoad();
      await flush();

      assert.isTrue(window.library.initialize.calledOnce);
    });

    test('loads library again if not detected present', async () => {
      window.library = undefined;
      const libraryConfig = {
        src: 'foo.js',
        checkPresent: () => window.library !== undefined,
      };

      grLibLoader.getLibrary(libraryConfig);

      resolveLoad();
      await flush();

      assert.isTrue(loadStub.called);
    });
  });
});

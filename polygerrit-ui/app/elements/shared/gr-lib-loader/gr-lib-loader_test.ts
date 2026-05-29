/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {waitEventLoop} from '../../../test/test-utils';
import './gr-lib-loader';
import {GrLibLoader} from './gr-lib-loader';

suite('gr-lib-loader tests', () => {
  let grLibLoader: GrLibLoader;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let resolveLoad: any;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let rejectLoad: any;
  let loadStub: sinon.SinonStub;

  setup(() => {
    grLibLoader = new GrLibLoader();

    loadStub = sinon.stub(grLibLoader, '_loadScript').callsFake(
      () =>
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
    await waitEventLoop();

    const lateLoaded = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

    await waitEventLoop();

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
    await waitEventLoop();

    const lateFailed = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).catch(lateFailed);

    await waitEventLoop();

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
    await waitEventLoop();

    const lateLoaded = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

    await waitEventLoop();

    assert.isTrue(configureCallback.calledOnce);
  });

  test('resolves to result of configureCallback, if any', async () => {
    const library = {someFunction: () => 'foobar'};

    const libraryConfig = {
      src: 'foo.js',
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      configureCallback: () => (window as any).library,
    };

    const loaded1 = sinon.stub();
    const loaded2 = sinon.stub();

    grLibLoader.getLibrary(libraryConfig).then(loaded1);
    grLibLoader.getLibrary(libraryConfig).then(loaded2);

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (window as any).library = library;
    resolveLoad();
    await waitEventLoop();

    assert.isTrue(loaded1.calledWith(library));
    assert.isTrue(loaded2.calledWith(library));

    const lateLoaded = sinon.stub();
    grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

    await waitEventLoop();

    assert.isTrue(lateLoaded.calledWith(library));
  });

  suite('preloaded', () => {
    setup(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).library = {
        initialize: sinon.stub(),
      };
    });

    teardown(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      delete (window as any).library;
    });

    test('does not load library again if detected present', async () => {
      const libraryConfig = {
        src: 'foo.js',
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        checkPresent: () => (window as any).library !== undefined,
      };

      const loaded1 = sinon.stub();
      const loaded2 = sinon.stub();

      grLibLoader.getLibrary(libraryConfig).then(loaded1);
      grLibLoader.getLibrary(libraryConfig).then(loaded2);

      resolveLoad();
      await waitEventLoop();

      const lateLoaded = sinon.stub();
      grLibLoader.getLibrary(libraryConfig).then(lateLoaded);

      await waitEventLoop();

      assert.isFalse(loadStub.called);
      assert.isTrue(loaded1.called);
      assert.isTrue(loaded2.called);
      assert.isTrue(lateLoaded.called);
    });

    test('runs configuration for externally loaded library', async () => {
      const libraryConfig = {
        src: 'foo.js',
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        checkPresent: () => (window as any).library !== undefined,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        configureCallback: () => (window as any).library.initialize(),
      };

      grLibLoader.getLibrary(libraryConfig);

      resolveLoad();
      await waitEventLoop();

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      assert.isTrue((window as any).library.initialize.calledOnce);
    });

    test('loads library again if not detected present', async () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).library = undefined;
      const libraryConfig = {
        src: 'foo.js',
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        checkPresent: () => (window as any).library !== undefined,
      };

      grLibLoader.getLibrary(libraryConfig);

      resolveLoad();
      await waitEventLoop();

      assert.isTrue(loadStub.called);
    });
  });
});

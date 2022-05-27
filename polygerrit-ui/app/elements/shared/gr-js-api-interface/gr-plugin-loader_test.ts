/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import {PLUGIN_LOADING_TIMEOUT_MS} from './gr-api-utils';
import {PluginLoader, _testOnly_resetPluginLoader} from './gr-plugin-loader';
import {resetPlugins, stubBaseUrl} from '../../../test/test-utils';
import {addListenerForTest, stubRestApi} from '../../../test/test-utils';
import {PluginApi} from '../../../api/plugin';
import {SinonFakeTimers} from 'sinon';
import {Timestamp} from '../../../api/rest-api';

suite('gr-plugin-loader tests', () => {
  let plugin: PluginApi;

  let url: string;
  let pluginLoader: PluginLoader;
  let clock: SinonFakeTimers;
  let bodyStub: sinon.SinonStub;

  setup(() => {
    clock = sinon.useFakeTimers();

    stubRestApi('getAccount').returns(
      Promise.resolve({name: 'Judy Hopps', registered_on: '' as Timestamp})
    );
    stubRestApi('send').returns(
      Promise.resolve({...new Response(), status: 200})
    );
    pluginLoader = _testOnly_resetPluginLoader();
    bodyStub = sinon.stub(document.body, 'appendChild');
    url = window.location.origin;
  });

  teardown(() => {
    clock.restore();
    resetPlugins();
  });

  test('reuse plugin for install calls', () => {
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );

    let otherPlugin;
    window.Gerrit.install(
      p => {
        otherPlugin = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    assert.strictEqual(plugin, otherPlugin);
  });

  test('versioning', () => {
    const callback = sinon.spy();
    window.Gerrit.install(callback, '0.0pre-alpha');
    assert(callback.notCalled);
  });

  test('report pluginsLoaded', async () => {
    const pluginsLoadedStub = sinon.stub(
      pluginLoader._getReporting(),
      'pluginsLoaded'
    );
    pluginsLoadedStub.reset();
    (window.Gerrit as any)._loadPlugins([]);
    await flush();
    assert.isTrue(pluginsLoadedStub.called);
  });

  test('arePluginsLoaded', () => {
    assert.isFalse(pluginLoader.arePluginsLoaded());
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    pluginLoader.loadPlugins(plugins);
    assert.isFalse(pluginLoader.arePluginsLoaded());
    // Timeout on loading plugins
    clock.tick(PLUGIN_LOADING_TIMEOUT_MS * 2);

    flush();
    assert.isTrue(pluginLoader.arePluginsLoaded());
  });

  test('plugins installed successfully', async () => {
    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(() => void 0, undefined, url);
    });
    const pluginsLoadedStub = sinon.stub(
      pluginLoader._getReporting(),
      'pluginsLoaded'
    );

    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];
    pluginLoader.loadPlugins(plugins);

    await flush();
    assert.isTrue(pluginsLoadedStub.calledWithExactly(['foo', 'bar']));
    assert.isTrue(pluginLoader.arePluginsLoaded());
  });

  test('isPluginEnabled and isPluginLoaded', () => {
    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(() => void 0, undefined, url);
    });

    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
      'bar/static/test.js',
    ];
    pluginLoader.loadPlugins(plugins);
    assert.isTrue(
      plugins.every(plugin => pluginLoader.isPluginEnabled(plugin))
    );

    flush();
    assert.isTrue(pluginLoader.arePluginsLoaded());
    assert.isTrue(plugins.every(plugin => pluginLoader.isPluginLoaded(plugin)));
  });

  test('plugins installed mixed result, 1 fail 1 succeed', async () => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sinon.stub();
    addListenerForTest(document, 'show-alert', alertStub);

    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(
        () => {
          if (url === plugins[0]) {
            throw new Error('failed');
          }
        },
        undefined,
        url
      );
    });

    const pluginsLoadedStub = sinon.stub(
      pluginLoader._getReporting(),
      'pluginsLoaded'
    );

    pluginLoader.loadPlugins(plugins);

    await flush();
    assert.isTrue(pluginsLoadedStub.calledWithExactly(['bar']));
    assert.isTrue(pluginLoader.arePluginsLoaded());
    assert.isTrue(alertStub.calledOnce);
  });

  test('isPluginEnabled and isPluginLoaded for mixed results', async () => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sinon.stub();
    addListenerForTest(document, 'show-alert', alertStub);

    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(
        () => {
          if (url === plugins[0]) {
            throw new Error('failed');
          }
        },
        undefined,
        url
      );
    });

    const pluginsLoadedStub = sinon.stub(
      pluginLoader._getReporting(),
      'pluginsLoaded'
    );

    pluginLoader.loadPlugins(plugins);
    assert.isTrue(
      plugins.every(plugin => pluginLoader.isPluginEnabled(plugin))
    );

    await flush();
    assert.isTrue(pluginsLoadedStub.calledWithExactly(['bar']));
    assert.isTrue(pluginLoader.arePluginsLoaded());
    assert.isTrue(alertStub.calledOnce);
    assert.isTrue(pluginLoader.isPluginLoaded(plugins[1]));
    assert.isFalse(pluginLoader.isPluginLoaded(plugins[0]));
  });

  test('plugins installed all failed', async () => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sinon.stub();
    addListenerForTest(document, 'show-alert', alertStub);

    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(
        () => {
          throw new Error('failed');
        },
        undefined,
        url
      );
    });

    const pluginsLoadedStub = sinon.stub(
      pluginLoader._getReporting(),
      'pluginsLoaded'
    );

    pluginLoader.loadPlugins(plugins);

    await flush();
    assert.isTrue(pluginsLoadedStub.calledWithExactly([]));
    assert.isTrue(pluginLoader.arePluginsLoaded());
    assert.isTrue(alertStub.calledTwice);
  });

  test('plugins installed failed because of wrong version', async () => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sinon.stub();
    addListenerForTest(document, 'show-alert', alertStub);

    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(() => {}, url === plugins[0] ? '' : 'alpha', url);
    });

    const pluginsLoadedStub = sinon.stub(
      pluginLoader._getReporting(),
      'pluginsLoaded'
    );

    pluginLoader.loadPlugins(plugins);

    await flush();
    assert.isTrue(pluginsLoadedStub.calledWithExactly(['foo']));
    assert.isTrue(pluginLoader.arePluginsLoaded());
    assert.isTrue(alertStub.calledOnce);
  });

  test('multiple assets for same plugin installed successfully', async () => {
    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(() => void 0, undefined, url);
    });
    const pluginsLoadedStub = sinon.stub(
      pluginLoader._getReporting(),
      'pluginsLoaded'
    );

    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/foo/static/test2.js',
      'http://test.com/plugins/bar/static/test.js',
    ];
    pluginLoader.loadPlugins(plugins);

    await flush();
    assert.isTrue(pluginsLoadedStub.calledWithExactly(['foo', 'bar']));
    assert.isTrue(pluginLoader.arePluginsLoaded());
  });

  suite('plugin path and url', () => {
    let loadJsPluginStub: sinon.SinonStub;
    setup(() => {
      loadJsPluginStub = sinon.stub();
      sinon
        .stub(pluginLoader, '_createScriptTag')
        .callsFake((url: string, _onerror?: OnErrorEventHandler | undefined) =>
          loadJsPluginStub(url)
        );
    });

    test('invalid plugin path', () => {
      const failToLoadStub = sinon.stub();
      sinon.stub(pluginLoader, '_failToLoad').callsFake((...args) => {
        failToLoadStub(...args);
      });

      pluginLoader.loadPlugins(['foo/bar']);

      assert.isTrue(failToLoadStub.calledOnce);
      assert.isTrue(
        failToLoadStub.calledWithExactly(
          'Unrecognized plugin path foo/bar',
          'foo/bar'
        )
      );
    });

    test('relative path for plugins', () => {
      pluginLoader.loadPlugins(['foo/bar.js']);

      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(loadJsPluginStub.calledWithExactly(`${url}/foo/bar.js`));
    });

    test('relative path should honor getBaseUrl', () => {
      const testUrl = '/test';
      stubBaseUrl(testUrl);

      pluginLoader.loadPlugins(['foo/bar.js']);

      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
        loadJsPluginStub.calledWithExactly(`${url}${testUrl}/foo/bar.js`)
      );
    });

    test('absolute path for plugins', () => {
      pluginLoader.loadPlugins(['http://e.com/foo/bar.js']);

      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
        loadJsPluginStub.calledWithExactly('http://e.com/foo/bar.js')
      );
    });
  });

  suite('With ASSETS_PATH', () => {
    let loadJsPluginStub: sinon.SinonStub;
    setup(() => {
      window.ASSETS_PATH = 'https://cdn.com';
      loadJsPluginStub = sinon.stub();
      sinon
        .stub(pluginLoader, '_createScriptTag')
        .callsFake((url: string, _onerror?: OnErrorEventHandler | undefined) =>
          loadJsPluginStub(url)
        );
    });

    teardown(() => {
      window.ASSETS_PATH = '';
    });

    test('Should try load plugins from assets path instead', () => {
      pluginLoader.loadPlugins(['foo/bar.js']);

      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
        loadJsPluginStub.calledWithExactly('https://cdn.com/foo/bar.js')
      );
    });

    test('Should honor original path if exists', () => {
      pluginLoader.loadPlugins(['http://e.com/foo/bar.js']);

      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
        loadJsPluginStub.calledWithExactly('http://e.com/foo/bar.js')
      );
    });

    test('Should try replace current host with assetsPath', () => {
      const host = window.location.origin;
      pluginLoader.loadPlugins([`${host}/foo/bar.js`]);

      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
        loadJsPluginStub.calledWithExactly('https://cdn.com/foo/bar.js')
      );
    });
  });

  test('adds js plugins will call the body', () => {
    pluginLoader.loadPlugins([
      'http://e.com/foo/bar.js',
      'http://e.com/bar/foo.js',
    ]);
    assert.isTrue(bodyStub.calledTwice);
  });

  test('can call awaitPluginsLoaded multiple times', async () => {
    const plugins = ['http://e.com/foo/bar.js', 'http://e.com/bar/foo.js'];

    let installed = false;
    function pluginCallback(url: string) {
      if (url === plugins[1]) {
        installed = true;
      }
    }
    sinon.stub(pluginLoader, '_loadJsPlugin').callsFake(url => {
      window.Gerrit.install(() => pluginCallback(url), undefined, url);
    });

    pluginLoader.loadPlugins(plugins);

    await pluginLoader.awaitPluginsLoaded();
    assert.isTrue(installed);
    await pluginLoader.awaitPluginsLoaded();
  });
});

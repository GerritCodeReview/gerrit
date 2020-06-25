
/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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


<meta charset="utf-8">







<test-fixture id="basic">
  <template>
    <gr-js-api-interface></gr-js-api-interface>
  </template>
</test-fixture>


import '../../../test/common-test-setup.js';
import './gr-js-api-interface.js';
import {BaseUrlBehavior} from '../../../behaviors/base-url-behavior/base-url-behavior.js';
import {PRELOADED_PROTOCOL, PLUGIN_LOADING_TIMEOUT_MS} from './gr-api-utils.js';
import {pluginLoader} from './gr-plugin-loader.js';
import {resetPlugins} from '../../../test/test-utils.js';
import {_testOnly_flushPreinstalls} from './gr-gerrit.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-plugin-loader tests', () => {
  let plugin;
  let sandbox;
  let url;
  let sendStub;

  setup(() => {
    window.clock = sinon.useFakeTimers();
    sandbox = sinon.sandbox.create();
    sendStub = sandbox.stub().returns(Promise.resolve({status: 200}));
    stub('gr-rest-api-interface', {
      getAccount() {
        return Promise.resolve({name: 'Judy Hopps'});
      },
      send(...args) {
        return sendStub(...args);
      },
    });
    sandbox.stub(document.body, 'appendChild');
    fixture('basic');
    url = window.location.origin;
  });

  teardown(() => {
    sandbox.restore();
    window.clock.restore();
    resetPlugins();
  });

  test('reuse plugin for install calls', () => {
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');

    let otherPlugin;
    pluginApi.install(p => { otherPlugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    assert.strictEqual(plugin, otherPlugin);
  });

  test('flushes preinstalls if provided', () => {
    assert.doesNotThrow(() => {
      _testOnly_flushPreinstalls();
    });
    window.Gerrit.flushPreinstalls = sandbox.stub();
    _testOnly_flushPreinstalls();
    assert.isTrue(window.Gerrit.flushPreinstalls.calledOnce);
    delete window.Gerrit.flushPreinstalls;
  });

  test('versioning', () => {
    const callback = sandbox.spy();
    pluginApi.install(callback, '0.0pre-alpha');
    assert(callback.notCalled);
  });

  test('report pluginsLoaded', done => {
    const pluginsLoadedStub = sandbox.stub(pluginLoader._getReporting(),
        'pluginsLoaded');
    pluginsLoadedStub.reset();
    window.Gerrit._loadPlugins([]);
    flush(() => {
      assert.isTrue(pluginsLoadedStub.calledOnce);
      done();
    });
  });

  test('arePluginsLoaded', done => {
    assert.isFalse(pluginLoader.arePluginsLoaded());
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    pluginLoader.loadPlugins(plugins);
    assert.isFalse(pluginLoader.arePluginsLoaded());
    // Timeout on loading plugins
    window.clock.tick(PLUGIN_LOADING_TIMEOUT_MS * 2);

    flush(() => {
      assert.isTrue(pluginLoader.arePluginsLoaded());
      done();
    });
  });

  test('plugins installed successfully', done => {
    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => void 0, undefined, url);
    });
    const pluginsLoadedStub = sandbox.stub(pluginLoader._getReporting(),
        'pluginsLoaded');

    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];
    pluginLoader.loadPlugins(plugins);

    flush(() => {
      assert.isTrue(pluginsLoadedStub.calledWithExactly(['foo', 'bar']));
      assert.isTrue(pluginLoader.arePluginsLoaded());
      done();
    });
  });

  test('isPluginEnabled and isPluginLoaded', done => {
    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => void 0, undefined, url);
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

    flush(() => {
      assert.isTrue(pluginLoader.arePluginsLoaded());
      assert.isTrue(
          plugins.every(plugin => pluginLoader.isPluginLoaded(plugin))
      );

      done();
    });
  });

  test('plugins installed mixed result, 1 fail 1 succeed', done => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sandbox.stub();
    document.addEventListener('show-alert', alertStub);

    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => {
        if (url === plugins[0]) {
          throw new Error('failed');
        }
      }, undefined, url);
    });

    const pluginsLoadedStub = sandbox.stub(pluginLoader._getReporting(),
        'pluginsLoaded');

    pluginLoader.loadPlugins(plugins);

    flush(() => {
      assert.isTrue(pluginsLoadedStub.calledWithExactly(['bar']));
      assert.isTrue(pluginLoader.arePluginsLoaded());
      assert.isTrue(alertStub.calledOnce);
      done();
    });
  });

  test('isPluginEnabled and isPluginLoaded for mixed results', done => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sandbox.stub();
    document.addEventListener('show-alert', alertStub);

    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => {
        if (url === plugins[0]) {
          throw new Error('failed');
        }
      }, undefined, url);
    });

    const pluginsLoadedStub = sandbox.stub(pluginLoader._getReporting(),
        'pluginsLoaded');

    pluginLoader.loadPlugins(plugins);
    assert.isTrue(
        plugins.every(plugin => pluginLoader.isPluginEnabled(plugin))
    );

    flush(() => {
      assert.isTrue(pluginsLoadedStub.calledWithExactly(['bar']));
      assert.isTrue(pluginLoader.arePluginsLoaded());
      assert.isTrue(alertStub.calledOnce);
      assert.isTrue(pluginLoader.isPluginLoaded(plugins[1]));
      assert.isFalse(pluginLoader.isPluginLoaded(plugins[0]));
      done();
    });
  });

  test('plugins installed all failed', done => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sandbox.stub();
    document.addEventListener('show-alert', alertStub);

    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => {
        throw new Error('failed');
      }, undefined, url);
    });

    const pluginsLoadedStub = sandbox.stub(pluginLoader._getReporting(),
        'pluginsLoaded');

    pluginLoader.loadPlugins(plugins);

    flush(() => {
      assert.isTrue(pluginsLoadedStub.calledWithExactly([]));
      assert.isTrue(pluginLoader.arePluginsLoaded());
      assert.isTrue(alertStub.calledTwice);
      done();
    });
  });

  test('plugins installed failed becasue of wrong version', done => {
    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/bar/static/test.js',
    ];

    const alertStub = sandbox.stub();
    document.addEventListener('show-alert', alertStub);

    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => {
      }, url === plugins[0] ? '' : 'alpha', url);
    });

    const pluginsLoadedStub = sandbox.stub(pluginLoader._getReporting(),
        'pluginsLoaded');

    pluginLoader.loadPlugins(plugins);

    flush(() => {
      assert.isTrue(pluginsLoadedStub.calledWithExactly(['foo']));
      assert.isTrue(pluginLoader.arePluginsLoaded());
      assert.isTrue(alertStub.calledOnce);
      done();
    });
  });

  test('multiple assets for same plugin installed successfully', done => {
    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => void 0, undefined, url);
    });
    const pluginsLoadedStub = sandbox.stub(pluginLoader._getReporting(),
        'pluginsLoaded');

    const plugins = [
      'http://test.com/plugins/foo/static/test.js',
      'http://test.com/plugins/foo/static/test2.js',
      'http://test.com/plugins/bar/static/test.js',
    ];
    pluginLoader.loadPlugins(plugins);

    flush(() => {
      assert.isTrue(pluginsLoadedStub.calledWithExactly(['foo', 'bar']));
      assert.isTrue(pluginLoader.arePluginsLoaded());
      done();
    });
  });

  suite('plugin path and url', () => {
    let importHtmlPluginStub;
    let loadJsPluginStub;
    setup(() => {
      importHtmlPluginStub = sandbox.stub();
      sandbox.stub(pluginLoader, '_loadHtmlPlugin', url => {
        importHtmlPluginStub(url);
      });
      loadJsPluginStub = sandbox.stub();
      sandbox.stub(pluginLoader, '_createScriptTag', url => {
        loadJsPluginStub(url);
      });
    });

    test('invalid plugin path', () => {
      const failToLoadStub = sandbox.stub();
      sandbox.stub(pluginLoader, '_failToLoad', (...args) => {
        failToLoadStub(...args);
      });

      pluginLoader.loadPlugins([
        'foo/bar',
      ]);

      assert.isTrue(failToLoadStub.calledOnce);
      assert.isTrue(failToLoadStub.calledWithExactly(
          'Unrecognized plugin path foo/bar',
          'foo/bar'
      ));
    });

    test('relative path for plugins', () => {
      pluginLoader.loadPlugins([
        'foo/bar.js',
        'foo/bar.html',
      ]);

      assert.isTrue(importHtmlPluginStub.calledOnce);
      assert.isTrue(
          importHtmlPluginStub.calledWithExactly(`${url}/foo/bar.html`)
      );
      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
          loadJsPluginStub.calledWithExactly(`${url}/foo/bar.js`)
      );
    });

    test('relative path should honor getBaseUrl', () => {
      const testUrl = '/test';
      sandbox.stub(BaseUrlBehavior, 'getBaseUrl', () => testUrl);

      pluginLoader.loadPlugins([
        'foo/bar.js',
        'foo/bar.html',
      ]);

      assert.isTrue(importHtmlPluginStub.calledOnce);
      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
          importHtmlPluginStub.calledWithExactly(
              `${url}${testUrl}/foo/bar.html`
          )
      );
      assert.isTrue(
          loadJsPluginStub.calledWithExactly(`${url}${testUrl}/foo/bar.js`)
      );
    });

    test('absolute path for plugins', () => {
      pluginLoader.loadPlugins([
        'http://e.com/foo/bar.js',
        'http://e.com/foo/bar.html',
      ]);

      assert.isTrue(importHtmlPluginStub.calledOnce);
      assert.isTrue(
          importHtmlPluginStub.calledWithExactly(`http://e.com/foo/bar.html`)
      );
      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
          loadJsPluginStub.calledWithExactly(`http://e.com/foo/bar.js`)
      );
    });
  });

  suite('With ASSETS_PATH', () => {
    let importHtmlPluginStub;
    let loadJsPluginStub;
    setup(() => {
      window.ASSETS_PATH = 'https://cdn.com';
      importHtmlPluginStub = sandbox.stub();
      sandbox.stub(pluginLoader, '_loadHtmlPlugin', url => {
        importHtmlPluginStub(url);
      });
      loadJsPluginStub = sandbox.stub();
      sandbox.stub(pluginLoader, '_createScriptTag', url => {
        loadJsPluginStub(url);
      });
    });

    teardown(() => {
      window.ASSETS_PATH = '';
    });

    test('Should try load plugins from assets path instead', () => {
      pluginLoader.loadPlugins([
        'foo/bar.js',
        'foo/bar.html',
      ]);

      assert.isTrue(importHtmlPluginStub.calledOnce);
      assert.isTrue(
          importHtmlPluginStub.calledWithExactly(`https://cdn.com/foo/bar.html`)
      );
      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
          loadJsPluginStub.calledWithExactly(`https://cdn.com/foo/bar.js`));
    });

    test('Should honor original path if exists', () => {
      pluginLoader.loadPlugins([
        'http://e.com/foo/bar.html',
        'http://e.com/foo/bar.js',
      ]);

      assert.isTrue(importHtmlPluginStub.calledOnce);
      assert.isTrue(
          importHtmlPluginStub.calledWithExactly(`http://e.com/foo/bar.html`)
      );
      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
          loadJsPluginStub.calledWithExactly(`http://e.com/foo/bar.js`));
    });

    test('Should try replace current host with assetsPath', () => {
      const host = window.location.origin;
      pluginLoader.loadPlugins([
        `${host}/foo/bar.html`,
        `${host}/foo/bar.js`,
      ]);

      assert.isTrue(importHtmlPluginStub.calledOnce);
      assert.isTrue(
          importHtmlPluginStub.calledWithExactly(`https://cdn.com/foo/bar.html`)
      );
      assert.isTrue(loadJsPluginStub.calledOnce);
      assert.isTrue(
          loadJsPluginStub.calledWithExactly(`https://cdn.com/foo/bar.js`));
    });
  });

  test('adds js plugins will call the body', () => {
    pluginLoader.loadPlugins([
      'http://e.com/foo/bar.js',
      'http://e.com/bar/foo.js',
    ]);
    assert.isTrue(document.body.appendChild.calledTwice);
  });

  test('can call awaitPluginsLoaded multiple times', done => {
    const plugins = [
      'http://e.com/foo/bar.js',
      'http://e.com/bar/foo.js',
    ];

    let installed = false;
    function pluginCallback(url) {
      if (url === plugins[1]) {
        installed = true;
      }
    }
    sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
      pluginApi.install(() => pluginCallback(url), undefined, url);
    });

    pluginLoader.loadPlugins(plugins);

    pluginLoader.awaitPluginsLoaded().then(() => {
      assert.isTrue(installed);

      pluginLoader.awaitPluginsLoaded().then(() => {
        done();
      });
    });
  });

  suite('preloaded plugins', () => {
    test('skips preloaded plugins when load plugins', () => {
      const importHtmlPluginStub = sandbox.stub();
      sandbox.stub(pluginLoader, '_importHtmlPlugin', url => {
        importHtmlPluginStub(url);
      });
      const loadJsPluginStub = sandbox.stub();
      sandbox.stub(pluginLoader, '_loadJsPlugin', url => {
        loadJsPluginStub(url);
      });

      window.Gerrit._preloadedPlugins = {
        foo: () => void 0,
        bar: () => void 0,
      };

      pluginLoader.loadPlugins([
        'http://e.com/plugins/foo.js',
        'plugins/bar.html',
        'http://e.com/plugins/test/foo.js',
      ]);

      assert.isTrue(importHtmlPluginStub.notCalled);
      assert.isTrue(loadJsPluginStub.calledOnce);
    });

    test('isPluginPreloaded', () => {
      window.Gerrit._preloadedPlugins = {baz: ()=>{}};
      assert.isFalse(pluginLoader.isPluginPreloaded('plugins/foo/bar'));
      assert.isFalse(pluginLoader.isPluginPreloaded('http://a.com/42'));
      assert.isTrue(
          pluginLoader.isPluginPreloaded(PRELOADED_PROTOCOL + 'baz')
      );
      window.Gerrit._preloadedPlugins = null;
    });

    test('preloaded plugins are installed', () => {
      const installStub = sandbox.stub();
      window.Gerrit._preloadedPlugins = {foo: installStub};
      pluginLoader.installPreloadedPlugins();
      assert.isTrue(installStub.called);
      const pluginApi = installStub.lastCall.args[0];
      assert.strictEqual(pluginApi.getPluginName(), 'foo');
    });

    test('installing preloaded plugin', () => {
      let plugin;
      pluginApi.install(p => { plugin = p; }, '0.1', 'preloaded:foo');
      assert.strictEqual(plugin.getPluginName(), 'foo');
      assert.strictEqual(plugin.url('/some/thing.html'),
          `${window.location.origin}/plugins/foo/some/thing.html`);
    });
  });
});


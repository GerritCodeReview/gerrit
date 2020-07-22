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
import './gr-js-api-interface.js';
import {GrPopupInterface} from '../../plugins/gr-popup-interface/gr-popup-interface.js';
import {GrSettingsApi} from '../../plugins/gr-settings-api/gr-settings-api.js';
import {GrPluginActionContext} from './gr-plugin-action-context.js';
import {PLUGIN_LOADING_TIMEOUT_MS} from './gr-api-utils.js';
import {pluginLoader} from './gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';
import {stubBaseUrl} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-js-api-interface');

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-js-api-interface tests', () => {
  let element;
  let plugin;
  let errorStub;

  let getResponseObjectStub;
  let sendStub;

  const throwErrFn = function() {
    throw Error('Unfortunately, this handler has stopped');
  };

  setup(() => {
    window.clock = sinon.useFakeTimers();

    getResponseObjectStub = sinon.stub().returns(Promise.resolve());
    sendStub = sinon.stub().returns(Promise.resolve({status: 200}));
    stub('gr-rest-api-interface', {
      getAccount() {
        return Promise.resolve({name: 'Judy Hopps'});
      },
      getResponseObject: getResponseObjectStub,
      send(...args) {
        return sendStub(...args);
      },
    });
    element = basicFixture.instantiate();
    errorStub = sinon.stub(console, 'error');
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    pluginLoader.loadPlugins([]);
  });

  teardown(() => {
    window.clock.restore();
    element._removeEventCallbacks();
    plugin = null;
  });

  test('url', () => {
    assert.equal(plugin.url(), 'http://test.com/plugins/testplugin/');
    assert.equal(plugin.url('/static/test.js'),
        'http://test.com/plugins/testplugin/static/test.js');
  });

  test('url for preloaded plugin without ASSETS_PATH', () => {
    let plugin;
    pluginApi.install(p => { plugin = p; }, '0.1',
        'preloaded:testpluginB');
    assert.equal(plugin.url(),
        `${window.location.origin}/plugins/testpluginB/`);
    assert.equal(plugin.url('/static/test.js'),
        `${window.location.origin}/plugins/testpluginB/static/test.js`);
  });

  test('url for preloaded plugin without ASSETS_PATH', () => {
    const oldAssetsPath = window.ASSETS_PATH;
    window.ASSETS_PATH = 'http://test.com';
    let plugin;
    pluginApi.install(p => { plugin = p; }, '0.1',
        'preloaded:testpluginC');
    assert.equal(plugin.url(), `${window.ASSETS_PATH}/plugins/testpluginC/`);
    assert.equal(plugin.url('/static/test.js'),
        `${window.ASSETS_PATH}/plugins/testpluginC/static/test.js`);
    window.ASSETS_PATH = oldAssetsPath;
  });

  test('_send on failure rejects with response text', () => {
    sendStub.returns(Promise.resolve(
        {status: 400, text() { return Promise.resolve('text'); }}));
    return plugin._send().catch(r => {
      assert.equal(r.message, 'text');
    });
  });

  test('_send on failure without text rejects with code', () => {
    sendStub.returns(Promise.resolve(
        {status: 400, text() { return Promise.resolve(null); }}));
    return plugin._send().catch(r => {
      assert.equal(r.message, '400');
    });
  });

  test('get', () => {
    const response = {foo: 'foo'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return plugin.get('/url', r => {
      assert.isTrue(sendStub.calledWith(
          'GET', 'http://test.com/plugins/testplugin/url'));
      assert.strictEqual(r, response);
    });
  });

  test('get using Promise', () => {
    const response = {foo: 'foo'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return plugin.get('/url', r => 'rubbish').then(r => {
      assert.isTrue(sendStub.calledWith(
          'GET', 'http://test.com/plugins/testplugin/url'));
      assert.strictEqual(r, response);
    });
  });

  test('post', () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return plugin.post('/url', payload, r => {
      assert.isTrue(sendStub.calledWith(
          'POST', 'http://test.com/plugins/testplugin/url', payload));
      assert.strictEqual(r, response);
    });
  });

  test('put', () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return plugin.put('/url', payload, r => {
      assert.isTrue(sendStub.calledWith(
          'PUT', 'http://test.com/plugins/testplugin/url', payload));
      assert.strictEqual(r, response);
    });
  });

  test('delete works', () => {
    const response = {status: 204};
    sendStub.returns(Promise.resolve(response));
    return plugin.delete('/url', r => {
      assert.equal(sendStub.lastCall.args[0], 'DELETE');
      assert.equal(
          sendStub.lastCall.args[1],
          'http://test.com/plugins/testplugin/url'
      );
      assert.strictEqual(r, response);
    });
  });

  test('delete fails', () => {
    sendStub.returns(Promise.resolve(
        {status: 400, text() { return Promise.resolve('text'); }}));
    return plugin.delete('/url', r => {
      throw new Error('Should not resolve');
    }).catch(err => {
      assert.equal(sendStub.lastCall.args[0], 'DELETE');
      assert.equal(
          sendStub.lastCall.args[1],
          'http://test.com/plugins/testplugin/url'
      );
      assert.equal('text', err.message);
    });
  });

  test('history event', done => {
    plugin.on(element.EventType.HISTORY, throwErrFn);
    plugin.on(element.EventType.HISTORY, path => {
      assert.equal(path, '/path/to/awesomesauce');
      assert.isTrue(errorStub.calledOnce);
      done();
    });
    element.handleEvent(element.EventType.HISTORY,
        {path: '/path/to/awesomesauce'});
  });

  test('showchange event', done => {
    const testChange = {
      _number: 42,
      revisions: {def: {_number: 2}, abc: {_number: 1}},
    };
    const expectedChange = {mergeable: false, ...testChange};
    plugin.on(element.EventType.SHOW_CHANGE, throwErrFn);
    plugin.on(element.EventType.SHOW_CHANGE, (change, revision, info) => {
      assert.deepEqual(change, expectedChange);
      assert.deepEqual(revision, testChange.revisions.abc);
      assert.deepEqual(info, {mergeable: false});
      assert.isTrue(errorStub.calledOnce);
      done();
    });
    element.handleEvent(element.EventType.SHOW_CHANGE,
        {change: testChange, patchNum: 1, info: {mergeable: false}});
  });

  test('show-revision-actions event', done => {
    const testChange = {
      _number: 42,
      revisions: {def: {_number: 2}, abc: {_number: 1}},
    };
    plugin.on(element.EventType.SHOW_REVISION_ACTIONS, throwErrFn);
    plugin.on(element.EventType.SHOW_REVISION_ACTIONS, (actions, change) => {
      assert.deepEqual(change, testChange);
      assert.deepEqual(actions, {test: {}});
      assert.isTrue(errorStub.calledOnce);
      done();
    });
    element.handleEvent(element.EventType.SHOW_REVISION_ACTIONS,
        {change: testChange, revisionActions: {test: {}}});
  });

  test('handleEvent awaits plugins load', done => {
    const testChange = {
      _number: 42,
      revisions: {def: {_number: 2}, abc: {_number: 1}},
    };
    const spy = sinon.spy();
    pluginLoader.loadPlugins(['plugins/test.html']);
    plugin.on(element.EventType.SHOW_CHANGE, spy);
    element.handleEvent(element.EventType.SHOW_CHANGE,
        {change: testChange, patchNum: 1});
    assert.isFalse(spy.called);

    // Timeout on loading plugins
    window.clock.tick(PLUGIN_LOADING_TIMEOUT_MS * 2);

    flush(() => {
      assert.isTrue(spy.called);
      done();
    });
  });

  test('comment event', done => {
    const testCommentNode = {foo: 'bar'};
    plugin.on(element.EventType.COMMENT, throwErrFn);
    plugin.on(element.EventType.COMMENT, commentNode => {
      assert.deepEqual(commentNode, testCommentNode);
      assert.isTrue(errorStub.calledOnce);
      done();
    });
    element.handleEvent(element.EventType.COMMENT, {node: testCommentNode});
  });

  test('revert event', () => {
    function appendToRevertMsg(c, revertMsg, originalMsg) {
      return revertMsg + '\n' + originalMsg.replace(/^/gm, '> ') + '\ninfo';
    }

    assert.equal(element.modifyRevertMsg(null, 'test', 'origTest'), 'test');
    assert.equal(errorStub.callCount, 0);

    plugin.on(element.EventType.REVERT, throwErrFn);
    plugin.on(element.EventType.REVERT, appendToRevertMsg);
    assert.equal(element.modifyRevertMsg(null, 'test', 'origTest'),
        'test\n> origTest\ninfo');
    assert.isTrue(errorStub.calledOnce);

    plugin.on(element.EventType.REVERT, appendToRevertMsg);
    assert.equal(element.modifyRevertMsg(null, 'test', 'origTest'),
        'test\n> origTest\ninfo\n> origTest\ninfo');
    assert.isTrue(errorStub.calledTwice);
  });

  test('postrevert event', () => {
    function getLabels(c) {
      return {'Code-Review': 1};
    }

    assert.deepEqual(element.getLabelValuesPostRevert(null), {});
    assert.equal(errorStub.callCount, 0);

    plugin.on(element.EventType.POST_REVERT, throwErrFn);
    plugin.on(element.EventType.POST_REVERT, getLabels);
    assert.deepEqual(
        element.getLabelValuesPostRevert(null), {'Code-Review': 1});
    assert.isTrue(errorStub.calledOnce);
  });

  test('commitmsgedit event', done => {
    const testMsg = 'Test CL commit message';
    plugin.on(element.EventType.COMMIT_MSG_EDIT, throwErrFn);
    plugin.on(element.EventType.COMMIT_MSG_EDIT, (change, msg) => {
      assert.deepEqual(msg, testMsg);
      assert.isTrue(errorStub.calledOnce);
      done();
    });
    element.handleCommitMessage(null, testMsg);
  });

  test('labelchange event', done => {
    const testChange = {_number: 42};
    plugin.on(element.EventType.LABEL_CHANGE, throwErrFn);
    plugin.on(element.EventType.LABEL_CHANGE, change => {
      assert.deepEqual(change, testChange);
      assert.isTrue(errorStub.calledOnce);
      done();
    });
    element.handleEvent(element.EventType.LABEL_CHANGE, {change: testChange});
  });

  test('submitchange', () => {
    plugin.on(element.EventType.SUBMIT_CHANGE, throwErrFn);
    plugin.on(element.EventType.SUBMIT_CHANGE, () => true);
    assert.isTrue(element.canSubmitChange());
    assert.isTrue(errorStub.calledOnce);
    plugin.on(element.EventType.SUBMIT_CHANGE, () => false);
    plugin.on(element.EventType.SUBMIT_CHANGE, () => true);
    assert.isFalse(element.canSubmitChange());
    assert.isTrue(errorStub.calledTwice);
  });

  test('highlightjs-loaded event', done => {
    const testHljs = {_number: 42};
    plugin.on(element.EventType.HIGHLIGHTJS_LOADED, throwErrFn);
    plugin.on(element.EventType.HIGHLIGHTJS_LOADED, hljs => {
      assert.deepEqual(hljs, testHljs);
      assert.isTrue(errorStub.calledOnce);
      done();
    });
    element.handleEvent(element.EventType.HIGHLIGHTJS_LOADED, {hljs: testHljs});
  });

  test('getLoggedIn', done => {
    // fake fetch for authCheck
    sinon.stub(window, 'fetch').callsFake(() => Promise.resolve({status: 204}));
    plugin.restApi().getLoggedIn()
        .then(loggedIn => {
          assert.isTrue(loggedIn);
          done();
        });
  });

  test('attributeHelper', () => {
    assert.isOk(plugin.attributeHelper());
  });

  test('deprecated.install', () => {
    plugin.deprecated.install();
    assert.strictEqual(plugin.popup, plugin.deprecated.popup);
    assert.strictEqual(plugin.onAction, plugin.deprecated.onAction);
    assert.notStrictEqual(plugin.install, plugin.deprecated.install);
  });

  test('getAdminMenuLinks', () => {
    const links = [{text: 'a', url: 'b'}, {text: 'c', url: 'd'}];
    const getCallbacksStub = sinon.stub(element, '_getEventCallbacks')
        .returns([
          {getMenuLinks: () => [links[0]]},
          {getMenuLinks: () => [links[1]]},
        ]);
    const result = element.getAdminMenuLinks();
    assert.deepEqual(result, links);
    assert.isTrue(getCallbacksStub.calledOnce);
    assert.equal(getCallbacksStub.lastCall.args[0],
        element.EventType.ADMIN_MENU_LINKS);
  });

  suite('test plugin with base url', () => {
    let baseUrlPlugin;

    setup(() => {
      stubBaseUrl('/r');

      pluginApi.install(p => { baseUrlPlugin = p; }, '0.1',
          'http://test.com/r/plugins/baseurlplugin/static/test.js');
    });

    test('url', () => {
      assert.notEqual(baseUrlPlugin.url(),
          'http://test.com/plugins/baseurlplugin/');
      assert.equal(baseUrlPlugin.url(),
          'http://test.com/r/plugins/baseurlplugin/');
      assert.equal(baseUrlPlugin.url('/static/test.js'),
          'http://test.com/r/plugins/baseurlplugin/static/test.js');
    });
  });

  suite('popup', () => {
    test('popup(element) is deprecated', () => {
      plugin.popup(document.createElement('div'));
      assert.isTrue(console.error.calledOnce);
    });

    test('popup(moduleName) creates popup with component', () => {
      const openStub = sinon.stub(GrPopupInterface.prototype, 'open').callsFake(
          function() {
            // Arrow function can't be used here, because we want to
            // get properties from the instance of GrPopupInterface
            // eslint-disable-next-line no-invalid-this
            const grPopupInterface = this;
            assert.equal(grPopupInterface.plugin, plugin);
            assert.equal(grPopupInterface._moduleName, 'some-name');
          });
      plugin.popup('some-name');
      assert.isTrue(openStub.calledOnce);
    });

    test('deprecated.popup(element) creates popup with element', () => {
      const el = document.createElement('div');
      el.textContent = 'some text here';
      const openStub = sinon.stub(GrPopupInterface.prototype, 'open');
      openStub.returns(Promise.resolve({
        _getElement() {
          return document.createElement('div');
        }}));
      plugin.deprecated.popup(el);
      assert.isTrue(openStub.calledOnce);
    });
  });

  suite('onAction', () => {
    let change;
    let revision;
    let actionDetails;

    setup(() => {
      change = {};
      revision = {};
      actionDetails = {__key: 'some'};
      sinon.stub(plugin, 'on').callsArgWith(1, change, revision);
      sinon.stub(plugin, 'changeActions').returns({
        addTapListener: sinon.stub().callsArg(1),
        getActionDetails: () => actionDetails,
      });
    });

    test('returns GrPluginActionContext', () => {
      const stub = sinon.stub();
      plugin.deprecated.onAction('change', 'foo', ctx => {
        assert.isTrue(ctx instanceof GrPluginActionContext);
        assert.strictEqual(ctx.change, change);
        assert.strictEqual(ctx.revision, revision);
        assert.strictEqual(ctx.action, actionDetails);
        assert.strictEqual(ctx.plugin, plugin);
        stub();
      });
      assert.isTrue(stub.called);
    });

    test('other actions', () => {
      const stub = sinon.stub();
      plugin.deprecated.onAction('project', 'foo', stub);
      plugin.deprecated.onAction('edit', 'foo', stub);
      plugin.deprecated.onAction('branch', 'foo', stub);
      assert.isFalse(stub.called);
    });
  });

  suite('screen', () => {
    test('screenUrl()', () => {
      stubBaseUrl('/base');
      assert.equal(
          plugin.screenUrl(),
          `${location.origin}/base/x/testplugin`
      );
      assert.equal(
          plugin.screenUrl('foo'),
          `${location.origin}/base/x/testplugin/foo`
      );
    });

    test('deprecated works', () => {
      const stub = sinon.stub();
      const hookStub = {onAttached: sinon.stub()};
      sinon.stub(plugin, 'hook').returns(hookStub);
      plugin.deprecated.screen('foo', stub);
      assert.isTrue(plugin.hook.calledWith('testplugin-screen-foo'));
      const fakeEl = {style: {display: ''}};
      hookStub.onAttached.callArgWith(0, fakeEl);
      assert.isTrue(stub.called);
      assert.equal(fakeEl.style.display, 'none');
    });

    test('works', () => {
      sinon.stub(plugin, 'registerCustomComponent');
      plugin.screen('foo', 'some-module');
      assert.isTrue(plugin.registerCustomComponent.calledWith(
          'testplugin-screen-foo', 'some-module'));
    });
  });

  suite('panel', () => {
    let fakeEl;
    let emulateAttached;

    setup(()=> {
      fakeEl = {change: {}, revision: {}};
      const hookStub = {onAttached: sinon.stub()};
      sinon.stub(plugin, 'hook').returns(hookStub);
      emulateAttached = () => hookStub.onAttached.callArgWith(0, fakeEl);
    });

    test('plugin.panel is deprecated', () => {
      plugin.panel('rubbish');
      assert.isTrue(console.error.called);
    });

    [
      ['CHANGE_SCREEN_BELOW_COMMIT_INFO_BLOCK', 'change-view-integration'],
      ['CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK', 'change-metadata-item'],
    ].forEach(([panelName, endpointName]) => {
      test(`deprecated.panel works for ${panelName}`, () => {
        const callback = sinon.stub();
        plugin.deprecated.panel(panelName, callback);
        assert.isTrue(plugin.hook.calledWith(endpointName));
        emulateAttached();
        assert.isTrue(callback.called);
        const args = callback.args[0][0];
        assert.strictEqual(args.body, fakeEl);
        assert.strictEqual(args.p.CHANGE_INFO, fakeEl.change);
        assert.strictEqual(args.p.REVISION_INFO, fakeEl.revision);
      });
    });
  });

  suite('settingsScreen', () => {
    test('plugin.settingsScreen is deprecated', () => {
      plugin.settingsScreen('rubbish');
      assert.isTrue(console.error.called);
    });

    test('plugin.settings() returns GrSettingsApi', () => {
      assert.isOk(plugin.settings());
      assert.isTrue(plugin.settings() instanceof GrSettingsApi);
    });

    test('plugin.deprecated.settingsScreen() works', () => {
      const hookStub = {onAttached: sinon.stub()};
      sinon.stub(plugin, 'hook').returns(hookStub);
      const fakeSettings = {};
      fakeSettings.title = sinon.stub().returns(fakeSettings);
      fakeSettings.token = sinon.stub().returns(fakeSettings);
      fakeSettings.module = sinon.stub().returns(fakeSettings);
      fakeSettings.build = sinon.stub().returns(hookStub);
      sinon.stub(plugin, 'settings').returns(fakeSettings);
      const callback = sinon.stub();

      plugin.deprecated.settingsScreen('path', 'menu', callback);
      assert.isTrue(fakeSettings.title.calledWith('menu'));
      assert.isTrue(fakeSettings.token.calledWith('path'));
      assert.isTrue(fakeSettings.module.calledWith('div'));
      assert.equal(fakeSettings.build.callCount, 1);

      const fakeBody = {};
      const fakeEl = {
        style: {
          display: '',
        },
        querySelector: sinon.stub().returns(fakeBody),
      };
      // Emulate settings screen attached
      hookStub.onAttached.callArgWith(0, fakeEl);
      assert.isTrue(callback.called);
      const args = callback.args[0][0];
      assert.strictEqual(args.body, fakeBody);
      assert.equal(fakeEl.style.display, 'none');
    });
  });
});


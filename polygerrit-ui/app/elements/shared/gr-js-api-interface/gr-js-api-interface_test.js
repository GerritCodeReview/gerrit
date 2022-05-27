/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma.js';
import './gr-js-api-interface.js';
import {GrPopupInterface} from '../../plugins/gr-popup-interface/gr-popup-interface.js';
import {EventType} from '../../../api/plugin.js';
import {PLUGIN_LOADING_TIMEOUT_MS} from './gr-api-utils.js';
import {getPluginLoader} from './gr-plugin-loader.js';
import {stubBaseUrl} from '../../../test/test-utils.js';
import {stubRestApi} from '../../../test/test-utils.js';
import {getAppContext} from '../../../services/app-context.js';

suite('GrJsApiInterface tests', () => {
  let element;
  let plugin;
  let errorStub;

  let sendStub;
  let clock;

  const throwErrFn = function() {
    throw Error('Unfortunately, this handler has stopped');
  };

  setup(() => {
    clock = sinon.useFakeTimers();

    stubRestApi('getAccount').returns(Promise.resolve({name: 'Judy Hopps'}));
    sendStub = stubRestApi('send').returns(Promise.resolve({status: 200}));
    element = getAppContext().jsApiService;
    errorStub = sinon.stub(element.reporting, 'error');
    window.Gerrit.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    getPluginLoader().loadPlugins([]);
  });

  teardown(() => {
    clock.restore();
    element._removeEventCallbacks();
    plugin = null;
  });

  test('url', () => {
    assert.equal(plugin.url(), 'http://test.com/plugins/testplugin/');
    assert.equal(plugin.url('/static/test.js'),
        'http://test.com/plugins/testplugin/static/test.js');
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

  test('history event', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    plugin.on(EventType.HISTORY, throwErrFn);
    plugin.on(EventType.HISTORY, resolve);
    element.handleEvent(EventType.HISTORY, {path: '/path/to/awesomesauce'});
    const path = await promise;
    assert.equal(path, '/path/to/awesomesauce');
    assert.isTrue(errorStub.calledOnce);
  });

  test('showchange event', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    const testChange = {
      _number: 42,
      revisions: {def: {_number: 2}, abc: {_number: 1}},
    };
    const expectedChange = {mergeable: false, ...testChange};
    plugin.on(EventType.SHOW_CHANGE, throwErrFn);
    plugin.on(EventType.SHOW_CHANGE, (change, revision, info) => {
      resolve({change, revision, info});
    });
    element.handleEvent(EventType.SHOW_CHANGE,
        {change: testChange, patchNum: 1, info: {mergeable: false}});

    const {change, revision, info} = await promise;
    assert.deepEqual(change, expectedChange);
    assert.deepEqual(revision, testChange.revisions.abc);
    assert.deepEqual(info, {mergeable: false});
    assert.isTrue(errorStub.calledOnce);
  });

  test('show-revision-actions event', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    const testChange = {
      _number: 42,
      revisions: {def: {_number: 2}, abc: {_number: 1}},
    };
    plugin.on(EventType.SHOW_REVISION_ACTIONS, throwErrFn);
    plugin.on(EventType.SHOW_REVISION_ACTIONS, (actions, change) => {
      resolve({change, actions});
    });
    element.handleEvent(EventType.SHOW_REVISION_ACTIONS,
        {change: testChange, revisionActions: {test: {}}});

    const {change, actions} = await promise;
    assert.deepEqual(change, testChange);
    assert.deepEqual(actions, {test: {}});
    assert.isTrue(errorStub.calledOnce);
  });

  test('handleEvent awaits plugins load', async () => {
    const testChange = {
      _number: 42,
      revisions: {def: {_number: 2}, abc: {_number: 1}},
    };
    const spy = sinon.spy();
    getPluginLoader().loadPlugins(['plugins/test.js']);
    plugin.on(EventType.SHOW_CHANGE, spy);
    element.handleEvent(EventType.SHOW_CHANGE,
        {change: testChange, patchNum: 1});
    assert.isFalse(spy.called);

    // Timeout on loading plugins
    clock.tick(PLUGIN_LOADING_TIMEOUT_MS * 2);

    await flush();
    assert.isTrue(spy.called);
  });

  test('comment event', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    const testCommentNode = {foo: 'bar'};
    plugin.on(EventType.COMMENT, throwErrFn);
    plugin.on(EventType.COMMENT, resolve);
    element.handleEvent(EventType.COMMENT, {node: testCommentNode});

    const commentNode = await promise;
    assert.deepEqual(commentNode, testCommentNode);
    assert.isTrue(errorStub.calledOnce);
  });

  test('revert event', () => {
    function appendToRevertMsg(c, revertMsg, originalMsg) {
      return revertMsg + '\n' + originalMsg.replace(/^/gm, '> ') + '\ninfo';
    }

    assert.equal(element.modifyRevertMsg(null, 'test', 'origTest'), 'test');
    assert.equal(errorStub.callCount, 0);

    plugin.on(EventType.REVERT, throwErrFn);
    plugin.on(EventType.REVERT, appendToRevertMsg);
    assert.equal(element.modifyRevertMsg(null, 'test', 'origTest'),
        'test\n> origTest\ninfo');
    assert.isTrue(errorStub.calledOnce);

    plugin.on(EventType.REVERT, appendToRevertMsg);
    assert.equal(element.modifyRevertMsg(null, 'test', 'origTest'),
        'test\n> origTest\ninfo\n> origTest\ninfo');
    assert.isTrue(errorStub.calledTwice);
  });

  test('postrevert event labels', () => {
    function getLabels(c) {
      return {'Code-Review': 1};
    }

    assert.deepEqual(element.getReviewPostRevert(null), {});
    assert.equal(errorStub.callCount, 0);

    plugin.on(EventType.POST_REVERT, throwErrFn);
    plugin.on(EventType.POST_REVERT, getLabels);
    assert.deepEqual(
        element.getReviewPostRevert(null), {labels: {'Code-Review': 1}});
    assert.isTrue(errorStub.calledOnce);
  });

  test('postrevert event review', () => {
    function getReview(c) {
      return {labels: {'Code-Review': 1}};
    }

    assert.deepEqual(element.getReviewPostRevert(null), {});
    assert.equal(errorStub.callCount, 0);

    plugin.on(EventType.POST_REVERT, throwErrFn);
    plugin.on(EventType.POST_REVERT, getReview);
    assert.deepEqual(
        element.getReviewPostRevert(null), {labels: {'Code-Review': 1}});
    assert.isTrue(errorStub.calledOnce);
  });

  test('commitmsgedit event', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    const testMsg = 'Test CL commit message';
    plugin.on(EventType.COMMIT_MSG_EDIT, throwErrFn);
    plugin.on(EventType.COMMIT_MSG_EDIT, (change, msg) => {
      resolve(msg);
    });
    element.handleCommitMessage(null, testMsg);

    const msg = await promise;
    assert.deepEqual(msg, testMsg);
    assert.isTrue(errorStub.calledOnce);
  });

  test('labelchange event', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    const testChange = {_number: 42};
    plugin.on(EventType.LABEL_CHANGE, throwErrFn);
    plugin.on(EventType.LABEL_CHANGE, resolve);
    element.handleEvent(EventType.LABEL_CHANGE, {change: testChange});

    const change = await promise;
    assert.deepEqual(change, testChange);
    assert.isTrue(errorStub.calledOnce);
  });

  test('submitchange', () => {
    plugin.on(EventType.SUBMIT_CHANGE, throwErrFn);
    plugin.on(EventType.SUBMIT_CHANGE, () => true);
    assert.isTrue(element.canSubmitChange());
    assert.isTrue(errorStub.calledOnce);
    plugin.on(EventType.SUBMIT_CHANGE, () => false);
    plugin.on(EventType.SUBMIT_CHANGE, () => true);
    assert.isFalse(element.canSubmitChange());
    assert.isTrue(errorStub.calledTwice);
  });

  test('highlightjs-loaded event', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    const testHljs = {_number: 42};
    plugin.on(EventType.HIGHLIGHTJS_LOADED, throwErrFn);
    plugin.on(EventType.HIGHLIGHTJS_LOADED, resolve);
    element.handleEvent(EventType.HIGHLIGHTJS_LOADED, {hljs: testHljs});

    const hljs = await promise;
    assert.deepEqual(hljs, testHljs);
    assert.isTrue(errorStub.calledOnce);
  });

  test('getLoggedIn', () => {
    // fake fetch for authCheck
    sinon.stub(window, 'fetch').callsFake(() => Promise.resolve({status: 204}));
    return plugin.restApi().getLoggedIn()
        .then(loggedIn => {
          assert.isTrue(loggedIn);
        });
  });

  test('attributeHelper', () => {
    assert.isOk(plugin.attributeHelper());
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
        EventType.ADMIN_MENU_LINKS);
  });

  suite('test plugin with base url', () => {
    let baseUrlPlugin;

    setup(() => {
      stubBaseUrl('/r');

      window.Gerrit.install(p => { baseUrlPlugin = p; }, '0.1',
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
      sinon.stub(console, 'error');
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
            assert.equal(grPopupInterface.moduleName, 'some-name');
          });
      plugin.popup('some-name');
      assert.isTrue(openStub.calledOnce);
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

    test('works', () => {
      sinon.stub(plugin, 'registerCustomComponent');
      plugin.screen('foo', 'some-module');
      assert.isTrue(plugin.registerCustomComponent.calledWith(
          'testplugin-screen-foo', 'some-module'));
    });
  });
});


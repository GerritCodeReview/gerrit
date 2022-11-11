/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {GrPopupInterface} from '../../plugins/gr-popup-interface/gr-popup-interface';
import {EventType} from '../../../api/plugin';
import {PLUGIN_LOADING_TIMEOUT_MS} from './gr-api-utils';
import {
  stubRestApi,
  stubBaseUrl,
  waitEventLoop,
  waitUntilCalled,
  assertFails,
} from '../../../test/test-utils';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {PluginLoader, pluginLoaderToken} from './gr-plugin-loader';
import {useFakeTimers, stub, SinonFakeTimers, SinonStub} from 'sinon';
import {GrJsApiInterface} from './gr-js-api-interface-element';
import {Plugin} from './gr-public-js-api';
import {
  ChangeInfo,
  HttpMethod,
  NumericChangeId,
  PatchSetNum,
  RevisionPatchSetNum,
  Timestamp,
} from '../../../api/rest-api';
import {ParsedChangeInfo} from '../../../types/types';
import {
  createChange,
  createParsedChange,
  createRevision,
} from '../../../test/test-data-generators';
import {EventCallback} from './gr-js-api-types';

suite('GrJsApiInterface tests', () => {
  let element: GrJsApiInterface;
  let plugin: Plugin;
  let errorStub: SinonStub;
  let pluginLoader: PluginLoader;

  let sendStub: SinonStub;
  let clock: SinonFakeTimers;

  const throwErrFn = function () {
    throw Error('Unfortunately, this handler has stopped');
  };

  setup(() => {
    clock = useFakeTimers();

    stubRestApi('getAccount').resolves({
      name: 'Judy Hopps',
      registered_on: '' as Timestamp,
    });
    sendStub = stubRestApi('send').resolves(
      new Response(undefined, {status: 200})
    );
    pluginLoader = testResolver(pluginLoaderToken);

    // We are using it as the implementation class to better set up tests
    element = pluginLoader.jsApiService as GrJsApiInterface;
    errorStub = stub(element.reporting, 'error');
    pluginLoader.install(
      p => {
        // We are using it as the implementation class to better set up tests
        plugin = p as Plugin;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    testResolver(pluginLoaderToken).loadPlugins([]);
  });

  teardown(() => {
    clock.restore();
    element._removeEventCallbacks();
  });

  test('url', () => {
    assert.equal(plugin.url(), 'http://test.com/plugins/testplugin/');
    assert.equal(
      plugin.url('/static/test.js'),
      'http://test.com/plugins/testplugin/static/test.js'
    );
  });

  test('_send on failure rejects with response text', async () => {
    sendStub.resolves({
      status: 400,
      text() {
        return Promise.resolve('text');
      },
    });
    const error = await assertFails(plugin._send(HttpMethod.POST, ''));
    assert.equal((error as Error).message, 'text');
  });

  test('_send on failure without text rejects with code', async () => {
    sendStub.resolves({
      status: 400,
      text() {
        return Promise.resolve(null);
      },
    });
    const error = await assertFails(plugin._send(HttpMethod.POST, ''));
    assert.equal((error as Error).message, '400');
  });

  test('showchange event', async () => {
    const showChangeStub = stub();
    const testChange: ParsedChangeInfo = {
      ...createParsedChange(),
      _number: 42 as NumericChangeId,
      revisions: {
        def: {...createRevision(), _number: 2 as RevisionPatchSetNum},
        abc: {...createRevision(), _number: 1 as RevisionPatchSetNum},
      },
    };
    const expectedChange = {mergeable: false, ...testChange};

    plugin.on(EventType.SHOW_CHANGE, throwErrFn);
    plugin.on(EventType.SHOW_CHANGE, showChangeStub);
    element.handleShowChange({
      change: testChange,
      patchNum: 1 as PatchSetNum,
      info: {mergeable: false},
    });
    await waitUntilCalled(showChangeStub, 'showChangeStub');

    const [change, revision, info] = showChangeStub.firstCall.args;
    assert.deepEqual(change, expectedChange);
    assert.deepEqual(revision, testChange.revisions.abc);
    assert.deepEqual(info, {mergeable: false});
    assert.isTrue(errorStub.calledOnce);
  });

  test('show-revision-actions event', async () => {
    const showRevisionActionsStub = stub();
    const testChange: ChangeInfo = {
      ...createChange(),
      _number: 42 as NumericChangeId,
      revisions: {
        def: {...createRevision(), _number: 2 as RevisionPatchSetNum},
        abc: {...createRevision(), _number: 1 as RevisionPatchSetNum},
      },
    };

    plugin.on(EventType.SHOW_REVISION_ACTIONS, throwErrFn);
    plugin.on(EventType.SHOW_REVISION_ACTIONS, showRevisionActionsStub);
    element.handleShowRevisionActions({
      change: testChange,
      revisionActions: {test: {}},
    });
    await waitUntilCalled(showRevisionActionsStub, 'showRevisionActionsStub');

    const [actions, change] = showRevisionActionsStub.firstCall.args;
    assert.deepEqual(change, testChange);
    assert.deepEqual(actions, {test: {}});
    assert.isTrue(errorStub.calledOnce);
  });

  test('handleShowChange awaits plugins load', async () => {
    const testChange: ParsedChangeInfo = {
      ...createParsedChange(),
      _number: 42 as NumericChangeId,
      revisions: {
        def: {...createRevision(), _number: 2 as RevisionPatchSetNum},
        abc: {...createRevision(), _number: 1 as RevisionPatchSetNum},
      },
    };
    const showChangeStub = stub();
    testResolver(pluginLoaderToken).loadPlugins(['plugins/test.js']);
    plugin.on(EventType.SHOW_CHANGE, showChangeStub);
    element.handleShowChange({
      change: testChange,
      patchNum: 1 as PatchSetNum,
      info: {mergeable: null},
    });
    assert.isFalse(showChangeStub.called);

    // Timeout on loading plugins
    clock.tick(PLUGIN_LOADING_TIMEOUT_MS * 2);

    await waitEventLoop();
    assert.isTrue(showChangeStub.called);
  });

  test('revert event', () => {
    function appendToRevertMsg(
      _c: unknown,
      revertMsg: string,
      originalMsg: string
    ) {
      return revertMsg + '\n' + originalMsg.replace(/^/gm, '> ') + '\ninfo';
    }
    const change = createChange();

    assert.equal(element.modifyRevertMsg(change, 'test', 'origTest'), 'test');
    assert.equal(errorStub.callCount, 0);

    plugin.on(EventType.REVERT, throwErrFn);
    plugin.on(EventType.REVERT, appendToRevertMsg);
    assert.equal(
      element.modifyRevertMsg(change, 'test', 'origTest'),
      'test\n> origTest\ninfo'
    );
    assert.isTrue(errorStub.calledOnce);

    plugin.on(EventType.REVERT, appendToRevertMsg);
    assert.equal(
      element.modifyRevertMsg(change, 'test', 'origTest'),
      'test\n> origTest\ninfo\n> origTest\ninfo'
    );
    assert.isTrue(errorStub.calledTwice);
  });

  test('postrevert event labels', () => {
    function getLabels(_c: unknown) {
      return {'Code-Review': 1};
    }

    assert.deepEqual(element.getReviewPostRevert(undefined), {});
    assert.equal(errorStub.callCount, 0);

    plugin.on(EventType.POST_REVERT, throwErrFn);
    plugin.on(EventType.POST_REVERT, getLabels);
    assert.deepEqual(element.getReviewPostRevert(undefined), {
      labels: {'Code-Review': 1},
    });
    assert.isTrue(errorStub.calledOnce);
  });

  test('postrevert event review', () => {
    function getReview(_c: unknown) {
      return {labels: {'Code-Review': 1}};
    }

    assert.deepEqual(element.getReviewPostRevert(undefined), {});
    assert.equal(errorStub.callCount, 0);

    plugin.on(EventType.POST_REVERT, throwErrFn);
    plugin.on(EventType.POST_REVERT, getReview);
    assert.deepEqual(element.getReviewPostRevert(undefined), {
      labels: {'Code-Review': 1},
    });
    assert.isTrue(errorStub.calledOnce);
  });

  test('commitmsgedit event', async () => {
    const commitMsgEditStub = stub();
    const testMsg = 'Test CL commit message';

    plugin.on(EventType.COMMIT_MSG_EDIT, throwErrFn);
    plugin.on(EventType.COMMIT_MSG_EDIT, commitMsgEditStub);
    element.handleCommitMessage(createChange(), testMsg);
    await waitUntilCalled(commitMsgEditStub, 'commitMsgEditStub');

    const msg = commitMsgEditStub.firstCall.args[1];
    assert.deepEqual(msg, testMsg);
    assert.isTrue(errorStub.calledOnce);
  });

  test('labelchange event', async () => {
    const labelChangeStub = stub();
    const testChange: ParsedChangeInfo = {
      ...createParsedChange(),
      _number: 42 as NumericChangeId,
    };

    plugin.on(EventType.LABEL_CHANGE, throwErrFn);
    plugin.on(EventType.LABEL_CHANGE, labelChangeStub);
    element.handleLabelChange({change: testChange});
    await waitUntilCalled(labelChangeStub, 'labelChangeStub');

    const [change] = labelChangeStub.firstCall.args;
    assert.deepEqual(change, testChange);
    assert.isTrue(errorStub.calledOnce);
  });

  test('submitchange', () => {
    plugin.on(EventType.SUBMIT_CHANGE, throwErrFn);
    plugin.on(EventType.SUBMIT_CHANGE, () => true);
    assert.isTrue(element.canSubmitChange(createChange()));
    assert.isTrue(errorStub.calledOnce);
    plugin.on(EventType.SUBMIT_CHANGE, () => false);
    plugin.on(EventType.SUBMIT_CHANGE, () => true);
    assert.isFalse(element.canSubmitChange(createChange()));
    assert.isTrue(errorStub.calledTwice);
  });

  test('getLoggedIn', async () => {
    // fake fetch for authCheck
    stub(window, 'fetch').resolves(new Response(undefined, {status: 204}));
    const loggedIn = await plugin.restApi().getLoggedIn();
    assert.isTrue(loggedIn);
  });

  test('attributeHelper', () => {
    assert.isOk(plugin.attributeHelper(document.createElement('div')));
  });

  test('getAdminMenuLinks', () => {
    const links = [
      {text: 'a', url: 'b'},
      {text: 'c', url: 'd'},
    ];
    // getAdminMenuLinks expects _getEventCallbacks to return GrAdminApi[] even
    // though the signature is the EventCallback[].
    const getCallbacksStub = stub(element, '_getEventCallbacks').returns([
      {getMenuLinks: () => [links[0]]},
      {getMenuLinks: () => [links[1]]},
    ] as unknown as EventCallback[]);
    const result = element.getAdminMenuLinks();
    assert.deepEqual(result, links);
    assert.isTrue(getCallbacksStub.calledOnce);
    assert.equal(getCallbacksStub.lastCall.args[0], EventType.ADMIN_MENU_LINKS);
  });

  suite('test plugin with base url', () => {
    let baseUrlPlugin: Plugin;

    setup(() => {
      stubBaseUrl('/r');

      pluginLoader.install(
        p => {
          // We are using it as the implementation class to better set up tests
          baseUrlPlugin = p as Plugin;
        },
        '0.1',
        'http://test.com/r/plugins/baseurlplugin/static/test.js'
      );
    });

    test('url', () => {
      assert.notEqual(
        baseUrlPlugin.url(),
        'http://test.com/plugins/baseurlplugin/'
      );
      assert.equal(
        baseUrlPlugin.url(),
        'http://test.com/r/plugins/baseurlplugin/'
      );
      assert.equal(
        baseUrlPlugin.url('/static/test.js'),
        'http://test.com/r/plugins/baseurlplugin/static/test.js'
      );
    });
  });

  suite('popup', () => {
    test('popup(moduleName) creates popup with component', () => {
      const openStub = stub(GrPopupInterface.prototype, 'open').callsFake(
        async function (this: GrPopupInterface) {
          // Arrow function can't be used here, because we want to
          // get properties from the instance of GrPopupInterface
          assert.equal(this.plugin, plugin);
          assert.equal(this.moduleName, 'some-name');
          return this;
        }
      );
      plugin.popup('some-name');
      assert.isTrue(openStub.calledOnce);
    });
  });

  suite('screen', () => {
    test('screenUrl()', () => {
      stubBaseUrl('/base');
      assert.equal(plugin.screenUrl(), `${location.origin}/base/x/testplugin`);
      assert.equal(
        plugin.screenUrl('foo'),
        `${location.origin}/base/x/testplugin/foo`
      );
    });

    test('works', () => {
      const registerCustomComponentStub = stub(
        plugin,
        'registerCustomComponent'
      );
      plugin.screen('foo', 'some-module');
      assert.isTrue(
        registerCustomComponentStub.calledWith(
          'testplugin-screen-foo',
          'some-module'
        )
      );
    });
  });
});

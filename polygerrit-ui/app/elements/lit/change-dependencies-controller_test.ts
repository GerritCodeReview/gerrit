/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {testResolver} from '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import * as sinon from 'sinon';
import {
  ChangeDependenciesRequest,
  ChangeDependenciesResponse,
  ChangeDependencyEdge,
} from '../../api/change-dependencies';
import {NumericChangeId} from '../../api/rest-api';
import {createChange} from '../../test/test-data-generators';
import {mockPromise, waitEventLoop, waitUntil} from '../../test/test-utils';
import {GrChangeList} from '../change-list/gr-change-list/gr-change-list';
import '../change-list/gr-change-list/gr-change-list';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {createDefaultPreferences} from '../../constants/constants';
import {userModelToken} from '../../models/user/user-model';
import {PluginApi} from '../../api/plugin';

suite('change dependency providers', () => {
  let element: GrChangeList;
  let plugin: PluginApi;
  const edge = (from = 2, to = 1): ChangeDependencyEdge => {
    return {
      from: from as NumericChangeId,
      to: to as NumericChangeId,
      type: 'depends-on',
    };
  };
  const changes = () =>
    [2, 1].map(number => createChange({_number: number as NumericChangeId}));
  const numbers = async () =>
    (await element.getListItems()).map(item => Number(item.change!._number));
  const register = (
    fetch: (
      request: ChangeDependenciesRequest
    ) => Promise<ChangeDependenciesResponse>,
    name = 'network'
  ) => {
    testResolver(pluginLoaderToken).install(
      p => {
        plugin = p;
      },
      '0.1',
      `http://test.com/plugins/${name}/static/test.js`
    );
    plugin.changeDependencies().register({fetch});
  };

  setup(async () => {
    element = await fixture(
      html`<gr-change-list .changes=${changes()}></gr-change-list>`
    );
    testResolver(userModelToken).setPreferences({
      ...createDefaultPreferences(),
      threaded_changes: true,
    });
    await element.updateComplete;
  });

  test('batches the view, filters missing nodes, deduplicates edges, and retains selection', async () => {
    const pending = mockPromise<ChangeDependenciesResponse>();
    const fetch = sinon.stub().returns(pending);
    register(fetch);
    await waitUntil(() => fetch.calledOnce);
    const request = fetch.firstCall.args[0] as ChangeDependenciesRequest;
    assert.equal(request.context, 'change-list');
    assert.deepEqual(
      request.changes.map(change => Number(change._number)),
      [2, 1]
    );
    assert.isTrue(Object.isFrozen(request.changes));
    assert.deepEqual(await numbers(), [2, 1]);
    pending.resolve({edges: [edge(), edge(), edge(2, 999)]});
    await waitUntil(async () => (await numbers())[0] === 1);
    assert.equal(element.selectedIndex, 1);
    const items = await element.getListItems();
    assert.lengthOf(items, 2);
    assert.lengthOf(items[1].thread!.parents, 1);
    assert.isTrue(items[1].selected);
    assert.isTrue(fetch.calledOnce);
  });

  test('default and missing preferences disable providers', async () => {
    const userModel = testResolver(userModelToken);
    userModel.setPreferences(createDefaultPreferences());
    await element.updateComplete;
    const fetch = sinon.stub().resolves({edges: [edge()]});
    register(fetch);
    await waitEventLoop();
    assert.isFalse(fetch.called);
    assert.deepEqual(await numbers(), [2, 1]);
    const legacyPreferences = {
      ...createDefaultPreferences(),
      group_topics: true,
    };
    delete legacyPreferences.threaded_changes;
    userModel.setPreferences(legacyPreferences);
    await waitEventLoop();
    assert.isFalse(fetch.called);
    assert.deepEqual(await numbers(), [2, 1]);
  });

  test('rejects duplicate registration even through a fresh API instance', () => {
    register(async () => {
      return {edges: []};
    });
    assert.throws(
      () =>
        plugin.changeDependencies().register({
          fetch: async () => {
            return {edges: []};
          },
        }),
      'Only one dependency provider'
    );
  });

  test('ignores stale responses after a reload and refetches unchanged revisions', async () => {
    const first = mockPromise<ChangeDependenciesResponse>();
    const second = mockPromise<ChangeDependenciesResponse>();
    const fetch = sinon
      .stub()
      .onFirstCall()
      .returns(first)
      .onSecondCall()
      .returns(second);
    register(fetch);
    await waitUntil(() => fetch.calledOnce);
    element.changes = changes();
    await waitUntil(() => fetch.calledTwice);
    assert.isTrue(
      (fetch.firstCall.args[0] as ChangeDependenciesRequest).signal.aborted
    );
    first.resolve({edges: [edge()]});
    await waitEventLoop();
    assert.deepEqual(await numbers(), [2, 1]);
    second.resolve({edges: []});
  });

  test('disabling threads cancels requests and re-enabling refetches', async () => {
    const pending = mockPromise<ChangeDependenciesResponse>();
    const fetch = sinon.stub().returns(pending);
    register(fetch);
    await waitUntil(() => fetch.calledOnce);
    const prefs = testResolver(userModelToken);
    prefs.setPreferences({
      ...prefs.getState().preferences!,
      threaded_changes: false,
    });
    await element.updateComplete;
    assert.isTrue(
      (fetch.firstCall.args[0] as ChangeDependenciesRequest).signal.aborted
    );
    pending.resolve({edges: [edge()]});
    await waitEventLoop();
    assert.deepEqual(await numbers(), [2, 1]);
    prefs.setPreferences({
      ...prefs.getState().preferences!,
      threaded_changes: true,
    });
    await waitUntil(() => fetch.calledTwice);
    await waitUntil(async () => (await numbers())[0] === 1);
  });

  test('a failed or hanging provider does not block another provider', async () => {
    sinon.stub(console, 'warn');
    const hanging = mockPromise<ChangeDependenciesResponse>();
    register(async () => {
      throw new Error('offline');
    }, 'failed');
    register(() => hanging, 'hanging');
    register(async () => {
      return {edges: [edge()]};
    }, 'working');
    await waitUntil(async () => (await numbers())[0] === 1);
    element.remove();
    hanging.resolve({edges: []});
  });

  test('times out a provider and ignores its eventual result', async () => {
    const clock = sinon.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
    const pending = mockPromise<ChangeDependenciesResponse>();
    const fetch = sinon.stub().returns(pending);
    register(fetch);
    await clock.tickAsync(0);
    assert.isTrue(fetch.calledOnce);
    await clock.tickAsync(30000);
    assert.isTrue(
      (fetch.firstCall.args[0] as ChangeDependenciesRequest).signal.aborted
    );
    pending.resolve({edges: [edge()]});
    await clock.tickAsync(0);
    assert.deepEqual(await numbers(), [2, 1]);
    clock.restore();
  });

  test('disconnect aborts an in-flight request', async () => {
    const pending = mockPromise<ChangeDependenciesResponse>();
    const fetch = sinon.stub().returns(pending);
    register(fetch);
    await waitUntil(() => fetch.calledOnce);
    element.remove();
    assert.isTrue(
      (fetch.firstCall.args[0] as ChangeDependenciesRequest).signal.aborted
    );
    pending.resolve({edges: [edge()]});
    await waitEventLoop();
  });
});

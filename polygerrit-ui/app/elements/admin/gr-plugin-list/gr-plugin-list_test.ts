/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-plugin-list';
import {GrPluginList, PluginInfoWithName} from './gr-plugin-list';
import {
  addListenerForTest,
  mockPromise,
  query,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {PluginInfo} from '../../../types/common';
import {AppElementAdminParams} from '../../gr-app-types';
import {GerritView} from '../../../services/router/router-model';
import {PageErrorEvent} from '../../../types/events';
import {SHOWN_ITEMS_COUNT} from '../../../constants/constants';

const basicFixture = fixtureFromElement('gr-plugin-list');

function pluginGenerator(counter: number) {
  const plugin: PluginInfo = {
    id: `test${counter}`,
    version: `version-${counter}`,
    disabled: false,
  };

  if (counter !== 2) {
    plugin.index_url = `plugins/test${counter}/`;
  }
  if (counter !== 4) {
    plugin.api_version = `api-version-${counter}`;
  }
  return plugin;
}

function createPluginList(n: number) {
  const plugins = [];
  for (let i = 0; i < n; ++i) {
    const plugin = pluginGenerator(i) as PluginInfoWithName;
    plugin.name = `test${i}`;
    plugins.push(plugin);
  }
  return plugins;
}

function createPluginObjectList(n: number) {
  const plugins: {[pluginName: string]: PluginInfo} | undefined = {};
  for (let i = 0; i < n; ++i) {
    plugins[`test${i}`] = pluginGenerator(i);
  }
  return plugins;
}

suite('gr-plugin-list tests', () => {
  let element: GrPluginList;
  let plugins: {[pluginName: string]: PluginInfo} | undefined;

  const value: AppElementAdminParams = {view: GerritView.ADMIN, adminView: ''};

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  suite('list with plugins', async () => {
    setup(async () => {
      plugins = createPluginObjectList(26);
      stubRestApi('getPlugins').returns(Promise.resolve(plugins));
      element.params = value;
      await element.paramsChanged();
      await element.updateComplete;
    });

    test('plugin in the list is formatted correctly', async () => {
      await element.updateComplete;
      assert.equal(element.plugins![5].id, 'test5');
      assert.equal(element.plugins![5].index_url, 'plugins/test5/');
      assert.equal(element.plugins![5].version, 'version-5');
      assert.equal(element.plugins![5].api_version, 'api-version-5');
      assert.equal(element.plugins![5].disabled, false);
    });

    test('with and without urls', async () => {
      await element.updateComplete;
      const names = queryAll<HTMLTableElement>(element, '.name');
      assert.isOk(queryAndAssert<HTMLAnchorElement>(names[2], 'a'));
      assert.equal(
        queryAndAssert<HTMLAnchorElement>(names[2], 'a').innerText,
        'test1'
      );
      assert.isNotOk(query(names[3], 'a'));
      assert.equal(names[3].innerText, 'test2');
    });

    test('versions', async () => {
      await element.updateComplete;
      const versions = queryAll<HTMLTableElement>(element, '.version');
      assert.equal(versions[3].innerText, 'version-2');
    });

    test('api versions', async () => {
      await element.updateComplete;
      const apiVersions = queryAll<HTMLTableElement>(element, '.apiVersion');
      assert.equal(apiVersions[4].innerText, 'api-version-3');
      assert.equal(apiVersions[5].innerText, '--');
    });

    test('plugins', () => {
      assert.equal(element.plugins!.slice(0, SHOWN_ITEMS_COUNT).length, 25);
    });
  });

  suite('list with less then 26 plugins', () => {
    setup(async () => {
      plugins = createPluginObjectList(25);
      stubRestApi('getPlugins').returns(Promise.resolve(plugins));
      element.params = value;
      await element.paramsChanged();
      await element.updateComplete;
    });

    test('plugins', () => {
      assert.equal(element.plugins!.slice(0, SHOWN_ITEMS_COUNT).length, 25);
    });
  });

  suite('filter', () => {
    test('paramsChanged', async () => {
      const getPluginsStub = stubRestApi('getPlugins');
      getPluginsStub.returns(Promise.resolve(plugins));
      value.filter = 'test';
      value.offset = 25;
      element.params = value;
      await element.paramsChanged();
      assert.equal(getPluginsStub.lastCall.args[0], 'test');
      assert.equal(getPluginsStub.lastCall.args[1], 25);
      assert.equal(getPluginsStub.lastCall.args[2], 25);
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', async () => {
      assert.isTrue(element.loading);
      assert.equal(
        getComputedStyle(queryAndAssert<HTMLTableElement>(element, '#loading'))
          .display,
        'block'
      );

      element.loading = false;
      element.plugins = createPluginList(25);

      await element.updateComplete;
      assert.isNotOk(query<HTMLTableElement>(element, '#loading'));
    });
  });

  suite('404', () => {
    test('fires page-error', async () => {
      const response = {status: 404} as Response;
      stubRestApi('getPlugins').callsFake(
        (_filter, _pluginsPerPage, _opt_offset, errFn) => {
          if (errFn !== undefined) {
            errFn(response);
          }
          return Promise.resolve(undefined);
        }
      );

      const promise = mockPromise();
      addListenerForTest(document, 'page-error', e => {
        assert.deepEqual((e as PageErrorEvent).detail.response, response);
        promise.resolve();
      });

      value.filter = 'test';
      value.offset = 25;
      element.params = value;
      await element.paramsChanged();
      await promise;
    });
  });
});

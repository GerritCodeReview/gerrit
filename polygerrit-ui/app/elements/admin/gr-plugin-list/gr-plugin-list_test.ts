/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
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
import {GerritView} from '../../../services/router/router-model';
import {PageErrorEvent} from '../../../types/events';
import {fixture, html, assert} from '@open-wc/testing';
import {AdminChildView, AdminViewState} from '../../../models/views/admin';

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

  const value: AdminViewState = {
    view: GerritView.ADMIN,
    adminView: AdminChildView.PLUGINS,
  };

  setup(async () => {
    element = await fixture(html`<gr-plugin-list></gr-plugin-list>`);
  });

  suite('list with plugins', async () => {
    setup(async () => {
      plugins = createPluginObjectList(26);
      stubRestApi('getPlugins').returns(Promise.resolve(plugins));
      element.params = value;
      await element.paramsChanged();
      await element.updateComplete;
    });

    test('render', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-list-view>
            <table class="genericList" id="list">
              <tbody>
                <tr class="headerRow">
                  <th class="name topHeader">Plugin Name</th>
                  <th class="topHeader version">Version</th>
                  <th class="apiVersion topHeader">API Version</th>
                  <th class="status topHeader">Status</th>
                </tr>
              </tbody>
              <tbody>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test0/"> test0 </a>
                  </td>
                  <td class="version">version-0</td>
                  <td class="apiVersion">api-version-0</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test1/"> test1 </a>
                  </td>
                  <td class="version">version-1</td>
                  <td class="apiVersion">api-version-1</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">test2</td>
                  <td class="version">version-2</td>
                  <td class="apiVersion">api-version-2</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test3/"> test3 </a>
                  </td>
                  <td class="version">version-3</td>
                  <td class="apiVersion">api-version-3</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test4/"> test4 </a>
                  </td>
                  <td class="version">version-4</td>
                  <td class="apiVersion">
                    <span class="placeholder"> -- </span>
                  </td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test5/"> test5 </a>
                  </td>
                  <td class="version">version-5</td>
                  <td class="apiVersion">api-version-5</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test6/"> test6 </a>
                  </td>
                  <td class="version">version-6</td>
                  <td class="apiVersion">api-version-6</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test7/"> test7 </a>
                  </td>
                  <td class="version">version-7</td>
                  <td class="apiVersion">api-version-7</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test8/"> test8 </a>
                  </td>
                  <td class="version">version-8</td>
                  <td class="apiVersion">api-version-8</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test9/"> test9 </a>
                  </td>
                  <td class="version">version-9</td>
                  <td class="apiVersion">api-version-9</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test10/"> test10 </a>
                  </td>
                  <td class="version">version-10</td>
                  <td class="apiVersion">api-version-10</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test11/"> test11 </a>
                  </td>
                  <td class="version">version-11</td>
                  <td class="apiVersion">api-version-11</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test12/"> test12 </a>
                  </td>
                  <td class="version">version-12</td>
                  <td class="apiVersion">api-version-12</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test13/"> test13 </a>
                  </td>
                  <td class="version">version-13</td>
                  <td class="apiVersion">api-version-13</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test14/"> test14 </a>
                  </td>
                  <td class="version">version-14</td>
                  <td class="apiVersion">api-version-14</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test15/"> test15 </a>
                  </td>
                  <td class="version">version-15</td>
                  <td class="apiVersion">api-version-15</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test16/"> test16 </a>
                  </td>
                  <td class="version">version-16</td>
                  <td class="apiVersion">api-version-16</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test17/"> test17 </a>
                  </td>
                  <td class="version">version-17</td>
                  <td class="apiVersion">api-version-17</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test18/"> test18 </a>
                  </td>
                  <td class="version">version-18</td>
                  <td class="apiVersion">api-version-18</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test19/"> test19 </a>
                  </td>
                  <td class="version">version-19</td>
                  <td class="apiVersion">api-version-19</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test20/"> test20 </a>
                  </td>
                  <td class="version">version-20</td>
                  <td class="apiVersion">api-version-20</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test21/"> test21 </a>
                  </td>
                  <td class="version">version-21</td>
                  <td class="apiVersion">api-version-21</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test22/"> test22 </a>
                  </td>
                  <td class="version">version-22</td>
                  <td class="apiVersion">api-version-22</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test23/"> test23 </a>
                  </td>
                  <td class="version">version-23</td>
                  <td class="apiVersion">api-version-23</td>
                  <td class="status">Enabled</td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/plugins/test24/"> test24 </a>
                  </td>
                  <td class="version">version-24</td>
                  <td class="apiVersion">api-version-24</td>
                  <td class="status">Enabled</td>
                </tr>
              </tbody>
            </table>
          </gr-list-view>
        `
      );
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
      const table = queryAndAssert(element, 'table');
      const rows = table.querySelectorAll('tr.table');
      assert.equal(rows.length, element.pluginsPerPage);
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
      const table = queryAndAssert(element, 'table');
      const rows = table.querySelectorAll('tr.table');
      assert.equal(rows.length, element.pluginsPerPage);
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

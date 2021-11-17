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

import '../../../test/common-test-setup-karma';
import './gr-plugin-list';
import {GrPluginList, PluginInfoWithName} from './gr-plugin-list';
import 'lodash/lodash';
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

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('list with plugins', async () => {
    setup(async () => {
      plugins = createPluginObjectList(26);
      stubRestApi('getPlugins').returns(Promise.resolve(plugins));
      await element._paramsChanged(value);
      await flush();
    });

    test('plugin in the list is formatted correctly', async () => {
      await flush();
      assert.equal(element._plugins![5].id, 'test5');
      assert.equal(element._plugins![5].index_url, 'plugins/test5/');
      assert.equal(element._plugins![5].version, 'version-5');
      assert.equal(element._plugins![5].api_version, 'api-version-5');
      assert.equal(element._plugins![5].disabled, false);
    });

    test('with and without urls', async () => {
      await flush();
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
      await flush();
      const versions = queryAll<HTMLTableElement>(element, '.version');
      assert.equal(versions[3].innerText, 'version-2');
    });

    test('api versions', async () => {
      await flush();
      const apiVersions = queryAll<HTMLTableElement>(element, '.apiVersion');
      assert.equal(apiVersions[4].innerText, 'api-version-3');
      assert.equal(apiVersions[5].innerText, '--');
    });

    test('_shownPlugins', () => {
      assert.equal(element._shownPlugins!.length, 25);
    });
  });

  suite('list with less then 26 plugins', () => {
    setup(async () => {
      plugins = createPluginObjectList(25);
      stubRestApi('getPlugins').returns(Promise.resolve(plugins));
      await element._paramsChanged(value);
      await flush();
    });

    test('_shownPlugins', () => {
      assert.equal(element._shownPlugins!.length, 25);
    });
  });

  suite('filter', () => {
    test('_paramsChanged', async () => {
      const getPluginsStub = stubRestApi('getPlugins');
      getPluginsStub.returns(Promise.resolve(plugins));
      value.filter = 'test';
      value.offset = 25;
      await element._paramsChanged(value);
      assert.equal(getPluginsStub.lastCall.args[0], 'test');
      assert.equal(getPluginsStub.lastCall.args[1], 25);
      assert.equal(getPluginsStub.lastCall.args[2], 25);
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', async () => {
      assert.isTrue(element._loading);
      assert.equal(element.computeLoadingClass(element._loading), 'loading');
      assert.equal(
        getComputedStyle(queryAndAssert<HTMLTableElement>(element, '#loading'))
          .display,
        'block'
      );

      element._loading = false;
      element._plugins = createPluginList(25);

      await flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(
        getComputedStyle(queryAndAssert<HTMLTableElement>(element, '#loading'))
          .display,
        'none'
      );
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
      await element._paramsChanged(value);
      await promise;
    });
  });
});

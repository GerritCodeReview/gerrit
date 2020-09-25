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

import '../../../test/common-test-setup-karma.js';
import './gr-plugin-list.js';

const basicFixture = fixtureFromElement('gr-plugin-list');

let counter;
const pluginGenerator = () => {
  const plugin = {
    id: `test${++counter}`,
    disabled: false,
  };

  if (counter !== 2) {
    plugin.index_url = `plugins/test${counter}/`;
  }
  if (counter !== 3) {
    plugin.version = `version-${counter}`;
  }
  if (counter !== 4) {
    plugin.api_version = `api-version-${counter}`;
  }
  return plugin;
};

suite('gr-plugin-list tests', () => {
  let element;
  let plugins;

  let value;

  setup(() => {
    element = basicFixture.instantiate();
    counter = 0;
  });

  suite('list with plugins', () => {
    setup(done => {
      plugins = _.times(26, pluginGenerator);

      stub('gr-rest-api-interface', {
        getPlugins(num, offset) {
          return Promise.resolve(plugins);
        },
      });

      element._paramsChanged(value).then(() => { flush(done); });
    });

    test('plugin in the list is formatted correctly', done => {
      flush(() => {
        assert.equal(element._plugins[4].id, 'test5');
        assert.equal(element._plugins[4].index_url, 'plugins/test5/');
        assert.equal(element._plugins[4].version, 'version-5');
        assert.equal(element._plugins[4].api_version, 'api-version-5');
        assert.equal(element._plugins[4].disabled, false);
        done();
      });
    });

    test('with and without urls', done => {
      flush(() => {
        const names = element.root.querySelectorAll('.name');
        assert.isOk(names[1].querySelector('a'));
        assert.equal(names[1].querySelector('a').innerText, 'test1');
        assert.isNotOk(names[2].querySelector('a'));
        assert.equal(names[2].innerText, 'test2');
        done();
      });
    });

    test('versions', done => {
      flush(() => {
        const versions = element.root.querySelectorAll('.version');
        assert.equal(versions[2].innerText, 'version-2');
        assert.equal(versions[3].innerText, '--');
        done();
      });
    });

    test('api versions', done => {
      flush(() => {
        const apiVersions = element.root.querySelectorAll(
            '.apiVersion');
        assert.equal(apiVersions[3].innerText, 'api-version-3');
        assert.equal(apiVersions[4].innerText, '--');
        done();
      });
    });

    test('_shownPlugins', () => {
      assert.equal(element._shownPlugins.length, 25);
    });
  });

  suite('list with less then 26 plugins', () => {
    setup(done => {
      plugins = _.times(25, pluginGenerator);

      stub('gr-rest-api-interface', {
        getPlugins(num, offset) {
          return Promise.resolve(plugins);
        },
      });

      element._paramsChanged(value).then(() => { flush(done); });
    });

    test('_shownPlugins', () => {
      assert.equal(element._shownPlugins.length, 25);
    });
  });

  suite('filter', () => {
    test('_paramsChanged', done => {
      sinon.stub(
          element.$.restAPI,
          'getPlugins')
          .callsFake(() => Promise.resolve(plugins));
      const value = {
        filter: 'test',
        offset: 25,
      };
      element._paramsChanged(value).then(() => {
        assert.equal(element.$.restAPI.getPlugins.lastCall.args[0],
            'test');
        assert.equal(element.$.restAPI.getPlugins.lastCall.args[1],
            25);
        assert.equal(element.$.restAPI.getPlugins.lastCall.args[2],
            25);
        done();
      });
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', () => {
      assert.isTrue(element._loading);
      assert.equal(element.computeLoadingClass(element._loading), 'loading');
      assert.equal(getComputedStyle(element.$.loading).display, 'block');

      element._loading = false;
      element._plugins = _.times(25, pluginGenerator);

      flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(getComputedStyle(element.$.loading).display, 'none');
    });
  });

  suite('404', () => {
    test('fires page-error', done => {
      const response = {status: 404};
      sinon.stub(element.$.restAPI, 'getPlugins').callsFake(
          (filter, pluginsPerPage, opt_offset, errFn) => {
            errFn(response);
          });

      element.addEventListener('page-error', e => {
        assert.deepEqual(e.detail.response, response);
        done();
      });

      const value = {
        filter: 'test',
        offset: 25,
      };
      element._paramsChanged(value);
    });
  });
});


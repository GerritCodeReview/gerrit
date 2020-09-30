/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import './gr-avatar.js';
import {getPluginLoader} from '../gr-js-api-interface/gr-plugin-loader.js';

const basicFixture = fixtureFromElement('gr-avatar');

suite('gr-avatar tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('methods', () => {
    assert.equal(
        element._buildAvatarURL({
          _account_id: 123,
        }),
        '/accounts/123/avatar?s=16');
    assert.equal(
        element._buildAvatarURL({
          email: 'test@example.com',
        }),
        '/accounts/test%40example.com/avatar?s=16');
    assert.equal(
        element._buildAvatarURL({
          name: 'John Doe',
        }),
        '/accounts/John%20Doe/avatar?s=16');
    assert.equal(
        element._buildAvatarURL({
          username: 'John_Doe',
        }),
        '/accounts/John_Doe/avatar?s=16');
    assert.equal(
        element._buildAvatarURL({
          _account_id: 123,
          avatars: [
            {
              url: 'https://cdn.example.com/s12-p/photo.jpg',
              height: 12,
            },
            {
              url: 'https://cdn.example.com/s16-p/photo.jpg',
              height: 16,
            },
            {
              url: 'https://cdn.example.com/s100-p/photo.jpg',
              height: 100,
            },
          ],
        }),
        'https://cdn.example.com/s16-p/photo.jpg');
    assert.equal(
        element._buildAvatarURL({
          _account_id: 123,
          avatars: [
            {
              url: 'https://cdn.example.com/s95-p/photo.jpg',
              height: 95,
            },
          ],
        }),
        '/accounts/123/avatar?s=16');
    assert.equal(element._buildAvatarURL(undefined), '');
  });

  suite('config set', () => {
    setup(() => {
      stub('gr-avatar', {
        _getConfig: () => { return Promise.resolve({plugin: {has_avatars: true}}); },
      });
      element = basicFixture.instantiate();
    });

    test('dom for existing account', () => {
      assert.isFalse(element.hasAttribute('hidden'));

      element.imageSize = 64;
      element.account = {
        _account_id: 123,
      };

      assert.strictEqual(element.style.backgroundImage, '');

      // Emulate plugins loaded.
      getPluginLoader().loadPlugins([]);

      return Promise.all([
        element.$.restAPI.getConfig(),
        getPluginLoader().awaitPluginsLoaded(),
      ]).then(() => {
        assert.isFalse(element.hasAttribute('hidden'));

        assert.isTrue(
            element.style.backgroundImage.includes(
                '/accounts/123/avatar?s=64'));
      });
    });
  });

  suite('plugin has avatars', () => {
    let element;

    setup(() => {
      stub('gr-avatar', {
        _getConfig: () => { return Promise.resolve({plugin: {has_avatars: true}}); },
      });

      element = basicFixture.instantiate();
    });

    test('dom for non available account', () => {
      assert.isFalse(element.hasAttribute('hidden'));

      // Emulate plugins loaded.
      getPluginLoader().loadPlugins([]);

      return Promise.all([
        element.$.restAPI.getConfig(),
        getPluginLoader().awaitPluginsLoaded(),
      ]).then(() => {
        assert.isTrue(element.hasAttribute('hidden'));

        assert.strictEqual(element.style.backgroundImage, '');
      });
    });
  });

  suite('config not set', () => {
    let element;

    setup(() => {
      stub('gr-avatar', {
        _getConfig: () => { return Promise.resolve({}); },
      });

      element = basicFixture.instantiate();
    });

    test('avatar hidden when account set', async () => {
      await flush();
      assert.isTrue(element.hasAttribute('hidden'));

      element.imageSize = 64;
      element.account = {
        _account_id: 123,
      };
      // Emulate plugins loaded.
      getPluginLoader().loadPlugins([]);

      return Promise.all([
        element.$.restAPI.getConfig(),
        getPluginLoader().awaitPluginsLoaded(),
      ]).then(() => {
        assert.isTrue(element.hasAttribute('hidden'));
      });
    });
  });
});


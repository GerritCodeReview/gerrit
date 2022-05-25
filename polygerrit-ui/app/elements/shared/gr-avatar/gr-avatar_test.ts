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

import '../../../test/common-test-setup-karma';
import './gr-avatar';
import {GrAvatar} from './gr-avatar';
import {getPluginLoader} from '../gr-js-api-interface/gr-plugin-loader';
import {getAppContext, AppContext} from '../../../services/app-context';
import {AvatarInfo} from '../../../types/common';
import {
  createAccountWithEmail,
  createAccountWithId,
  createServerInfo,
} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-avatar');

suite('gr-avatar tests', () => {
  let element: GrAvatar;
  const defaultAvatars: AvatarInfo[] = [
    {
      url: 'https://cdn.example.com/s12-p/photo.jpg',
      height: 12,
      width: 0,
    },
  ];

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('account without avatar', () => {
    assert.equal(element._buildAvatarURL(createAccountWithId(123)), '');
  });

  test('methods', () => {
    assert.equal(
      element._buildAvatarURL({
        ...createAccountWithId(123),
        avatars: defaultAvatars,
      }),
      '/accounts/123/avatar?s=16'
    );
    assert.equal(
      element._buildAvatarURL({
        ...createAccountWithEmail('test@example.com'),
        avatars: defaultAvatars,
      }),
      '/accounts/test%40example.com/avatar?s=16'
    );
    assert.equal(
      element._buildAvatarURL({
        name: 'John Doe',
        avatars: defaultAvatars,
      }),
      '/accounts/John%20Doe/avatar?s=16'
    );
    assert.equal(
      element._buildAvatarURL({
        username: 'John_Doe',
        avatars: defaultAvatars,
      }),
      '/accounts/John_Doe/avatar?s=16'
    );
    assert.equal(
      element._buildAvatarURL({
        ...createAccountWithId(123),
        avatars: [
          {
            url: 'https://cdn.example.com/s12-p/photo.jpg',
            height: 12,
            width: 0,
          },
          {
            url: 'https://cdn.example.com/s16-p/photo.jpg',
            height: 16,
            width: 0,
          },
          {
            url: 'https://cdn.example.com/s100-p/photo.jpg',
            height: 100,
            width: 0,
          },
        ] as AvatarInfo[],
      }),
      'https://cdn.example.com/s16-p/photo.jpg'
    );
    assert.equal(
      element._buildAvatarURL({
        ...createAccountWithId(123),
        avatars: [
          {
            url: 'https://cdn.example.com/s95-p/photo.jpg',
            height: 95,
            width: 0,
          },
        ] as AvatarInfo[],
      }),
      '/accounts/123/avatar?s=16'
    );
    assert.equal(element._buildAvatarURL(undefined), '');
  });

  suite('config set', () => {
    let appContext: AppContext;
    setup(() => {
      appContext = getAppContext();
      const config = {
        ...createServerInfo(),
        plugin: {has_avatars: true, js_resource_paths: []},
      };
      stub('gr-avatar', '_getConfig').returns(Promise.resolve(config));
      element = basicFixture.instantiate();
    });

    test('dom for existing account', () => {
      assert.isFalse(element.hasAttribute('hidden'));

      element.imageSize = 64;
      element.account = {
        ...createAccountWithId(123),
        avatars: defaultAvatars,
      };
      flush();

      assert.strictEqual(element.style.backgroundImage, '');

      // Emulate plugins loaded.
      getPluginLoader().loadPlugins([]);

      return Promise.all([
        appContext.restApiService.getConfig(),
        getPluginLoader().awaitPluginsLoaded(),
      ]).then(() => {
        assert.isFalse(element.hasAttribute('hidden'));

        assert.isTrue(
          element.style.backgroundImage.includes('/accounts/123/avatar?s=64')
        );
      });
    });
  });

  suite('plugin has avatars', () => {
    let appContext: AppContext;
    setup(() => {
      appContext = getAppContext();
      const config = {
        ...createServerInfo(),
        plugin: {has_avatars: true, js_resource_paths: []},
      };
      stub('gr-avatar', '_getConfig').returns(Promise.resolve(config));

      element = basicFixture.instantiate();
    });

    test('dom for non available account', () => {
      assert.isFalse(element.hasAttribute('hidden'));

      // Emulate plugins loaded.
      getPluginLoader().loadPlugins([]);

      return Promise.all([
        appContext.restApiService.getConfig(),
        getPluginLoader().awaitPluginsLoaded(),
      ]).then(() => {
        assert.isTrue(element.hasAttribute('hidden'));

        assert.strictEqual(element.style.backgroundImage, '');
      });
    });
  });

  suite('config not set', () => {
    let element: GrAvatar;
    let appContext: AppContext;

    setup(() => {
      stub('gr-avatar', '_getConfig').returns(Promise.resolve(undefined));
      appContext = getAppContext();
      element = basicFixture.instantiate();
    });

    test('avatar hidden when account set', async () => {
      await flush();
      assert.isTrue(element.hasAttribute('hidden'));

      element.imageSize = 64;
      element.account = {
        ...createAccountWithId(123),
        avatars: defaultAvatars,
      };
      // Emulate plugins loaded.
      getPluginLoader().loadPlugins([]);

      return Promise.all([
        appContext.restApiService.getConfig(),
        getPluginLoader().awaitPluginsLoaded(),
      ]).then(() => {
        assert.isTrue(element.hasAttribute('hidden'));
      });
    });
  });
});

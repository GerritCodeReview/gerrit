/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-avatar';

import {assert, fixture, html} from '@open-wc/testing';

import {clearAvatarProviders, registerAvatarProvider,} from '../../../api/avatar';
import {createAccountWithEmailOnly, createAccountWithId, createServerInfo,} from '../../../test/test-data-generators';
import {isVisible, stubRestApi} from '../../../test/test-utils';
import {AvatarInfo} from '../../../types/common';

import {GrAvatar} from './gr-avatar';

suite('gr-avatar tests', () => {
  let element: GrAvatar;
  const defaultAvatars: AvatarInfo[] = [
    {
      url: 'https://cdn.example.com/s12-p/photo.jpg',
      height: 12,
    },
  ];

  test('renders hidden when no config is set', async () => {
    stubRestApi('getConfig').resolves(undefined);
    const accountWithId = {
      ...createAccountWithId(123),
      avatars: defaultAvatars,
    };
    element = await fixture(
      html`<gr-avatar .account=${accountWithId}></gr-avatar>`
    );

    assert.isFalse(isVisible(element));
  });

  test('renders hidden when config does not use avatars', async () => {
    stubRestApi('getConfig').resolves({
      ...createServerInfo(),
      plugin: {has_avatars: false, js_resource_paths: []},
    });
    const accountWithId = {
      ...createAccountWithId(123),
      avatars: defaultAvatars,
    };
    element = await fixture(
      html`<gr-avatar .account=${accountWithId}></gr-avatar>`
    );

    assert.isFalse(isVisible(element));
  });

  suite('no avatar providers', () => {
    setup(async () => {
      stubRestApi('getConfig').resolves({
        ...createServerInfo(),
        plugin: {has_avatars: true, js_resource_paths: []},
      });
      clearAvatarProviders();
    });

    test('scenario 1: fallback to avatars', async () => {
      const accountWithCustomAvatars = {
        ...createAccountWithId(123),
        avatars: [
          {
            url: 'https://cdn.example.com/s16-p/photo.jpg',
            height: 16,
          },
        ],
      };
      element = await fixture(
          html`<gr-avatar .account=${accountWithCustomAvatars}></gr-avatar>`);
      assert.isTrue(isVisible(element));
      assert.equal(
          element.style.backgroundImage,
          'url("https://cdn.example.com/s16-p/photo.jpg")');
    });
  });

  suite('config has avatars', () => {
    setup(async () => {
      stubRestApi('getConfig').resolves({
        ...createServerInfo(),
        plugin: {has_avatars: true, js_resource_paths: []},
      });
      clearAvatarProviders();
    });

    test('loads correct size', async () => {
      const accountWithId = {
        ...createAccountWithId(123),
        avatars: defaultAvatars,
      };
      element = await fixture(
        html`<gr-avatar .account=${accountWithId} .imageSize=${64}></gr-avatar>`
      );

      assert.isTrue(isVisible(element));
      assert.equal(
        element.style.backgroundImage,
        'url("/accounts/123/avatar?s=64")'
      );
    });

    test('loads using id', async () => {
      const accountWithId = {
        ...createAccountWithId(123),
        avatars: defaultAvatars,
      };
      element = await fixture(
        html`<gr-avatar .account=${accountWithId}></gr-avatar>`
      );

      assert.isTrue(isVisible(element));
      assert.equal(
        element.style.backgroundImage,
        'url("/accounts/123/avatar?s=16")'
      );
    });

    test('loads using email', async () => {
      const accountWithEmail = {
        ...createAccountWithEmailOnly('foo@gmail.com'),
        avatars: defaultAvatars,
      };
      element = await fixture(
        html`<gr-avatar .account=${accountWithEmail}></gr-avatar>`
      );

      assert.isTrue(isVisible(element));
      assert.equal(
        element.style.backgroundImage,
        'url("/accounts/foo%40gmail.com/avatar?s=16")'
      );
    });

    test('loads using name', async () => {
      const accountWithName = {
        name: 'John Doe',
        avatars: defaultAvatars,
      };
      element = await fixture(
        html`<gr-avatar .account=${accountWithName}></gr-avatar>`
      );

      assert.isTrue(isVisible(element));
      assert.equal(
        element.style.backgroundImage,
        'url("/accounts/John%20Doe/avatar?s=16")'
      );
    });

    test('loads using username', async () => {
      const accountWithUsername = {
        username: 'John_Doe',
        avatars: defaultAvatars,
      };
      element = await fixture(
        html`<gr-avatar .account=${accountWithUsername}></gr-avatar>`
      );

      assert.isTrue(isVisible(element));
      assert.equal(
        element.style.backgroundImage,
        'url("/accounts/John_Doe/avatar?s=16")'
      );
    });

    test('loads using custom URL from matching height', async () => {
      const accountWithCustomAvatars = {
        ...createAccountWithId(123),
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
      };
      element = await fixture(
        html`<gr-avatar .account=${accountWithCustomAvatars}></gr-avatar>`
      );

      assert.isTrue(isVisible(element));
      assert.equal(
        element.style.backgroundImage,
        'url("https://cdn.example.com/s16-p/photo.jpg")'
      );
    });

    test('loads using normal URL when no custom URL sizes match', async () => {
      const accountWithCustomAvatars = {
        ...createAccountWithId(123),
        avatars: [
          {
            url: 'https://cdn.example.com/s95-p/photo.jpg',
            height: 95,
          },
        ],
      };
      element = await fixture(
        html`<gr-avatar .account=${accountWithCustomAvatars}></gr-avatar>`
      );

      assert.isTrue(isVisible(element));
      assert.equal(
        element.style.backgroundImage,
        'url("/accounts/123/avatar?s=16")'
      );
    });

    test('scenario 2: single provider returns url', async () => {
      registerAvatarProvider(account => {
        if (account.email === 'treehugger-gerrit@google.com') {
          return 'https://gstatic.com/buganizer/img/v2/gerrit_logo.svg';
        }
        return undefined;
      });
      const robotAccount = {
        ...createAccountWithEmailOnly('treehugger-gerrit@google.com'),
        avatars: defaultAvatars,
      };
      element =
          await fixture(html`<gr-avatar .account=${robotAccount}></gr-avatar>`);

      assert.isTrue(isVisible(element));
      assert.equal(
          element.style.backgroundImage,
          'url("https://gstatic.com/buganizer/img/v2/gerrit_logo.svg")');
    });

    test('scenario 3: single provider returns falsy', async () => {
      registerAvatarProvider(account => {
        if (account.email === 'treehugger-gerrit@google.com') {
          return 'https://gstatic.com/buganizer/img/v2/gerrit_logo.svg';
        }
        return undefined;
      });
      const accountWithCustomAvatars = {
        ...createAccountWithId(123),
        avatars: [
          {
            url: 'https://cdn.example.com/s16-p/photo.jpg',
            height: 16,
          },
        ],
      };
      element = await fixture(
          html`<gr-avatar .account=${accountWithCustomAvatars}></gr-avatar>`);
      assert.isTrue(isVisible(element));
      assert.equal(
          element.style.backgroundImage,
          'url("https://cdn.example.com/s16-p/photo.jpg")');
    });

    test('scenario 4: multiple providers pick first', async () => {
      registerAvatarProvider(account => {
        if (account._account_id === 123) {
          return 'https://provider1.com/123.jpg';
        }
        return undefined;
      });
      registerAvatarProvider(account => {
        if (account._account_id === 123) {
          return 'https://provider2.com/123.jpg';
        }
        return undefined;
      });
      const account = {
        ...createAccountWithId(123),
        avatars: defaultAvatars,
      };
      element =
          await fixture(html`<gr-avatar .account=${account}></gr-avatar>`);
      assert.isTrue(isVisible(element));
      assert.equal(
          element.style.backgroundImage,
          'url("https://provider1.com/123.jpg")');
    });
  });
});

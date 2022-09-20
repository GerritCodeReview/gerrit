/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-avatar';
import {GrAvatar} from './gr-avatar';
import {AvatarInfo} from '../../../types/common';
import {
  createAccountWithEmailOnly,
  createAccountWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {fixture, html, assert} from '@open-wc/testing';
import {isVisible, stubRestApi} from '../../../test/test-utils';

suite('gr-avatar tests', () => {
  let element: GrAvatar;
  const defaultAvatars: AvatarInfo[] = [
    {
      url: 'https://cdn.example.com/s12-p/photo.jpg',
      height: 12,
      width: 0,
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

  suite('config has avatars', () => {
    setup(async () => {
      stubRestApi('getConfig').resolves({
        ...createServerInfo(),
        plugin: {has_avatars: true, js_resource_paths: []},
      });
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
            width: 0,
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
  });
});

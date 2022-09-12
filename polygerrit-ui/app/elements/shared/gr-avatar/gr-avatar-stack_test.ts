/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-avatar-stack';
import {
  createAccountWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {fixture, html, assert} from '@open-wc/testing';
import {stubRestApi} from '../../../test/test-utils';
import {LitElement} from 'lit';

suite('gr-avatar tests', () => {
  suite('config with avatars', () => {
    setup(() => {
      // Set up server response, so that gr-avatar is not hidden.
      stubRestApi('getConfig').resolves({
        ...createServerInfo(),
        plugin: {has_avatars: true, js_resource_paths: []},
      });
    });

    test('renders avatars', async () => {
      const accounts = [];
      for (let i = 0; i < 2; ++i) {
        accounts.push({
          ...createAccountWithId(i),
          avatars: [
            {
              url: `https://a.b.c/photo${i}.jpg`,
              height: 32,
              width: 32,
            },
          ],
        });
      }
      accounts.push({
        ...createAccountWithId(2),
        avatars: [
          {
            // Account with duplicate avatar will be skipped.
            url: 'https://a.b.c/photo1.jpg',
            height: 32,
            width: 32,
          },
        ],
      });

      const element: LitElement = await fixture(
        html`<gr-avatar-stack
          .accounts=${accounts}
          .imageSize=${32}
        ></gr-avatar-stack>`
      );
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `<gr-avatar
            style='background-image: url("https://a.b.c/photo0.jpg");'
          >
          </gr-avatar>
          <gr-avatar style='background-image: url("https://a.b.c/photo1.jpg");'>
          </gr-avatar> `
      );
      // Verify that margins are set correctly.
      const avatars = element.shadowRoot!.querySelectorAll('gr-avatar');
      assert.strictEqual(avatars.length, 2);
      assert.strictEqual(window.getComputedStyle(avatars[0]).marginLeft, '0px');
      for (let i = 1; i < avatars.length; ++i) {
        assert.strictEqual(
          window.getComputedStyle(avatars[i]).marginLeft,
          '-8px'
        );
      }
    });

    test('renders many accounts fallback', async () => {
      const accounts = [];
      for (let i = 0; i < 5; ++i) {
        accounts.push({
          ...createAccountWithId(i),
          avatars: [
            {
              url: `https://a.b.c/photo${i}.jpg`,
              height: 32,
              width: 32,
            },
          ],
        });
      }

      const element = await fixture(
        html`<gr-avatar-stack .accounts=${accounts} .imageSize=${32}>
          <span slot="fallback">Fall back!</span>
        </gr-avatar-stack>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ '<slot name="fallback"></slot>'
      );
    });

    test('renders no accounts fallback', async () => {
      // Single account without an avatar.
      const accounts = [createAccountWithId(1)];

      const element = await fixture(
        html`<gr-avatar-stack .accounts=${accounts} .imageSize=${32}>
          <span slot="fallback">Fall back!</span>
        </gr-avatar-stack>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ '<slot name="fallback"></slot>'
      );
    });
  });

  test('renders no avatars fallback', async () => {
    // Set up server response, to indicate that no avatars are being served.
    stubRestApi('getConfig').resolves({
      ...createServerInfo(),
      plugin: {has_avatars: false, js_resource_paths: []},
    });
    // Single account without an avatar.
    const accounts = [createAccountWithId(1)];

    const element = await fixture(
      html`<gr-avatar-stack .accounts=${accounts} .imageSize=${32}>
        <span slot="fallback">Fall back!</span>
      </gr-avatar-stack>`
    );
    assert.shadowDom.equal(element, /* HTML */ '<slot name="fallback"></slot>');
  });
});

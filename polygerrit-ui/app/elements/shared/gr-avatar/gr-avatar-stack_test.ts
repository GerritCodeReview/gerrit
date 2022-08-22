/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-avatar-stack';
import {
  createAccountWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {fixture, html} from '@open-wc/testing-helpers';
import {stubRestApi} from '../../../test/test-utils';

suite('gr-avatar tests', () => {
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

    const element = await fixture(
      html`<gr-avatar-stack
        .accounts=${accounts}
        .imageSize=${32}
      ></gr-avatar-stack>`
    );

    assert.shadowDom.equal(
      element,
      /* HTML */ `<gr-avatar
          style='height: 32px; width: 32px; border: 1px solid rgb(255, 255, 255); margin-right: -16px; background-image: url("https://a.b.c/photo0.jpg");'
        >
        </gr-avatar>
        <gr-avatar
          style='height: 32px; width: 32px; border: 1px solid rgb(255, 255, 255); background-image: url("https://a.b.c/photo1.jpg");'
        >
        </gr-avatar> `
    );
  });

  test('renders fallback', async () => {
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
    assert.shadowDom.equal(element, /* HTML */ '<slot name="fallback"></slot>');
  });
});

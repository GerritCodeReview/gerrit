/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-account-dropdown';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrAccountDropdown} from './gr-account-dropdown';
import {AccountInfo} from '../../../types/common';
import {createServerInfo} from '../../../test/test-data-generators';

suite('gr-account-dropdown tests', () => {
  let element: GrAccountDropdown;

  setup(async () => {
    element = await fixture(html`<gr-account-dropdown></gr-account-dropdown>`);
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-dropdown link="">
        <span> Name of user not set </span>
        <gr-avatar aria-label="Account avatar" hidden=""> </gr-avatar>
      </gr-dropdown>
    `);
  });

  test('account information', () => {
    element.account = {name: 'John Doe', email: 'john@doe.com'} as AccountInfo;
    assert.deepEqual(element.topContent, [
      {text: 'John Doe', bold: true},
      {text: 'john@doe.com'},
    ]);
  });

  test('test for account without a name', () => {
    element.account = {id: '0001'} as AccountInfo;
    assert.deepEqual(element.topContent, [
      {text: 'Name of user not set', bold: true},
      {text: ''},
    ]);
  });

  test('test for account without a name but using config', () => {
    element.config = {
      ...createServerInfo(),
      user: {
        anonymous_coward_name: 'WikiGerrit',
      },
    };
    element.account = {id: '0001'} as AccountInfo;
    assert.deepEqual(element.topContent, [
      {text: 'WikiGerrit', bold: true},
      {text: ''},
    ]);
  });

  test('test for account name as an email', () => {
    element.config = {
      ...createServerInfo(),
      user: {
        anonymous_coward_name: 'WikiGerrit',
      },
    };
    element.account = {email: 'john@doe.com'} as AccountInfo;
    assert.deepEqual(element.topContent, [
      {text: 'john@doe.com', bold: true},
      {text: 'john@doe.com'},
    ]);
  });

  test('switch account', () => {
    // Missing params.
    assert.isUndefined(element._getLinks());
    assert.isUndefined(element._getLinks(undefined));

    // No switch account link.
    assert.equal(element._getLinks('', '')!.length, 3);

    // Unparameterized switch account link.
    let links = element._getLinks('/switch-account', '')!;
    assert.equal(links.length, 4);
    assert.deepEqual(links[2], {
      name: 'Switch account',
      url: '/switch-account',
      external: true,
    });

    // Parameterized switch account link.
    links = element._getLinks('/switch-account${path}', '/c/123')!;
    assert.equal(links.length, 4);
    assert.deepEqual(links[2], {
      name: 'Switch account',
      url: '/switch-account/c/123',
      external: true,
    });
  });

  test('_interpolateUrl', () => {
    const replacements = {
      foo: 'bar',
      test: 'TEST',
    };
    const interpolate = (url: string) =>
      element._interpolateUrl(url, replacements);

    assert.equal(interpolate('test'), 'test');
    assert.equal(interpolate('${test}'), 'TEST');
    assert.equal(
      interpolate('${}, ${test}, ${TEST}, ${foo}'),
      '${}, TEST, , bar'
    );
  });
});

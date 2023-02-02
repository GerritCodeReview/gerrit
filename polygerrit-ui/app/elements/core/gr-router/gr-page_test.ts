/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {html, assert, fixture, waitUntil} from '@open-wc/testing';
import './gr-router';
import {Page, PageContext} from './gr-page';

suite('gr-page tests', () => {
  let page: Page;

  setup(() => {
    page = new Page();
    page.start({dispatch: false, popstate: false, base: ''});
  });

  teardown(() => {
    page.stop();
  });

  test('click handler', async () => {
    const spy = sinon.spy();
    page.registerRoute(/\/settings/, spy);
    const link = await fixture<HTMLAnchorElement>(
      html`<a href="/settings"></a>`
    );
    link.click();
    assert.isTrue(spy.calledOnce);
  });

  test('register route and exit', () => {
    const handleA = sinon.spy();
    const handleAExit = sinon.stub();
    page.registerRoute(/\/A/, handleA);
    page.registerExitRoute(/\/A/, handleAExit);

    page.show('/A');
    assert.equal(handleA.callCount, 1);
    assert.equal(handleAExit.callCount, 0);

    page.show('/B');
    assert.equal(handleA.callCount, 1);
    assert.equal(handleAExit.callCount, 1);
  });

  test('register, show, replace', () => {
    const handleA = sinon.spy();
    const handleB = sinon.stub();
    page.registerRoute(/\/A/, handleA);
    page.registerRoute(/\/B/, handleB);

    page.show('/A');
    assert.equal(handleA.callCount, 1);
    assert.equal(handleB.callCount, 0);

    page.show('/B');
    assert.equal(handleA.callCount, 1);
    assert.equal(handleB.callCount, 1);

    page.replace('/A');
    assert.equal(handleA.callCount, 2);
    assert.equal(handleB.callCount, 1);

    page.replace('/B');
    assert.equal(handleA.callCount, 2);
    assert.equal(handleB.callCount, 2);
  });

  test('register pattern, check context', async () => {
    let context: PageContext;
    const handler = (ctx: PageContext) => (context = ctx);
    page.registerRoute(/\/asdf\/(.*)\/qwer\/(.*)\//, handler);
    page.stop();
    page.start({dispatch: false, popstate: false, base: '/base'});

    page.show('/base/asdf/1234/qwer/abcd/');

    await waitUntil(() => !!context);
    assert.equal(context!.canonicalPath, '/base/asdf/1234/qwer/abcd/');
    assert.equal(context!.path, '/asdf/1234/qwer/abcd/');
    assert.equal(context!.querystring, '');
    assert.equal(context!.hash, '');
    assert.equal(context!.params[0], '1234');
    assert.equal(context!.params[1], 'abcd');

    page.show('/asdf//qwer////?a=b#go');

    await waitUntil(() => !!context);
    assert.equal(context!.canonicalPath, '/base/asdf//qwer////?a=b#go');
    assert.equal(context!.path, '/asdf//qwer////?a=b');
    assert.equal(context!.querystring, 'a=b');
    assert.equal(context!.hash, 'go');
    assert.equal(context!.params[0], '');
    assert.equal(context!.params[1], '//');
  });
});

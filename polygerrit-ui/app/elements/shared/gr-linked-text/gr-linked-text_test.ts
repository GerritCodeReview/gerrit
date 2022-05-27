/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-linked-text';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrLinkedText} from './gr-linked-text';
import {CommentLinks} from '../../../types/common';
import {queryAndAssert} from '../../../test/test-utils';

suite('gr-linked-text tests', () => {
  let element: GrLinkedText;

  let originalCanonicalPath: string | undefined;

  setup(async () => {
    originalCanonicalPath = window.CANONICAL_PATH;
    element = await fixture<GrLinkedText>(html`
      <gr-linked-text>
        <div id="output"></div>
      </gr-linked-text>
    `);

    sinon.stub(GerritNav, 'mapCommentlinks').value((x: CommentLinks) => x);
    element.config = {
      ph: {
        match: '([Bb]ug|[Ii]ssue)\\s*#?(\\d+)',
        link: 'https://bugs.chromium.org/p/gerrit/issues/detail?id=$2',
      },
      prefixsameinlinkandpattern: {
        match: '([Hh][Tt][Tt][Pp]example)\\s*#?(\\d+)',
        link: 'https://bugs.chromium.org/p/gerrit/issues/detail?id=$2',
      },
      changeid: {
        match: '(I[0-9a-f]{8,40})',
        link: '#/q/$1',
      },
      changeid2: {
        match: 'Change-Id: +(I[0-9a-f]{8,40})',
        link: '#/q/$1',
      },
      googlesearch: {
        match: 'google:(.+)',
        link: 'https://bing.com/search?q=$1', // html should supercede link.
        html: '<a href="https://google.com/search?q=$1">$1</a>',
      },
      hashedhtml: {
        match: 'hash:(.+)',
        html: '<a href="#/awesomesauce">$1</a>',
      },
      baseurl: {
        match: 'test (.+)',
        html: '<a href="/r/awesomesauce">$1</a>',
      },
      anotatstartwithbaseurl: {
        match: 'a test (.+)',
        html: '[Lookup: <a href="/r/awesomesauce">$1</a>]',
      },
      disabledconfig: {
        match: 'foo:(.+)',
        link: 'https://google.com/search?q=$1',
        enabled: false,
      },
    };
  });

  teardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  test('URL pattern was parsed and linked.', async () => {
    // Regular inline link.
    const url = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';
    element.content = url;
    await element.updateComplete;

    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.rel, 'noopener');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, url);
  });

  test('Bug pattern was parsed and linked', async () => {
    // "Issue/Bug" pattern.
    element.content = 'Issue 3650';
    await element.updateComplete;

    let linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    const url = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, 'Issue 3650');

    element.content = 'Bug 3650';
    await element.updateComplete;

    linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.rel, 'noopener');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, 'Bug 3650');
  });

  test('Pattern with same prefix as link was correctly parsed', async () => {
    // Pattern starts with the same prefix (`http`) as the url.
    element.content = 'httpexample 3650';
    await element.updateComplete;

    assert.equal(queryAndAssert(element, 'span#output').childNodes.length, 1);
    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    const url = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, 'httpexample 3650');
  });

  test('Change-Id pattern was parsed and linked', async () => {
    // "Change-Id:" pattern.
    const changeID = 'I11d6a37f5e9b5df0486f6c922d8836dfa780e03e';
    const prefix = 'Change-Id: ';
    element.content = prefix + changeID;
    await element.updateComplete;

    const textNode = queryAndAssert(element, 'span#output').childNodes[0];
    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[1] as HTMLAnchorElement;
    assert.equal(textNode.textContent, prefix);
    const url = '/q/' + changeID;
    assert.isFalse(linkEl.hasAttribute('target'));
    // Since url is a path, the host is added automatically.
    assert.isTrue(linkEl.href.endsWith(url));
    assert.equal(linkEl.textContent, changeID);
  });

  test('Change-Id pattern was parsed and linked with base url', async () => {
    window.CANONICAL_PATH = '/r';

    // "Change-Id:" pattern.
    const changeID = 'I11d6a37f5e9b5df0486f6c922d8836dfa780e03e';
    const prefix = 'Change-Id: ';
    element.content = prefix + changeID;
    await element.updateComplete;

    const textNode = queryAndAssert(element, 'span#output').childNodes[0];
    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[1] as HTMLAnchorElement;
    assert.equal(textNode.textContent, prefix);
    const url = '/r/q/' + changeID;
    assert.isFalse(linkEl.hasAttribute('target'));
    // Since url is a path, the host is added automatically.
    assert.isTrue(linkEl.href.endsWith(url));
    assert.equal(linkEl.textContent, changeID);
  });

  test('Multiple matches', async () => {
    element.content = 'Issue 3650\nIssue 3450';
    await element.updateComplete;

    const linkEl1 = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    const linkEl2 = queryAndAssert(element, 'span#output')
      .childNodes[2] as HTMLAnchorElement;

    assert.equal(linkEl1.target, '_blank');
    assert.equal(
      linkEl1.href,
      'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650'
    );
    assert.equal(linkEl1.textContent, 'Issue 3650');

    assert.equal(linkEl2.target, '_blank');
    assert.equal(
      linkEl2.href,
      'https://bugs.chromium.org/p/gerrit/issues/detail?id=3450'
    );
    assert.equal(linkEl2.textContent, 'Issue 3450');
  });

  test('Change-Id pattern parsed before bug pattern', async () => {
    // "Change-Id:" pattern.
    const changeID = 'I11d6a37f5e9b5df0486f6c922d8836dfa780e03e';
    const prefix = 'Change-Id: ';

    // "Issue/Bug" pattern.
    const bug = 'Issue 3650';

    const changeUrl = '/q/' + changeID;
    const bugUrl = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';

    element.content = prefix + changeID + bug;
    await element.updateComplete;

    const textNode = queryAndAssert(element, 'span#output').childNodes[0];
    const changeLinkEl = queryAndAssert(element, 'span#output')
      .childNodes[1] as HTMLAnchorElement;
    const bugLinkEl = queryAndAssert(element, 'span#output')
      .childNodes[2] as HTMLAnchorElement;

    assert.equal(textNode.textContent, prefix);

    assert.isFalse(changeLinkEl.hasAttribute('target'));
    assert.isTrue(changeLinkEl.href.endsWith(changeUrl));
    assert.equal(changeLinkEl.textContent, changeID);

    assert.equal(bugLinkEl.target, '_blank');
    assert.equal(bugLinkEl.href, bugUrl);
    assert.equal(bugLinkEl.textContent, 'Issue 3650');
  });

  test('html field in link config', async () => {
    element.content = 'google:do a barrel roll';
    await element.updateComplete;

    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.equal(
      linkEl.getAttribute('href'),
      'https://google.com/search?q=do a barrel roll'
    );
    assert.equal(linkEl.textContent, 'do a barrel roll');
  });

  test('removing hash from links', async () => {
    element.content = 'hash:foo';
    await element.updateComplete;

    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('html with base url', async () => {
    window.CANONICAL_PATH = '/r';

    element.content = 'test foo';
    await element.updateComplete;

    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/r/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('a is not at start', async () => {
    window.CANONICAL_PATH = '/r';

    element.content = 'a test foo';
    await element.updateComplete;

    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[1] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/r/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('hash html with base url', async () => {
    window.CANONICAL_PATH = '/r';

    element.content = 'hash:foo';
    await element.updateComplete;

    const linkEl = queryAndAssert(element, 'span#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/r/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('disabled config', async () => {
    element.content = 'foo:baz';
    await element.updateComplete;

    assert.equal(queryAndAssert(element, 'span#output').innerHTML, 'foo:baz');
  });

  test('R=email labels link correctly', async () => {
    element.removeZeroWidthSpace = true;
    element.content = 'R=\u200Btest@google.com';
    await element.updateComplete;

    assert.equal(
      queryAndAssert(element, 'span#output').textContent,
      'R=test@google.com'
    );
    assert.equal(
      queryAndAssert(element, 'span#output').innerHTML.match(/(R=<a)/g)!.length,
      1
    );
  });

  test('CC=email labels link correctly', async () => {
    element.removeZeroWidthSpace = true;
    element.content = 'CC=\u200Btest@google.com';
    await element.updateComplete;

    assert.equal(
      queryAndAssert(element, 'span#output').textContent,
      'CC=test@google.com'
    );
    assert.equal(
      queryAndAssert(element, 'span#output').innerHTML.match(/(CC=<a)/g)!
        .length,
      1
    );
  });

  test('only {http,https,mailto} protocols are linkified', async () => {
    element.content = 'xx mailto:test@google.com yy';
    await element.updateComplete;

    let links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'mailto:test@google.com');
    assert.equal(links[0].innerHTML, 'mailto:test@google.com');

    element.content = 'xx http://google.com yy';
    await element.updateComplete;

    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'http://google.com');
    assert.equal(links[0].innerHTML, 'http://google.com');

    element.content = 'xx https://google.com yy';
    await element.updateComplete;

    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'https://google.com');
    assert.equal(links[0].innerHTML, 'https://google.com');

    element.content = 'xx ssh://google.com yy';
    await element.updateComplete;

    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 0);

    element.content = 'xx ftp://google.com yy';
    await element.updateComplete;

    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 0);
  });

  test('links without leading whitespace are linkified', async () => {
    element.content = 'xx abcmailto:test@google.com yy';
    await element.updateComplete;

    assert.equal(
      queryAndAssert(element, 'span#output').innerHTML.substr(0, 6),
      'xx abc'
    );
    let links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'mailto:test@google.com');
    assert.equal(links[0].innerHTML, 'mailto:test@google.com');

    element.content = 'xx defhttp://google.com yy';
    await element.updateComplete;

    assert.equal(
      queryAndAssert(element, 'span#output').innerHTML.substr(0, 6),
      'xx def'
    );
    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'http://google.com');
    assert.equal(links[0].innerHTML, 'http://google.com');

    element.content = 'xx qwehttps://google.com yy';
    await element.updateComplete;

    assert.equal(
      queryAndAssert(element, 'span#output').innerHTML.substr(0, 6),
      'xx qwe'
    );
    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'https://google.com');
    assert.equal(links[0].innerHTML, 'https://google.com');

    // Non-latin character
    element.content = 'xx абвhttps://google.com yy';
    await element.updateComplete;

    assert.equal(
      queryAndAssert(element, 'span#output').innerHTML.substr(0, 6),
      'xx абв'
    );
    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'https://google.com');
    assert.equal(links[0].innerHTML, 'https://google.com');

    element.content = 'xx ssh://google.com yy';
    await element.updateComplete;

    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 0);

    element.content = 'xx ftp://google.com yy';
    await element.updateComplete;

    links = queryAndAssert(element, 'span#output').querySelectorAll('a');
    assert.equal(links.length, 0);
  });

  test('overlapping links', async () => {
    element.config = {
      b1: {
        match: '(B:\\s*)(\\d+)',
        html: '$1<a href="ftp://foo/$2">$2</a>',
      },
      b2: {
        match: '(B:\\s*\\d+\\s*,\\s*)(\\d+)',
        html: '$1<a href="ftp://foo/$2">$2</a>',
      },
    };
    element.content = '- B: 123, 45';
    await element.updateComplete;

    const links = element.querySelectorAll('a');

    assert.equal(links.length, 2);
    assert.equal(
      queryAndAssert<HTMLSpanElement>(element, 'span').textContent,
      '- B: 123, 45'
    );

    assert.equal(links[0].href, 'ftp://foo/123');
    assert.equal(links[0].textContent, '123');

    assert.equal(links[1].href, 'ftp://foo/45');
    assert.equal(links[1].textContent, '45');
  });

  test('_contentOrConfigChanged called with config', async () => {
    const contentConfigStub = sinon.stub(element, '_contentOrConfigChanged');
    element.content = 'some text';
    await element.updateComplete;

    assert.isTrue(contentConfigStub.called);
  });
});

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
import './gr-linked-text';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {GrLinkedText} from './gr-linked-text';
import {CommentLinks} from '../../../types/common';
import {queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromTemplate(html`
  <gr-linked-text>
    <div id="output"></div>
  </gr-linked-text>
`);

suite('gr-linked-text tests', () => {
  let element: GrLinkedText;

  let originalCanonicalPath: string | undefined;

  setup(() => {
    originalCanonicalPath = window.CANONICAL_PATH;
    element = basicFixture.instantiate() as GrLinkedText;

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

  test('URL pattern was parsed and linked.', () => {
    // Regular inline link.
    const url = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';
    element.content = url;
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.rel, 'noopener');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, url);
  });

  test('Bug pattern was parsed and linked', () => {
    // "Issue/Bug" pattern.
    element.content = 'Issue 3650';

    let linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    const url = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, 'Issue 3650');

    element.content = 'Bug 3650';
    linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.rel, 'noopener');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, 'Bug 3650');
  });

  test('Pattern with same prefix as link was correctly parsed', () => {
    // Pattern starts with the same prefix (`http`) as the url.
    element.content = 'httpexample 3650';

    assert.equal(queryAndAssert(element, '#output').childNodes.length, 1);
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    const url = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';
    assert.equal(linkEl.target, '_blank');
    assert.equal(linkEl.href, url);
    assert.equal(linkEl.textContent, 'httpexample 3650');
  });

  test('Change-Id pattern was parsed and linked', () => {
    // "Change-Id:" pattern.
    const changeID = 'I11d6a37f5e9b5df0486f6c922d8836dfa780e03e';
    const prefix = 'Change-Id: ';
    element.content = prefix + changeID;

    const textNode = queryAndAssert(element, '#output').childNodes[0];
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[1] as HTMLAnchorElement;
    assert.equal(textNode.textContent, prefix);
    const url = '/q/' + changeID;
    assert.isFalse(linkEl.hasAttribute('target'));
    // Since url is a path, the host is added automatically.
    assert.isTrue(linkEl.href.endsWith(url));
    assert.equal(linkEl.textContent, changeID);
  });

  test('Change-Id pattern was parsed and linked with base url', () => {
    window.CANONICAL_PATH = '/r';

    // "Change-Id:" pattern.
    const changeID = 'I11d6a37f5e9b5df0486f6c922d8836dfa780e03e';
    const prefix = 'Change-Id: ';
    element.content = prefix + changeID;

    const textNode = queryAndAssert(element, '#output').childNodes[0];
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[1] as HTMLAnchorElement;
    assert.equal(textNode.textContent, prefix);
    const url = '/r/q/' + changeID;
    assert.isFalse(linkEl.hasAttribute('target'));
    // Since url is a path, the host is added automatically.
    assert.isTrue(linkEl.href.endsWith(url));
    assert.equal(linkEl.textContent, changeID);
  });

  test('Multiple matches', () => {
    element.content = 'Issue 3650\nIssue 3450';
    const linkEl1 = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    const linkEl2 = queryAndAssert(element, '#output')
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

  test('Change-Id pattern parsed before bug pattern', () => {
    // "Change-Id:" pattern.
    const changeID = 'I11d6a37f5e9b5df0486f6c922d8836dfa780e03e';
    const prefix = 'Change-Id: ';

    // "Issue/Bug" pattern.
    const bug = 'Issue 3650';

    const changeUrl = '/q/' + changeID;
    const bugUrl = 'https://bugs.chromium.org/p/gerrit/issues/detail?id=3650';

    element.content = prefix + changeID + bug;

    const textNode = queryAndAssert(element, '#output').childNodes[0];
    const changeLinkEl = queryAndAssert(element, '#output')
      .childNodes[1] as HTMLAnchorElement;
    const bugLinkEl = queryAndAssert(element, '#output')
      .childNodes[2] as HTMLAnchorElement;

    assert.equal(textNode.textContent, prefix);

    assert.isFalse(changeLinkEl.hasAttribute('target'));
    assert.isTrue(changeLinkEl.href.endsWith(changeUrl));
    assert.equal(changeLinkEl.textContent, changeID);

    assert.equal(bugLinkEl.target, '_blank');
    assert.equal(bugLinkEl.href, bugUrl);
    assert.equal(bugLinkEl.textContent, 'Issue 3650');
  });

  test('html field in link config', () => {
    element.content = 'google:do a barrel roll';
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.equal(
      linkEl.getAttribute('href'),
      'https://google.com/search?q=do a barrel roll'
    );
    assert.equal(linkEl.textContent, 'do a barrel roll');
  });

  test('removing hash from links', () => {
    element.content = 'hash:foo';
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('html with base url', () => {
    window.CANONICAL_PATH = '/r';

    element.content = 'test foo';
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/r/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('a is not at start', () => {
    window.CANONICAL_PATH = '/r';

    element.content = 'a test foo';
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[1] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/r/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('hash html with base url', () => {
    window.CANONICAL_PATH = '/r';

    element.content = 'hash:foo';
    const linkEl = queryAndAssert(element, '#output')
      .childNodes[0] as HTMLAnchorElement;
    assert.isTrue(linkEl.href.endsWith('/r/awesomesauce'));
    assert.equal(linkEl.textContent, 'foo');
  });

  test('disabled config', () => {
    element.content = 'foo:baz';
    assert.equal(queryAndAssert(element, '#output').innerHTML, 'foo:baz');
  });

  test('R=email labels link correctly', () => {
    element.removeZeroWidthSpace = true;
    element.content = 'R=\u200Btest@google.com';
    assert.equal(
      queryAndAssert(element, '#output').textContent,
      'R=test@google.com'
    );
    assert.equal(
      queryAndAssert(element, '#output').innerHTML.match(/(R=<a)/g)!.length,
      1
    );
  });

  test('CC=email labels link correctly', () => {
    element.removeZeroWidthSpace = true;
    element.content = 'CC=\u200Btest@google.com';
    assert.equal(
      queryAndAssert(element, '#output').textContent,
      'CC=test@google.com'
    );
    assert.equal(
      queryAndAssert(element, '#output').innerHTML.match(/(CC=<a)/g)!.length,
      1
    );
  });

  test('only {http,https,mailto} protocols are linkified', () => {
    element.content = 'xx mailto:test@google.com yy';
    let links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'mailto:test@google.com');
    assert.equal(links[0].innerHTML, 'mailto:test@google.com');

    element.content = 'xx http://google.com yy';
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'http://google.com');
    assert.equal(links[0].innerHTML, 'http://google.com');

    element.content = 'xx https://google.com yy';
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'https://google.com');
    assert.equal(links[0].innerHTML, 'https://google.com');

    element.content = 'xx ssh://google.com yy';
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 0);

    element.content = 'xx ftp://google.com yy';
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 0);
  });

  test('links without leading whitespace are linkified', () => {
    element.content = 'xx abcmailto:test@google.com yy';
    assert.equal(
      queryAndAssert(element, '#output').innerHTML.substr(0, 6),
      'xx abc'
    );
    let links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'mailto:test@google.com');
    assert.equal(links[0].innerHTML, 'mailto:test@google.com');

    element.content = 'xx defhttp://google.com yy';
    assert.equal(
      queryAndAssert(element, '#output').innerHTML.substr(0, 6),
      'xx def'
    );
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'http://google.com');
    assert.equal(links[0].innerHTML, 'http://google.com');

    element.content = 'xx qwehttps://google.com yy';
    assert.equal(
      queryAndAssert(element, '#output').innerHTML.substr(0, 6),
      'xx qwe'
    );
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'https://google.com');
    assert.equal(links[0].innerHTML, 'https://google.com');

    // Non-latin character
    element.content = 'xx абвhttps://google.com yy';
    assert.equal(
      queryAndAssert(element, '#output').innerHTML.substr(0, 6),
      'xx абв'
    );
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 1);
    assert.equal(links[0].getAttribute('href'), 'https://google.com');
    assert.equal(links[0].innerHTML, 'https://google.com');

    element.content = 'xx ssh://google.com yy';
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 0);

    element.content = 'xx ftp://google.com yy';
    links = queryAndAssert(element, '#output').querySelectorAll('a');
    assert.equal(links.length, 0);
  });

  test('overlapping links', () => {
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
    const links = element.root!.querySelectorAll('a');

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

  test('_contentOrConfigChanged called with config', () => {
    const contentStub = sinon.stub(element, '_contentChanged');
    const contentConfigStub = sinon.stub(element, '_contentOrConfigChanged');
    element.content = 'some text';
    assert.isTrue(contentStub.called);
    assert.isTrue(contentConfigStub.called);
  });
});

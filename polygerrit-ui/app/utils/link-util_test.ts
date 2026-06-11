/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {linkifyUrlsAndApplyRewrite} from './link-util';
import {assert} from '@open-wc/testing';

suite('link-util tests', () => {
  function link(text: string, href: string) {
    return `<a href="${href}" rel="noopener noreferrer" target="_blank">${text}</a>`;
  }

  function internalLink(text: string, href: string) {
    return `<a href="${href}">${text}</a>`;
  }

  suite('link rewrites', () => {
    test('without text', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo', {
          fooLinkWithoutText: {
            match: 'foo',
            link: 'http://foo.gov',
          },
        }),
        link('foo', 'http://foo.gov')
      );
    });

    test('with text', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo', {
          fooLinkWithText: {
            match: 'foo',
            link: 'http://foo.gov',
            text: 'foo site',
          },
        }),
        link('foo site', 'http://foo.gov')
      );
    });

    test('with prefix and suffix', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('there are 12 foos here', {
          fooLinkWithText: {
            match: '(.*)(bug|foo)s(.*)',
            link: 'http://$2.gov',
            text: '$2 list',
            prefix: '$1on the ',
            suffix: '$3',
          },
        }),
        `there are 12 on the ${link('foo list', 'http://foo.gov')} here`
      );
    });

    test('multiple matches', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo foo', {
          foo: {
            match: 'foo',
            link: 'http://foo.gov',
          },
        }),
        `${link('foo', 'http://foo.gov')} ${link('foo', 'http://foo.gov')}`
      );
    });
  });

  test('for overlapping rewrites prefer the latest ending', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('foobarbaz', {
        foo: {
          match: 'foo',
          link: 'http://foo.gov',
        },
        foobarbaz: {
          match: 'foobarbaz',
          link: 'http://foobarbaz.gov',
        },
        foobar: {
          match: 'foobar',
          link: 'http://foobar.gov',
        },
      }),
      link('foobarbaz', 'http://foobarbaz.gov')
    );
  });

  test('overlapping rewrites with same ending prefers earliest start', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('foobarbaz', {
        foo: {
          match: 'baz',
          link: 'http://Baz.gov',
        },
        foobarbaz: {
          match: 'foobarbaz',
          link: 'http://FooBarBaz.gov',
        },
        foobar: {
          match: 'barbaz',
          link: 'http://BarBaz.gov',
        },
      }),
      link('foobarbaz', 'http://FooBarBaz.gov')
    );
  });

  test('removed overlapping rewrites do not prevent other rewrites', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('foobarbaz', {
        foo: {
          match: 'foo',
          link: 'http://FOO',
        },
        oobarba: {
          match: 'oobarba',
          link: 'http://OOBARBA',
        },
        baz: {
          match: 'baz',
          link: 'http://BAZ',
        },
      }),
      `${link('foo', 'http://FOO')}bar${link('baz', 'http://BAZ')}`
    );
  });

  test('rewrites do not interfere with each other matching', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('bugs: 123 234 345', {
        bug1: {
          match: '(bugs:) (\\d+)',
          prefix: '$1 ',
          link: 'bug/$2',
          text: 'bug/$2',
        },
        bug2: {
          match: '(bugs:) (\\d+) (\\d+)',
          prefix: '$1 $2 ',
          link: 'bug/$3',
          text: 'bug/$3',
        },
        bug3: {
          match: '(bugs:) (\\d+) (\\d+) (\\d+)',
          prefix: '$1 $2 $3 ',
          link: 'bug/$4',
          text: 'bug/$4',
        },
      }),
      `bugs: ${internalLink('bug/123', 'bug/123')} ${internalLink(
        'bug/234',
        'bug/234'
      )} ${internalLink('bug/345', 'bug/345')}`
    );
  });
});

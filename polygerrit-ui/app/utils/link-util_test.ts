/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {linkifyUrlsAndApplyRewrite} from './link-util';
import {assert} from '@open-wc/testing';

suite('link-util tests', () => {
  function link(text: string, href: string) {
    return `<a href="${href}" rel="noopener" target="_blank">${text}</a>`;
  }

  suite('link rewrites', () => {
    test('default linking', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('http://www.google.com', {}),
        link('http://www.google.com', 'http://www.google.com')
      );
      assert.equal(
        linkifyUrlsAndApplyRewrite('https://www.google.com', {}),
        link('https://www.google.com', 'https://www.google.com')
      );
    });

    test('without text', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo', {
          fooLinkWithoutText: {
            match: 'foo',
            link: 'foo.gov',
          },
        }),
        link('foo', 'foo.gov')
      );
    });

    test('with text', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo', {
          fooLinkWithText: {
            match: 'foo',
            link: 'foo.gov',
            text: 'foo site',
          },
        }),
        link('foo site', 'foo.gov')
      );
    });

    test('with prefix and suffix', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('there are 12 foos here', {
          fooLinkWithText: {
            match: '(.*)(bug|foo)s(.*)',
            link: '$2.gov',
            text: '$2 list',
            prefix: '$1on the ',
            suffix: '$3',
          },
        }),
        `there are 12 on the ${link('foo list', 'foo.gov')} here`
      );
    });

    test('multiple matches', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo foo', {
          foo: {
            match: 'foo',
            link: 'foo.gov',
          },
        }),
        `${link('foo', 'foo.gov')} ${link('foo', 'foo.gov')}`
      );
    });
  });

  test('for overlapping rewrites prefer the latest ending', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('foobarbaz', {
        foo: {
          match: 'foo',
          link: 'foo.gov',
        },
        foobarbaz: {
          match: 'foobarbaz',
          link: 'foobarbaz.gov',
        },
        foobar: {
          match: 'foobar',
          link: 'foobar.gov',
        },
      }),
      link('foobarbaz', 'foobarbaz.gov')
    );
  });

  test('overlapping rewrites with same ending prefers earliest start', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('foobarbaz', {
        foo: {
          match: 'baz',
          link: 'Baz.gov',
        },
        foobarbaz: {
          match: 'foobarbaz',
          link: 'FooBarBaz.gov',
        },
        foobar: {
          match: 'barbaz',
          link: 'BarBaz.gov',
        },
      }),
      link('foobarbaz', 'FooBarBaz.gov')
    );
  });

  test('removed overlapping rewrites do not prevent other rewrites', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('foobarbaz', {
        foo: {
          match: 'foo',
          link: 'FOO',
        },
        oobarba: {
          match: 'oobarba',
          link: 'OOBARBA',
        },
        baz: {
          match: 'baz',
          link: 'BAZ',
        },
      }),
      `${link('foo', 'FOO')}bar${link('baz', 'BAZ')}`
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
      `bugs: ${link('bug/123', 'bug/123')} ${link('bug/234', 'bug/234')} ${link(
        'bug/345',
        'bug/345'
      )}`
    );
  });
});

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

  suite('html rewrites', () => {
    test('basic case', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo', {
          foo: {
            match: '(foo)',
            html: '<div>$1</div>',
          },
        }),
        '<div>foo</div>'
      );
    });

    test('only inserts', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo', {
          foo: {
            match: 'foo',
            html: 'foo bar',
          },
        }),
        'foo bar'
      );
    });

    test('only deletes', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo bar baz', {
          bar: {
            match: 'bar',
            html: '',
          },
        }),
        'foo  baz'
      );
    });

    test('multiple matches', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('foo foo', {
          foo: {
            match: '(foo)',
            html: '<div>$1</div>',
          },
        }),
        '<div>foo</div> <div>foo</div>'
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
          html: '<div>foobarbaz.gov</div>',
        },
        foobar: {
          match: 'foobar',
          link: 'foobar.gov',
        },
      }),
      '<div>foobarbaz.gov</div>'
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
          html: '<div>FooBarBaz.gov</div>',
        },
        foobar: {
          match: 'barbaz',
          link: 'BarBaz.gov',
        },
      }),
      '<div>FooBarBaz.gov</div>'
    );
  });

  test('removed overlapping rewrites do not prevent other rewrites', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('foobarbaz', {
        foo: {
          match: 'foo',
          html: 'FOO',
        },
        oobarba: {
          match: 'oobarba',
          html: 'OOBARBA',
        },
        baz: {
          match: 'baz',
          html: 'BAZ',
        },
      }),
      'FOObarBAZ'
    );
  });

  test('rewrites do not interfere with each other matching', () => {
    assert.equal(
      linkifyUrlsAndApplyRewrite('bugs: 123 234 345', {
        bug1: {
          match: '(bugs:) (\\d+)',
          html: '$1 <div>bug/$2</div>',
        },
        bug2: {
          match: '(bugs:) (\\d+) (\\d+)',
          html: '$1 $2 <div>bug/$3</div>',
        },
        bug3: {
          match: '(bugs:) (\\d+) (\\d+) (\\d+)',
          html: '$1 $2 $3 <div>bug/$4</div>',
        },
      }),
      'bugs: <div>bug/123</div> <div>bug/234</div> <div>bug/345</div>'
    );
  });
});

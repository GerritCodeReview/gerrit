/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {linkifyUrlsAndApplyRewrite, linkifyNormalUrls} from './link-util';
import {assert} from '@open-wc/testing';

suite('link-util tests', () => {
  function link(text: string, href: string) {
    return `<a href="${href}" rel="noopener" target="_blank">${text}</a>`;
  }

  suite('link rewrites', () => {
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

    test('does not apply within normal links', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('google.com', {
          ogle: {
            match: 'ogle',
            link: 'gerritcodereview.com',
          },
        }),
        link('google.com', 'http://google.com')
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

    test('does not apply within normal links', () => {
      assert.equal(
        linkifyUrlsAndApplyRewrite('google.com', {
          ogle: {
            match: 'ogle',
            html: '<div>gerritcodereview.com<div>',
          },
        }),
        link('google.com', 'http://google.com')
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

  suite('normal urls', () => {
    test('links urls', () => {
      const googleLink = link('google.com', 'http://google.com');
      const mapsLink = link('maps.google.com', 'http://maps.google.com');

      assert.equal(
        linkifyNormalUrls('google.com, maps.google.com'),
        `${googleLink}, ${mapsLink}`
      );
    });

    test('links emails without including R= prefix', () => {
      const fooEmail = link('foo@gmail.com', 'mailto:foo@gmail.com');
      const barEmail = link('bar@gmail.com', 'mailto:bar@gmail.com');
      assert.equal(
        linkifyNormalUrls('R=foo@gmail.com, bar@gmail.com'),
        `R=${fooEmail}, ${barEmail}`
      );
    });

    test('links emails without including CC= prefix', () => {
      const fooEmail = link('foo@gmail.com', 'mailto:foo@gmail.com');
      const barEmail = link('bar@gmail.com', 'mailto:bar@gmail.com');
      assert.equal(
        linkifyNormalUrls('CC=foo@gmail.com, bar@gmail.com'),
        `CC=${fooEmail}, ${barEmail}`
      );
    });

    test('links emails maintains R= and CC= within addresses', () => {
      const fooBarBazEmail = link(
        'fooR=barCC=baz@gmail.com',
        'mailto:fooR=barCC=baz@gmail.com'
      );
      assert.equal(
        linkifyNormalUrls('fooR=barCC=baz@gmail.com'),
        fooBarBazEmail
      );
    });
  });
});

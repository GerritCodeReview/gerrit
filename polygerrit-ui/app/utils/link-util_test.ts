/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  applyHtmlRewritesFromConfig,
  applyLinkRewritesFromConfig,
  linkifyNormalUrls,
} from './link-util';
import {assert} from '@open-wc/testing';

suite('link-util tests', () => {
  function link(text: string, href: string) {
    return `<a href="${href}" rel="noopener" target="_blank">${text}</a>`;
  }

  test('applyHtmlRewritesFromConfig', () => {
    assert.equal(
      applyHtmlRewritesFromConfig('#12345 foo', {
        'number-emphasizer': {
          match: '#(\\d+)',
          html: '<h1>Change $1 is the best change</h1>',
        },
        'foo-capitalizer': {
          match: 'foo',
          html: '<div>FOO</div>',
        },
      }),
      '<h1>Change 12345 is the best change</h1> <div>FOO</div>'
    );
  });

  test('applyLinkRewritesFromConfig', () => {
    const linkedNumber = link('#12345', 'google.com/12345');
    const linkedFoo = link('foo', 'foo.gov');
    const linkedBar = link('Bar Page: 300', 'bar.com/page?id=300');
    assert.equal(
      applyLinkRewritesFromConfig('#12345 foo crowbar:12 bar:300', {
        'number-linker': {
          match: '#(\\d+)',
          link: 'google.com/$1',
        },
        'foo-linker': {
          match: 'foo',
          link: 'foo.gov',
        },
        'advanced-link': {
          match: '(^|\\s)bar:(\\d+)($|\\s)',
          link: 'bar.com/page?id=$2',
          text: 'Bar Page: $2',
          prefix: '$1',
          suffix: '$3'
        }
      }),
      `${linkedNumber} ${linkedFoo} crowbar:12 ${linkedBar}`
    );
  });

  suite('linkifyNormalUrls', () => {
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

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
    assert.equal(
      applyLinkRewritesFromConfig('#12345 foo', {
        'number-linker': {
          match: '#(\\d+)',
          link: 'google.com/$1',
        },
        'foo-linker': {
          match: 'foo',
          link: 'foo.gov',
        },
      }),
      `${linkedNumber} ${linkedFoo}`
    );
  });
  test('linkifyNormalUrls', () => {
    const googleLink = link('google.com', 'http://google.com');
    const mapsLink = link('maps.google.com', 'http://maps.google.com');

    assert.equal(
      linkifyNormalUrls('google.com, maps.google.com'),
      `${googleLink}, ${mapsLink}`
    );
  });
});

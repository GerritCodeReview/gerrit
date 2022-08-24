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

suite('link-util tests', () => {
  test('applyHtmlRewritesFromConfig', () => {
    assert.equal(applyHtmlRewritesFromConfig('', {}), '');
    // TODO
  });
  test('applyLinkRewritesFromConfig', () => {
    assert.equal(applyLinkRewritesFromConfig('', {}), '');
    // TODO
  });
  test('linkifyNormalUrls', () => {
    assert.equal(linkifyNormalUrls(''), '');
    // TODO
  });
});

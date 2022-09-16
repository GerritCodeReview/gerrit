/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import 'ba-linkify/ba-linkify';
import {CommentLinks} from '../types/common';
import {getBaseUrl} from './url-util';

export function linkifyNormalUrls(base: string): string {
  // Some tools are known to look for reviewers/CCs by finding lines such as
  // "R=foo@gmail.com, bar@gmail.com". However, "=" is technically a valid email
  // character, so ba-linkify interprets the entire string "R=foo@gmail.com" as
  // an email address. To fix this, we insert a zero width space character
  // \u200B before linking that prevents ba-linkify from associating the prefix
  // with the email. After linking we remove the zero width space.
  const baseWithZeroWidthSpace = base.replace(/^(R=|CC=)/g, '$&\u200B');
  const parts: string[] = [];
  window.linkify(baseWithZeroWidthSpace, {
    callback: (text, href) => {
      const result = href ? createLinkTemplate(text, href) : text;
      const resultWithoutZeroWidthSpace = result.replace(/\u200B/g, '');
      parts.push(resultWithoutZeroWidthSpace);
    },
  });
  return parts.join('');
}

export function applyLinkRewritesFromConfig(
  base: string,
  repoCommentLinks: CommentLinks
) {
  const linkRewritesFromConfig = Object.values(repoCommentLinks).filter(
    commentLinkInfo => commentLinkInfo.enabled !== false && commentLinkInfo.link
  );
  const rewrites = linkRewritesFromConfig.map(rewrite => {
    const replacementHref = rewrite.link!.startsWith('/')
      ? `${getBaseUrl()}${rewrite.link!}`
      : rewrite.link!;
    return {
      match: new RegExp(rewrite.match, 'g'),
      replace: createLinkTemplate('$&', replacementHref),
    };
  });
  return applyRewrites(base, rewrites);
}

export function applyHtmlRewritesFromConfig(
  base: string,
  repoCommentLinks: CommentLinks
) {
  const htmlRewritesFromConfig = Object.values(repoCommentLinks).filter(
    commentLinkInfo => commentLinkInfo.enabled !== false && commentLinkInfo.html
  );
  const rewrites = htmlRewritesFromConfig.map(rewrite => {
    return {
      match: new RegExp(rewrite.match, 'g'),
      replace: rewrite.html!,
    };
  });
  return applyRewrites(base, rewrites);
}

function applyRewrites(
  base: string,
  rewrites: {match: RegExp | string; replace: string}[]
) {
  return rewrites.reduce(
    (text, rewrite) => text.replace(rewrite.match, rewrite.replace),
    base
  );
}

function createLinkTemplate(displayText: string, href: string) {
  return `<a href="${href}" rel="noopener" target="_blank">${displayText}</a>`;
}

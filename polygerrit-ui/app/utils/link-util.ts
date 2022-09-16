/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import 'ba-linkify/ba-linkify';
import {CommentLinks} from '../types/common';
import {getBaseUrl} from './url-util';

const REVIEWER_CC_LIST_START = /\b(CC=|R=)([^\b]+)/;

export function linkifyNormalUrls(base: string): string {
  const parts: string[] = [];
  window.linkify(base, {
    callback: (text, href) => {
      if (href !== undefined) {
        // Some tools are known to check commit message for reviewers/CC emails
        // by looking for lines with R= or CC=. However "=" is technically a
        // valid email part, so we remove these from emails if they are prefixes
        // only.
        const match = text.match(REVIEWER_CC_LIST_START);
        if (match !== null) {
          // An example match will be: ["R=foo@gmail.com","R=", "foo@gmail.com"]
          // We should output:
          //   R=<a href="mailto:foo@gmail.com">foo@gmail.com</a>
          // rather than:
          //   <a href="mailto:R=foo@gmail.com">R=foo@gmail.com</a>
          parts.push(
            `${match[1]}${createLinkTemplate(
              match[2],
              href.replace(match[0], match[2])
            )}`
          );
        } else {
          parts.push(createLinkTemplate(text, href));
        }
      } else {
        parts.push(text);
      }
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

/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import 'ba-linkify/ba-linkify';
import {CommentLinkInfo, CommentLinks} from '../types/common';
import {getSharedPrefix, getSharedSuffix, trimMatching} from './string-util';
import {getBaseUrl} from './url-util';

/**
 * Finds links within the base string and convert them to HTML. Config-based
 * rewrites are only applied on text that is not linked by the default linking
 * library.
 */
export function linkifyUrlsAndApplyRewrite(
  base: string,
  repoCommentLinks: CommentLinks
): string {
  const parts: string[] = [];
  window.linkify(insertZeroWidthSpace(base), {
    callback: (text, href) => {
      if (href) {
        parts.push(removeZeroWidthSpace(createLinkTemplate(href, text)));
      } else {
        const rewriteResults = getRewriteResultsFromConfig(
          text,
          repoCommentLinks
        );
        parts.push(removeZeroWidthSpace(applyRewrites(text, rewriteResults)));
      }
    },
  });
  return parts.join('');
}

/**
 * Generates a list of rewrites that would be applied to a base string. They are
 * not applied immediately to the base text because one rewrite may interfere or
 * overlap with a later rewrite. Only after all rewrites are known they are
 * carefully merged with `applyRewrites`.
 */
function getRewriteResultsFromConfig(
  base: string,
  repoCommentLinks: CommentLinks
): RewriteResult[] {
  const enabledRewrites = Object.values(repoCommentLinks).filter(
    commentLinkInfo =>
      commentLinkInfo.enabled !== false &&
      (commentLinkInfo.link || commentLinkInfo.html)
  );
  return enabledRewrites.flatMap(rewrite => {
    const regexp = new RegExp(rewrite.match, 'g');
    const partialResults: RewriteResult[] = [];
    let match: RegExpExecArray | null;

    while ((match = regexp.exec(base)) !== null) {
      const fullReplacementText = getReplacementText(match[0], rewrite);
      // The replacement may not be changing the entire matched substring so we
      // "trim" the replacement position and text to the part that is actually
      // different. This makes sure that unchanged portions are still eligible
      // for other rewrites without being rejected as overlaps during
      // `applyRewrites`. The new `replacementText` is not eligible for other
      // rewrites since it would introduce unexpected interactions between
      // rewrites depending on their order of definition/execution.
      const replacementText = trimMatching(fullReplacementText, match[0]);

      partialResults.push({
        replacedTextStartPosition:
          match.index + getSharedPrefix(match[0], fullReplacementText).length,
        replacedTextEndPosition:
          match.index +
          (match[0].length -
            getSharedSuffix(match[0], fullReplacementText).length),
        replacementText,
      });
    }
    return partialResults;
  });
}

/**
 * Applies all the rewrites to the given base string. To resolve cases where
 * multiple rewrites target overlapping pieces of the base string, the rewrite
 * that ends latest is kept and the rest are not applied and discarded.
 */
function applyRewrites(base: string, rewriteResults: RewriteResult[]): string {
  const rewritesByEndPosition = [...rewriteResults]
    .sort((a, b) => {
      if (b.replacedTextEndPosition !== a.replacedTextEndPosition) {
        return b.replacedTextEndPosition - a.replacedTextEndPosition;
      }
      return a.replacedTextStartPosition - b.replacedTextStartPosition;
    })
    .filter(
      (a, i, array) =>
        // Remove overlaps by only keeping entries whose first overlap is with
        // themselves.
        array.findIndex(
          b => b.replacedTextStartPosition < a.replacedTextEndPosition
        ) === i
    );
  return rewritesByEndPosition.reduce(
    (text, rewrite) =>
      `${text.substring(0, rewrite.replacedTextStartPosition)}${
        rewrite.replacementText
      }${text.substring(rewrite.replacedTextEndPosition)}`,
    base
  );
}

/**
 * For a given regexp match, apply the rewrite based on the rewrite's type and
 * return the resulting string.
 */
function getReplacementText(
  matchedText: string,
  rewrite: CommentLinkInfo
): string {
  if (rewrite.link) {
    const replacementHref = rewrite.link.startsWith('/')
      ? `${getBaseUrl()}${rewrite.link}`
      : rewrite.link;
    const regexp = new RegExp(rewrite.match, 'g');
    return matchedText.replace(
      regexp,
      createLinkTemplate(
        replacementHref,
        rewrite.text ?? '$&',
        rewrite.prefix,
        rewrite.suffix
      )
    );
  } else if (rewrite.html) {
    return matchedText.replace(new RegExp(rewrite.match, 'g'), rewrite.html);
  } else {
    throw new Error('commentLinkInfo is not a link or html rewrite');
  }
}

/**
 * Some tools are known to look for reviewers/CCs by finding lines such as
 * "R=foo@gmail.com, bar@gmail.com". However, "=" is technically a valid email
 * character, so ba-linkify interprets the entire string "R=foo@gmail.com" as an
 * email address. To fix this, we insert a zero width space character \u200B
 * before linking that prevents ba-linkify from associating the prefix with the
 * email. After linking we remove the zero width space.
 */
function insertZeroWidthSpace(base: string) {
  return base.replace(/^(R=|CC=)/g, '$&\u200B');
}

function removeZeroWidthSpace(base: string) {
  return base.replace(/\u200B/g, '');
}

function createLinkTemplate(
  href: string,
  displayText: string,
  prefix?: string,
  suffix?: string
) {
  return `${
    prefix ?? ''
  }<a href="${href}" rel="noopener" target="_blank">${displayText}</a>${
    suffix ?? ''
  }`;
}

interface RewriteResult {
  replacedTextStartPosition: number;
  replacedTextEndPosition: number;
  replacementText: string;
}

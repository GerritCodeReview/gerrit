/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import 'ba-linkify';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {sanitizeHtml, htmlEscape} from 'safevalues';
import '@polymer/marked-element';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {CommentLinks} from '../../../api/rest-api';
import {getBaseUrl} from '../../../utils/url-util';

/**
 * This element renders markdown and also applies some regex replacements to
 * linkify key parts of the text defined by the host's config.
 *
 * Note: Do not use sharedStyles or other styles that should not affect the
 * generated HTML of the markdown.
 */
@customElement('gr-markdown')
export class GrMarkdown extends LitElement {
  @property({type: String})
  markdown = '';

  @state()
  private repoCommentLinks: CommentLinks = {};

  private readonly getConfigModel = resolve(this, configModelToken);

  static override styles = [
    css`
      [slot='markdown-html'] p {
        margin: 0;
      }
    `,
  ];

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().repoCommentLinks$,
      repoCommentLinks => (this.repoCommentLinks = repoCommentLinks)
    );
  }

  override render() {
    // Escaping the message should be done first to make sure user's literal
    // input does not get rendered without affecting html added in later steps.
    const escaped = htmlEscape(this.markdown).toString();
    // Turn universally identifiable URLs into links. Ex: www.google.com. The
    // markdown library inside marked-element does this too, but is more
    // conservative and misses some URLs like "google.com" without "www" prefix.
    const linkedNormalUrls = linkifyNormalUrls(escaped);
    // Apply the host's config-specific regex replacements to create links.
    // Ex: link "Bug 12345" to "google.com/bug/12345"
    const linkedFromConfig = this.applyLinkRewritesFromConfig(linkedNormalUrls);
    // Apply the host's config-specific regex replacements to write arbitrary
    // html. Most examples seen in the wild are also used for linking but with
    // finer control over the rendered text. Ex: "Bug 12345" => "#12345"
    const htmledFromConfig = this.applyHtmlRewritesFromConfig(linkedFromConfig);
    // Final sanitization should preserve our modifications but sort out any XSS
    // attacks that may sneak in. Many polymer and lit parsers do not expect a
    // TrustedHTML object from sanitization and so it is manually stringified.
    const sanitized = sanitizeHtml(htmledFromConfig).toString();
    // The child with slot is optional but allows us control over the styling.
    return html`
      <marked-element .markdown=${sanitized}>
        <div slot="markdown-html"></div>
      </marked-element>
    `;
  }

  private applyLinkRewritesFromConfig(base: string) {
    const linkRewritesFromConfig = Object.values(this.repoCommentLinks).filter(
      commentLinkInfo =>
        commentLinkInfo.enabled !== false && commentLinkInfo.link
    );
    const rewrites = linkRewritesFromConfig.map(rewrite => {
      const replacementHref = rewrite.link!.startsWith('/')
        ? `${getBaseUrl()}${rewrite.link!}`
        : rewrite.link!;
      return {
        match: new RegExp(rewrite.match, 'g'),
        // TODO: To match old behavior, this should be the entire match and not
        // "$1" which is the first group in the match.
        replace: createLinkTemplate('$1', replacementHref),
      };
    });
    return applyRewrites(base, rewrites);
  }

  private applyHtmlRewritesFromConfig(base: string) {
    const htmlRewritesFromConfig = Object.values(this.repoCommentLinks).filter(
      commentLinkInfo =>
        commentLinkInfo.enabled !== false && commentLinkInfo.html
    );
    const rewrites = htmlRewritesFromConfig.map(rewrite => {
      return {
        match: new RegExp(rewrite.match, 'g'),
        replace: rewrite.html!,
      };
    });
    return applyRewrites(base, rewrites);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-markdown': GrMarkdown;
  }
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

// TODO: make a link-util.ts for this function
function linkifyNormalUrls(base: string): string {
  const parts: string[] = [];
  window.linkify(base, {
    callback: (text, href) =>
      parts.push(href ? createLinkTemplate(text, href) : text),
  });
  return parts.join('');
}

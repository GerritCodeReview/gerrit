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
 * This is a separate element to wrap Polymer's marked-element so that style
 * imports like sharedStyles will not influence the generated HTML.
 */
@customElement('gr-markdown')
export class GrMarkdown extends LitElement {
  @property({type: String})
  markdown: string = '';

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
    /**
     * We process the text with these steps before it is rendered:
     * 1. Escape HTML characters using 'safevalues' package.
     * 2. Linkify links (using 'ba-linkify' package loaded into global window
     *    object).
     * 3. Link regex replacement from gerrit.config.
     * 4. HTML regex replacement from gerrit.config.
     * 5. Sanitize HTML using 'safevalues' package.
     * 6. Prepend a newline to correct some markdown --> HTML weirdness with the
     *    first line.
     * 7. Markdown conversion using webcomponent '@polymer/marked-element'.
     */
    const escaped = htmlEscape(this.markdown).toString(); // #1
    const linkedNormalUrls = linkifyNormalUrls(escaped); // #2
    const linkedFromConfig = this.applyLinkRewritesFromConfig(linkedNormalUrls); // #3
    const htmledFromConfig = this.applyHtmlRewritesFromConfig(linkedFromConfig); // #4
    const sanitized = sanitizeHtml(htmledFromConfig); // #5
    const newlined = `\n${sanitized}`; // #6
    // #7
    return html`<marked-element>
      <div slot="markdown-html"></div>
      <script type="text/markdown">
        ${newlined}
      </script>
    </marked-element>`;
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
    const rewrites = htmlRewritesFromConfig.map(rewrite => ({
      match: new RegExp(rewrite.match, 'g'),
      replace: rewrite.html!,
    }));
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

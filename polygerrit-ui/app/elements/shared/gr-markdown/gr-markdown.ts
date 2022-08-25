/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {sanitizeHtml, htmlEscape} from '../../../utils/inner-html-util';
import '@polymer/marked-element';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {CommentLinks} from '../../../api/rest-api';
import {
  applyHtmlRewritesFromConfig,
  applyLinkRewritesFromConfig,
  linkifyNormalUrls,
} from '../../../utils/link-util';

/**
 * This element renders markdown and also applies some regex replacements to
 * linkify key parts of the text defined by the host's config.
 *
 * TODO: Remove gr-formatted-text once this is rolled out.
 */
@customElement('gr-markdown')
export class GrMarkdown extends LitElement {
  @property({type: String})
  markdown?: string;

  @state()
  private repoCommentLinks: CommentLinks = {};

  private readonly getConfigModel = resolve(this, configModelToken);

  /**
   * Note: Do not use sharedStyles or other styles here that should not affect
   * the generated HTML of the markdown.
   */
  static override styles = [
    css`
      a {
        color: var(--link-color);
      }
      p,
      ul,
      code,
      blockquote {
        margin: 0 0 var(--spacing-m) 0;
        max-width: var(--gr-formatted-text-prose-max-width, none);
      }
      blockquote {
        border-left: var(--spacing-xxs) solid var(--gray-500);
        padding: 0 var(--spacing-m);
      }
      code {
        background-color: var(--background-color-secondary);
        border: var(--spacing-xxs) solid var(--border-color);
        display: block;
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-mono);
        margin: var(--spacing-m) 0;
        padding: var(--spacing-xxs) var(--spacing-s);
        overflow-x: auto;
        /* pre will preserve whitespace and linebreaks but not wrap */
        white-space: pre;
      }
      /* code within a sentence needs display:inline to shrink and not take a
         whole row */
      p code {
        display: inline;
      }
      li {
        margin-left: var(--spacing-xl);
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
    // Note: Handling \u200B added in gr-change-view.ts is not needed here
    // because the commit message is not markdown formatted.

    // Escaping the message should be done first to make sure user's literal
    // input does not get rendered without affecting html added in later steps.
    const escaped = htmlEscape(this.markdown ?? '').toString();

    // Turn universally identifiable URLs into links. Ex: www.google.com. The
    // markdown library inside marked-element does this too, but is more
    // conservative and misses some URLs like "google.com" without "www" prefix.
    const linkedNormalUrls = linkifyNormalUrls(escaped);
    // Apply the host's config-specific regex replacements to create links. Ex:
    // link "Bug 12345" to "google.com/bug/12345"
    const linkedFromConfig = applyLinkRewritesFromConfig(
      linkedNormalUrls,
      this.repoCommentLinks
    );
    // Apply the host's config-specific regex replacements to write arbitrary
    // html. Most examples seen in the wild are also used for linking but with
    // finer control over the rendered text. Ex: "Bug 12345" => "#12345"
    const htmledFromConfig = applyHtmlRewritesFromConfig(
      linkedFromConfig,
      this.repoCommentLinks
    );
    // Final sanitization should preserve our modifications but sort out any XSS
    // attacks that may sneak in. Many polymer and lit parsers do not expect a
    // TrustedHTML object from sanitization and so it is manually stringified.
    const sanitized = sanitizeHtml(htmledFromConfig).toString();

    // Unescape block quotes '>'. This is slightly dangerous as '>' can be used
    // in HTML fragments, but it is insufficient on it's own.
    const quotesUnescaped = sanitized.replace(/(^|\n)&gt;/g, '$1>');

    // The child with slot is optional but allows us control over the styling.
    return html`
      <marked-element .markdown=${quotesUnescaped} .breaks=${true}>
        <div slot="markdown-html"></div>
      </marked-element>
    `;
  }
}
declare global {
  interface HTMLElementTagNameMap {
    'gr-markdown': GrMarkdown;
  }
}

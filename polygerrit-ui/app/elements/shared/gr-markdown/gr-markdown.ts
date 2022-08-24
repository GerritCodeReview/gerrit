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
import {
  applyHtmlRewritesFromConfig,
  applyLinkRewritesFromConfig,
  linkifyNormalUrls,
} from '../../../utils/link-util';

/**
 * This element renders markdown and also applies some regex replacements to
 * linkify key parts of the text defined by the host's config.
 */
@customElement('gr-markdown')
export class GrMarkdown extends LitElement {
  @property({type: String})
  markdown = '';

  @state()
  private repoCommentLinks: CommentLinks = {};

  private readonly getConfigModel = resolve(this, configModelToken);

  /**
   * Note: Do not use sharedStyles or other styles here that should not affect
   * the generated HTML of the markdown.
   */
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
    // Note: Handling \u200B added in gr-change-view.ts is not needed here
    // because the commit message is not markdown formatted.

    // Escaping the message should be done first to make sure user's literal
    // input does not get rendered without affecting html added in later steps.
    const escaped = htmlEscape(this.markdown).toString();
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

    // The child with slot is optional but allows us control over the styling.
    return html`
      <marked-element .markdown=${sanitized}>
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

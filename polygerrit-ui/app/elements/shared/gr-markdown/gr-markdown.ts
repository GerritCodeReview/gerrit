/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {unsafeHTML} from 'lit/directives/unsafe-html.js';
import {htmlEscape} from '../../../utils/inner-html-util';
import {unescapeHTML} from '../../../utils/syntax-util';
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
 * TODO: Replace gr-formatted-text with this once markdown flag is rolled out.
 */
@customElement('gr-markdown')
export class GrMarkdown extends LitElement {
  @property({type: String})
  content = '';

  @property({type: Boolean})
  asMarkdown = false;

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
      p:last-child,
      ul:last-child,
      blockquote:last-child,
      pre:last-child {
        margin: 0;
      }
      blockquote {
        border-left: var(--spacing-xxs) solid var(--comment-quote-marker-color);
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
        /* Pre will preserve whitespace and line breaks but not wrap */
        white-space: pre;
      }
      /* Code within a sentence needs display:inline to shrink and not take a
         whole row */
      p code {
        display: inline;
      }
      p {
        /* prose will automatically wrap but inline <code> blocks won't and we
           should overflow in that case rather than wrapping or leaking out */
        overflow-x: auto;
      }
      li {
        margin-left: var(--spacing-xl);
      }
      .plaintext {
        font: inherit;
        white-space: var(--linked-text-white-space, pre-wrap);
        word-wrap: var(--linked-text-word-wrap, break-word);
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
    if (this.asMarkdown) {
      return this.renderAsMarkdown();
    } else {
      return this.renderAsPlaintext();
    }
  }

  private renderAsPlaintext() {
    const linkedText = this.rewriteText(
      htmlEscape(this.content).toString(),
      this.repoCommentLinks
    );
    return html`<pre class="plaintext">${unsafeHTML(linkedText)}</pre>`;
  }

  private renderAsMarkdown() {
    // Note: Handling \u200B added in gr-change-view.ts is not needed here
    // because the commit message is not markdown formatted.

    // <marked-element> internals will be in charge of calling our custom
    // renderer so we wrap 'this.rewriteText' so that 'this' is preserved via
    // closure.
    const boundRewriteText = (text: string) =>
      this.rewriteText(text, this.repoCommentLinks);

    // We are overriding some marked-element renderers for a few reasons:
    // 1. Disable inline images as a design/policy choice.
    // 2. Inline code blocks ("codespan") do not unescape HTML characters when
    //    rendering without <pre> and so we must do this manually.
    //    <marked-element> is already escaping these internally. See test
    //    covering this.
    // 3. Multiline code blocks ("code") is similarly handling escaped
    //    characters using <pre>. The convention is to only use <pre> for multi-
    //    line code blocks so it is not used for inline code blocks. See test
    //    for this.
    // 4. Rewrite plain text ("text") to apply linking and other config-based
    //    rewrites. Text within code blocks is not passed here.
    function customRenderer(renderer: {[type: string]: Function}) {
      renderer['image'] = (href: string, _title: string, text: string) =>
        `![${text}](${href})`;
      renderer['codespan'] = (text: string) =>
        `<code>${unescapeHTML(text)}</code>`;
      renderer['code'] = (text: string) => `<pre><code>${text}</code></pre>`;
      renderer['text'] = boundRewriteText;
    }

    // The child with slot is optional but allows us control over the styling.
    return html`
      <marked-element
        .markdown=${this.escapeAllButBlockQuotes(this.content)}
        .breaks=${true}
        .renderer=${customRenderer}
      >
        <div slot="markdown-html"></div>
      </marked-element>
    `;
  }

  private escapeAllButBlockQuotes(text: string) {
    // Escaping the message should be done first to make sure user's literal
    // input does not get rendered without affecting html added in later steps.
    text = htmlEscape(text).toString();
    // Unescape block quotes '>'. This is slightly dangerous as '>' can be used
    // in HTML fragments, but it is insufficient on it's own.
    text = text.replace(/(^|\n)&gt;/g, '$1>');

    return text;
  }

  private rewriteText(text: string, repoCommentLinks: CommentLinks) {
    // Turn universally identifiable URLs into links. Ex: www.google.com. The
    // markdown library inside marked-element does this too, but is more
    // conservative and misses some URLs like "google.com" without "www" prefix.
    text = linkifyNormalUrls(text);

    // Apply the host's config-specific regex replacements to create links. Ex:
    // link "Bug 12345" to "google.com/bug/12345"
    text = applyLinkRewritesFromConfig(text, repoCommentLinks);

    // Apply the host's config-specific regex replacements to write arbitrary
    // html. Most examples seen in the wild are also used for linking but with
    // finer control over the rendered text. Ex: "Bug 12345" => "#12345"
    text = applyHtmlRewritesFromConfig(text, repoCommentLinks);

    return text;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-markdown': GrMarkdown;
  }
}

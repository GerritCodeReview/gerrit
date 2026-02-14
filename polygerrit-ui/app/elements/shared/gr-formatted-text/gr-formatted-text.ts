/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  htmlEscape,
  sanitizeHtmlToFragment,
} from '../../../utils/inner-html-util';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {CommentLinks, EmailAddress} from '../../../api/rest-api';
import {linkifyUrlsAndApplyRewrite} from '../../../utils/link-util';
import '../gr-account-chip/gr-account-chip';
import '../gr-user-suggestion-fix/gr-user-suggestion-fix';
import {
  getUserSuggestionFromString,
  USER_SUGGESTION_INFO_STRING,
} from '../../../utils/comment-util';
import {sameOrigin} from '../../../utils/url-util';
import '../gr-marked-element/gr-marked-element';
import {Renderer, Tokenizer, Tokens} from 'marked';

// MIME types for images we allow showing. Do not include SVG, it can contain
// arbitrary JavaScript.
const IMAGE_MIME_PATTERN =
  /^data:image\/(bmp|gif|x-icon|jpeg|jpg|png|tiff|webp);base64,/;

/**
 * This element optionally renders markdown and also applies some regex
 * replacements to linkify key parts of the text defined by the host's config.
 */
@customElement('gr-formatted-text')
export class GrFormattedText extends LitElement {
  @property({type: String})
  content = '';

  @property({type: Boolean})
  markdown = false;

  @state()
  private repoCommentLinks: CommentLinks = {};

  private readonly getConfigModel = resolve(this, configModelToken);

  // Private const but used in tests.
  // Limit the length of markdown because otherwise the markdown lexer will
  // run out of memory causing the tab to crash.
  @state()
  MARKDOWN_LIMIT = 100000;

  @state()
  private allowMarkdownBase64ImagesInComments = false;

  /**
   * Note: Do not use sharedStyles or other styles here that should not affect
   * the generated HTML of the markdown.
   */
  static override get styles() {
    return [
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
        pre:last-child,
        pre.plaintext {
          margin: 0;
        }
        blockquote {
          border-left: var(--spacing-xxs) solid
            var(--comment-quote-marker-color);
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
        /* Non-multiline code elements need display:inline to shrink and not take
         a whole row */
        :not(pre) > code {
          display: inline;
        }
        li {
          margin-left: var(--spacing-xl);
        }
        gr-account-chip {
          display: inline;
        }
        .plaintext {
          font: inherit;
          white-space: var(--linked-text-white-space, pre-wrap);
          word-wrap: var(--linked-text-word-wrap, break-word);
        }
        .markdown-html {
          /* code overrides white-space to pre, everything else should wrap as
           normal. */
          white-space: normal;
          /* prose will automatically wrap but inline <code> blocks won't and we
           should overflow in that case rather than wrapping or leaking out */
          overflow-x: auto;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().repoCommentLinks$,
      repoCommentLinks => {
        this.repoCommentLinks = repoCommentLinks;
        // Always linkify URLs starting with https?://
        this.repoCommentLinks['ALWAYS_LINK_HTTP'] = {
          match: '(https?://((?!&(gt|lt|quot|apos);)\\S)+[\\w/~-])',
          link: '$1',
          enabled: true,
        };
        // Linkify email addresses.
        this.repoCommentLinks['ALWAYS_LINK_EMAIL'] = {
          match: '([\\w.+-]+@[\\w.-]+\\.[\\w]{2,})',
          link: 'mailto:$1',
          enabled: true,
        };

        // List of common TLDs to specifically match for schemeless URLs.
        const TLD_REGEX = [
          'com',
          'org',
          'net',
          'edu',
          'gov',
          'co',
          'jp',
          'de',
          'uk',
          'fr',
          'us',
          'io',
        ].join('|');

        // Linkify schemeless URLs with proper domain structures.
        this.repoCommentLinks['ALWAYS_LINK_SCHEMELESS'] = {
          // (?<=\s|^|[('":[])   // Ensure the match is preceded by whitespace,
          //                     // start of line, or one of ( ' " : [
          // (                   // Start capture group 1
          //   (?:               // Start non-capturing domain group
          //     [\w-]+\.        //   Sequence of words/hyphens with dot, e.g. "a-b."
          //   )+                // End domain group. Require at least one match
          //   (?:${TLD_REGEX})  // Ensure the match ends with a common TLD
          //   (?=.*?/)          // Positive lookahead to ensure a '/' exists in the path/query/fragment
          //   (?:               // Start non-capturing path/query/fragment group
          //     [/?#]           //   Start with one of / ? #
          //     [^\s'"]*        //   Followed by some chars that are not whitespace,
          //                     //   ' or " (to not grab trailing quotes)
          //     ?<![)'"!?.,]+   // and not ending with one or more of ) ' " ! ? . ,
          //   )                 // End path/query/fragment group
          // )                   // End capture group 1
          // (?=\s|$|[)'"!?.,])  // Ensure the match is followed by whitespace,
          //                     // end of line, or one of ) ' " ! ? . ,
          match: `(?<=\\s|^|[('":[])((?:[\\w-]+\\.)+(?:${TLD_REGEX})(?=.*?/)(?:[/?#][^\\s'"]*(?<![)'"!?.,]+)))(?=\\s|$|[)'"!?.,])`,
          // Prepend http:// for the link href otherwise it will be treated as
          // a relative URL.
          link: 'http://$1',
          enabled: true,
        };
      }
    );

    subscribe(
      this,
      () => this.getConfigModel().allowMarkdownBase64ImagesInComments$,
      allow => {
        this.allowMarkdownBase64ImagesInComments = allow;
      }
    );
  }

  override render() {
    return html`
      <gr-endpoint-decorator name="formatted-text-endpoint">
        ${this.markdown && this.content.length < this.MARKDOWN_LIMIT
          ? this.renderAsMarkdown()
          : this.renderAsPlaintext()}
      </gr-endpoint-decorator>
    `;
  }

  private renderAsPlaintext() {
    const linkedText = linkifyUrlsAndApplyRewrite(
      htmlEscape(this.content).toString(),
      this.repoCommentLinks
    );

    return html`
      <pre class="plaintext">${sanitizeHtmlToFragment(linkedText)}</pre>
    `;
  }

  private renderAsMarkdown() {
    // Bind `this` via closure.
    //
    // <gr-marked-element> internals will be in charge of calling our custom
    // renderer so we write this utility function separately so that 'this' is
    // preserved via closure.
    const boundRewriteText = (text: string) =>
      linkifyUrlsAndApplyRewrite(text, this.repoCommentLinks);

    const allowMarkdownBase64ImagesInComments =
      this.allowMarkdownBase64ImagesInComments;

    // We are overriding some gr-marked-element renderers for a few reasons:
    // 1. Disable inline images as a design/policy choice.
    // 2. Rewrite plain text ("text") to apply linking and other config-based
    //    rewrites. Text within code blocks is not passed here.
    // 3. Open links in a new tab by rendering with target="_blank" attribute.
    // 4. Relative links without "/" prefix are assumed to be absolute links.
    function patchRenderer(renderer: Renderer) {
      // Use the `function` syntax, so that we can add type annotation for
      // `this`, allowing the overridden methods access to all members of
      // the merged Renderer object, including to `this.parser`.
      renderer.link = function (
        this: Renderer,
        {href, title, tokens}: Tokens.Link
      ): string {
        const text = this.parser.parseInline(tokens);
        if (
          !href.startsWith('https://') &&
          !href.startsWith('mailto:') &&
          !href.startsWith('http://') &&
          !href.startsWith('/')
        ) {
          href = `https://${href}`;
        }
        /* HTML */
        return `<a
          href="${href}"
          ${sameOrigin(href) ? '' : 'target="_blank" rel="noopener noreferrer"'}
          ${title ? `title="${title}"` : ''}
          >${text}</a
        >`;
      };

      renderer.image = function (
        this: Renderer,
        {href, title, text}: Tokens.Image
      ): string {
        // Check if this is a base64-encoded image
        if (
          allowMarkdownBase64ImagesInComments &&
          IMAGE_MIME_PATTERN.test(href)
        ) {
          return `<img src="${href}" alt="${text}" ${
            title ? `title="${title}"` : ''
          } />`;
        }
        // For non-base64 images just return the markdown
        return `![${text}](${href})`;
      };

      renderer.code = function (this: Renderer, token: Tokens.Code): string {
        if (token.lang === USER_SUGGESTION_INFO_STRING) {
          // Default sanitizer in gr-marked-element is very restrictive, so
          // we need to use an existing html element to insert the content to.
          // We cannot use css class for it. Therefore we pick <mark> - as not
          // frequently used html element - to represent unconverted
          // gr-user-suggestion-fix.
          // TODO(milutin): Find a way to override sanitizer to directly use
          // gr-user-suggestion-fix
          return `<mark>${
            token.escaped ? token.text : htmlEscape(token.text)
          }</mark>`;
        }
        // Fall back to default renderer's `code` function.
        return Renderer.prototype.code.call(this, token);
      };

      // Treat HTML as plaintext and don't render it.
      // Assumes that inline HTML is already disabled in the tokenizer, so it
      // needs to render only block-level HTML.
      renderer.html = function (this: Renderer, {text}: Tokens.HTML): string {
        // Keep all new lines except the trailing ones, thus respecting
        // the `breaks: true` option.
        text = text.replace(/\n+$/, '');
        return (
          '<p>' + htmlEscape(text).toString().replaceAll('\n', '<br>') + '</p>'
        );
      };

      renderer.text = function (
        this: Renderer,
        token: Tokens.Text | Tokens.Escape
      ): string {
        // Don't process text in raw blocks.
        if (token.type === 'escape') {
          return htmlEscape(token.text).toString();
        }
        // Recurse when not in a terminal node.
        if (token.type === 'text' && token.tokens) {
          return this.parser.parseInline(token.tokens);
        }
        return boundRewriteText(
          token.type === 'text' && token.escaped
            ? token.text
            : htmlEscape(token.text).toString()
        );
      };
    }

    // Disables "marked"'s default autolinking of URLs and emails, since we
    // want to use our own custom linkification. Disables tokenizing of
    // inline HTML tags, so that they are treated as text.
    function patchTokenizer(tokenizer: Tokenizer) {
      // Return undefined to skip the default autolink/url tokenizers.
      tokenizer.url = () => undefined;
      tokenizer.autolink = () => undefined;

      // Return undefined to skip the default tag tokenizer. This effectively
      // causes _inline_ HTML tags to be treated as text, as opposed to HTML.
      //
      // Preventing rendering of inline HTML should better happen in the
      // tokenizer (as opposed to the renderer), since the default `tag`
      // tokenizer makes changes to the lexer's state for some HTML tags
      // (like <a>), which is undesired.
      tokenizer.tag = () => undefined;
    }

    // The child with slot is optional but allows us control over the styling.
    // No need to sanitize the output since the <gr-marked-element> component
    // does that internally.
    return html`
      <gr-marked-element
        .markdown=${this.content}
        .breaks=${true}
        .renderer=${patchRenderer}
        .tokenizer=${patchTokenizer}
      >
        <div class="markdown-html" slot="markdown-html"></div>
      </gr-marked-element>
    `;
  }

  override updated() {
    this.removeEventListener(
      'marked-render-complete',
      this.markedRenderComplete
    );
    this.addEventListener('marked-render-complete', this.markedRenderComplete);
  }

  readonly markedRenderComplete = () => {
    // Look for @mentions and replace them with an account-label chip.
    this.convertEmailsToAccountChips();
    this.convertCodeToSuggestions();
  };

  private convertEmailsToAccountChips() {
    for (const emailLink of this.renderRoot.querySelectorAll(
      'a[href^="mailto"]'
    )) {
      const previous = emailLink.previousSibling;
      // This Regexp matches the beginning of the MENTIONS_REGEX at the end of
      // an element.
      if (
        previous?.nodeName === '#text' &&
        previous?.textContent?.match(/(^|\s)@$/)
      ) {
        const accountChip = document.createElement('gr-account-chip');
        accountChip.account = {
          email: emailLink.textContent as EmailAddress,
        };
        accountChip.removable = false;
        // Remove the trailing @ from the previous element.
        previous.textContent = previous.textContent.slice(0, -1);
        emailLink.parentNode?.replaceChild(accountChip, emailLink);
      }
    }
  }

  private convertCodeToSuggestions() {
    const marks = this.renderRoot.querySelectorAll('mark');
    marks.forEach((userSuggestionMark, index) => {
      const userSuggestion = document.createElement('gr-user-suggestion-fix');
      // Temporary workaround for bug - tabs replacement
      if (this.content.includes('\t')) {
        userSuggestion.textContent = getUserSuggestionFromString(
          this.content,
          index
        );
      } else {
        userSuggestion.textContent = userSuggestionMark.textContent ?? '';
      }
      userSuggestionMark.parentNode?.replaceChild(
        userSuggestion,
        userSuggestionMark
      );
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-formatted-text': GrFormattedText;
  }
}

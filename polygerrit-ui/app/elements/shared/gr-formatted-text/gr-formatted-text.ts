/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  htmlEscape,
  sanitizeHtml,
  sanitizeHtmlToFragment,
} from '../../../utils/inner-html-util';
import {unescapeHTML} from '../../../utils/syntax-util';
import '@polymer/marked-element';
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
          //   )                 // End path/query/fragment group
          // )                   // End capture group 1
          // (?=\s|$|[)'"!?.,])  // Ensure the match is followed by whitespace,
          //                     // end of line, or one of ) ' " ! ? . ,
          match: `(?<=\\s|^|[('":[])((?:[\\w-]+\\.)+(?:${TLD_REGEX})(?=.*?/)(?:[/?#][^\\s'"]*))(?=\\s|$|[)'"!?.,])`,
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
    const boundRewriteText = (text: string) => {
      const nonAsteriskRewrites = Object.fromEntries(
        Object.entries(this.repoCommentLinks).filter(
          ([_name, rewrite]) => !rewrite.match.includes('\\*')
        )
      );
      return linkifyUrlsAndApplyRewrite(text, nonAsteriskRewrites);
    };

    // Due to a tokenizer bug in the old version of markedjs we use, text with a
    // single asterisk is separated into 2 tokens before passing to renderer
    // ['text'] which breaks our rewrites that would span across the 2 tokens.
    // Since upgrading our markedjs version is infeasible, we are applying those
    // asterisk rewrites again at the end (using renderer['paragraph'] hook)
    // after all the nodes are combined.
    // Bind `this` via closure.
    const boundRewriteAsterisks = (text: string) => {
      const asteriskRewrites = Object.fromEntries(
        Object.entries(this.repoCommentLinks).filter(([_name, rewrite]) =>
          rewrite.match.includes('\\*')
        )
      );
      const linkedText = linkifyUrlsAndApplyRewrite(text, asteriskRewrites);
      return `<p>${linkedText}</p>`;
    };

    const allowMarkdownBase64ImagesInComments =
      this.allowMarkdownBase64ImagesInComments;

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
    // 5. Open links in a new tab by rendering with target="_blank" attribute.
    // 6. Relative links without "/" prefix are assumed to be absolute links.
    function customRenderer(renderer: {[type: string]: Function}) {
      renderer['link'] = (href: string, title: string, text: string) => {
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
      renderer['image'] = (href: string, title: string, text: string) => {
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
      renderer['codespan'] = (text: string) =>
        `<code>${unescapeHTML(text)}</code>`;
      renderer['code'] = (text: string, infostring: string) => {
        if (infostring === USER_SUGGESTION_INFO_STRING) {
          // default santizer in markedjs is very restrictive, we need to use
          // existing html element to mark element. We cannot use css class for
          // it. Therefore we pick mark - as not frequently used html element to
          // represent unconverted gr-user-suggestion-fix.
          // TODO(milutin): Find a way to override sanitizer to directly use
          // gr-user-suggestion-fix
          return `<mark>${text}</mark>`;
        } else {
          return `<pre><code>${text}</code></pre>`;
        }
      };
      // <marked-element> internals will be in charge of calling our custom
      // renderer so we write these functions separately so that 'this' is
      // preserved via closure.
      renderer['paragraph'] = boundRewriteAsterisks;
      renderer['text'] = boundRewriteText;
    }

    // The child with slot is optional but allows us control over the styling.
    // The `callback` property lets us do a final sanitization of the output
    // HTML string before it is rendered by `<marked-element>` in case any
    // rewrites have been abused to attempt an XSS attack.
    return html`
      <marked-element
        .markdown=${this.escapeAllButBlockQuotes(this.content)}
        .breaks=${true}
        .renderer=${customRenderer}
        .callback=${(_error: string | null, contents: string) =>
          sanitizeHtml(contents)}
      >
        <div class="markdown-html" slot="markdown-html"></div>
      </marked-element>
    `;
  }

  private escapeAllButBlockQuotes(text: string) {
    // Escaping the message should be done first to make sure user's literal
    // input does not get rendered without affecting html added in later steps.
    text = htmlEscape(text).toString();
    // Unescape block quotes '>'. This is slightly dangerous as '>' can be used
    // in HTML fragments, but it is insufficient on it's own.
    for (;;) {
      const newText = text.replace(
        /(^|\n)((?:\s{0,3}&gt;)*\s{0,3})&gt;/g,
        '$1$2>'
      );
      if (newText === text) {
        break;
      }
      text = newText;
    }

    return text;
  }

  override updated() {
    // Look for @mentions and replace them with an account-label chip.
    this.convertEmailsToAccountChips();
    this.convertCodeToSuggestions();
  }

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

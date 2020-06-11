import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrDiffPreferences} from '../../../../elements/shared/gr-diff-preferences/gr-diff-preferences';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrDiffPreferencesCheck extends GrDiffPreferences
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `diffPreferences`);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.setAttribute('id', `contextSelect`);
      el.bindValue = this._convertToString(__f(this.diffPrefs)!.context);
      el.addEventListener('change', this._handleDiffContextChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `contextLineSelect`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `lineWrappingInput`);
      el.checked = this._convertToBoolean(__f(this.diffPrefs)!.line_wrapping);
      el.addEventListener('change', this._handleLineWrappingTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this.diffPrefs)!.line_length);
      el.addEventListener('change', this._handleDiffLineLengthChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `columnsInput`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this.diffPrefs)!.tab_size);
      el.addEventListener('change', this._handleDiffTabSizeChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `tabSizeInput`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!__f(this.diffPrefs)!.font_size}`);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this.diffPrefs)!.font_size);
      el.addEventListener('change', this._handleDiffFontSizeChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `fontSizeInput`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `showTabsInput`);
      el.checked = this._convertToBoolean(__f(this.diffPrefs)!.show_tabs);
      el.addEventListener('change', this._handleShowTabsTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `showTrailingWhitespaceInput`);
      el.checked = this._convertToBoolean(__f(this.diffPrefs)!.show_whitespace_errors);
      el.addEventListener('change', this._handleShowTrailingWhitespaceTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `syntaxHighlightInput`);
      el.checked = this._convertToBoolean(__f(this.diffPrefs)!.syntax_highlighting);
      el.addEventListener('change', this._handleSyntaxHighlightTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `automaticReviewInput`);
      el.checked = !this._convertToBoolean(__f(this.diffPrefs)!.manual_review);
      el.addEventListener('change', this._handleAutomaticReviewTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `pref`);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-select'] = null!;
      useVars(el);
      el.bindValue = this._convertToString(__f(this.diffPrefs)!.ignore_whitespace);
      el.addEventListener('change', this._handleDiffIgnoreWhitespaceChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['select'] = null!;
      useVars(el);
      el.setAttribute('id', `ignoreWhiteSpace`);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['option'] = null!;
      useVars(el);
    }
  }
}


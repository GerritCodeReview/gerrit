import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrTextarea} from '../../../../elements/shared/gr-textarea/gr-textarea';

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

export class GrTextareaCheck extends GrTextarea
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `hiddenText`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('id', `caratSpan`);
    }
    {
      const el: HTMLElementTagNameMap['gr-autocomplete-dropdown'] = null!;
      useVars(el);
      el.setAttribute('id', `emojiSuggestions`);
      el.suggestions = this._suggestions;
      el.index = this._index;
      el.verticalOffset = this._verticalOffset;
      el.addEventListener('dropdown-closed', this._resetEmojiDropdown.bind(this));
      el.addEventListener('item-selected', this._handleEmojiSelect.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['iron-autogrow-textarea'] = null!;
      useVars(el);
      el.setAttribute('id', `textarea`);
      el.autocomplete = this.autocomplete;
      el.placeholder = this.placeholder;
      el.disabled = this.disabled;
      el.rows = this.rows;
      el.maxRows = this.maxRows;
      el.value = this.text;
      this.text = el.value;
      el.addEventListener('bind-value-changed', this._onValueChanged.bind(this));
    }
  }
}


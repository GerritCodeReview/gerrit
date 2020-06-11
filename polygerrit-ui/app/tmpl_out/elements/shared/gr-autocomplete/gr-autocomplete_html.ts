import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrAutocomplete} from '../../../../elements/shared/gr-autocomplete/gr-autocomplete';

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

export class GrAutocompleteCheck extends GrAutocomplete
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['paper-input'] = null!;
      useVars(el);
      el.setAttribute('id', `input`);
      el.setAttribute('class', `${this._computeClass(this.borderless)}`);
      el.setAttribute('disabled', `${this.disabled}`);
      el.value = this.text;
      this.text = el.value;
      el.placeholder = this.placeholder;
      el.addEventListener('keydown', this._handleKeydown.bind(this));
      el.addEventListener('focus', this._onInputFocus.bind(this));
      el.addEventListener('blur', this._onInputBlur.bind(this));
      el.label = this.label;
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('class', `searchIcon ${this._computeShowSearchIconClass(this.showSearchIcon)}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['slot'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-autocomplete-dropdown'] = null!;
      useVars(el);
      el.verticalOffset = this.verticalOffset;
      el.setAttribute('id', `suggestions`);
      el.addEventListener('item-selected', this._handleItemSelect.bind(this));
      el.addEventListener('keydown', this._handleKeydown.bind(this));
      el.suggestions = this._suggestions;
      el.index = this._index;
      el.positionTarget = this._inputElement;
    }
  }
}


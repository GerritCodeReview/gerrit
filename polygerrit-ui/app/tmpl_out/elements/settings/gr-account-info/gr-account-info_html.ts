import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrAccountInfo} from '../../../../elements/settings/gr-account-info/gr-account-info';

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

export class GrAccountInfoCheck extends GrAccountInfo
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-avatar'] = null!;
      useVars(el);
      el.account = this._account;
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('class', `${this._hideAvatarChangeUrl(this._avatarChangeUrl)}`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._avatarChangeUrl}`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${__f(this._account)!._account_id}`);

    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${__f(this._account)!.email}`);

    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['gr-date-formatter'] = null!;
      useVars(el);
      el.hasTooltip = true;
      el.dateStr = __f(this._account)!.registered_on;
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('id', `usernameSection`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this.usernameMutable}`);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${this._username}`);

    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!this.usernameMutable}`);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.addEventListener('keydown', this._handleKeydown.bind(this));
      el.bindValue = this._username;
      this._username = convert(el.bindValue);
      el.setAttribute('id', `usernameIronInput`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `usernameInput`);
      el.disabled = this._saving;
      el.addEventListener('keydown', this._handleKeydown.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('id', `nameSection`);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this.nameMutable}`);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${__f(this._account)!.name}`);

    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!this.nameMutable}`);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.addEventListener('keydown', this._handleKeydown.bind(this));
      el.bindValue = __f(this._account)!.name;
      __f(this._account)!.name = convert(el.bindValue);
      el.setAttribute('id', `nameIronInput`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `nameInput`);
      el.disabled = this._saving;
      el.addEventListener('keydown', this._handleKeydown.bind(this));
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
      el.addEventListener('keydown', this._handleKeydown.bind(this));
      el.bindValue = __f(this._account)!.display_name;
      __f(this._account)!.display_name = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `displayNameInput`);
      el.disabled = this._saving;
      el.addEventListener('keydown', this._handleKeydown.bind(this));
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
      el.addEventListener('keydown', this._handleKeydown.bind(this));
      el.bindValue = __f(this._account)!.status;
      __f(this._account)!.status = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `statusInput`);
      el.disabled = this._saving;
      el.addEventListener('keydown', this._handleKeydown.bind(this));
    }
  }
}


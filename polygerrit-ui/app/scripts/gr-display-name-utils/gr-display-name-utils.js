(function(window) {
  'use strict';

  if (window.GrDisplayNameUtils) {
    return;
  }

  const ANONYMOUS_NAME = 'Anonymous';

  class GrDisplayNameUtils {
    /**
     * enableEmail when true enables to fallback to using email if
     * the account name is not avilable.
     */
    static getUserName(config, account, enableEmail) {
      if (account && account.name) {
        return account.name;
      } else if (account && account.username) {
        return account.username;
      } else if (enableEmail && account && account.email) {
        return account.email;
      } else if (config && config.user &&
          config.user.anonymous_coward_name !== 'Anonymous Coward') {
        return config.user.anonymous_coward_name;
      }

      return ANONYMOUS_NAME;
    }

    static getAccountDisplayName(config, account, enableEmail) {
      const reviewerName = this._accountOrAnon(config, account, enableEmail);
      const reviewerEmail = this._accountEmail(account.email);
      const reviewerStatus = account.status ? '(' + account.status + ')' : '';
      return [reviewerName, reviewerEmail, reviewerStatus]
          .filter(p => p.length > 0).join(' ');
    }

    static _accountOrAnon(config, reviewer, enableEmail) {
      return this.getUserName(config, reviewer, !!enableEmail);
    }

    static _accountEmail(email) {
      if (typeof email !== 'undefined') {
        return '<' + email + '>';
      }
      return '';
    }

    static getGroupDisplayName(group) {
      return group.name + ' (group)';
    }
  }

  window.GrDisplayNameUtils = GrDisplayNameUtils;
})(window);

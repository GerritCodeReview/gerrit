(() => {
  'use strict';

  Gerrit.install(plugin => {
    plugin.styleApi().insertRule(`
      html {
        --header-background-color: #34adbe;
        --header-text-color: #fff;
        --header-title-content: "Gerrit";
        --header-icon: url("/static/diffy.png");
        --header-icon-size: 1em;
      }
    `);
    plugin.styleApi().insertRule(`
      html.lightTheme {
        --header-background-color: #54cdde;
        --header-title-content: "Lerrit";
        --header-text-color: green;
      }
    `);
    plugin.styleApi().insertRule(`
      html.darkTheme {
        --header-background-color: #148d9e;
        --header-title-content: "Derrit";
        --header-text-color: red;
      }
    `);
  });
})();

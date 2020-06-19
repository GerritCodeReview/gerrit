module.exports = {
  "overrides": [
    {
      "files": ["**/*.ts"],
      "options": {
          ...require('gts/.prettierrc.json')
      }
    }
  ]
};

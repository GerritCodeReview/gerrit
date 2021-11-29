const JSON_PREFIX = ')]}\'';
function parsePrefixedJSON(jsonWithPrefix) {
  return JSON.parse(
    jsonWithPrefix.substring(JSON_PREFIX.length)
  );
}
function readResponsePayload(response) {
  return response.text().then(text => {
    let result;
    try {
    result = parsePrefixedJSON(text);
    } catch (_) {
    result = null;
    }
    return result;
  });
}

console.log(`SW registered!`);

self.addEventListener('install', () => {
  console.log('SW installed');
});

self.addEventListener('activate', async () => {
  console.log('SW activated');
  const options = {};
  const response = await fetch(`http://localhost:8081/changes/?O=1000081&S=0&n=25&q=status%3Aopen%20-is%3Awip`);
  const changes = await readResponsePayload(response);
  self.registration.showNotification(changes[0].subject, options);
});
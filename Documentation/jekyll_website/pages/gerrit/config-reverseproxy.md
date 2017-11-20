---
title: " Gerrit Code Review - Reverse Proxy"
sidebar: gerritdoc_sidebar
permalink: config-reverseproxy.html
---
## Description

Gerrit can be configured to run behind a third-party web server. This
allows the other web server to bind to the privileged port 80 (or 443
for SSL), as well as offloads the SSL processing overhead from Java to
optimized native C code.

## Gerrit Configuration

Ensure `'$site_path'/etc/gerrit.config` has the property
[httpd.listenUrl](config-gerrit.html#httpd.listenUrl) configured to use
*proxy-http://* or *proxy-https://* and a free port number. This may
have already been configured if proxy support was enabled during *init*.

``` 
  [httpd]
        listenUrl = proxy-http://127.0.0.1:8081/r/
```

## Apache 2 Configuration

To run Gerrit behind an Apache server using *mod\_proxy*, enable the
necessary Apache2 modules:

``` 
  a2enmod proxy_http
  a2enmod ssl          ; # optional, needed for HTTPS / SSL
```

Configure an Apache VirtualHost to proxy to the Gerrit daemon, setting
the *ProxyPass* line to use the *http://* URL configured above. Ensure
the path of ProxyPass and httpd.listenUrl match, or links will redirect
to incorrect locations.

``` 
        <VirtualHost *>
          ServerName review.example.com

          ProxyRequests Off
          ProxyVia Off
          ProxyPreserveHost On

          <Proxy *>
            Order deny,allow
            Allow from all
            # Use following line instead of the previous two on Apache >= 2.4
            # Require all granted
          </Proxy>

          AllowEncodedSlashes On
          ProxyPass /r/ http://127.0.0.1:8081/r/ nocanon
        </VirtualHost>
```

The two options *AllowEncodedSlashes On* and *ProxyPass .. nocanon* are
required since Gerrit 2.6.

### SSL

To enable Apache to perform the SSL processing, use *proxy-https://* in
httpd.listenUrl within Gerrit’s configuration file, and enable the SSL
engine in the Apache VirtualHost block:

``` 
        <VirtualHost *:443>
          SSLEngine on
          SSLCertificateFile    conf/server.crt
          SSLCertificateKeyFile conf/server.key

          ... same as above ...
        </VirtualHost>
```

See the Apache *mod\_ssl* documentation for more details on how to
configure SSL within the server, like controlling how strong of an
encryption algorithm is required.

### Troubleshooting

If you are encountering *Page Not Found* errors when opening the change
screen, your Apache proxy is very likely decoding the passed URL. Make
sure to either use *AllowEncodedSlashes On* together with *ProxyPass ..
nocanon* or alternatively a *mod\_rewrite* configuration with
*AllowEncodedSlashes NoDecode* set.

## Nginx Configuration

To run Gerrit behind an Nginx server, use a server statement such as
this one:

``` 
        server {
          listen 80;
          server_name review.example.com;

          location ^~ /r/ {
            proxy_pass        http://127.0.0.1:8081;
            proxy_set_header  X-Forwarded-For $remote_addr;
            proxy_set_header  Host $host;
          }
        }
```

### SSL

To enable Nginx to perform the SSL processing, use *proxy-https://* in
httpd.listenUrl within Gerrit’s configuration file, and enable the SSL
engine in the Nginx server statement:

``` 
        server {
          listen 443;
          server_name review.example.com;

          ssl  on;
          ssl_certificate      conf/server.crt;
          ssl_certificate_key  conf/server.key;

          ... same as above ...
        }
```

See the Nginx *http ssl module* documentation for more details on how to
configure SSL within the server, like controlling how strong of an
encryption algorithm is required.

### Troubleshooting

If you are encountering *Page Not Found* errors when opening the change
screen, your Nginx proxy is very likely decoding the passed URL. Make
sure to use a *proxy\_pass* URL without any path (esp. no trailing */*
after the *host:port*).

If you are using Apache httpd server with mod\_jk and AJP connector, add
the following option to your httpd.conf directly or included from
another file:

    JkOptions +ForwardURICompatUnparsed

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX


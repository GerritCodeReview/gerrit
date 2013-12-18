// ui-api-proxy is a reverse http proxy that allows the UI to be served from
// a different host than the API. This allows testing new UI features served
// from localhost but using live production data.
//
// Run the binary, download & install the Go tools available at
// http://golang.org/doc/install . To run, execute `go run ui-api-proxy.go`.
// For a description of the available flags, execute
// `go run ui-api-proxy.go --help`.
package main

import (
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"time"
)

var (
	ui   = flag.String("ui", "http://localhost:8080", "host to which ui requests will be forwarded")
	api  = flag.String("api", "https://gerrit-review.googlesource.com", "host to which api requests will be forwarded")
	port = flag.Int("port", 0, "port on which to run this server")
)

func main() {
	flag.Parse()

	uiURL, err := url.Parse(*ui)
	if err != nil {
		log.Fatalf("proxy: parsing ui addr %q failed: %v\n", *ui, err)
	}
	apiURL, err := url.Parse(*api)
	if err != nil {
		log.Fatalf("proxy: parsing api addr %q failed: %v\n", *api, err)
	}

	l, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%v", *port))
	if err != nil {
		log.Fatalln("proxy: listen failed: ", err)
	}
	defer l.Close()
	fmt.Printf("OK\nListening on http://%v/\n", l.Addr())

	err = http.Serve(l, &httputil.ReverseProxy{
		FlushInterval: 500 * time.Millisecond,
		Director: func(r *http.Request) {
			if strings.HasPrefix(r.URL.Path, "/changes/") || strings.HasPrefix(r.URL.Path, "/projects/") {
				r.URL.Scheme, r.URL.Host = apiURL.Scheme, apiURL.Host
			} else {
				r.URL.Scheme, r.URL.Host = uiURL.Scheme, uiURL.Host
			}
			if r.URL.Scheme == "" {
				r.URL.Scheme = "http"
			}
			r.Host, r.URL.Opaque, r.URL.RawQuery = r.URL.Host, r.RequestURI, ""
		},
	})
	if err != nil {
		log.Fatalln("proxy: serve failed: ", err)
	}
}

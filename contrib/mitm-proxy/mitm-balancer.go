package main

import (
  "flag"
  "io"
  "log"
  "net"
  "strings"
)

var (
  serversStr = flag.String("servers", "", "comma separated servers to balance")
  port = flag.String("port", "8444", "Port to serve HTTP requests on")
)

func connect(from io.Reader, to io.WriteCloser) {
  defer to.Close()
  io.Copy(to, from)
}

func handleConnection(incoming net.Conn, server string) {
  upstream, err := net.Dial("tcp", server)
  if err != nil {
    log.Fatal(err)
  }
  go connect(incoming, upstream)
  go connect(upstream, incoming)
}

func main() {
  flag.Parse()
  if len(*serversStr) == 0 {
    log.Fatal("You should specify at least one server")
  }
  servers := strings.Split(*serversStr, ",")
  log.Println("Number of upstreams: ", len(servers))
    ln, err := net.Listen("tcp", "127.0.0.1:" + *port)
  if err != nil {
    log.Fatal(err)
  }
  serverIndex := 0
  for {
    conn, err := ln.Accept()
    if err != nil {
      log.Println(err)
    }
    log.Println("ServerIndex=", serverIndex)
    go handleConnection(conn, servers[serverIndex])
    serverIndex = (serverIndex + 1) % len(servers)
  }

}

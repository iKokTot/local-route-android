// Local Route adapter for OpenFlux v0.13.0. GPL-3.0-or-later.
// Credentials arrive only on stdin. Stdout contains fixed status events, never core logs.
package main

import (
 "context"
 "bufio"
 "encoding/json"
 "io"
 "net"
 "os"
 "strings"
 "time"
 engine "openflux-mobile"
 "github.com/p1neappleXpress/OpenFlux/share"
)

func event(value string) { _ = json.NewEncoder(os.Stdout).Encode(map[string]string{"event":value}) }
func main() {
 // Core diagnostics can contain transport URLs; do not forward them to the app.
 engine.SetDebugLevel(0)
 engine.SetLowMemory(true)
 line, err := bufio.NewReader(io.LimitReader(os.Stdin,32770)).ReadString('\n')
 if err != nil || len(line)>32768 { event("invalid"); return }
 c, err := share.Decode(strings.TrimSpace(line))
 if err != nil { event("invalid"); return }
 if c.Mode!="" { event("unsupported"); return }
 if len(os.Args)>1 && os.Args[1]=="--check" { event("valid"); return }
 if dns:=os.Getenv("OPENFLUX_DNS"); net.ParseIP(dns)!=nil {
  net.DefaultResolver=&net.Resolver{PreferGo:true,Dial:func(ctx context.Context,network,address string)(net.Conn,error){return (&net.Dialer{Timeout:8*time.Second}).DialContext(ctx,"udp",net.JoinHostPort(dns,"53"))}}
 }
 var result string
 if c.Negotiate {
  specs:=[]map[string]interface{}{}
  for _, t:=range c.Transports {
   s:=map[string]interface{}{"type":t.Type,"url":t.URL,"name":t.Name,"priority":t.Priority}
   if t.Dial!="" { s["params"]=map[string]string{"dial":t.Dial} }
   specs=append(specs,s)
  }
  raw,_:=json.Marshal(map[string]interface{}{"context":c.Context,"transports":specs})
  result=engine.StartSessionProxy(string(raw),c.Secret,"127.0.0.1:1082","","","")
 } else {
  t:=c.Transports[0]
  result=engine.StartProxy(t.Type,t.URL,c.Secret,c.Codec,"","","127.0.0.1:1082","","","")
 }
 if result!="" { event("failed"); return }
 defer engine.StopProxy()
 event("listening")
 ticker:=time.NewTicker(time.Second); defer ticker.Stop()
 for range ticker.C {
  if !engine.ProxyIsRunning() { event("failed"); return }
  if engine.ProxyIsConnected() { event("connected") } else { event("connecting") }
 }
}

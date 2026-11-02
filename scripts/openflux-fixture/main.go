// Local-only integration fixture, NOT packaged in the delivered application.
package main
import (
 "fmt"
 "net/http"
 "time"
 engine "openflux-mobile"
)
func main() {
 engine.SetDebugLevel(0)
 go http.ListenAndServe("0.0.0.0:18081",http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){fmt.Fprint(w,"local-route-openflux-fixture")}))
 result:=engine.StartSessionExit(`{"context":"local-route-test","transports":[{"type":"direct","name":"direct","priority":100,"params":{"listen":"127.0.0.1:18082"}}]}`,"local-route-public-test-secret-2026")
 if result!="" { panic("exit fixture failed") }
 link,err:=engine.ExitShareLink("127.0.0.1","Local test")
 if err!=nil {panic("fixture link failed")}
 fmt.Println(link)
 for { time.Sleep(time.Hour) }
}

# OpenFlux linked Go modules

libopenflux.so: go1.26.6
	path	openflux-mobile/cmd/localroute
	mod	openflux-mobile	(devel)	
	dep	github.com/dlclark/regexp2/v2	v2.5.2	h1:HAsucWRhsqcDzl6Ua9aR8JwYOTzrZyPrF0/FNxJVAI0=
	dep	github.com/dop251/goja	v0.0.0-20260926152631-39ec2650adc9	h1:Ksd3Vil12b/ykT5NjGzd3qNc688cJB9OUXJZZChEktI=
	dep	github.com/dop251/goja_nodejs	v0.0.0-20260918173711-b481721df8a2	h1:4cOnoHK48VXmDGOPp2h+u7sYZw/1Ou2JbHjTgU0DF0A=
	dep	github.com/go-sourcemap/sourcemap	v2.1.4+incompatible	h1:a+iTbH5auLKxaNwQFg0B+TCYl6lbukKPc7b5x0n1s6Q=
	dep	github.com/google/btree	v1.1.2	h1:xf4v41cLI2Z6FxbKm+8Bu+m8ifhj15JuZ9sa0jZCMUU=
	dep	github.com/google/pprof	v0.0.0-20240727154555-813a5fbdbec8	h1:FKHo8hFI3A+7w0aUQuYXQ+6EN5stWmeY/AZqtM8xk9k=
	dep	github.com/google/uuid	v1.6.0	h1:NIvaJDMOsjHA8n1jAhLSgzrAzy1Hgr+hNrb57e+94F0=
	dep	github.com/gorilla/websocket	v1.5.3	h1:saDtZ6Pbx/0u+bgYQ3q96pZgCzfhKXGPqt7kZ72aNNg=
	dep	github.com/jlaffaye/ftp	v0.2.4	h1:JqI85DdkfZj8ntaHk8W9U2SC3jNfiPUU70+wtIWmlfE=
	dep	github.com/klauspost/compress	v1.20.0	h1:a3C1ke2ohxFymNlb2HWAHjDeKCI90scRskErZkR0ezA=
	dep	github.com/p1neappleXpress/OpenFlux	v0.0.0
	=>	..	(devel)	
	
	dep	github.com/pierrec/lz4/v4	v4.1.27	h1:+PhzhWDrjRj89TH2sw43nE3+4+W8lSxIuQadEHZyjUk=
	dep	github.com/pion/datachannel	v1.6.3	h1:HZAYLpunVI0xi057ZiANl558d0uSpomcWw4UEPz3qXs=
	dep	github.com/pion/dtls/v3	v3.1.9	h1:rpeycmLIkc4krpk1IxP7+39o11QdCXbmV9+FGi9yZJ8=
	dep	github.com/pion/ice/v4	v4.4.4	h1:nPhqLkf8NoE9O8NBKd3Xo2NNc2PlBMdM07tWZ+IH/i8=
	dep	github.com/pion/interceptor	v0.1.49	h1:iyBsNHRoLNNXZiJA/DdGvJgdyfS8YWDCzxIAlVjt5nY=
	dep	github.com/pion/logging	v0.2.4	h1:tTew+7cmQ+Mc1pTBLKH2puKsOvhm32dROumOZ655zB8=
	dep	github.com/pion/mdns/v2	v2.2.1	h1:lQPWv6Mo5jG1sKwqkYgfeXGX7/seban4x7/j0n485/M=
	dep	github.com/pion/randutil	v0.1.0	h1:CFG1UdESneORglEsnimhUjf33Rwjubwj6xfiOXBa3mA=
	dep	github.com/pion/rtcp	v1.2.18	h1:ZPgMZMCtBoReTixtFAyzcOkuJiebyB94qxtBAyvSooM=
	dep	github.com/pion/rtp	v1.10.5	h1:ip0HhO/wYZqQ4bKS+R99KnZh/GRCmIT0jDXikub7vlE=
	dep	github.com/pion/sctp	v1.11.3
	=>	../third_party/sctp	(devel)	
	
	dep	github.com/pion/sdp/v3	v3.0.20	h1:TS6DViqcmp+49f0+mjw9anbr9xY3vJtsZewxAvlMCRQ=
	dep	github.com/pion/srtp/v3	v3.1.0	h1:keugrBOxtvAv5Urf4lBMfso8OCAX3mm7Brdl26JSvik=
	dep	github.com/pion/stun/v4	v4.0.1	h1:S2ggK4hsUJJKoZfpL90uIqIRTj6xJ0NOIEkXgcpQif0=
	dep	github.com/pion/transport/v5	v5.1.1	h1:Sauq8YJqRopgRfIgnpfKpdpKG14g0N4jWlRgpWsEfN8=
	dep	github.com/pion/turn/v5	v5.1.2	h1:acEOO+D5txJ52vKfcilmSMItfP23Q9K6pJ1LM5P2VV0=
	dep	github.com/pion/webrtc/v4	v4.2.22	h1:eoRTHzLKW5tI9o5iahw3s4KL4IHPESsfIF06zWi3JAo=
	dep	github.com/skip2/go-qrcode	v0.0.0-20200617195104-da1b6568686e	h1:MRM5ITcdelLK2j1vwZ3Je0FKVCfqOLp5zO6trqMLYs0=
	dep	github.com/wlynxg/anet	v0.0.5	h1:J3VJGi1gvo0JwZ/P1/Yc/8p63SoW98B5dHkYDmpgvvU=
	dep	golang.org/x/crypto	v0.57.0	h1:3ZVCjf8Ggz7zneR/EHRVx68Ctf+2pmIMP2UFhh9cC6M=
	dep	golang.org/x/exp	v0.0.0-20250711185948-6ae5c78190dc	h1:TS73t7x3KarrNd5qAipmspBDS1rkMcgVG/fS1aRb4Rc=
	dep	golang.org/x/net	v0.59.0	h1:5zfYln+w5XCxwrnMMJPufRgNoXEaGxl0wo5GqPXyues=
	dep	golang.org/x/sys	v0.48.0	h1:bbX/i/6MgT9BVLM9RT1thmxL04yeTAhbEz4SyadbXoo=
	dep	golang.org/x/text	v0.42.0	h1:JbOZXgfeCPU9gacVtYliJqOhD+zhrEqK4LfdpmlUZqI=
	dep	golang.org/x/time	v0.15.0	h1:bbrp8t3bGUeFOx08pvsMYRTCVSMk89u4tKbNOZbp88U=
	dep	gvisor.dev/gvisor	v0.0.0-20260530041128-23ef90c42be7	h1:ZodhPVQzlCmuc1jt1ZIovMTwAJEArTPgq+UTu+mkVME=
	build	-buildmode=pie
	build	-compiler=gc
	build	-trimpath=true
	build	CGO_ENABLED=1
	build	GOARCH=arm64
	build	GOOS=android
	build	GOARM64=v8.0

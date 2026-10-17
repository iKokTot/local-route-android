"""Local emulator test fixture, not a public proxy. No real credentials or forwarding.

Accepts VLESS over TCP or WebSocket and returns a fixed HTTP response only on
test destination port 18089; forwards only Cloudflare DoH on 1.1.1.1:443.
Bind is loopback-only. This fixture does not provide general Internet access.
"""
import argparse
import base64
import hashlib
import socketserver
import struct
import socket
import select
import ssl
import threading
import http.server

class LocalHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = b'local-route-dpi-fixture'
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *args):
        pass

class UdpHandler(socketserver.BaseRequestHandler):
    def handle(self):
        data, connection = self.request
        connection.sendto(data, self.client_address)

UUID = bytes.fromhex('00000000000040008000000000000000')
BODY = b'local-route-vless-fixture'


def exact(stream, count):
    data = b''
    while len(data) < count:
        part = stream.read(count - len(data))
        if not part:
            raise EOFError()
        data += part
    return data


class Handler(socketserver.StreamRequestHandler):
    rbufsize = 0
    def handle(self):
        try:
            self.serve()
        except (EOFError, ConnectionError, TimeoutError, ssl.SSLError):
            pass

    def serve(self):
        self.connection.settimeout(15)
        first = exact(self.rfile, 1)
        websocket = first == b'G'
        if websocket:
            headers = first + self.rfile.readline()
            while True:
                line = self.rfile.readline()
                headers += line
                if line == b'\r\n':
                    break
            key = next(line.split(b':', 1)[1].strip() for line in headers.split(b'\r\n') if line.lower().startswith(b'sec-websocket-key:'))
            accept = base64.b64encode(hashlib.sha1(key + b'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest())
            self.wfile.write(b'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + b'\r\n\r\n')
            payload = self.ws_frame()
            while len(payload) < 26:
                payload += self.ws_frame()
            first, raw = payload[:1], payload[1:]
        else:
            raw = exact(self.rfile, 25)
        assert first == b'\x00' and raw[:16] == UUID
        assert raw[16] == 0 and raw[17] == 1
        port = int.from_bytes(raw[18:20], 'big')
        address = socket.inet_ntoa(raw[21:25]) if raw[20] == 1 else ''
        if port == 443 and (address == '1.1.1.1' or address.startswith(('149.154.', '91.108.'))):
            # Only real Telegram DCs and Cloudflare DoH are forwarded by this loopback fixture.
            print('RELAY: ' + address + ':443', flush=True)
            with socket.create_connection((address, 443), timeout=15) as upstream:
                self.respond(b'\x00\x00', websocket)
                if websocket:
                    upstream.sendall(payload[26:])
                while True:
                    readable, _, _ = select.select([self.connection, upstream], [], [], 15)
                    if not readable:
                        return
                    for source in readable:
                        if source is upstream:
                            data = upstream.recv(65536)
                            if not data:
                                return
                            self.respond(data, websocket)
                        else:
                            data = self.ws_frame() if websocket else self.connection.recv(65536)
                            if not data:
                                return
                            upstream.sendall(data)
        if raw[20] != 1 or port != 18089:
            return
        if websocket:
            while b'\r\n\r\n' not in payload[26:]:
                payload += self.ws_frame()
        else:
            request = b''
            while b'\r\n\r\n' not in request:
                request += exact(self.rfile, 1)
        response = b'\x00\x00HTTP/1.1 200 OK\r\nContent-Length: ' + str(len(BODY)).encode() + b'\r\nConnection: close\r\n\r\n' + BODY
        self.respond(response, websocket)
        print('PASS: VLESS ' + ('WebSocket' if websocket else 'TCP') + ' request reached fixture', flush=True)

    def ws_frame(self):
        header = exact(self.rfile, 2)
        size = header[1] & 127
        if size == 126:
            size = struct.unpack('!H', exact(self.rfile, 2))[0]
        elif size == 127:
            size = struct.unpack('!Q', exact(self.rfile, 8))[0]
        mask = exact(self.rfile, 4) if header[1] & 128 else b''
        data = exact(self.rfile, size)
        return bytes(value ^ mask[index % 4] for index, value in enumerate(data)) if mask else data

    def respond(self, data, websocket):
        if websocket:
            header = bytes([len(data)]) if len(data) < 126 else b'\x7e' + struct.pack('!H', len(data))
            self.wfile.write(b'\x82' + header + data)
        else:
            self.wfile.write(data)


parser = argparse.ArgumentParser()
parser.add_argument('--cert')
parser.add_argument('--key')
args = parser.parse_args()
local_http = http.server.ThreadingHTTPServer(('127.0.0.1', 18445), LocalHandler)
local_udp = socketserver.ThreadingUDPServer(('127.0.0.1', 18446), UdpHandler)
threading.Thread(target=local_http.serve_forever, daemon=True).start()
threading.Thread(target=local_udp.serve_forever, daemon=True).start()
if args.cert and args.key:
    secure = socketserver.ThreadingTCPServer(('127.0.0.1', 18444), Handler)
    secure.daemon_threads = True
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(args.cert, args.key)
    secure.socket = tls.wrap_socket(secure.socket, server_side=True)
    threading.Thread(target=secure.serve_forever, daemon=True).start()
with socketserver.ThreadingTCPServer(('127.0.0.1', 18443), Handler) as server:
    print('VLESS fixture listening on localhost:18443', flush=True)
    server.serve_forever()

import http.server
import socketserver
import subprocess
import urllib.parse
import os
import socket
import threading

# Configuración de Puertos
HTTP_PORT = 8080
UDP_PORT = 8081
VD_PATH = r"C:\Program Files\Virtual Desktop Streamer\VirtualDesktop.Streamer.exe"

def udp_discovery_listener():
    """Escucha peticiones Broadcast en el puerto 8081 y responde con la IP del PC"""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(('', UDP_PORT))
    print(f"[UDP Discovery] Escuchando en puerto {UDP_PORT}...")
    
    while True:
        try:
            data, addr = sock.recvfrom(1024)
            message = data.decode('utf-8').strip()
            if message == "QUESTPLAY_DISCOVER":
                print(f"[UDP Discovery] Petición recibida desde {addr[0]}. Respondiendo...")
                sock.sendto(b"QUESTPLAY_OFFER", addr)
        except Exception as e:
            print(f"[UDP Error] {e}")

# Iniciar hilo de autodescubrimiento UDP en segundo plano
threading.Thread(target=udp_discovery_listener, daemon=True).start()

class DynamicVRHandler(http.server.SimpleHTTPRequestHandler):
    """Maneja las peticiones HTTP para lanzar los juegos de PCVR"""
    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        query_params = urllib.parse.parse_qs(parsed_url.query)
        
        if parsed_url.path == '/launch' and 'exe' in query_params:
            exe_path = urllib.parse.unquote(query_params['exe'][0])
            if os.path.exists(exe_path):
                print(f"[+] Inyectando PCVR: {exe_path}")
                subprocess.Popen([VD_PATH, "-launch", exe_path])
                self.send_response(200)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(b"OK")
                return
            else:
                print(f"[-] Ruta no encontrada: {exe_path}")
        
        self.send_response(400)
        self.end_headers()

if __name__ == '__main__':
    with socketserver.TCPServer(("", HTTP_PORT), DynamicVRHandler) as httpd:
        print(f"=== Servidor PCVR Activo (HTTP:{HTTP_PORT} | UDP:{UDP_PORT}) ===")
        print("Esperando peticiones de las Meta Quest en la red local...")
        httpd.serve_forever()
from socket import *
from time import sleep

from uuid import UUID

def to_int(bytes):
    shift = 0
    out = 0
    for b in bytes:
        out |= b << shift
        shift += 8
    return out

LOGIN_PACKET = "UTO\0\1\0".encode()
HEARTBEAT_HEADER = "UTO\0\2".encode()

MAX_SESSIONS = 1

sock = socket(AF_INET, SOCK_DGRAM)
addr = ("127.0.0.1", 1337)

sessions = []

for i in range(MAX_SESSIONS):
    sock.sendto(LOGIN_PACKET, addr)
    msg, _ = sock.recvfrom(4096)

    if msg[4] == 255: # Kick packet - occurs when the server is full
        break
    if msg[4] == 253: # Ratelimit warning packet - chill out for a second
        sleep(1)
        continue

    num = to_int(msg[5:9])
    uuid = UUID(bytes=msg[9:])

    sessions.append((num, uuid))
    print("Session %02d (%s)"%sessions[-1])

print("Created", len(sessions), "dummy sessions")
sleep(2)
while True:
    print("Heartbeat")
    for session in sessions:
            try:
                sock.sendto(HEARTBEAT_HEADER + session[1].bytes, addr)
                sock.settimeout(4)
                res, _ = sock.recvfrom(4096)
                if res[4] == 255:
                    print("Session", session, "disconnected")
                    print("Reason:", res[5:-1].decode("UTF-8"))
                    sessions.remove(session)
                sock.settimeout(0)
            except:
                print("Zombie session", session)
                sessions.remove(session)
    if not sessions:
        print("All sessions closed")
        break
    sleep(1)

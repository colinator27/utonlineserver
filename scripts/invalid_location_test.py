from time import sleep
from uuid import UUID, uuid4 as rand_uuid

from socket import *
import traceback
import threading
import struct
import sys

def packet_to_string(packet):
    return "".join(["\\x%02x"%i for i in packet])

CLIENT_PACKETS = {
    "LOGIN":                lambda:                                 chr(0x01).encode(),
    "HEARTBEAT":            lambda uuid:                            chr(0x02).encode() + uuid.bytes,
    "PLAYER_CHANGE_ROOM":   lambda uuid, room, sprite, frame, x, y: chr(0x0a).encode() + uuid.bytes + struct.pack("<hhhff", room, sprite, frame, x, y),
    "PLAYER_VISUAL_UPDATE": lambda uuid, sprite, frame, x, y:       chr(0x0b).encode() + uuid.bytes + struct.pack("<hhff", sprite, frame, x, y),
}

# Why am I like this
# Packet parsing using struct, list comprehension, and dictionaries
SERVER_PACKETS = {
    
    1:("SESSION", lambda b: {
        "id" : struct.unpack("<I", b[:4])[0],
        "uuid" : UUID(bytes=b[4:])
    }),
    
    2:("HEARTBEAT", lambda b: {}),
    
    10:("PLAYER_JOIN_ROOM", lambda b: {
        "room" : struct.unpack("<I", b[:4])[0],
        "numPlayers" : struct.unpack("<H", b[4:6])[0],
        "players" : [{
            "id"     : player_id,
            "sprite" : sprite,
            "frame"  : frame,
            "coords" : (x, y)
        } for player_id, sprite, frame, x, y in [struct.unpack("<Ihhff", b[6+16*i:6+16*(i+1)]) for i in range(struct.unpack("<H", b[4:6])[0])]]
    }),

    11:("PLAYER_LEAVE_ROOM", lambda b: {
        "room" : struct.unpack("<i", b[:4])[0],
        "id"   : struct.unpack("<I", b[4:])[0]
    }),
        
    12:("PLAYER_VISUAL_UPDATE", lambda b: [{
        "timestamp" : timestamp,
        "room"      : room,
        "id"        : player_id,
        "sprite"    : sprite,
        "frame"     : frame,
        "coords"    : (x, y)
    } for timestamp, room, player_id, sprite, frame, x, y in [struct.unpack("<Qiihhff", b)]][0]),

    253:("RATELIMIT_WARNING", lambda b: {}),
        
    254:("FORCE_TELEPORT", lambda b: {
        "coords" : struct.unpack("<ff", b)
    }),
    255:("KICK_MESSAGE", lambda b: {
        "message" : b[:-1].decode()
    })
}

sock = socket(AF_INET, SOCK_STREAM)
sock.connect(("127.0.0.1", 1337))

def func_wrapper(func, data):
    print(packet_to_string(data))
    return func(data)

def retrieve_packets(data):
    out = []
    header = "\x55\x54\x4f\x00".encode()
    while data[:4] == header:
        try:
            next_index = data[4:].index(header)
            out.append(data[:next_index+4])
            data = data[next_index:]
        except ValueError:
            out.append(data)
            break
    return out

def recv():
    data = sock.recv(4096)
    if not data:
        print("Connection closed")
        sys.exit(0)

    packets = retrieve_packets(data)
    for packet in packets:
        name, func = None, None
        try:
            name, func = SERVER_PACKETS[packet[4]]
        except Exception as e:
            traceback.print_exc()
            return None, None
        try:
            return name, func_wrapper(func, packet[5:])
        except Exception as e:
            traceback.print_exc()
        return None, None

def send(data):
    data = "UTO\x00".encode() + data
    sock.sendall(data)

def receive_loop():
    try:
        while True:
            print(recv())
    except Exception as e:
        print(e, file=sys.stderr)
        sock.close()
        sys.exit(1)

def heartbeater():
    while True:
        send(CLIENT_PACKETS["HEARTBEAT"](uuid))
        sleep(2)

inf = float("inf")
nan = float("nan")

heartbeat_thread = threading.Thread(target=heartbeater)
receive_thread = threading.Thread(target=receive_loop)

send(CLIENT_PACKETS["LOGIN"]())
session = recv()

uuid = session[1]["uuid"]
heartbeat_thread.start()
receive_thread.start()

origin_x = 0
origin_y = 100

pos_x = 269
pos_y = 100

send(CLIENT_PACKETS["PLAYER_CHANGE_ROOM"](uuid, 132, 1131, 0, nan, origin_y))

while True:
    send(CLIENT_PACKETS["PLAYER_VISUAL_UPDATE"](uuid, 1131, 0, nan, origin_y))
    send(CLIENT_PACKETS["PLAYER_VISUAL_UPDATE"](uuid, 1131, 0, origin_x, origin_y))
    sleep(1)

    send(CLIENT_PACKETS["PLAYER_VISUAL_UPDATE"](uuid, 1131, 0, nan, origin_y))
    send(CLIENT_PACKETS["PLAYER_VISUAL_UPDATE"](uuid, 1131, 0, pos_x, pos_y))
    sleep(1)


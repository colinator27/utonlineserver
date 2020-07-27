from socket import *
from os import urandom
from random import choice
from time import sleep

packet_types = [
    ("LOGIN",               0x01, 0),
    ("HEARTBEAT",           0x02, 0),
    ("PLAYER_CHANGE_ROOM",  0x0a,30),
    ("PLAYER_VISUAL_UPDATE",0x0b,12),
]

sock = socket(AF_INET, SOCK_DGRAM)
addr = ("127.0.0.1", 1337)

delay = 0

while True:
    name, packet, arg_length = choice(packet_types)
    print("Sending", name, "packet")
    data= "UTO\x00".encode()
    data += chr(packet).encode()
    data += urandom(arg_length)
    data += '\x00'.encode()
    print("Packet", ("\\x%02x"*len(data))%tuple(data))
    sock.sendto(data, addr)

    if delay > 0:
        sleep(delay)
    

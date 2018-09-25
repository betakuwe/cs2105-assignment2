import sys
import socket
from struct import unpack
from zlib import crc32

if len(sys.argv) != 2:
    print(f"Usage: {sys.argv[0]} <port>")
    exit(-1)

# Create a socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('', int(sys.argv[1])))

while True:
    try:
        data, addr = sock.recvfrom(1000)
        if (len(data) < 8):
            print("Pkt too short")
            continue

        my_checksum = crc32(data[4:])

        # Debug print
        print(f"Received CRC:{my_checksum} Data:{data}")

        your_checksum, num = unpack("Ii", data)
        if (your_checksum != my_checksum):
            print("Pkt corrupt")
        else:
            print(f"Pkt {num}")

            # send ack
            sock.sendto(b"A", addr)
    except:
        print("Client disconnected") 

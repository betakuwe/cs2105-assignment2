import sys
import socket
from struct import pack
from zlib import crc32

if len(sys.argv) != 4:
    print(f"Usage: {sys.argv[0]} <host> <port> <num_pkts>")
    exit(-1)
    
(_, host, port, num) = sys.argv
address = (host, int(port))

# Create a socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)


for i in range(int(num)):
    # pack an integer into byte string
    data = pack("i", i)
    checksum = crc32(data)

    # add crc in front of byte string
    data = pack("I", checksum) + data

    sock.sendto(data, address)

    # Debug output
    print(f"Sent CRC:{checksum} Contents:{data}")

sock.close()

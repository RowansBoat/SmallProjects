#!/usr/bin/env python3
"""
A simple FTP client supporting basic file operations over passive mode connections.

Usage:
    ./4700ftp [operation] [param1] [param2]

Operations:
    ls      - List directory contents
    cp      - Copy files to/from server
    mv      - Move files to/from server
    rm      - Delete a file
    mkdir   - Create a directory
    rmdir   - Remove a directory
"""

import socket
import urllib.parse
import sys
import os


def send_message(sock, message):
    """Send a message to the FTP server and return the response."""
    sock.sendall(message.encode("utf-8") + b"\r\n")
    response = receive_message(sock)
    print(f"C: {message}")
    print(f"S: {response}")
    return response


def receive_message(sock):
    """Receive a complete FTP response (terminated by CRLF) from the server."""
    response = b""
    while b"\r\n" not in response:
        response += sock.recv(4096)
    return response.decode("utf-8").strip()


def FTPparser(url):
    """
    Parse an FTP URL and extract connection parameters.
    
    Returns:
        tuple: (username, password, hostname, port, path)
               Defaults: username='anonymous', password='', port=21, path='/'
    """
    parsed = urllib.parse.urlparse(url)
    username = parsed.username or "anonymous"
    password = parsed.password or ""
    hostname = parsed.hostname
    port = parsed.port or 21
    path = parsed.path or "/"
    return username, password, hostname, port, path


def PASVparse(response):
    """
    Parse a PASV response to extract the data connection IP and port.
    
    Args:
        response: PASV response string, e.g., '227 Entering Passive Mode (h1,h2,h3,h4,p1,p2).'
    
    Returns:
        tuple: (ip_address, port)
    """
    start = response.find("(")
    end = response.find(")")
    numbers = response[start + 1:end].split(",")
    ip = ".".join(numbers[:4])
    port = int(numbers[4]) * 256 + int(numbers[5])
    return ip, port


def listdiretory(control_sock, path):
    """
    List the contents of a directory on the FTP server.
    
    Args:
        control_sock: The FTP control connection socket
        path: Remote directory path to list
    
    Returns:
        str: Directory listing from the server
    """
    pasv_response = send_message(control_sock, "PASV")
    pasv_ip, pasv_port = PASVparse(pasv_response)

    data_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    data_sock.connect((pasv_ip, pasv_port))

    send_message(control_sock, f"LIST {path}")

    listing = b""
    while True:
        data = data_sock.recv(4096)
        if not data:
            break
        listing += data

    data_sock.close()

    final_response = receive_message(control_sock)
    print(f"S: {final_response}")

    return listing.decode("utf-8")


def upload(control_sock, remote_path, local_path):
    """
    Upload a local file to the FTP server.
    
    Args:
        control_sock: The FTP control connection socket
        remote_path: Destination path on the server
        local_path: Path to the local file to upload
    """
    pasv_response = send_message(control_sock, "PASV")
    pasv_ip, pasv_port = PASVparse(pasv_response)

    data_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    data_sock.connect((pasv_ip, pasv_port))

    send_message(control_sock, f"STOR {remote_path}")

    with open(local_path, "rb") as f:
        file_data = f.read()

    data_sock.sendall(file_data)
    data_sock.close()

    print(f"S: {receive_message(control_sock)}")


def download(control_sock, remote_path, local_path):
    """
    Download a file from the FTP server to a local file.
    
    Args:
        control_sock: The FTP control connection socket
        remote_path: Path to the file on the server
        local_path: Destination path for the local file
    
    Returns:
        str: The downloaded file contents as a string
    """
    pasv_response = send_message(control_sock, "PASV")
    pasv_ip, pasv_port = PASVparse(pasv_response)

    data_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    data_sock.connect((pasv_ip, pasv_port))

    send_message(control_sock, f"RETR {remote_path}")

    file_data = b""
    while True:
        data = data_sock.recv(4096)
        if not data:
            break
        file_data += data

    data_sock.close()

    print(f"S: {receive_message(control_sock)}")

    with open(local_path, "wb") as f:
        f.write(file_data)

    return file_data.decode("utf-8")


def quit_ftp(sock):
    """Send the QUIT command to gracefully close the FTP session."""
    send_message(sock, "QUIT")


def main():
    """Main entry point for the FTP client."""
    operation = sys.argv[1]
    param1 = sys.argv[2]
    param2 = sys.argv[3] if len(sys.argv) > 3 else None

    # Determine which parameter contains the FTP URL
    if param1.startswith("ftp://"):
        ftp_url = param1
    elif param2 and param2.startswith("ftp://"):
        ftp_url = param2

    username, password, host, port, path = FTPparser(ftp_url)

    try:
        print("Connecting...")
        sockfd = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sockfd.connect((host, port))
        print("Connected")

        # Receive and display the server welcome message
        welcome = receive_message(sockfd)
        print(f"Welcome: {welcome}")

        # Authenticate with the server
        send_message(sockfd, f"USER {username}")
        if password:
            send_message(sockfd, f"PASS {password}")

        # Configure transfer parameters (binary mode, stream, file structure)
        send_message(sockfd, "TYPE I")
        send_message(sockfd, "MODE S")
        send_message(sockfd, "STRU F")

        # Execute the requested operation
        if operation == "ls":
            listing = listdiretory(sockfd, path)
            print(listing)
        elif operation == "rm":
            send_message(sockfd, f"DELE {path}")
        elif operation == "rmdir":
            send_message(sockfd, f"RMD {path}")
        elif operation == "mkdir":
            send_message(sockfd, f"MKD {path}")
        elif operation == "cp":
            if param1.startswith("ftp://"):
                download(sockfd, path, param2)
            else:
                upload(sockfd, path, param1)
        elif operation == "mv":
            if param1.startswith("ftp://"):
                download(sockfd, param1, param2)
                send_message(sockfd, f"DELE {param1}")
                receive_message(sockfd)
            else:
                remote_name = os.path.basename(param1)
                upload(sockfd, remote_name, param1)
                os.remove(param1)

        quit_ftp(sockfd)
        sockfd.close()

    except Exception as e:
        print(f"Error: {e}")


if __name__ == "__main__":
    main()
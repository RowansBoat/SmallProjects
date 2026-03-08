#!/usr/bin/env -S python3 -u
# I used the starter code for this project
# kept the useless import from starter code but I did end up using time but later deleted that code as it was for testing
import argparse, socket, time, json, select, struct, sys, math

class Router:
    # These three are from starter code
    relations = {} # Either a cust, peer, or prov
    sockets = {} # Sockets used to talk to neighbors
    ports = {} # UDP port the neighbor is listening on

    def __init__(self, asn, connections):
        print("Router at AS %s starting up" % asn)
        self.asn = asn

        # Needed to add this for my implementation
        # forwarding table for my implementation
        self.routingtable = []

        # log of every raw update message received
        self.announcements = []

        # log of every raw withdraw message received
        self.withdrawals = []

        # From Starter code
        for relationship in connections:
            port, neighbor, relation = relationship.split("-")

            # Creating our personal socket
            self.sockets[neighbor] = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.sockets[neighbor].bind(('localhost', 0))
            self.ports[neighbor] = int(port)
            self.relations[neighbor] = relation

            # Per spec we send a handshake to all neighbors to let em know we are alive
            self.send(neighbor, json.dumps({ "type": "handshake", "src": self.our_addr(neighbor), "dst": neighbor, "msg": {}  }))
    
    # From Starter code
    # Just tells us our ip which also always ends in .1
    def our_addr(self, dst):
        quads = list(int(qdn) for qdn in dst.split('.'))
        quads[3] = 1
        return "%d.%d.%d.%d" % (quads[0], quads[1], quads[2], quads[3])

    # From Starter code
    # Just sends a message to a neighbors port
    def send(self, network, message):
        self.sockets[network].sendto(message.encode('utf-8'), ('localhost', self.ports[network]))

    # Converts an ip address into an int
    def ip_to_int(self, ip):
        parts = list(int(x) for x in ip.split('.'))
        result = 0

        # Ip addresses should always have a few defining characterists
        # Thus I can create an array of parts based off that to convert to an int
        for part in parts:
            result = (result << 8) | part
        return result

    # Converts an int into an ip address
    # Pull out each of the 4 bytes and turn it back into an ip string
    # Essentially the our_addr function. 
    # I just rewrote it to be more readable as I was having trouble understanding the original format
    def int_to_ip(self, n):
        byte1 = (n >> 24) & 0xFF
        byte2 = (n >> 16) & 0xFF
        byte3 = (n >> 8)  & 0xFF
        byte4 = n         & 0xFF
        return "%d.%d.%d.%d" % (byte1, byte2, byte3, byte4)

    # Convert netmask to int and count the 1 bits to get prefix length
    def mask_len(self, netmask):
        return bin(self.ip_to_int(netmask)).count('1')

    # Convert a prefix length back into a netmask integer
    def prefix_len_to_mask(self, length):
        if length == 0:
            return 0
        return ((1 << length) - 1) << (32 - length)

    # Determines our best route
    # We return None if a route does not exist otherwise return the best routing table for the given dst_ip
    def route_finder(self, dst_ip):
        # Need it as an int so we can do the mask math
        dst = self.ip_to_int(dst_ip)

        # start by finding all possible possible routes to the dst
        candidates = []
        for entry in self.routingtable:
            net  = self.ip_to_int(entry['network'])
            mask = self.ip_to_int(entry['netmask'])
            if (dst & mask) == net:
                candidates.append(entry)

        # This is for if there is no possible route to the dst
        # This is a possibility which we must cover
        if not candidates:
            return None
        
        # Sort by BGP tiebreak rules, best route ends up at index 0
        candidates.sort(key=self.get_route_score, reverse=True)

        # Returns the candidate with the best path
        return candidates[0]
    
    # Returns a tuple used to score a route, higher score = better route
    def get_route_score(self, e):
        # The longer the prefix the more specific the address is 
        prefix_len = self.mask_len(e['netmask'])

        # “localpref” is the “weight” assigned to this route
        localpref = e['localpref']

        # “selfOrigin” describes whether this route was added by the local administrator (true) or not (false), where “true” routes are preferred
        self_origin = 1 if e['selfOrigin'] else 0

        # “ASPath” is the list of Autonomous Systems that the packets along this route will traverse, where preference is given to routes with shorter ASPaths
        aspath_len = -len(e['ASPath'])

        # the preference order is IGP > EGP > UNK
        origin_score = {'IGP': 2, 'EGP': 1, 'UNK': 0}.get(e['origin'], 0)

        # lowest IP wins. This is our tiebreak
        peer_ip = -self.ip_to_int(e['peer'])

        return (prefix_len, localpref, self_origin, aspath_len, origin_score, peer_ip)

    # 1) adjacent numerically 2) forward to the same next-hop router, and 3) have the same attributes
    # then the two entries can be aggregated into a single entry

    def try_aggregate(self, entry1, entry2):
        # We should only be aggregating if all of the following match
        # These are the attributes mentioned above
        if entry1['netmask'] != entry2['netmask']:
            return None
        if entry1['peer'] != entry2['peer']:
            return None
        if entry1['localpref'] != entry2['localpref']:
            return None
        if entry1['selfOrigin'] != entry2['selfOrigin']:
            return None
        if entry1['origin'] != entry2['origin']:
            return None
        if entry1['ASPath'] != entry2['ASPath']:
            return None
        
        # Cant merge a /0 so we skip it
        prefix_length = self.mask_len(entry1['netmask'])
        if prefix_length == 0:
            return None
        
        # The merged route has a prefix one bit shorter
        merged_mask = self.prefix_len_to_mask(prefix_length - 1)
        lower_network = self.ip_to_int(entry1['network'])
        higher_network = self.ip_to_int(entry2['network'])

        # Both networks need to be in the same bigger network for this to work
        same_prefix = (lower_network & merged_mask) == (higher_network & merged_mask)
        if not same_prefix:
            return None
        if lower_network > higher_network:

            # Make sure lower_network is actually the lower one
            lower_network, higher_network = higher_network, lower_network

        # This bit is what separates the two networks we are trying to merge
        split_bit = 1 << (32 - prefix_length)

        # lower_half must have that bit = 0 and upper_half must have it = 1
        is_lower_half = (lower_network & split_bit) == 0
        is_upper_half = (higher_network & split_bit) != 0
        if not is_lower_half or not is_upper_half:
            return None
        
        # Copy entry1 and update the network and mask to the merged version
        merged = dict(entry1)
        merged['network'] = self.int_to_ip(lower_network & merged_mask)
        merged['netmask'] = self.int_to_ip(merged_mask & 0xFFFFFFFF)
        return merged

    # Merges routes until no more routes can be merged together
    def merge_routes(self, routes):
        # Keep merging until no more merges are possible
        changed = True
        while changed:
            changed = False

            # Check every pair of entries
            for i in range(len(routes)):
                for j in range(i + 1, len(routes)):
                    result = self.try_aggregate(routes[i], routes[j])

                    # If they can be merged, replace both with the merged entry and restart
                    if result is not None:
                        first = routes[i]
                        second = routes[j]
                        routes.remove(first)
                        routes.remove(second)
                        routes.append(result)
                        changed = True
                        break
                if changed:

                    # A merge happened so restart the whole loop from scratch
                    break
        return routes

    # Function which allows us to check if we are allowed to forward something
    def allowed_to_forward(self, srcif, dst_neighbor):
        # Look up relationship type for src
        src_rel = self.relations[srcif]

        # Look up relationship type for dst 
        dst_rel = self.relations[dst_neighbor]

        # We always forward to customers
        # They are literally paying us to forward them stuff so we do
        if dst_rel == 'cust':
            return True
        
        # Forward to peers/providers only if learned from a customer
        # Should only do this if a customer needs it
        if src_rel == 'cust':
            return True
        return False
    
    # Function which allows us to check if we are allowed to forward data
    def allowed_to_forward_data(self, srcif, dst_neighbor):
        # Figure out if this is a customer, provider, peer, or empty
        src_rel = self.relations.get(srcif, '')

        # Figure out if this is a customer, provider, peer, or empty
        dst_rel = self.relations.get(dst_neighbor, '')

        # We should only forward if it is a customer
        if src_rel == 'cust' or dst_rel == 'cust':
            return True
        return False

    # aggregation should be triggered after each “update” has been received, i.e to compress the table.
    def manage_update(self, srcif, msg):
        # Save raw update for use in rebuild
        self.announcements.append(msg)
        # Rebuild our forwarding table with the new info
        self.rebuild()

        # We need to forward to correct neighbors
        for neighbor in self.sockets:
            if neighbor == srcif:
                continue # never send back to the source
            if not self.allowed_to_forward(srcif, neighbor):
                continue
            update_msg = {
                "type": "update",
                "src":  self.our_addr(neighbor),
                "dst":  neighbor,
                "msg": 
                {
                    "network": msg['msg']['network'],
                    "netmask": msg['msg']['netmask'],
                    "ASPath": [self.asn] + msg['msg']['ASPath'],
                }
            }
            # Tell our neighbors about the new information
            self.send(neighbor, json.dumps(update_msg))

    # "“withdraw” messages may require your router to disaggregate its table"
    def manage_withdraw(self, srcif, msg):
        # Save raw withdrawl for use in rebuild
        self.withdrawals.append(msg)
        # Rebuild our forwarding table with the new info
        self.rebuild()

        # We need to forward withdrawl notis to correct neighbors
        for neighbor in self.sockets:
            if neighbor == srcif:
                continue # never send back to the source
            if not self.allowed_to_forward(srcif, neighbor):
                continue
            withdraw_msg = {
                "type": "withdraw",
                "src": self.our_addr(neighbor),
                "dst": neighbor,
                "msg": msg['msg'],
            }
            # Tell our neighbors about the new information
            self.send(neighbor, json.dumps(withdraw_msg))

    # easiest way I found to handle withdrawals is to just wipe the table and start over using our saved messages
    def rebuild(self):
        # Start with an empty dict to track active routes
        # We care about peer, network, and netmask
        current_routes = {}  

        # Add all updates to the active dict
        for update in self.announcements:
            peer = update['src']
            net = update['msg']['network']
            mask = update['msg']['netmask']
            route_id = (peer, net, mask)
            current_routes[route_id] = update

        # Remove any routes that were withdrawn
        for withdrawal in self.withdrawals:
            peer = withdrawal['src']
            for net_info in withdrawal['msg']:
                route_id = (peer, net_info['network'], net_info['netmask'])
                if route_id in current_routes:
                    del current_routes[route_id]

        # We now need to rebuild our route list with what is left after removal
        routes = []
        for update in current_routes.values():
            entry = {
                'network': update['msg']['network'],
                'netmask': update['msg']['netmask'],
                'peer': update['src'],
                'localpref': update['msg']['localpref'],
                'selfOrigin': update['msg']['selfOrigin'],
                'ASPath': update['msg']['ASPath'],
                'origin': update['msg']['origin'],
            }
            routes.append(entry)

        # Merge any remaining routes to get rid of any dupes.
        # This will now be our new routing table :)
        self.routingtable = self.merge_routes(routes)

    # Once your router has received some updates, 
    # it will have a forwarding table that it can use to try and 
    # deliver data messages to their final destination.
    def manage_data(self, srcif, msg):
        # Get the destination IP from the message
        dst_ip = msg['dst']
        # Find the best route for this data to the destination
        route = self.route_finder(dst_ip)

        # If no route exists we must send an error saying no route
        # Made a helper for this since i had to use this multiple times 
        if route is None:
            self.send_no_route_avaliable(srcif, msg)
            return

        # Get the next hop peer from the route
        next_hop = route['peer']

        # Check BGP policy
        # We must comply with this policy which is why we have to check this
        can_send = self.allowed_to_forward_data(srcif, next_hop)
        if not can_send:
            self.send_no_route_avaliable(srcif, msg)
            return
        # By this point we must be in compliance so we can send the data to the next hop
        self.send(next_hop, json.dumps(msg))

    # Sends a no route message
    # Just a simple helper method since i needed to use this quite a few trimes.
    def send_no_route_avaliable(self, srcif, msg):
        self.send(srcif, json.dumps(
            {
                "type": "no route",
                "src": self.our_addr(srcif),
                "dst": msg['src'],
                "msg": {}
            }))

    # When your router receives a “dump” message, 
    # it must respond with a “table” message that contains 
    # a copy of the current routing announcement cache in your router.
    def manage_dump(self, srcif, msg):
        # Build the list of routes and send it back to whoever asked
        # Per the spec this is serialized
        table_entries = []
        for route in self.routingtable:
            entry = {
                "network": route['network'],
                "netmask": route['netmask'],
                "peer": route['peer'],
                "localpref": route['localpref'],
                "selfOrigin": route['selfOrigin'],
                "ASPath": route['ASPath'],
                "origin": route['origin'],
            }
            table_entries.append(entry)
        # Respond with table message which contains copy of the current routing announcement cache
        # Taken from spec but all that rlly means is send it back
        response_for_dump = json.dumps(
            {
            "type": "table",
            "src": self.our_addr(srcif),
            "dst": msg["src"],
            "msg": table_entries,
        })
        self.send(srcif, response_for_dump)
    
    # Mostly From Starter code
    def run(self):
        while True:
            socks = select.select(self.sockets.values(), [], [], 0.2)[0]
            for conn in socks:
                k, addr = conn.recvfrom(65535)
                srcif = None
                for sock in self.sockets:
                    if self.sockets[sock] == conn:
                        srcif = sock
                        break
                msg = json.loads(k.decode('utf-8')) # This was changed so we can decode json into a string

                print("Received message '%s' from %s" % (msg, srcif))

                # Added onto starter code
                # Essentially figure out what kind of message this is and handle it
                # Pretty obvious how this works
                t = msg.get('type')
                # Update = Route Announcements
                if t == 'update':
                    self.manage_update(srcif, msg)

                # Withdraw = Neighbor says they want to withdraw a announcement
                elif t == 'withdraw':
                    self.manage_withdraw(srcif, msg)

                # Data = Try to deliver data to its final destination
                elif t == 'data':
                    self.manage_data(srcif, msg)

                # Dump = Retunrs a “table” message that contains a copy of the current routing announcement cache
                elif t == 'dump':
                    self.manage_dump(srcif, msg)

# From starter code
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='route packets')
    parser.add_argument('asn', type=int, help="AS number of this router")
    parser.add_argument('connections', metavar='connections', type=str, nargs='+', help="connections")
    args = parser.parse_args()
    router = Router(args.asn, args.connections)
    router.run()
package edu.wisc.cs.sdn.sr;

import net.floodlightcontroller.packet.*;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements RIP.
 *
 * @author Anubhavnidhi Abhashkumar and Aaron Gember-Jacobson
 */
public class RIP implements Runnable {
    private static final int RIP_MULTICAST_IP = 0xE0000009;
    private static final byte[] BROADCAST_MAC = {(byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    /**
     * Send RIP updates every 10 seconds
     */
    private static final int UPDATE_INTERVAL = 10;

    /**
     * Timeout routes that neighbors last advertised more than 30 seconds ago
     */
    private static final int TIMEOUT = 30;

    /**
     * Router whose route table is being managed
     */
    private Router router;

    /**
     * Thread for periodic tasks
     */
    private Thread tasksThread;

    public RIP(Router router) {
        this.router = router;
        this.tasksThread = new Thread(this);
    }

    public void init() {
        // If we are using static routing, then don't do anything
        if (this.router.getRouteTable().getEntries().size() > 0) {
            return;
        }

        System.out.println("RIP: Build initial routing table");
        assert this.router.getInterfaces().values().size() > 0;
        for (Iface iface : this.router.getInterfaces().values()) {
            this.router.getRouteTable().addEntry(
                    (iface.getIpAddress() & iface.getSubnetMask()),
                    0, // No gateway for subnets this router is connected to
                    iface.getSubnetMask(), iface.getName(), 0);
        }
        System.out.println("Route Table:\n" + this.router.getRouteTable());

        this.tasksThread.start();

        /*********************************************************************/
        /* TODO: Add other initialization code as necessary                  */
        for (Iface iface : this.router.getInterfaces().values()) {
            Ethernet ethernet = wrapRipv2(RIPv2.COMMAND_REQUEST, null, iface);
            this.router.sendPacket(ethernet, iface);
        }


        /*********************************************************************/
    }

    /**
     * Handle a RIP packet received by the router.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
        // Make sure it is in fact a RIP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
            return;
        }
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        if (ipPacket.getProtocol() != IPv4.PROTOCOL_UDP) {
            return;
        }
        UDP udpPacket = (UDP) ipPacket.getPayload();
        if (udpPacket.getDestinationPort() != UDP.RIP_PORT) {
            return;
        }
        RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();

        /*********************************************************************/
        /* TODO: Handle RIP packet                                           */
        if (ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE) {
            List<RIPv2Entry> riPv2Entries = ripPacket.getEntries();
            for (RIPv2Entry riPv2Entry : riPv2Entries) {
                RouteTableEntry r = this.router.getRouteTable().findEntry(riPv2Entry.getAddress(), riPv2Entry.getSubnetMask());
                if (r != null) {
                    if (riPv2Entry.getMetric() <= 15 && r.metric >= riPv2Entry.getMetric() + 1)
                        this.router.getRouteTable().updateEntry(riPv2Entry.getAddress(), riPv2Entry.getSubnetMask(), ipPacket.getSourceAddress(), inIface.getName(), riPv2Entry.getMetric() + 1);
                } else {
                    this.router.getRouteTable().addEntry(riPv2Entry.getAddress(), ipPacket.getSourceAddress(), riPv2Entry.getSubnetMask(), inIface.getName(), riPv2Entry.getMetric() + 1);
                }
            }
        } else {
            assert ripPacket.getCommand() == RIPv2.COMMAND_REQUEST;
            RIPresponse(inIface);
        }

        /*********************************************************************/
    }

    /**
     * Perform periodic RIP tasks.
     */
    @Override
    public void run() {
        /*********************************************************************/
        /* TODO: Send period updates and time out route table entries        */

        while (true) {
            // wait for 10 seconds
            try {
                Thread.sleep(RIP.UPDATE_INTERVAL * 1000);
            } catch (InterruptedException e) {
                break;
            }

            // broadcast route table every 10 seconds
            for (Iface iface : this.router.getInterfaces().values()) {
                RIPresponse(iface);
            }

            // Time out old entries
            for (RouteTableEntry entry : this.router.getRouteTable().getEntries()) {
                if (entry.metric > 0 && System.currentTimeMillis() - entry.timeAdded > TIMEOUT * 1000) {
                    this.router.getRouteTable().removeEntry(entry.getDestinationAddress(), entry.getMaskAddress());
                }
            }
        }

        /*********************************************************************/
    }

    private RIPv2 generateRipv2Packet(byte command, List<RIPv2Entry> entries) {
        RIPv2 riPv2 = new RIPv2();
        riPv2.setCommand(command);
        if (entries != null)
            riPv2.setEntries(entries);
        return riPv2;
    }

    private UDP generateUdpPacket(short sourcePort, short destinationPort) {
        UDP udp = new UDP();
        udp.setSourcePort(sourcePort);
        udp.setDestinationPort(destinationPort);
        return udp;
    }

    private IPv4 generateIpPacket(byte protocol, byte ttl, int sourceAddress, int destinationAddress) {
        IPv4 iPv4 = new IPv4();
        iPv4.setTtl(ttl);
        iPv4.setProtocol(protocol);
        iPv4.setSourceAddress(sourceAddress);
        iPv4.setDestinationAddress(destinationAddress);
        return iPv4;
    }

    private Ethernet generateEthernet(short etherType, byte[] sourceMAC, byte[] destinationMAC) {
        Ethernet ethernet = new Ethernet();
        ethernet.setEtherType(etherType);
        ethernet.setSourceMACAddress(sourceMAC);
        ethernet.setDestinationMACAddress(destinationMAC);
        return ethernet;
    }

    private void parentChildMeet(IPacket parent, IPacket child) {
        parent.setPayload(child);
        child.setParent(parent);
    }

    private Ethernet wrapRipv2(byte type, List<RIPv2Entry> entries, Iface outIface) {
        RIPv2 riPv2 = generateRipv2Packet(type, entries);
        UDP udp = generateUdpPacket(UDP.RIP_PORT, UDP.RIP_PORT);
        IPv4 iPv4 = generateIpPacket(IPv4.PROTOCOL_UDP, (byte) 16, outIface.getIpAddress(), RIP_MULTICAST_IP);
        Ethernet ethernet = generateEthernet(Ethernet.TYPE_IPv4, outIface.getMacAddress().toBytes(), RIP.BROADCAST_MAC);

        parentChildMeet(ethernet, iPv4);
        parentChildMeet(iPv4, udp);
        parentChildMeet(udp, riPv2);
        riPv2.resetChecksum();
        return ethernet;
    }

    private void RIPresponse(Iface outIface) {
        List<RouteTableEntry> entries = this.router.getRouteTable().getEntries();
        List<RIPv2Entry> entriesToSend = new LinkedList<>();
        for (RouteTableEntry entry : entries) {
            // Split horizon, if a entry is towards this interface, then it will not broadcast through this interface.
            if (!entry.getInterface().equals(outIface.getName())) {
                entriesToSend.add(new RIPv2Entry(entry.getDestinationAddress(), entry.getMaskAddress(), entry.metric));
            }
        }
        Ethernet ethernet = wrapRipv2(RIPv2.COMMAND_RESPONSE, entriesToSend, outIface);
        this.router.sendPacket(ethernet, outIface);
    }
}

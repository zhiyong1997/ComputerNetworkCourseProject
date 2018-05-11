package edu.wisc.cs.sdn.sr;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import edu.wisc.cs.sdn.sr.vns.VNSComm;

import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.util.MACAddress;

import static java.lang.System.exit;

/**
 * @author Aaron Gember-Jacobson
 */
public class Router 
{
	/** User under which the router is running */
	private String user;
	
	/** Hostname for the router */
	private String host;
	
	/** Template name for the router; null if no template */
	private String template;
	
	/** Topology ID for the router */
	private short topo;
	
	/** List of the router's interfaces; maps interface name's to interfaces */
	private Map<String,Iface> interfaces;
	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/** PCAP dump file for logging all packets sent/received by the router;
	 *  null if packets should not be logged */
	private DumpFile logfile;
	
	/** Virtual Network Simulator communication manager for the router */
	private VNSComm vnsComm;

    /** RIP subsystem */
    private RIP rip;
	
	/**
	 * Creates a router for a specific topology, host, and user.
	 * @param topo topology ID for the router
	 * @param host hostname for the router
	 * @param user user under which the router is running
	 * @param template template name for the router; null if no template
	 */
	public Router(short topo, String host, String user, String template)
	{
		this.topo = topo;
		this.host = host;
		this.setUser(user);
		this.template = template;
		this.logfile = null;
		this.interfaces = new HashMap<String,Iface>();
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache(this);
		this.vnsComm = null;
        this.rip = new RIP(this);
	}
	
	public void init()
	{ this.rip.init(); }
	
	/**
	 * @param logfile PCAP dump file for logging all packets sent/received by 
	 * 		  the router; null if packets should not be logged
	 */
	public void setLogFile(DumpFile logfile)
	{ this.logfile = logfile; }
	
	/**
	 * @return PCAP dump file for logging all packets sent/received by the
	 *         router; null if packets should not be logged
	 */
	public DumpFile getLogFile()
	{ return this.logfile; }
	
	/**
	 * @param template template name for the router; null if no template
	 */
	public void setTemplate(String template)
	{ this.template = template; }
	
	/**
	 * @return template template name for the router; null if no template
	 */
	public String getTemplate()
	{ return this.template; }
		
	/**
	 * @param user user under which the router is running; if null, use current 
	 *        system user
	 */
	public void setUser(String user)
	{
		if (null == user)
		{ this.user = System.getProperty("user.name"); }
		else
		{ this.user = user; }
	}
	
	/**
	 * @return user under which the router is running
	 */
	public String getUser()
	{ return this.user; }
	
	/**
	 * @return hostname for the router
	 */
	public String getHost()
	{ return this.host; }
	
	/**
	 * @return topology ID for the router
	 */
	public short getTopo()
	{ return this.topo; }
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * @return list of the router's interfaces; maps interface name's to
	 * 	       interfaces
	 */
	public Map<String,Iface> getInterfaces()
	{ return this.interfaces; }
	
	/**
	 * @param vnsComm Virtual Network System communication manager for the router
	 */
	public void setVNSComm(VNSComm vnsComm)
	{ this.vnsComm = vnsComm; }
	
	/**
	 * Close the PCAP dump file for the router, if logging is enabled.
	 */
	public void destroy()
	{
		if (logfile != null)
		{ this.logfile.close(); }
	}
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			exit(1);
		}
		
		System.out.println("Loading routing table");
		System.out.println("---------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("---------------------------------------------");
	}
	
	/**
	 * Add an interface to the router.
	 * @param ifaceName the name of the interface
	 */
	public Iface addInterface(String ifaceName)
	{
		Iface iface = new Iface(ifaceName);
		this.interfaces.put(ifaceName, iface);
		return iface;
	}
	
	/**
	 * Gets an interface on the router by the interface's name.
	 * @param ifaceName name of the desired interface
	 * @return requested interface; null if no interface with the given name 
	 * 		   exists
	 */
	public Iface getInterface(String ifaceName)
	{ return this.interfaces.get(ifaceName); }
	
	/**
	 * Send an Ethernet packet out a specific interface.
	 * @param etherPacket an Ethernet packet with all fields, encapsulated
	 * 		  headers, and payloads completed
	 * @param iface interface on which to send the packet
	 * @return true if the packet was sent successfully, otherwise false
	 */
	public boolean sendPacket(Ethernet etherPacket, Iface iface) {
		debug("Sending packet.");
		debug(etherPacket);
		debug(iface);
		boolean tmp = this.vnsComm.sendPacket(etherPacket, iface.getName());
		return tmp;
	}
	
	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		 System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets                                             */
		short payloadType = etherPacket.getEtherType();
		if (payloadType == etherPacket.TYPE_IPv4) {
			IPv4 ipPacket = (IPv4) etherPacket.getPayload();
			if (packetCorrect(ipPacket)) {
				if (destinedSelf(ipPacket)) {
					respond(ipPacket, inIface);
				} else {
					forward(ipPacket, inIface);
				}
			} else {
				System.out.println("Received an IPv4 packet with wrong checksum.");
			}
		} else if (payloadType == etherPacket.TYPE_ARP) {
			handleArpPacket(etherPacket, inIface);
		} else {
			System.out.println("The network layer packet type not support now:");
			System.out.println(payloadType);
		}


		/********************************************************************/
	}
	
	/**
	 * Handle an ARP packet received on a specific interface.
	 * @param etherPacket the complete ARP packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	private void handleArpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an ARP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_ARP)
		{ return; }
		
		// Get ARP header
		ARP arpPacket = (ARP)etherPacket.getPayload();
		int targetIp = ByteBuffer.wrap(
				arpPacket.getTargetProtocolAddress()).getInt();
		
		switch(arpPacket.getOpCode())
		{
		case ARP.OP_REQUEST:
			// Check if request is for one of my interfaces
			if (targetIp == inIface.getIpAddress())
			{ this.arpCache.sendArpReply(etherPacket, inIface); }
			break;
		case ARP.OP_REPLY:
			// Check if reply is for one of my interfaces
			if (targetIp != inIface.getIpAddress())
			{ break; }
			
			// Update ARP cache with contents of ARP reply
		    int senderIp = ByteBuffer.wrap(
				    arpPacket.getSenderProtocolAddress()).getInt();
			ArpRequest request = this.arpCache.insert(
					new MACAddress(arpPacket.getSenderHardwareAddress()),
					senderIp);
			// Process pending ARP request entry, if there is one
			if (request != null)
			{
				int gatewayIP = request.getIpAddress();
				Iface outIface = request.getIface();
				String sourceMAC = outIface.getMacAddress().toString();
				String destMAC = arpCache.lookup(gatewayIP).getMac().toString();
				for (Ethernet packet : request.getWaitingPackets())
				{
					/*********************************************************/
					/* TODO: send packet waiting on this request             */
					packet.setSourceMACAddress(sourceMAC);
					packet.setDestinationMACAddress(destMAC);
					sendPacket(packet, outIface);
					/*********************************************************/
				}
			}
			break;
		}
	}

	private boolean packetCorrect(IPv4 packet) {
		short checksum = packet.getChecksum();
		packet.setChecksum((short)0);
		byte[] bytes = packet.serialize();
		packet.deserialize(bytes, 0, bytes.length);
		short checksumNew = packet.getChecksum();
		return checksum == checksumNew;
	}

	private boolean destinedSelf(IPv4 packet) {
		int address = packet.getDestinationAddress();
		if (address == Util.dottedDecimalToInt("224.0.0.9")) return true;
		for (Iface iface :interfaces.values()) {
			if (address == iface.getIpAddress()) return true;
		}
		return false;
	}

	private void forward(IPv4 packet, Iface inIface) {
		if (packet.getTtl() == 0) {
			ICMPReply(generateICMP((byte) 11, (byte) 0, packet), inIface);
			return;
		}
		packet.setTtl((byte)(packet.getTtl() - 1));
		packet.resetChecksum();

		// Local: get gateway IP addr & get local interface by looking up
		int destIPAddress = packet.getDestinationAddress();
		RouteTableEntry entry = routeTable.lookup(destIPAddress);
		if (entry == null) {
			ICMPReply(generateICMP((byte) 3, (byte) 0, packet), inIface);
			return;
		}
		Iface outIface = this.interfaces.get(entry.getInterface());
		int gateWayAddress = entry.getGatewayAddress();
		gateWayAddress = gateWayAddress == 0 ? destIPAddress : gateWayAddress;

		Ethernet etherPacket = (Ethernet) packet.getParent();
		ArpEntry destMACAddress = arpCache.lookup(gateWayAddress);
		if (destMACAddress != null) {
			// Packet : set packet Source and Dest MAC addr
			etherPacket.setSourceMACAddress(outIface.getMacAddress().toString());
			etherPacket.setDestinationMACAddress(destMACAddress.getMac().toString());
			sendPacket(etherPacket, outIface);
		} else {
			arpCache.waitForArp(etherPacket, outIface, gateWayAddress);
		}
	}
	
	private void respond(IPv4 packet, Iface inIface) {
		byte protocol = packet.getProtocol();
		if (protocol == packet.PROTOCOL_ICMP) {
			ICMP icmpPacket = (ICMP) packet.getPayload();
			if (icmpPacket.getIcmpType() == 8 && icmpPacket.getIcmpCode() == 0) {
				ICMP icmp = setICMP(icmpPacket, (byte) 0, (byte) 0, null);
				ICMPReply(icmp, inIface);
			}
		} else if (protocol == packet.PROTOCOL_TCP) {
			ICMPReply(generateICMP((byte) 3, (byte) 3, packet), inIface);

		} else {
			assert protocol == packet.PROTOCOL_UDP;
			UDP udpPacket = (UDP) packet.getPayload();
			if (udpPacket.getDestinationPort() == 520) {
				this.rip.handlePacket((Ethernet) packet.getParent(), inIface);
			} else {
				ICMPReply(generateICMP((byte) 3, (byte) 3, packet), inIface);
			}
		}
	}

	public void ICMPReply(ICMP icmp, Iface outIface) {
		IPv4 iPv4 = (IPv4) icmp.getParent();
		Ethernet ethernet = (Ethernet) iPv4.getParent();
		iPv4.setPayload(icmp);
		iPv4.setDestinationAddress(iPv4.getSourceAddress());
		iPv4.setSourceAddress(outIface.getIpAddress());
		ethernet.setDestinationMACAddress(ethernet.getSourceMACAddress());
		ethernet.setSourceMACAddress(outIface.getMacAddress().toString());
		sendPacket(ethernet, outIface);
	}

	public ICMP setICMP(ICMP icmp, byte type, byte code, IPv4 parent) {
		icmp.setIcmpCode(code);
		icmp.setIcmpType(type);
		if (parent != null) icmp.setParent(parent);
		icmp.resetChecksum();
		return icmp;
	}

	public ICMP generateICMP(byte type, byte code, IPv4 parent) {
		ICMP icmp = new ICMP();
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);
		icmp.setParent(parent);
		icmp.resetChecksum();
		appendTruncatedParent(icmp, parent);
		return icmp;
	}

	private void appendTruncatedParent(ICMP icmp, IPv4 iPv4) {
		int headLength = iPv4.getHeaderLength();
		byte[] payload = iPv4.serialize();
		payload = Arrays.copyOfRange(payload, 0, headLength * 4 + 8);
		IPacket payloadPacket = new Data().setData(payload);
		icmp.setPayload(payloadPacket);
		payloadPacket.setParent(icmp);
	}

	private void debug(Object x) {
		System.out.println(x);
	}
}

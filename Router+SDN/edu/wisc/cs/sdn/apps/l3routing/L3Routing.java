package edu.wisc.cs.sdn.apps.l3routing;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.util.Host;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;

public class L3Routing implements IFloodlightModule, IOFSwitchListener,
		ILinkDiscoveryListener, IDeviceListener, IL3Routing
{
	public static final String MODULE_NAME = L3Routing.class.getSimpleName();

	// Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;

    // Interface to link discovery service
    private ILinkDiscoveryService linkDiscProv;

    // Interface to device manager service
    private IDeviceService deviceProv;

    // Switch table in which rules should be installed
    public static byte table;

    // Map of hosts to devices
    private Map<IDevice,Host> knownHosts;

    private void update() {
        Collection<Host> hosts = getHosts();
        Collection<Link> links = getLinks();
        Map<Long, IOFSwitch> switches = getSwitches();
        Graph graph = new Graph(hosts, links, switches);
        ArrayList<Entry> entries = graph.floyd();
        addEntries(entries);
    }

    private void addEntries(ArrayList<Entry> entries) {
        for (Entry entry : entries) {
            OFMatch match = new OFMatch();
            match.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
            match.setNetworkDestination(entry.dstIpv4);
            OFAction action = new OFActionOutput(entry.nxtPort);
            OFInstruction instruction = new OFInstructionApplyActions(Arrays.asList(action));
            SwitchCommands.installRule(entry.srcSwitch, table, SwitchCommands.DEFAULT_PRIORITY, match, Arrays.asList(instruction));
        }
    }

	/**
     * Loads dependencies and initializes data structures.
     */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		Map<String,String> config = context.getConfigParams(this);
        this.table = Byte.parseByte(config.get("table"));

		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
        this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);

        this.knownHosts = new ConcurrentHashMap<IDevice,Host>();

        /*********************************************************************/
        /* TODO: Initialize other class variables, if necessary              */
        /*********************************************************************/
	}

	/**
     * Subscribes to events and performs other startup tasks.
     */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.linkDiscProv.addListener(this);
		this.deviceProv.addListener(this);

		/*********************************************************************/
		/* TODO: Perform other tasks, if necessary                           */
		/*********************************************************************/
	}

	/**
	 * Get the table in which this application installs rules.
	 */
	public byte getTable()
	{ return this.table; }

    /**
     * Get a list of all known hosts in the network.
     */
    private Collection<Host> getHosts()
    { return this.knownHosts.values(); }

    /**
     * Get a map of all active switches in the network. Switch DPID is used as
     * the key.
     */
	private Map<Long, IOFSwitch> getSwitches()
    { return floodlightProv.getAllSwitchMap(); }

    /**
     * Get a list of all active links in the network.
     */
    private Collection<Link> getLinks()
    { return linkDiscProv.getLinks().keySet(); }

    /**
     * Event handler called when a host joins the network.
     * @param device information about the host
     */
	@Override
	public void deviceAdded(IDevice device)
	{
		Host host = new Host(device, this.floodlightProv);
		// We only care about a new host if we know its IP
		if (host.getIPv4Address() != null)
		{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);

			/*****************************************************************/
			/* TODO: Update routing: add rules to route to new host          */
            update();
			/*****************************************************************/
		}
	}

	/**
     * Event handler called when a host is no longer attached to a switch.
     * @param device information about the host
     */
	@Override
	public void deviceRemoved(IDevice device)
	{
	    debug("device Removed");
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}

		log.info(String.format("Host %s is no longer attached to a switch",
				host.getName()));

		/*********************************************************************/
		/* TODO: Update routing: remove rules to route to host               */
        update();
		/*********************************************************************/
	}

	/**
     * Event handler called when a host moves within the network.
     * @param device information about the host
     */
	@Override
	public void deviceMoved(IDevice device)
	{
        debug("device Moved");
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}

		if (!host.isAttachedToSwitch())
		{
			this.deviceRemoved(device);
			return;
		}
		log.info(String.format("Host %s moved to s%d:%d", host.getName(),
				host.getSwitch().getId(), host.getPort()));

		/*********************************************************************/
		/* TODO: Update routing: change rules to route to host               */
		update();
		/*********************************************************************/
	}

    /**
     * Event handler called when a switch joins the network.
     * #param switchID DPID for the switch
     */
	@Override
	public void switchAdded(long switchId)
	{
        debug("Switch Added");
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));

		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
        update();
		/*********************************************************************/
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * #param switchID DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId)
	{
        debug("Switch Removed");
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d removed", switchId));

		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
        update();
		/*********************************************************************/
	}

	/**
	 * Event handler called when multiple links go up or down.
	 * @param updateList information about the change in each link's state
	 */
	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList)
	{
        debug("Link Updated");
		for (LDUpdate update : updateList)
		{
			// If we only know the switch & port for one end of the link, then
			// the link must be from a switch to a host
			if (0 == update.getDst())
			{
				log.info(String.format("Link s%s:%d -> host updated",
					update.getSrc(), update.getSrcPort()));
			}
			// Otherwise, the link is between two switches
			else
			{
				log.info(String.format("Link s%s:%d -> %s:%d updated",
					update.getSrc(), update.getSrcPort(),
					update.getDst(), update.getDstPort()));
			}
		}

		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
        update();
		/*********************************************************************/
	}

	/**
	 * Event handler called when link goes up or down.
	 * @param update information about the change in link state
	 */
	@Override
	public void linkDiscoveryUpdate(LDUpdate update)
	{ this.linkDiscoveryUpdate(Arrays.asList(update)); }

	/**
     * Event handler called when the IP address of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceIPV4AddrChanged(IDevice device)
	{ this.deviceAdded(device); }

	/**
     * Event handler called when the VLAN of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceVlanChanged(IDevice device)
	{ /* Nothing we need to do, since we're not using VLANs */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * #param switchID DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId)
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when some attribute of a switch changes.
	 * #param switchID DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId)
	{ /* Nothing we need to do */ }

	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * #param switchID DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type)
	{ /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName()
	{ return this.MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(String type, String name)
	{ return false; }

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(String type, String name)
	{ return false; }

    /**
     * Tell the module system which services we provide.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices()
	{
		Collection<Class<? extends IFloodlightService>> services =
					new ArrayList<Class<? extends IFloodlightService>>();
		services.add(IL3Routing.class);
		return services;
	}

	/**
     * Tell the module system which services we implement.
     */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
			getServiceImpls()
	{
        Map<Class<? extends IFloodlightService>, IFloodlightService> services =
        			new HashMap<Class<? extends IFloodlightService>,
        					IFloodlightService>();
        // We are the class that implements the service
        services.put(IL3Routing.class, this);
        return services;
	}

	/**
     * Tell the module system which modules we depend on.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>>
			getModuleDependencies()
	{
		Collection<Class<? extends IFloodlightService >> modules =
	            new ArrayList<Class<? extends IFloodlightService>>();
		modules.add(IFloodlightProviderService.class);
		modules.add(ILinkDiscoveryService.class);
		modules.add(IDeviceService.class);
        return modules;
	}

	private void debug(Object o) {
	    System.out.println(o);
    }
}

class Graph {
    ArrayList<IOFSwitch> switches = new ArrayList<IOFSwitch>();
    ArrayList<Host> hosts = new ArrayList<Host>();
    HashMap<IOFSwitch, Integer> switchIds = new HashMap<IOFSwitch, Integer>();
    int[][] costs;
    int[][] nexts;
    Link[][] links;

    Graph(Collection<Host> hosts, Collection<Link> links, Map<Long, IOFSwitch> switches) {
        int n_switches = switches.size();

        this.switches.addAll(switches.values());
        this.hosts.addAll(hosts);
        costs = new int[n_switches][n_switches];
        nexts = new int[n_switches][n_switches];
        this.links = new Link[n_switches][n_switches];

        int infinity = (int) 1e7;
        for (int i = 0; i < n_switches; ++i){
            for (int j = i; j < n_switches; ++j) {
                nexts[i][j] = nexts[j][i] = -1;
                this.links[i][j] = this.links[j][i] = null;
                if (i == j) {
                    costs[i][j] = 0;
                } else {
                    costs[i][j] = costs[j][i] = infinity;
                }
            }
        }

        for (int i = 0; i < n_switches; ++i) {
            switchIds.put(this.switches.get(i), i);
        }

        for (Link link : links) {
            int srcSwitchId = switchIds.get(switches.get(link.getSrc()));
            int dstSwitchId = switchIds.get(switches.get(link.getDst()));
            costs[srcSwitchId][dstSwitchId] = 1;
            nexts[srcSwitchId][dstSwitchId] = dstSwitchId;
            this.links[srcSwitchId][dstSwitchId] = link;
        }

        print_cost();

    }

    public ArrayList<Entry> floyd() {
        ArrayList<Entry> entries = new ArrayList<Entry>();
        int n_switches = switches.size();
        for (int k = 0; k < n_switches; ++k) {
            for (int i = 0; i < n_switches; ++i) {
                for (int j = 0; j < n_switches; ++j) {
                    if (costs[i][j] > costs[i][k] + costs[k][j]){
                        costs[i][j] = costs[i][k] + costs[k][j];
                        nexts[i][j] = nexts[i][k];
                    }

                }
            }
        }

        print_cost();

        for (Host host: hosts) {
            assert host.isAttachedToSwitch();
            IOFSwitch iofSwitch = host.getSwitch();
            int switchId = switchIds.get(iofSwitch);
            Integer dstIpv4 = host.getIPv4Address();
            for (int i = 0; i < n_switches; ++i) {
                if (switchId == i) {
                    entries.add(new Entry(dstIpv4, switches.get(i), host.getPort()));
                } else {
                    int nxtNode = nexts[i][switchId];
                    int nxtPort = links[i][nxtNode].getSrcPort();
                    entries.add(new Entry(dstIpv4, switches.get(i), nxtPort));
                }
            }
        }
        return entries;
    }

    private void print_cost() {
        int n_switches = this.switches.size();
        debug("==========================");
        debug("cost:");
        for (int i = 0; i < n_switches; ++i){
            for (int j = 0; j < n_switches; ++j) {
                System.out.print(costs[i][j]);
                System.out.print(" ");
            }
            System.out.println();
        }

        debug("nexts:");
        for (int i = 0; i < n_switches; ++i){
            for (int j = 0; j < n_switches; ++j) {
                System.out.print(this.nexts[i][j]);
                System.out.print(" ");
            }
            System.out.println();
        }
        debug("==========================");
    }

    static void debug(Object o) {
        System.out.println(o);
    }
}

class Entry {
    Integer dstIpv4;
    IOFSwitch srcSwitch;
    Integer nxtPort;

    Entry(Integer dstIpv4, IOFSwitch srcSwitch, Integer nxtPort) {
        this.dstIpv4 = dstIpv4;
        this.srcSwitch = srcSwitch;
        this.nxtPort = nxtPort;
    }
}


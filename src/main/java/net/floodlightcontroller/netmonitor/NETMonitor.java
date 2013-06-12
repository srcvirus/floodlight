/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Shihabur Rahman Chowdhury
 * 
 */
public class NETMonitor implements IOFMessageListener, IFloodlightModule, INetMonitorService {

    /*
     * Required Floodlight service modules
     */
    protected IFloodlightProviderService floodLightProvider;
    protected IRestApiService restApi;
    protected IThreadPoolService threadPool;
    
    /*
     * For logging purpose
     */
    protected static Logger logger;
    
    /**
     * Algorithm for flow statistics collection.
     * flowsense = basic flowsense implementation
     * payless = flowsense augmented with variable frequency stat collection
     */
    public static String ALGORITHM = "flowsense";
    
    /*
     * Minimum and Maximum Scheduling timeout (ms)
     */
    public static int MIN_SCHEDULE_TIMEOUT = 200;
    public static int MAX_SCHEDULE_TIMEOUT = 1000;
    
    protected SortedSet<FlowEntry> activeFlowTable;
    protected SortedMap<Long, SwitchStatistics> switchStatTable;
    protected SchedulerTable schedule;
    
    /*
     * Worker class for polling switches for statistics
     */
    protected class PollSwitchWorker implements Runnable
    {
        Integer timeout;
        Object container;
        
        public PollSwitchWorker(Object container)
        {
            timeout = new Integer(NETMonitor.MIN_SCHEDULE_TIMEOUT);
            this.container = container;
        }
        
        public PollSwitchWorker(Integer timeout, Object container)
        {
            this.timeout = timeout;
            this.container = container;
        }
        
        public PollSwitchWorker(int timeout, Object container)
        {
            this.timeout = new Integer(timeout);
            this.container = container;
        }
        
        public void run() 
        {
            ArrayList <FlowEntry> flows = schedule.getAllEntries(timeout);
            for(int i = 0; i < flows.size(); i++)
            {
                FlowEntry entry = flows.get(i);
                OFMatch match = new OFMatch();
                match.setDataLayerType((short)entry.getDlType());
                match.setNetworkProtocol((byte)entry.getNwProto());
                match.setNetworkSource(entry.getSrcIp());
                match.setNetworkDestination(entry.getDestIp());
                match.setInputPort((short)entry.getInputPort());
                IOFSwitch sw = floodLightProvider.getSwitches().get(entry.getSwId());
                Integer wildcard_hints = ((Integer) sw
                                        .getAttribute(IOFSwitch.PROP_FASTWILDCARDS))    //PROP_FASTWILDCARDS
                                        .intValue()
                                        & ~OFMatch.OFPFW_DL_TYPE
                                        & ~OFMatch.OFPFW_IN_PORT
                                        //& ~OFMatch.OFPFW_DL_VLAN
                                        & ~OFMatch.OFPFW_DL_SRC
                                        & ~OFMatch.OFPFW_DL_DST
                                        & ~OFMatch.OFPFW_NW_SRC_MASK
                                        & ~OFMatch.OFPFW_NW_DST_MASK
                                        & ~OFMatch.OFPFW_NW_PROTO
                                        & ~OFMatch.OFPFW_NW_SRC_ALL
                                        & ~OFMatch.OFPFW_NW_DST_ALL;
                match.setWildcards(wildcard_hints.intValue());
                
                OFStatisticsRequest statRequest = (OFStatisticsRequest) floodLightProvider.getOFMessageFactory()
                                                                                          .getMessage(OFType.STATS_REQUEST);
                statRequest.setStatisticType(OFStatisticsType.FLOW);
                OFFlowStatisticsRequest fs = (OFFlowStatisticsRequest)floodLightProvider.getOFMessageFactory()
                                                                                        .getStatistics(OFType.STATS_REQUEST, 
                                                                                        OFStatisticsType.FLOW);
                fs.setMatch(match);
                ArrayList <OFStatistics> statList = new ArrayList<OFStatistics>();
                statList.add(fs);
                statRequest.setStatistics(statList);
                
                try {
                    sw.sendStatsQuery(statRequest, sw.getNextTransactionId(), (NETMonitor)container);
                    sw.flush();
                } catch (IOException ex) {
                    logger.error(ex.getMessage());
                }
            }
        }
    }
    
    
    /*public void processPacketInMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        OFPacketIn pktInMsg = (OFPacketIn) msg;
        
        int buffer_id = pktInMsg.getBufferId();
        int in_port = pktInMsg.getInPort();
        
        OFPacketIn.OFPacketInReason reason = pktInMsg.getReason();
        String str_reason = reason.toString();
        
        OFMatch match = new OFMatch();
        match.loadFromPacket(pktInMsg.getPacketData(), pktInMsg.getInPort());

        int source_ip = match.getNetworkSource();
        int dest_ip = match.getNetworkDestination();

        byte net_proto = match.getNetworkProtocol();
        short dl_type = match.getDataLayerType();
     
        logger.info("Received a PACKET_IN from sw = " + sw.getId() + ", " + (sw.getInetAddress()).toString() 
                + ", in port = " + in_port + ", buffer = " + buffer_id
                + ", " + str_reason + ", source = " + IPv4.fromIPv4Address(source_ip)
                + ", destination = " + IPv4.fromIPv4Address(dest_ip) + ", protocol = " + Integer.toHexString(net_proto)
                + ", dl_type = " + Integer.toHexString(dl_type));


        FlowEntry entry = new FlowEntry(sw.getId(), in_port, source_ip, dest_ip, net_proto, dl_type);
        
        synchronized(activeFlowTable)
        {
            if (activeFlowTable.contains(entry) == false && entry.getDlType() == Ethernet.TYPE_IPv4
                    && entry.getNwProto() != IPv4.PROTOCOL_UDP) {
                entry.setTimestamp(System.currentTimeMillis());
                activeFlowTable.add(entry);
                logger.debug("Flow " + entry.toString() + " added to the Active Flow Table");
            }
        }
        printActiveFlows();
    }*/

    public void processFlowRemovedMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        OFMatch match;
        OFFlowRemoved flRmMsg = (OFFlowRemoved) msg;
        match = flRmMsg.getMatch();

        int src_port = match.getTransportSource();
        int dst_port = match.getTransportDestination();

        int src_ip = match.getNetworkSource();
        int dst_ip = match.getNetworkDestination();

        byte proto = match.getNetworkProtocol();
        short dl_type = match.getDataLayerType();
        
        OFFlowRemoved.OFFlowRemovedReason rsn = flRmMsg.getReason();

        logger.info("Flow Removed from sw = " + sw.getId() + ", "
                + " byte counts = " + flRmMsg.getByteCount()
                + " source = " + IPv4.fromIPv4Address(src_ip) + ":" + src_port
                + " dest = " + IPv4.fromIPv4Address(dst_ip) + ":" + dst_port
                + " protocol = " + proto
                + " dl_type = " + dl_type
                + " reason = " + rsn.toString()
                + " duration = " + flRmMsg.getDurationSeconds()
                + " in port = " + match.getInputPort());

        FlowEntry removedEntry = new FlowEntry(sw.getId(), match.getInputPort(), src_ip, dst_ip, proto, dl_type);
        logger.debug("Removed flow: " + removedEntry.toString());
        
        long checkPointTimeStamp = System.currentTimeMillis();
        if (rsn.equals(OFFlowRemoved.OFFlowRemovedReason.OFPRR_IDLE_TIMEOUT)) {
            checkPointTimeStamp -= (long) (flRmMsg.getIdleTimeout() * 1000);
        }
        FlowEntry matchedFlow = null, tmpMatchFlow = null;

        synchronized (activeFlowTable) {
            for (Iterator<FlowEntry> it = activeFlowTable.iterator(); it.hasNext();) {
                FlowEntry flow = it.next();
                if (flow.equals(removedEntry)) {
                    tmpMatchFlow = flow;
                    break;
                }
            }
        }

        if (tmpMatchFlow != null) {
            logger.debug("Found a Matching Flow: " + tmpMatchFlow.toString());
            matchedFlow = tmpMatchFlow.clone();

            activeFlowTable.remove(matchedFlow);
            printActiveFlows();
            double utilization = (double) flRmMsg.getByteCount() / (double) flRmMsg.getDurationSeconds();

            if (switchStatTable.get(new Long(sw.getId())) == null) {
                logger.debug("Adding a switch entry for the first time, swId = " + sw.getId());
                LinkStatistics linkStat = new LinkStatistics();
                linkStat.setInputPort(matchedFlow.getInputPort());
                linkStat.addStatData(checkPointTimeStamp, utilization);

                SwitchStatistics swStat = new SwitchStatistics();
                swStat.setSwId(sw.getId());
                swStat.addLinkStat(linkStat);
                switchStatTable.put(sw.getId(), swStat);
            } else if (switchStatTable.get(new Long(sw.getId())).linkExists(match.getInputPort()) == false) {
                logger.debug("Adding entry for port = " + matchedFlow.getInputPort() + " on switch " + sw.getId());
                LinkStatistics linkStat = new LinkStatistics();
                linkStat.setInputPort(matchedFlow.getInputPort());
                linkStat.addStatData(checkPointTimeStamp, utilization);
                switchStatTable.get(new Long(sw.getId())).addLinkStat(linkStat);
            } else {
                SwitchStatistics swStat = switchStatTable.get(new Long(sw.getId()));
                synchronized(swStat.getLinkStatTable())
                {
                    for(Iterator <LinkStatistics> lsIt = swStat.getLinkStatTable().iterator(); lsIt.hasNext(); )
                    {
                        LinkStatistics ls = lsIt.next();
                        if (ls.getInputPort() == match.getInputPort()) {
                            Set<Long> timestampSet = ls.getStatData().keySet();
                            for (Iterator<Long> it = timestampSet.iterator(); it.hasNext();) {
                                Long ts = it.next();
                                if (ts.longValue() > matchedFlow.getTimestamp()
                                        && ts.longValue() < checkPointTimeStamp) {
                                    logger.debug("[" + matchedFlow.getTimestamp() + "," + ts.toString() + "," + checkPointTimeStamp + "]");
                                    ls.getStatData().put(ts, utilization + ls.getStatData().get(ts).doubleValue());
                                }
                            }
                            ls.addStatData(checkPointTimeStamp, utilization);
                        }
                    }
                }
            }
        }
    }

    public void processFlowModMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
    {
        OFFlowMod flowModMsg = (OFFlowMod)msg;
        logger.debug("Intercepted FlowMod Message:\t" + flowModMsg.toString());
        OFMatch match = flowModMsg.getMatch();
        FlowEntry entry = new FlowEntry(sw.getId(), match.getInputPort(), match.getNetworkSource(),
                match.getNetworkDestination(), match.getNetworkProtocol(), match.getDataLayerType());
        synchronized(activeFlowTable)
        {
            if (activeFlowTable.contains(entry) == false && entry.getDlType() == Ethernet.TYPE_IPv4
                    && entry.getNwProto() != IPv4.PROTOCOL_UDP) {
                entry.setTimestamp(System.currentTimeMillis());
                entry.setScheduleTimeout(MIN_SCHEDULE_TIMEOUT);
                schedule.addFlowEntry(new Integer(MIN_SCHEDULE_TIMEOUT), entry);
                if(ALGORITHM.equals("payless") == true)
                {
                    if(schedule.getAction(MIN_SCHEDULE_TIMEOUT) == null)
                    {
                        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
                        SingletonTask action = new SingletonTask(ses, new PollSwitchWorker(MIN_SCHEDULE_TIMEOUT, this));
                        action.reschedule(MIN_SCHEDULE_TIMEOUT, TimeUnit.MILLISECONDS);
                        schedule.addAction(MIN_SCHEDULE_TIMEOUT, action);
                    }
                }
                logger.debug("Adding flow " + entry.toString() + " to the Active Flow Table");
                activeFlowTable.add(entry);
            }
        }
        printActiveFlows();
    }
    
    public void processStatReplyMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        OFStatisticsReply statReply = (OFStatisticsReply)msg;
        
    }
    
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                //processPacketInMessage(sw, msg, cntx);
                break;

            case FLOW_REMOVED:
                processFlowRemovedMessage(sw, msg, cntx);
                printUtilization();
                break;
                
            case FLOW_MOD:
                processFlowModMessage(sw, msg, cntx);
                break;
                
            case STATS_REPLY:
                processStatReplyMessage(sw, msg, cntx);
                break;
        }

        return Command.CONTINUE;
    }

    public String getName() {
        return NETMonitor.class.getSimpleName();
    }

    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(INetMonitorService.class);
        return l;
    }

    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(INetMonitorService.class, this);
        return m;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> dependencyList = new ArrayList<Class<? extends IFloodlightService>>();
        dependencyList.add(IFloodlightProviderService.class);
        dependencyList.add(IRestApiService.class);
        dependencyList.add(IThreadPoolService.class);
        return dependencyList;
    }

    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        initServices(context);
        initDSandVariables(context);
    }

    public void initServices(FloodlightModuleContext context)
    {
        this.floodLightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        this.restApi = context.getServiceImpl(IRestApiService.class);
        this.logger = LoggerFactory.getLogger(NETMonitor.class);
        this.threadPool = context.getServiceImpl(IThreadPoolService.class);
    }
    
    public void initDSandVariables(FloodlightModuleContext context)
    {
        this.activeFlowTable = Collections.synchronizedSortedSet(new TreeSet<FlowEntry>());
        this.switchStatTable = Collections.synchronizedSortedMap(new TreeMap<Long, SwitchStatistics>());
        Map<String, String> configOptions = context.getConfigParams(this);
        try {
            String algorithm = configOptions.get("algorithm");
            if (algorithm != null) {
                ALGORITHM = algorithm;
            }
            else throw new NullPointerException("no value for parameter algorithm");
            
            MIN_SCHEDULE_TIMEOUT = Integer.parseInt(configOptions.get("min_schedule_timeout"));
            MAX_SCHEDULE_TIMEOUT = Integer.parseInt(configOptions.get("max_schedule_timeout"));
        } catch (Exception ex) {
            logger.warn(ex.getMessage());
        }
    }
    
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        addMessageListeners(context);
        restApi.addRestletRoutable(new NetMonitorWebRoutable());
    }

    public void addMessageListeners(FloodlightModuleContext context)
    {
        floodLightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodLightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        floodLightProvider.addOFMessageListener(OFType.FLOW_MOD, this);
        floodLightProvider.addOFMessageListener(OFType.STATS_REPLY, this);
    }
    
    public void printUtilization() {
        synchronized(switchStatTable)
        {
            logger.info("nSwitches = " + switchStatTable.size());
            Set<Long> switchIds = switchStatTable.keySet();
            for (Long swId : switchIds) {
                switchStatTable.get(swId).printSwitchStatistcs(logger);
            }
        }
    }
    
    public void printActiveFlows() {
        String text = "Active Flow Set:";
        Object[] allEntries = null;
        synchronized(activeFlowTable)
        {
            allEntries = activeFlowTable.toArray(new FlowEntry[0]);
        }
        
        if(allEntries == null || allEntries.length <= 0)
            text += " [empty]";
        else
        {
            for(int i = 0; i < allEntries.length; i++)
            {
                text += "\n\t" + allEntries[i].toString();
            }
        }
       
        logger.debug(text);
    }

    public SortedMap<Long, SwitchStatistics> getSwitchStatistics() {
        return switchStatTable;
    }
}

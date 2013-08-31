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
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.restlet.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Shihabur Rahman Chowdhury
 * 
 */
public class NETMonitor implements IOFMessageListener, IFloodlightModule, INetMonitorService
{

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
    public static int MIN_SCHEDULE_BYTE_THRESHOLD = 1024;
    public static int MAX_SCHEDULE_BYTE_THRESHOLD = 10240;
    public static int SCHEDULE_TIMEOUT_DAMPING_FACTOR = 4;
    public static int SCHEDULE_TIMEOUT_AMPLIFY_FACTOR = 2;
    protected SortedSet<FlowEntry> activeFlowTable;
    protected SortedMap<Long, SwitchStatistics> switchStatTable;
    protected SchedulerTable schedule;
    protected int statMessageCounter = 0;
    protected SortedMap<Long, Integer> overhead;
    protected SortedMap <FlowEntry, SortedMap <Long, Double>> flowStatTable;
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
            ArrayList<FlowEntry> flows = schedule.getAllEntries(timeout);
            if (flows == null || flows.isEmpty())
            {
                return;
            }
            logger.debug("My Timeout = " + timeout + "ms");
            logger.debug("Need to Schedule " + flows.size() + " flows");
            overhead.put(System.currentTimeMillis(), flows.size());
            for (int i = 0; i < flows.size(); i++)
            {
                FlowEntry entry = flows.get(i);
                logger.debug("Scheduling flow " + (i + 1) + " " + entry.toString());
                OFMatch match = entry.getMatch().clone();
                IOFSwitch sw = floodLightProvider.getSwitches().get(entry.getSwId());
                if (sw == null)
                {
                    return;
                }
                Integer wildcard_hints = ((Integer) sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)) //PROP_FASTWILDCARDS
                        .intValue()
                        & ~OFMatch.OFPFW_DL_DST
                        & ~OFMatch.OFPFW_DL_SRC
                        & ~OFMatch.OFPFW_DL_TYPE
                        & ~OFMatch.OFPFW_IN_PORT
                        & ~OFMatch.OFPFW_NW_SRC_MASK
                        & ~OFMatch.OFPFW_NW_DST_MASK
                        & ~OFMatch.OFPFW_NW_PROTO
                        & ~OFMatch.OFPFW_NW_SRC_ALL
                        & ~OFMatch.OFPFW_NW_DST_ALL;
                match.setWildcards(wildcard_hints.intValue());

                OFStatisticsRequest statRequest = (OFStatisticsRequest) floodLightProvider.getOFMessageFactory().getMessage(OFType.STATS_REQUEST);
                statRequest.setStatisticType(OFStatisticsType.FLOW);
                OFFlowStatisticsRequest fs = (OFFlowStatisticsRequest) floodLightProvider.getOFMessageFactory().getStatistics(OFType.STATS_REQUEST, OFStatisticsType.FLOW);
                fs.setMatch(match);
                fs.setTableId((byte) 0xFF);
                fs.setOutPort(OFPort.OFPP_NONE.getValue());

                ArrayList<OFStatistics> statList = new ArrayList<OFStatistics>();
                statList.add(fs);
                statRequest.setStatistics(statList);
                statRequest.setLengthU(statRequest.getLengthU() + fs.getLength());

                try
                {
                    logger.debug("Stat request sent to sw = " + sw.getId());
                    sw.sendStatsQuery(statRequest, ++statMessageCounter, (NETMonitor) container);
                    sw.flush();
                }
                catch (Exception ex)
                {
                    logger.error(ex.getMessage());
                }
            }
            if (schedule.getAction(timeout) != null)
            {
                schedule.getAction(timeout).reschedule(timeout, TimeUnit.MILLISECONDS);
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
    public void updateLinkUsage(IOFSwitch sw, FlowEntry removedEntry, OFMatch match, OFMessage msg, OFType type)
    {
        long checkPointTimeStamp = System.currentTimeMillis();
        long timeOffset = 0;
        double duration = 0.0;
        long byteCount = 0;
        FlowEntry matchedFlow = null, tmpMatchFlow = null;

        synchronized (activeFlowTable)
        {
            for (Iterator<FlowEntry> it = activeFlowTable.iterator(); it.hasNext();)
            {
                FlowEntry flow = it.next();
                if (flow.equals(removedEntry))
                {
                    tmpMatchFlow = flow;
                    break;
                }
            }
        }


        checkPointTimeStamp -= timeOffset;
        if (type.equals(OFType.FLOW_REMOVED))
        {
            logger.debug("[FLOW_MOD] Checkpoint = " + checkPointTimeStamp);
        }

        if (tmpMatchFlow != null)
        {

            logger.debug("Found a Matching Flow: " + tmpMatchFlow.toString());
            matchedFlow = tmpMatchFlow;

            switch (type)
            {
                case FLOW_REMOVED:
                    OFFlowRemoved flRmMsg = (OFFlowRemoved) msg;
                    duration = flRmMsg.getDurationSeconds() + (flRmMsg.getDurationNanoseconds() / 1e9);

                    if (flRmMsg.getReason().equals(OFFlowRemoved.OFFlowRemovedReason.OFPRR_IDLE_TIMEOUT))
                    {
                        timeOffset = (long) flRmMsg.getIdleTimeout() * 1000;
                        duration -= flRmMsg.getIdleTimeout();
                    }
                    byteCount = flRmMsg.getByteCount();
                    matchedFlow = tmpMatchFlow.clone();
                    if (ALGORITHM.equals("payless"))
                    {
                        //duration = tmpMatchFlow.getScheduleTimeout() / 1000.0;
                        duration = ((double) flRmMsg.getDurationSeconds() + (double) flRmMsg.getDurationNanoseconds() / 1e9)
                                - tmpMatchFlow.getDuration();

                        byteCount = flRmMsg.getByteCount() - matchedFlow.getMatchedByteCount();
                        logger.debug("Flow Removed, Matched flow bytes= " + matchedFlow.getMatchedByteCount());
                        schedule.removeFlowEntry(tmpMatchFlow.getScheduleTimeout(), tmpMatchFlow);
                    }
                    activeFlowTable.remove(tmpMatchFlow);
                    printActiveFlows();
                    break;

                case STATS_REPLY:
                    OFStatisticsReply statReply = (OFStatisticsReply) msg;
                    ArrayList<OFStatistics> statList = (ArrayList<OFStatistics>) statReply.getStatistics();
                    logger.debug("Received Stat Reply " + statList.size());
                    if (statList != null && statList.size() > 0)
                    {
                        OFFlowStatisticsReply fstatReply = (OFFlowStatisticsReply) statList.get(0);
                        //duration = fstatReply.getDurationSeconds() + (fstatReply.getDurationNanoseconds() / 1e9);
                        //duration = matchedFlow.getScheduleTimeout() / 1000.0;
                        duration = (double) fstatReply.getDurationSeconds() + (double) fstatReply.getDurationNanoseconds() / 1e9
                                - matchedFlow.getDuration();

                        byteCount = fstatReply.getByteCount() - matchedFlow.getMatchedByteCount();
                        logger.debug("Matched Flow Prev. Byte Count = " + matchedFlow.getMatchedByteCount());
                        logger.debug("Stat reply, Del-byte = " + byteCount);
                        if (byteCount < MIN_SCHEDULE_BYTE_THRESHOLD)
                        {
                            int oldTimeout = matchedFlow.getScheduleTimeout();
                            int newTimeout = Math.min(matchedFlow.getScheduleTimeout() * SCHEDULE_TIMEOUT_AMPLIFY_FACTOR,
                                                      MAX_SCHEDULE_TIMEOUT);
                            matchedFlow.setScheduleTimeout(newTimeout);
                            schedule.updateTimeout(oldTimeout, newTimeout, matchedFlow);
                            if (schedule.getAction(newTimeout) == null)
                            {
                                ScheduledExecutorService ses = threadPool.getScheduledExecutor();
                                SingletonTask action = new SingletonTask(ses, new PollSwitchWorker(newTimeout, this));
                                action.reschedule(newTimeout, TimeUnit.MILLISECONDS);
                                schedule.addAction(newTimeout, action);
                            }
                        }
                        else if (byteCount > MAX_SCHEDULE_BYTE_THRESHOLD)
                        {
                            int oldTimeout = matchedFlow.getScheduleTimeout();
                            int newTimeout = Math.max(matchedFlow.getScheduleTimeout() / SCHEDULE_TIMEOUT_DAMPING_FACTOR,
                                                      MIN_SCHEDULE_TIMEOUT);
                            matchedFlow.setScheduleTimeout(newTimeout);
                            schedule.updateTimeout(oldTimeout, newTimeout, matchedFlow);
                            if (schedule.getAction(newTimeout) == null)
                            {
                                ScheduledExecutorService ses = threadPool.getScheduledExecutor();
                                SingletonTask action = new SingletonTask(ses, new PollSwitchWorker(newTimeout, this));
                                action.reschedule(newTimeout, TimeUnit.MILLISECONDS);
                                schedule.addAction(newTimeout, action);
                            }
                        }
                        matchedFlow.setMatchedByteCount(fstatReply.getByteCount());
                        matchedFlow.setDuration((double) fstatReply.getDurationSeconds()
                                + (double) fstatReply.getDurationNanoseconds() / 1e9);
                    }
                    break;
            }

            double utilization = (double) byteCount / duration;
            logger.debug("Instant utilization = " + utilization);
            
            if(flowStatTable.get(matchedFlow) == null)
            {
                flowStatTable.put(matchedFlow, Collections.synchronizedSortedMap(new TreeMap<Long, Double>()));
            }
            flowStatTable.get(matchedFlow).put(checkPointTimeStamp, utilization);
            
            if (switchStatTable.get(new Long(sw.getId())) == null)
            {
                logger.debug("Adding a switch entry for the first time, swId = " + sw.getId());
                LinkStatistics linkStat = new LinkStatistics();
                linkStat.setInputPort(matchedFlow.getInputPort());
                linkStat.addStatData(checkPointTimeStamp, utilization);

                SwitchStatistics swStat = new SwitchStatistics();
                swStat.setSwId(sw.getId());
                swStat.addLinkStat(linkStat);
                switchStatTable.put(sw.getId(), swStat);
            }
            else if (switchStatTable.get(new Long(sw.getId())).linkExists(match.getInputPort()) == false)
            {
                logger.debug("Adding entry for port = " + matchedFlow.getInputPort() + " on switch " + sw.getId());
                LinkStatistics linkStat = new LinkStatistics();
                linkStat.setInputPort(matchedFlow.getInputPort());
                linkStat.addStatData(checkPointTimeStamp, utilization);
                switchStatTable.get(new Long(sw.getId())).addLinkStat(linkStat);
            }
            else
            {
                SwitchStatistics swStat = switchStatTable.get(new Long(sw.getId()));
                synchronized (swStat.getLinkStatTable())
                {
                    for (Iterator<LinkStatistics> lsIt = swStat.getLinkStatTable().iterator(); lsIt.hasNext();)
                    {
                        LinkStatistics ls = lsIt.next();
                        if (ls.getInputPort() == match.getInputPort())
                        {
                            Set<Long> timestampSet = ls.getStatData().keySet();
                            for (Iterator<Long> it = timestampSet.iterator(); it.hasNext();)
                            {
                                Long ts = it.next();
                                if (ts.longValue() > matchedFlow.getTimestamp()
                                        && ts.longValue() <= checkPointTimeStamp)
                                {
                                    //logger.debug("[" + matchedFlow.getTimestamp() + "," + ts.toString() + "," + checkPointTimeStamp + "]");
                                    ls.getStatData().put(ts, utilization + ls.getStatData().get(ts).doubleValue());
                                }
                            }
                            ls.addStatData(checkPointTimeStamp, utilization);
                        }
                    }
                }
            }
            if (type.equals(OFType.STATS_REPLY))
            {
                matchedFlow.setTimestamp(checkPointTimeStamp);
            }
        }
    }

    public void processFlowRemovedMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
    {
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
                + " duration = " + (flRmMsg.getDurationSeconds() + flRmMsg.getDurationNanoseconds() / 1e9)
                + " in port = " + match.getInputPort());

        FlowEntry removedEntry = new FlowEntry(sw.getId(), match.getInputPort(), src_ip, dst_ip, proto, dl_type);
        logger.debug("Removed flow: " + removedEntry.toString());
        updateLinkUsage(sw, removedEntry, match, msg, OFType.FLOW_REMOVED);
        /*long checkPointTimeStamp = System.currentTimeMillis();
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
        double duration = flRmMsg.getDurationSeconds() + (flRmMsg.getDurationNanoseconds() / 1e9);
        if (rsn.equals(OFFlowRemoved.OFFlowRemovedReason.OFPRR_IDLE_TIMEOUT)) {
        duration -= (double) flRmMsg.getIdleTimeout();
        }
        
        double utilization = (double) flRmMsg.getByteCount() / duration;
        
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
        synchronized (swStat.getLinkStatTable()) {
        for (Iterator<LinkStatistics> lsIt = swStat.getLinkStatTable().iterator(); lsIt.hasNext();) {
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
        }*/
    }

    boolean isValidFlow(FlowEntry entry, OFMatch match)
    {
        return activeFlowTable.contains(entry) == false
                && entry.getDlType() == Ethernet.TYPE_IPv4
                && entry.getNwProto() == IPv4.PROTOCOL_UDP
                && (match.getTransportDestination() != 67 && match.getTransportDestination() != 68);
    }
    
    public void processFlowModMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
    {
        OFFlowMod flowModMsg = (OFFlowMod) msg;
        logger.debug("Intercepted FlowMod Message:\t" + flowModMsg.toString());
        OFMatch match = flowModMsg.getMatch();
        FlowEntry entry = new FlowEntry(sw.getId(), match.getInputPort(), match.getNetworkSource(),
                                        match.getNetworkDestination(), match.getNetworkProtocol(), match.getDataLayerType());
        entry.setMatch(match);
        synchronized (activeFlowTable)
        {
            if(isValidFlow(entry, match))
/*            if (activeFlowTable.contains(entry) == false && entry.getDlType() == Ethernet.TYPE_IPv4
                    && entry.getNwProto() != IPv4.PROTOCOL_UDP)
*/
            {
                entry.setTimestamp(System.currentTimeMillis());
                entry.setScheduleTimeout(MIN_SCHEDULE_TIMEOUT);
                logger.debug(ALGORITHM);
                if (ALGORITHM.equals("payless") == true)
                {
                    schedule.addFlowEntry(new Integer(MIN_SCHEDULE_TIMEOUT), entry);
                    if (schedule.getAction(MIN_SCHEDULE_TIMEOUT) == null)
                    {
                        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
                        SingletonTask action = new SingletonTask(ses, new PollSwitchWorker(MIN_SCHEDULE_TIMEOUT, this));
                        action.reschedule(MIN_SCHEDULE_TIMEOUT, TimeUnit.MILLISECONDS);
                        schedule.addAction(MIN_SCHEDULE_TIMEOUT, action);
                    }
                }
                logger.debug("Adding flow " + entry.toString() + " to the Active Flow Table");
                activeFlowTable.add(entry);
                printActiveFlows();
            }
        }
        
    }

    public void processStatReplyMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
    {
        OFStatisticsReply statReply = (OFStatisticsReply) msg;
        ArrayList<OFStatistics> statList = (ArrayList<OFStatistics>) statReply.getStatistics();
        logger.debug("Received Stat Reply " + statList.size());
        if (statList != null && statList.size() > 0)
        {
            OFFlowStatisticsReply fsr = (OFFlowStatisticsReply) statList.get(0);
            logger.debug("Status reply received from Sw = " + sw.getId() + " " + fsr.toString());
            FlowEntry entry = new FlowEntry(sw.getId(),
                                            fsr.getMatch().getInputPort(),
                                            fsr.getMatch().getNetworkSource(),
                                            fsr.getMatch().getNetworkDestination(),
                                            fsr.getMatch().getNetworkProtocol(),
                                            fsr.getMatch().getDataLayerType());

            updateLinkUsage(sw, entry, fsr.getMatch(), msg, OFType.STATS_REPLY);
            //printUtilization();
        }
    }

    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
    {
        switch (msg.getType())
        {
            case PACKET_IN:
                //processPacketInMessage(sw, msg, cntx);
                break;

            case FLOW_REMOVED:
                processFlowRemovedMessage(sw, msg, cntx);
                //printUtilization();
                printFlowStats();
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

    public String getName()
    {
        return NETMonitor.class.getSimpleName();
    }

    public boolean isCallbackOrderingPrereq(OFType type, String name)
    {
        return false;
    }

    public boolean isCallbackOrderingPostreq(OFType type, String name)
    {
        return false;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(INetMonitorService.class);
        return l;
    }

    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls()
    {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(INetMonitorService.class, this);
        return m;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
    {
        Collection<Class<? extends IFloodlightService>> dependencyList = new ArrayList<Class<? extends IFloodlightService>>();
        dependencyList.add(IFloodlightProviderService.class);
        dependencyList.add(IRestApiService.class);
        dependencyList.add(IThreadPoolService.class);
        return dependencyList;
    }

    public void init(FloodlightModuleContext context) throws FloodlightModuleException
    {
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
        this.schedule = new SchedulerTable();
        Map<String, String> configOptions = context.getConfigParams(this);
        this.overhead = Collections.synchronizedSortedMap(new TreeMap<Long, Integer>());
        this.flowStatTable = Collections.synchronizedSortedMap(new TreeMap <FlowEntry, SortedMap <Long, Double>>());
        try
        {
            String algorithm = configOptions.get("algorithm");
            if (algorithm != null)
            {
                ALGORITHM = algorithm;
            }
            else
            {
                throw new NullPointerException("no value for parameter algorithm");
            }

            MIN_SCHEDULE_TIMEOUT = Integer.parseInt(configOptions.get("min_schedule_timeout"));
            MAX_SCHEDULE_TIMEOUT = Integer.parseInt(configOptions.get("max_schedule_timeout"));
            MIN_SCHEDULE_BYTE_THRESHOLD = Integer.parseInt(configOptions.get("min_schedule_byte_threshold"));
            MAX_SCHEDULE_BYTE_THRESHOLD = Integer.parseInt(configOptions.get("max_schedule_byte_threshold"));
            SCHEDULE_TIMEOUT_DAMPING_FACTOR = Integer.parseInt(configOptions.get("schedule_timeout_damping_factor"));
            SCHEDULE_TIMEOUT_AMPLIFY_FACTOR = Integer.parseInt(configOptions.get("schedule_timeout_amplify_factor"));
        }
        catch (Exception ex)
        {
            logger.warn(ex.getMessage());
        }
    }

    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException
    {
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

    public void printUtilization()
    {
        synchronized (switchStatTable)
        {
            logger.info("nSwitches = " + switchStatTable.size());
            Set<Long> switchIds = switchStatTable.keySet();
            for (Long swId : switchIds)
            {
                switchStatTable.get(swId).printSwitchStatistcs(logger);
            }
        }

        synchronized (overhead)
        {
            logger.info("****Overhead****");
            Set<Long> timestamps = overhead.keySet();
            for (Long ts : timestamps)
            {
                logger.info(ts.toString() + "\t" + overhead.get(ts).toString());
            }
        }
    }

    public void printActiveFlows()
    {
        String text = "Active Flow Set:";
        Object[] allEntries = null;
        synchronized (activeFlowTable)
        {
            allEntries = activeFlowTable.toArray(new FlowEntry[0]);
        }

        if (allEntries == null || allEntries.length <= 0)
        {
            text += " [empty]";
        }
        else
        {
            for (int i = 0; i < allEntries.length; i++)
            {
                text += "\n\t" + allEntries[i].toString();
            }
        }

        logger.debug(text);
    }
    
    public String formatUnit(double x)
    {
        return Double.toString(x * 8.0 / 1e6);
    }
    
    public void printFlowStats()
    {
        String text = "Flow Statistics\n";
        Object[] allEntries = null;
        synchronized(flowStatTable)
        {
            allEntries = flowStatTable.keySet().toArray(new FlowEntry[0]);
        }
        if(allEntries == null || allEntries.length <= 0)
            text += "[empty]";
        else
        {
            for(int i = 0; i < allEntries.length; i++)
            {
                //text += IPv4.fromIPv4Address( ((FlowEntry)allEntries[i]).getSrcIp() ) + ",";
                //text += IPv4.fromIPv4Address( ((FlowEntry)allEntries[i]).getDestIp() );
                text += ((FlowEntry)allEntries[i]).toString();
                text += "\n";
                
                synchronized(flowStatTable)
                {
                    Object[] timestamps = flowStatTable.get((FlowEntry)allEntries[i]).keySet().toArray(new Long[0]);
                    for(int j = 0; j < timestamps.length; j++)
                    {
                        text += "\t";
                        text += ((Long)timestamps[j]).toString() + " ";
                        text += formatUnit(flowStatTable.get((FlowEntry)allEntries[i]).get((Long)timestamps[j]).doubleValue());
                        text += "\n";
                    }
                    //text += flowStatTable.get((FlowEntry)allEntries[i]).toString();
                }
                text += "\n";
            }
        }
        
        logger.info(text);
    }

    public SortedMap<Long, SwitchStatistics> getSwitchStatistics()
    {
        return switchStatTable;
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.IPv4;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Shihabur Rahman Chowdhury
 * 
 */
public class NETMonitor implements IOFMessageListener, IFloodlightModule
{
    protected IFloodlightProviderService floodLightProvider;
    protected static Logger logger;
    protected SortedSet <ActiveFlow> activeFlowTable;
    protected SortedMap <Long, SwitchStatistics> switchStatTable;
    
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
    {
        OFMatch match;
        switch(msg.getType())
        {
            case PACKET_IN:
                OFPacketIn pktInMsg = (OFPacketIn)msg;
                int buffer_id = pktInMsg.getBufferId();
                int in_port = pktInMsg.getInPort();
                OFPacketIn.OFPacketInReason reason = pktInMsg.getReason();
                String str_reason = reason.toString();
                match = new OFMatch();
                match.loadFromPacket(pktInMsg.getPacketData(), pktInMsg.getInPort());
                
                int source_port = match.getTransportSource();
                int dest_port = match.getTransportDestination();
                
                int source_ip = match.getNetworkSource();
                int dest_ip = match.getNetworkDestination();
                
                byte net_proto = match.getNetworkProtocol();
                
                logger.info("Received a PACKET_IN from sw = " + sw.getId()
                        + ", in port = " + in_port + ", buffer = " + buffer_id
                        + ", " + str_reason + ", source = " + IPv4.fromIPv4Address(source_ip) + ":" + source_port
                        + ", destination = " + IPv4.fromIPv4Address(dest_ip) + ":" + dest_port + ", protocol = " + net_proto);
                
                ActiveFlow entry = new ActiveFlow(sw.getId(), in_port, source_ip, dest_ip, net_proto, source_port, dest_port);
                if(activeFlowTable.contains(entry) == false)
                {
                    entry.setTimestamp(System.currentTimeMillis());
                    activeFlowTable.add(entry);
                }
                break;
                
            case FLOW_REMOVED:
                OFFlowRemoved flRmMsg = (OFFlowRemoved)msg;
                match = flRmMsg.getMatch();
                
                int src_port = match.getTransportSource();
                int dst_port = match.getTransportDestination();
                
                int src_ip = match.getNetworkSource();
                int dst_ip = match.getNetworkDestination();
                
                byte proto = match.getNetworkProtocol();
                OFFlowRemoved.OFFlowRemovedReason rsn = flRmMsg.getReason();
                
                logger.info("Flow Removed from sw = " + sw.getId() + ", "
                        + " byte counts = " + flRmMsg.getByteCount()
                        + " source = " + IPv4.fromIPv4Address(src_ip) + ":" + src_port
                        + " dest = " + IPv4.fromIPv4Address(dst_ip) + ":" + dst_port
                        + " protocol = " + proto
                        + " reason = " + rsn.toString()
                        + " duration = " + flRmMsg.getDurationSeconds()
                        + " in port = " + match.getInputPort());
                
                ActiveFlow removedEntry = new ActiveFlow(sw.getId(), match.getInputPort(), src_ip, dst_ip, proto, src_port, dst_port);
                long checkPointTimeStamp = System.currentTimeMillis();
                if(rsn.equals(OFFlowRemoved.OFFlowRemovedReason.OFPRR_IDLE_TIMEOUT))
                {
                    checkPointTimeStamp -= (long)(flRmMsg.getIdleTimeout() * 1000);
                }
                ActiveFlow matchedFlow = null, tmpMatchFlow = null;
                for(Iterator<ActiveFlow> it = activeFlowTable.iterator(); it.hasNext() ; )
                {
                    ActiveFlow flow = it.next();
                    if(flow.equals(removedEntry))
                    {
                        tmpMatchFlow = flow;
                        break;
                    }
                }
                
                if(tmpMatchFlow != null)
                {
                    matchedFlow = tmpMatchFlow.clone();
                    activeFlowTable.remove(matchedFlow);
                    
                    double utilization = (double)flRmMsg.getByteCount() / (double)flRmMsg.getDurationSeconds();
                    
                    if(switchStatTable.get(new Long(sw.getId())) == null)
                    {
                        LinkStatistics linkStat = new LinkStatistics();
                        linkStat.setInputPort(matchedFlow.getInputPort());
                        linkStat.addStatData(checkPointTimeStamp, utilization);
                        
                        SwitchStatistics swStat = new SwitchStatistics();
                        swStat.setSwId(sw.getId());
                        swStat.addLinkStat(linkStat);
                    }
                    else if(switchStatTable.get(new Long(sw.getId())).linkExists(match.getInputPort()) == false)
                    {
                        LinkStatistics linkStat = new LinkStatistics();
                        linkStat.setInputPort(matchedFlow.getInputPort());
                        linkStat.addStatData(checkPointTimeStamp, utilization);
                        switchStatTable.get(new Long(sw.getId())).addLinkStat(null);
                    }
                    else
                    {
                        SwitchStatistics swStat = switchStatTable.get(new Long(sw.getId()));
                        for(int i = 0; i < swStat.getLinkStatTable().size(); i++)
                        {
                            LinkStatistics ls = swStat.getLinkStatTable().get(i);
                            if(ls.getInputPort() == match.getInputPort())
                            {
                                Set <Long> timestampSet = ls.getStatData().keySet();
                                for(Iterator <Long> it = timestampSet.iterator(); it.hasNext(); )
                                {
                                    Long ts = it.next();
                                    if(ts.longValue() > matchedFlow.getTimestamp()
                                            && ts.longValue() < checkPointTimeStamp)
                                    {
                                        ls.getStatData().put(ts, utilization + ls.getStatData().get(ts).doubleValue());
                                    }
                                }
                            }
                        }
                        LinkStatistics latestLinkStat = new LinkStatistics();
                        latestLinkStat.setInputPort(matchedFlow.getInputPort());
                        latestLinkStat.addStatData(checkPointTimeStamp, utilization);
                        swStat.getLinkStatTable().add(latestLinkStat);
                    }
                }
                break;
        }
        
        return Command.CONTINUE;
    }

    public String getName() 
    {
        return NETMonitor.class.getSimpleName();
    }

    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() 
    {
        Collection<Class<? extends IFloodlightService>> dependencyList = new ArrayList<Class<? extends IFloodlightService>>();
        dependencyList.add(IFloodlightProviderService.class);
        return dependencyList;
    }

    public void init(FloodlightModuleContext context) throws FloodlightModuleException 
    {
        floodLightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        logger = LoggerFactory.getLogger(NETMonitor.class);
        activeFlowTable = new TreeSet<ActiveFlow>();
        switchStatTable = new TreeMap<Long, SwitchStatistics>();
    }

    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodLightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodLightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
    }
    
    public void printUtilization()
    {
        Set <Long> switchIds = switchStatTable.keySet();
        for(Long swId:switchIds)
        {
            switchStatTable.get(swId).printSwitchStatistcs();
        }
    }
}

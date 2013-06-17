/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import net.floodlightcontroller.packet.IPv4;
import org.openflow.protocol.OFMatch;

/**
 *
 * @author Shihabur Rahman Chowdhury
 */
public class FlowEntry implements Comparable
{
    private long swId;
    private int inputPort;
    private int srcIp, destIp;
    private int nwProto;
    private int dlType;
    //private int destPort;
    //meta-data
    private long timestamp;
    private int scheduleTimeout;
    private OFMatch match;
    private long matchedByteCount;
    private double duration;
    
    public String toString()
    {
        String ret = "";
        ret += "Switch ID = " + swId;
        ret += ", In Port = " + inputPort;
        ret += ", Source IP = " + IPv4.fromIPv4Address(srcIp);
        ret += ", Destination IP = " + IPv4.fromIPv4Address(destIp);// + ":" + destPort;
        ret += ", DL Type = " + Integer.toHexString(dlType);
        ret += ", NW Protocol = " + Integer.toHexString(nwProto);
        ret += ", Timestamp = " + timestamp;
//        ret += ", [Match = " + match.toString() + "]";
        return ret;
    }
    
    public FlowEntry(long swId, int inputPort, int srcIp, int destIp, int nwProto, int dlType) {
        this.swId = swId;
        this.inputPort = inputPort;
        this.srcIp = srcIp;
        this.destIp = destIp;
        this.nwProto = nwProto;
        this.dlType = dlType;
        //this.destPort = destPort;
    }
    
    public int compareTo(Object arg0) 
    {
        FlowEntry obj = (FlowEntry)arg0;
        if(swId != obj.swId)
            return (int)(swId - obj.swId);
        if(inputPort != obj.inputPort)
            return inputPort - obj.inputPort;
        if(srcIp != obj.srcIp)
            return srcIp - obj.srcIp;
        if(destIp != obj.destIp)
            return destIp - obj.destIp;
        if(nwProto != obj.nwProto)
            return nwProto - obj.nwProto;
        //if(dlType != obj.dlType)
        return dlType - obj.dlType;
        //return destPort - obj.destPort;
    }

    public int getDestIp() {
        return destIp;
    }

    public void setDestIp(int destIp) {
        this.destIp = destIp;
    }

    public int getInputPort() {
        return inputPort;
    }

    public void setInputPort(int inputPort) {
        this.inputPort = inputPort;
    }

    public int getSrcIp() {
        return srcIp;
    }

    public void setSrcIp(int srcIp) {
        this.srcIp = srcIp;
    }
    
    public long getSwId() {
        return swId;
    }
    
    public void setSwId(int swId) {
        this.swId = swId;
    }

    /*public int getDestPort() {
        return destPort;
    }

    public void setDestPort(int destPort) {
        this.destPort = destPort;
    }*/

    public int getNwProto() {
        return nwProto;
    }

    public void setNwProto(int nwProto) {
        this.nwProto = nwProto;
    }

    public int getDlType() {
        return dlType;
    }

    public void setDlType(int dlType) {
        this.dlType = dlType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FlowEntry other = (FlowEntry) obj;
        if (this.swId != other.swId) {
            return false;
        }
        if (this.inputPort != other.inputPort) {
            return false;
        }
        if (this.srcIp != other.srcIp) {
            return false;
        }
        if (this.destIp != other.destIp) {
            return false;
        }
        if (this.nwProto != other.nwProto) {
            return false;
        }
        if (this.dlType != other.dlType) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (int) (this.swId ^ (this.swId >>> 32));
        hash = 17 * hash + this.inputPort;
        hash = 17 * hash + this.srcIp;
        hash = 17 * hash + this.destIp;
        hash = 17 * hash + this.nwProto;
        hash = 17 * hash + this.dlType;
        return hash;
    }

    public int getScheduleTimeout() {
        return scheduleTimeout;
    }

    public void setScheduleTimeout(int timeout) {
        this.scheduleTimeout = timeout;
    }

    public OFMatch getMatch() {
        return match;
    }

    public void setMatch(OFMatch match) {
        this.match = match;
    }

    public long getMatchedByteCount() {
        return matchedByteCount;
    }

    public void setMatchedByteCount(long matchedByteCount) {
        this.matchedByteCount = matchedByteCount;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }
    
    
    @Override
    protected FlowEntry clone() {
        //return new FlowEntry(swId, inputPort, srcIp, destIp, nwProto, dlType, destPort);
        FlowEntry ret = new FlowEntry(swId, inputPort, srcIp, destIp, nwProto, dlType);
        ret.setTimestamp(this.timestamp);
        ret.setScheduleTimeout(this.scheduleTimeout);
        ret.setMatch(this.match.clone());
        ret.setMatchedByteCount(matchedByteCount);
        ret.setDuration(duration);
        return ret;
    }
}

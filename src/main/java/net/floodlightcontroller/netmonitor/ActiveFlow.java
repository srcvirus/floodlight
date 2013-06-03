/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

/**
 *
 * @author Shihabur Rahman Chowdhury
 */
public class ActiveFlow implements Comparable
{
    private long swId;
    private int inputPort;
    private int srcIp, destIp;
    private int nwProto;
    private int srcPort, destPort;
    private long timestamp;
    
    public ActiveFlow(long swId, int inputPort, int srcIp, int destIp, int nwProto, int srcPort, int destPort) {
        this.swId = swId;
        this.inputPort = inputPort;
        this.srcIp = srcIp;
        this.destIp = destIp;
        this.nwProto = nwProto;
        this.srcPort = srcPort;
        this.destPort = destPort;
    }
    
    public int compareTo(Object arg0) 
    {
        ActiveFlow obj = (ActiveFlow)arg0;
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
        if(srcPort != obj.srcPort)
            return srcPort - obj.srcPort;
        return destPort - obj.destPort;
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

    public int getDestPort() {
        return destPort;
    }

    public void setDestPort(int destPort) {
        this.destPort = destPort;
    }

    public int getNwProto() {
        return nwProto;
    }

    public void setNwProto(int nwProto) {
        this.nwProto = nwProto;
    }

    public int getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(int srcPort) {
        this.srcPort = srcPort;
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
        final ActiveFlow other = (ActiveFlow) obj;
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
        if (this.srcPort != other.srcPort) {
            return false;
        }
        if (this.destPort != other.destPort) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + (int) (this.swId ^ (this.swId >>> 32));
        hash = 59 * hash + this.inputPort;
        hash = 59 * hash + this.srcIp;
        hash = 59 * hash + this.destIp;
        hash = 59 * hash + this.nwProto;
        hash = 59 * hash + this.srcPort;
        hash = 59 * hash + this.destPort;
        return hash;
    }

    @Override
    protected ActiveFlow clone() {
        return new ActiveFlow(swId, inputPort, srcIp, destIp, nwProto, srcPort, destPort);
    }
    
    
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Set;
import org.slf4j.Logger;

/**
 *
 * @author sr2chowd
 */
public class LinkStatistics {
    private int inputPort;
    SortedMap<Long, Double> statData;
    
    public void printLinkStatistics(Logger log)
    {
        log.info("\tInput Port = " + inputPort);
        Set <Long> ts = statData.keySet();
        for(Long t: ts)
        {
            double utilization = statData.get(t);
            String unit = "Bps";
            if(utilization > 1000.0)
            {
                utilization /= 1000.0;
                unit = "KBps";
            }
            if(utilization > 1000.0)
            {
                utilization /= 1000.0;
                unit = "MBps";
            }
            if(utilization > 1000.0)
            {
                utilization /= 1000.0;
                unit = "GBps";
            }
            log.info("\t\tTimestamp = " + t + ", Utilization = " + utilization + unit);
        }
    }
    public LinkStatistics()
    {
        statData = new TreeMap<Long, Double>();
    }

    public int getInputPort() {
        return inputPort;
    }

    public void setInputPort(int inputPort) {
        this.inputPort = inputPort;
    }

    public void addStatData(long timestamp, double utilization) {
        statData.put(timestamp, utilization);
    }
   
    public double getUtilization(long timestamp) {
        return statData.get(timestamp).doubleValue();
    }

    public SortedMap<Long, Double> getStatData() {
        return statData;
    }
    
    
}

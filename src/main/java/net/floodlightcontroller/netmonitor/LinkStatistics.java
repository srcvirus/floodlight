/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Set;

/**
 *
 * @author sr2chowd
 */
public class LinkStatistics {
    private int inputPort;
    SortedMap<Long, Double> statData;
    
    public void printLinkStatistics()
    {
        System.out.println("\tInput Port = " + inputPort);
        Set <Long> ts = statData.keySet();
        for(Long t: ts)
        {
            System.out.println("\t\tTimestamp = " + t + ", Utilization = " + statData.get(t) + "bps");
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

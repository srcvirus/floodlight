/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import com.google.common.collect.Collections2;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Set;
import org.slf4j.Logger;

/**
 *
 * @author sr2chowd
 */
public class LinkStatistics implements Comparable {
    private int inputPort;
    SortedMap <Long, Double> statData;
    
    public synchronized void printLinkStatistics(Logger log)
    {
        log.info("\tInput Port = " + inputPort);
        Set <Long> ts = statData.keySet();
        for(Long t: ts)
        {
            double utilization = statData.get(t);
            utilization *= 8.0;
            String unit = "";
            utilization /= 1e6;
/*            if(utilization > 1000.0)
            {
                utilization /= 1000.0;
                unit = "Kbps";
            }
            if(utilization > 1000.0)
            {
                utilization /= 1000.0;
                unit = "Mbps";
            }
            if(utilization > 1000.0)
            {
                utilization /= 1000.0;
                unit = "Gbps";
            }*/
//            utilization = Double.valueOf(new DecimalFormat("#.####").format(utilization));
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

    public synchronized void addStatData(long timestamp, double utilization) {
        statData.put(timestamp, utilization);
    }
   
    public synchronized double getUtilization(long timestamp) {
        return statData.get(timestamp).doubleValue();
    }

    public SortedMap<Long, Double> getStatData() {
        return statData;
    }

    public int compareTo(Object arg0) {
        if(arg0 == null)
            return 1;
        LinkStatistics obj = (LinkStatistics)arg0;
        return this.inputPort - obj.inputPort;
    }
    
    public double getLatestStatistics()
    {
        Long maxTimeStamp = Collections.max(statData.keySet());
        return statData.get(maxTimeStamp).doubleValue();
    }
}

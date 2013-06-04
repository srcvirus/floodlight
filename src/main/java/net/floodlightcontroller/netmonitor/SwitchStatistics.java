/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.util.ArrayList;
import org.slf4j.Logger;

/**
 *
 * @author sr2chowd
 */
public class SwitchStatistics {
    long swId;
    ArrayList<LinkStatistics> linkStatTable;
    
    void printSwitchStatistcs(Logger log)
    {
        log.info("Switch ID = " + swId);
        for(int i = 0; i < linkStatTable.size(); i++)
            linkStatTable.get(i).printLinkStatistics(log);
    }
    public SwitchStatistics()
    {
        linkStatTable = new ArrayList<LinkStatistics>();
    }

    public ArrayList<LinkStatistics> getLinkStatTable() {
        return linkStatTable;
    }

    public void setLinkStatTable(ArrayList<LinkStatistics> linkStatTable) {
        this.linkStatTable = linkStatTable;
    }

    public long getSwId() {
        return swId;
    }

    public void setSwId(long swId) {
        this.swId = swId;
    }
    
    public void addLinkStat(LinkStatistics ls)
    {
        linkStatTable.add(ls);
    }
    
    public boolean linkExists(int inputPort)
    {
        for(int i = 0; i < linkStatTable.size(); i++)
            if(linkStatTable.get(i).getInputPort() == inputPort)
                return true;
        
        return false;
    }
}

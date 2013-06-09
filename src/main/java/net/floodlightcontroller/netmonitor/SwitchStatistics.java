/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import org.slf4j.Logger;

/**
 *
 * @author sr2chowd
 */
public class SwitchStatistics {
    long swId;
    SortedSet<LinkStatistics> linkStatTable;
    Iterator <LinkStatistics> linkStatIterator;
    
    public void printSwitchStatistcs(Logger log)
    {
        log.info("Switch ID = " + swId);
        LinkStatistics[] linkStats = new LinkStatistics[1];
        synchronized(linkStatTable)
        {
            linkStats = linkStatTable.toArray(new LinkStatistics[0]);
        }
        for(int i = 0; i < linkStats.length; i++)
            linkStats[i].printLinkStatistics(log);
    }
    public SwitchStatistics()
    {
        linkStatTable = new TreeSet<LinkStatistics> ();
        linkStatIterator = linkStatTable.iterator();
    }

    public synchronized void resetIterator()
    {
        linkStatIterator = linkStatTable.iterator();
    }
    
    public SortedSet<LinkStatistics> getLinkStatTable() {
        return linkStatTable;
    }

    public void setLinkStatTable(TreeSet<LinkStatistics> linkStatTable) {
        this.linkStatTable = linkStatTable;
    }

    public long getSwId() {
        return swId;
    }

    public void setSwId(long swId) {
        this.swId = swId;
    }
    
    public synchronized void addLinkStat(LinkStatistics ls)
    {
        linkStatTable.add(ls);
    }
    
    public synchronized boolean linkExists(int inputPort)
    {
        LinkStatistics[] linkStats = linkStatTable.toArray(new LinkStatistics[0]);
        for(int i = 0; i < linkStats.length; i++)
            if(linkStats[i].getInputPort() == inputPort)
                return true;
        
        return false;
    }
}

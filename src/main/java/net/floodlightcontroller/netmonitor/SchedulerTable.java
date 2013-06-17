/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import net.floodlightcontroller.core.util.SingletonTask;

/**
 *
 * @author sr2chowd
 */
public class SchedulerTable {
    private ConcurrentMap < Integer, ArrayList <FlowEntry> > scheduleTable;
    private ConcurrentMap < Integer, SingletonTask > actionSet;
    
    public SchedulerTable()
    {
        scheduleTable = new ConcurrentHashMap<Integer, ArrayList <FlowEntry> >();
        actionSet = new ConcurrentHashMap<Integer, SingletonTask>();
    }
    
    public synchronized void addFlowEntry(Integer timeout, FlowEntry entry)
    {
        if(scheduleTable.get(timeout) == null)
        {
            scheduleTable.put(timeout, new ArrayList<FlowEntry>());
        }
        scheduleTable.get(timeout).add(entry);
    }
    
    public synchronized int getFlowEntryCount(Integer timeout)
    {
        return scheduleTable.get(timeout).size();
    }
    
    public synchronized void addAction(Integer timeout, SingletonTask action)
    {
        actionSet.put(timeout, action);
    }
    
    public SingletonTask getAction(Integer timeout)
    {
        return actionSet.get(timeout);
    }
    
    public synchronized void updateTimeout(Integer oldTimeout, Integer newTimeout, FlowEntry entry)
    {
        removeFlowEntry(oldTimeout, entry);
        this.addFlowEntry(newTimeout, entry);
    }
    
    public ArrayList <FlowEntry> getAllEntries(Integer timeout)
    {
        return scheduleTable.get(timeout);
    }
    
    public synchronized void removeFlowEntry(Integer timeout, FlowEntry entry)
    {
        scheduleTable.get(timeout).remove(entry);
        if(scheduleTable.get(timeout).isEmpty())
            actionSet.remove(timeout);
    }
}

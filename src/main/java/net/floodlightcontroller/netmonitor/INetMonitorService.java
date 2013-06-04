/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import java.util.SortedMap;

/**
 *
 * @author Shihabur Rahman Chowdhury
 */
public interface INetMonitorService extends IFloodlightService {
    public SortedMap <Long, SwitchStatistics> getSwitchStatistics();
}

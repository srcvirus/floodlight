/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import java.util.SortedMap;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 *
 * @author sr2chowd
 */
public class NetMonitorResource extends ServerResource {
    @Get("json")
    public SortedMap <Long, SwitchStatistics> retrieve()
    {
        INetMonitorService netMonitorService = (INetMonitorService)getContext().getAttributes().get(INetMonitorService.class.getCanonicalName());
        return netMonitorService.getSwitchStatistics();
    }
}

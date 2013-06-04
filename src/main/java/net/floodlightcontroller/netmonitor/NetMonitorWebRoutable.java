/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.floodlightcontroller.netmonitor;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 *
 * @author sr2chowd
 */
public class NetMonitorWebRoutable implements RestletRoutable {

    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/linkstat/json", NetMonitorResource.class);
        return router;
    }

    public String basePath() {
        return "/wm/netmonitor";
    }
    
}

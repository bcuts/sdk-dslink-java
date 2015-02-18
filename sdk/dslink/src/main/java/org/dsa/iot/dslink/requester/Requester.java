package org.dsa.iot.dslink.requester;

import com.google.common.eventbus.EventBus;
import lombok.NonNull;
import lombok.val;
import org.dsa.iot.dslink.events.ResponseEvent;
import org.dsa.iot.dslink.requester.requests.*;
import org.dsa.iot.dslink.requester.responses.*;
import org.dsa.iot.dslink.util.Linkable;
import org.dsa.iot.dslink.util.StreamState;
import org.dsa.iot.dslink.connection.Client;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * @author Samuel Grenier
 */
public class Requester extends Linkable {

    public Requester(EventBus bus) {
        super(bus);
    }

    public void sendRequest(@NonNull Client client,
                            @NonNull Request req) {
        ensureConnected();

        val obj = new JsonObject();
        obj.putNumber("rid", client.getRequestTracker().track(req));
        obj.putString("method", req.getName());
        req.addJsonValues(obj);

        val requests = new JsonArray();
        requests.add(obj);

        val top = new JsonObject();
        top.putArray("requests", requests);
        client.write(top);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void parse(Client client, JsonArray responses) {
        try {
            val it = responses.iterator();
            for (JsonObject o; it.hasNext();) {
                o = (JsonObject) it.next();

                int rid = o.getNumber("rid").intValue();
                Request request = client.getRequestTracker().getRequest(rid);
                String name = request.getName();
                Response<?> resp;
                if (rid != 0) {
                    // Response
                    String state = o.getString("state");
                    if (StreamState.CLOSED.jsonName.equals(state)) {
                        client.getRequestTracker().untrack(rid);
                    }

                    switch (name) {
                        case "list":
                            resp = new ListResponse((ListRequest) request, getManager());
                            break;
                        case "set":
                            resp = new SetResponse((SetRequest) request);
                            break;
                        case "remove":
                            resp = new RemoveResponse((RemoveRequest) request);
                            break;
                        case "invoke":
                            resp = new InvokeResponse((InvokeRequest) request);
                            break;
                        case "subscribe":
                            resp = new SubscribeResponse((SubscribeRequest) request);
                            break;
                        case "unsubscribe":
                            resp = new UnsubscribeResponse((UnsubscribeRequest) request);
                            break;
                        case "close":
                            resp = new CloseResponse((CloseRequest) request);
                            break;
                        default:
                            throw new RuntimeException("Unknown method");
                    }
                    resp.populate(o.getArray("updates"));
                } else {
                    // Subscription update
                    SubscribeRequest req = (SubscribeRequest) request;
                    resp = new SubscriptionResponse(req, getManager());
                    resp.populate(o.getArray("updates"));
                }
                val ev = new ResponseEvent(client, rid, name, resp);
                getBus().post(ev);
            }
        } catch (Exception e) {
            // Error handler data
            e.printStackTrace(System.err);
        }
    }
}
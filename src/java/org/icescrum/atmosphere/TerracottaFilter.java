/*
package org.icescrum.atmosphere;


import org.atmosphere.cpr.BroadcastFilter;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.ClusterBroadcastFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.message.pipe.Pipe;
import org.terracotta.message.pipe.subscriber.Subscriber;
import org.terracotta.message.pipe.subscriber.SubscriberProcessor;
import org.terracotta.message.topology.Topology;
import org.terracotta.message.topology.TopologyManager;

import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Support for Redis
 *
 * @author Jeanfrancois Arcand

public class TerracottaFilter implements ClusterBroadcastFilter {

    private static final Logger logger = LoggerFactory.getLogger(TerracottaFilter.class);

    private Broadcaster bc;
    private String subID;
    private final ConcurrentLinkedQueue<String> receivedMessages = new ConcurrentLinkedQueue<String>();
    private Subscriber<String> subscriber;
    private Pipe <String> pubSub;
    private Topology topology;
    private TerracottaPubSubListener listener;

    public TerracottaFilter(Broadcaster bc) {
        this.bc = bc;
        this.subID = this.bc.getID()+(new Date().getTime());
        System.out.println("Creating Atmosphere Terracotta Clustering support");
        topology = TopologyManager.getInstance().<String, String>getOrCreateTopology("bcTpl", null);
        pubSub = topology.getOrCreatePublishSubscribePipeFor("bcPubSub");
        subscriber = topology.getOrCreateVolatileSubscriberFor("bcPubSub",this.subID);
    }


    @Override
    public void init() {
        System.out.println("Starting Atmosphere Terracotta Clustering support on "+bc.getID());
        listener = new TerracottaPubSubListener(subscriber,bc);
        listener.start();
    }


    @Override
    public void destroy() {
        listener.stop();
        topology.removeVolatileSubscriberFor("bcPubSub",this.subID);
    }


    @Override
    public BroadcastFilter.BroadcastAction filter(Object originalMessage, Object o) {
        if (!receivedMessages.remove(originalMessage.toString())) {
            try {
                System.out.println("put new message(): {}");
                pubSub.put(originalMessage.toString());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return new BroadcastFilter.BroadcastAction(BroadcastAction.ACTION.CONTINUE, o);
    }

    @Override
    public Broadcaster getBroadcaster() {
        return bc;
    }

    @Override
    public void setUri(String name) {

    }


    @Override
    public void setBroadcaster(Broadcaster bc) {
        this.bc = bc;
    }

    private class TerracottaPubSubListener extends SubscriberProcessor {

        private Broadcaster broadcaster;

        public TerracottaPubSubListener(Subscriber<String> subscriber, Broadcaster broadcaster){
            super(subscriber,true);
            this.broadcaster = broadcaster;
        }

        @Override
        public boolean event(Object message) throws Exception {
            System.out.println("new message received(): {}"+message);
            receivedMessages.offer((String)message);
            broadcaster.broadcast((String)message);
            return true;
        }
    }
}
        */
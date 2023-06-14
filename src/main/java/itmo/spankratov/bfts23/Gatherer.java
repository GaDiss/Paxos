package itmo.spankratov.bfts23;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class Gatherer {
    // record for deliver(v) call
    public record Deliver(int from, int v) implements Process.Message {
    }

    public static Behavior<Process.Message> create(int n) {
        return Behaviors.setup(context -> new Gatherer.GathererBehavior(context, n));
    }

    public static class GathererBehavior extends AbstractBehavior<Process.Message> {

        // how many processes need to deliver(v) before gatherer stops
        private final int deliverFrom;
        private int delivered = 0;
        private int v = -1;

        private GathererBehavior(ActorContext<Process.Message> context, int deliverFrom) {
            super(context);
            this.deliverFrom = deliverFrom;
        }

        @Override
        public Receive<Process.Message> createReceive() {
            ReceiveBuilder<Process.Message> builder = newReceiveBuilder();

            builder.onMessage(Deliver.class, this::onDeliver);

            return builder.build();
        }

        private Behavior<Process.Message> onDeliver(Deliver delivery) {
            // assert all processes deliver the same value
            if (delivered == 0) v = delivery.v;
            assert v == delivery.v;

            // when deliverFrom processes deliver(v) gatherer stops
            delivered++;
            if (delivered == deliverFrom) return Behaviors.stopped();
            return this;
        }
    }
}

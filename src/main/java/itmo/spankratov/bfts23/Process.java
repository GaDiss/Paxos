package itmo.spankratov.bfts23;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.japi.Pair;

import java.util.*;

// implementation of Synod OFCons algorithm
public class Process {

    public interface Message {
    }

    public record Launch() implements Message {
    }

    public record Init(List<ActorRef<Message>> processes) implements Message {
    }

    public record Crash(double a) implements Message {
    }

    public record Hold() implements Message {
    }

    public record Read(long ballot, int from) implements Message {
    }

    public record Abort(long ballot) implements Message {
    }

    public record Gather(long ballot, long estBallot, int est, int from) implements Message {
    }

    public record Impose(long ballot, int v, int from) implements Message {
    }

    public record Ack(long ballot, int from) implements Message {
    }

    public record Decide(int v) implements Message {
    }

    public static Behavior<Message> create(int i, int n) {
        return Behaviors.setup(context -> new ProcessBehavior(context, i, n));
    }

    public static class ProcessBehavior extends AbstractBehavior<Message> {
        Random rand = new Random();
        private List<ActorRef<Message>> processes;
        private final int n;
        private final int quorum;
        private final int pId;
        private final List<Pair<Integer, Long>> states;
        private int statesCollected = 0;
        private int ackCollected = 0;
        private long ballot;
        private int proposal;
        private long readBallot;
        private long imposeBallot;
        private int estimate;
        static final Pair<Integer, Long> ZERO_STATE = Pair.create(-1, 0L);
        private boolean faultProne = false;
        private boolean isOnHold = false;
        private double crashProb = 0.0;


        private ProcessBehavior(ActorContext<Message> context, int i, int n) {
            super(context);

            this.n = n;
            this.quorum = n / 2 + 1;
            this.pId = i;
            this.states = new ArrayList<>(Collections.nCopies(n + 1, ZERO_STATE));
            this.ballot = i - n;
            this.proposal = -1;
            this.readBallot = 0;
            this.imposeBallot = i - n;
            this.estimate = -1;
        }

        @Override
        public Receive<Message> createReceive() {
            ReceiveBuilder<Message> builder = newReceiveBuilder();

            builder.onMessage(Init.class, this::onInit);
            builder.onMessage(Crash.class, this::onCrash);
            builder.onMessage(Launch.class, this::onLaunch);
            builder.onMessage(Hold.class, this::onHold);
            builder.onMessage(Read.class, this::onRead);
            builder.onMessage(Abort.class, this::onAbort);
            builder.onMessage(Gather.class, this::onGather);
            builder.onMessage(Impose.class, this::onImpose);
            builder.onMessage(Ack.class, this::onAck);
            builder.onMessage(Decide.class, this::onDecide);

            return builder.build();
        }

        private Behavior<Message> tryCrash(Behavior<Message> b) {
            if (faultProne && rand.nextDouble() <= crashProb) {
                getContext().getLog().info("Process {} Crashed", pId);
                return Behaviors.stopped(); // crash
            } else return b;
        }

        private Behavior<Message> onInit(Init init) {
            this.processes = init.processes;
            return this;
        }

        private Behavior<Message> onCrash(Crash crash) {
            this.faultProne = true;
            this.crashProb = crash.a;
            return tryCrash(this);
        }

        private Behavior<Message> onHold(Hold hold) {
            isOnHold = true;
            return this;
        }

        private Behavior<Message> onLaunch(Launch launch) {
            int v = rand.nextInt(2);
            getContext().getLog().info("Process {} Propose {}", pId, v);
            propose(v);
            return tryCrash(this);
        }

        private void propose(int v) {
            proposal = v;
            ballot = ballot + n;

            Collections.fill(states, ZERO_STATE);
            statesCollected = 0;
            ackCollected = 0;
            processes.forEach(p -> p.tell(new Read(ballot, pId)));
        }

        private Behavior<Message> onRead(Read read) {
            if (readBallot > read.ballot || imposeBallot > read.ballot) {
                processes.get(read.from).tell(new Abort(read.ballot));
            } else {
                readBallot = read.ballot;
                processes.get(read.from).tell(new Gather(read.ballot, imposeBallot, estimate, pId));
            }
            return tryCrash(this);
        }

        private Behavior<Message> onAbort(Abort abort) {
            if (abort.ballot != ballot) return this;
            if (!isOnHold) propose(proposal);
            return tryCrash(this);
        }

        private Behavior<Message> onGather(Gather gather) {
            if (gather.ballot != ballot) return this;

            statesCollected++;
            states.set(gather.from, Pair.create(gather.est, gather.estBallot));

            if (statesCollected == quorum) {
                Pair<Integer, Long> maxState = Collections.max(states, Comparator.comparing(Pair::second));
                if (maxState.second() > 0) {
                    proposal = maxState.first();
                }

                Collections.fill(states, ZERO_STATE);
                statesCollected = 0;

                processes.forEach(p -> p.tell(new Impose(ballot, proposal, pId)));
            }
            return tryCrash(this);
        }

        private Behavior<Message> onImpose(Impose impose) {
            if (readBallot > impose.ballot || imposeBallot > impose.ballot) {
                processes.get(impose.from).tell(new Abort(impose.ballot));
            } else {
                estimate = impose.v;
                imposeBallot = impose.ballot;
                processes.get(impose.from).tell(new Ack(impose.ballot, pId));
            }
            return tryCrash(this);
        }

        private Behavior<Message> onAck(Ack ack) {
            if (ack.ballot != ballot) return this;
            ackCollected++;
            if (ackCollected == quorum) {
                processes.forEach(p -> p.tell(new Decide(proposal)));
            }
            return tryCrash(this);
        }

        private Behavior<Message> onDecide(Decide decide) {
            processes.forEach(p -> p.tell(new Decide(decide.v)));
            getContext().getLog().info("Process {} Decided {}", pId, decide.v);
            processes.get(0).tell(new Gatherer.Deliver(pId, decide.v));
            return Behaviors.stopped();
        }
    }
}

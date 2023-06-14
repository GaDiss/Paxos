package itmo.spankratov.bfts23;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.Behaviors;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    public static Behavior<Void> create(int n, int f, double a, long t) {
        return Behaviors.setup(
                context -> {
                    // list of N actors
                    List<ActorRef<Process.Message>> processes = new ArrayList<>();
                    // processes[0] is an actor for gathering delivery responses
                    processes.add(context.spawn(Gatherer.create(n - f), "gatherer"));
                    // an array for choosing random f crash prone actors
                    List<Integer> shuffled = new ArrayList<>();

                    // main stops when gatherer stops
                    context.watch(processes.get(0));

                    for (int i = 1; i <= n; i++) {
                        shuffled.add(i);
                        // add a process with pId in [1, n] interval
                        processes.add(context.spawn(Behaviors.withMdc(
                                Process.Message.class,
                                message -> {
                                    Map<String, String> msgMdc = new HashMap<>();
                                    // add system time field to logs (typed actors don't have this by default)
                                    msgMdc.put("akkaTimestamp", String.valueOf(System.currentTimeMillis()));
                                    return msgMdc;
                                },
                                Process.create(i, n)), Integer.toString(i)));
                    }

                    // choose faulty actors
                    Collections.shuffle(shuffled);
                    List<Integer> faulty = shuffled.subList(n - f, n);
                    // choose non-leader actors
                    List<Integer> hold = shuffled.subList(1, n);

                    // launch actors
                    processes.forEach(p -> p.tell(new Process.Init(processes)));
                    processes.forEach(p -> p.tell(new Process.Launch()));
                    faulty.forEach(i -> processes.get(i).tell(new Process.Crash(a)));

                    // leader election completes after t ms
                    Thread.sleep(t);
                    hold.forEach(i -> processes.get(i).tell(new Process.Hold()));

                    // stop when gatherer stops
                    return Behaviors.receive(Void.class)
                            .onSignal(Terminated.class, sig -> Behaviors.stopped())
                            .build();
                });
    }

    public static void main(String[] args) throws InterruptedException, TimeoutException {

        for (int n : List.of(3, 25, 50, 75, 100)) {             // N - number of actors
            int f = n / 2 - 1 + n % 2;                          // f - number of crash prone actors
            for (double a : List.of(0., 0.01, 0.1, 1.)) {       // a - crash probability
                for (long t : List.of(500, 1000, 1500, 2000)) { // t_{le} - leader election timeout
                    for (int iter = 0; iter < 5; iter++) {      // iteration number
                        // run the Paxos with parameters [N, f, a, t]
                        ActorSystem<Void> system = ActorSystem.create(
                                Main.create(n, f, a, t),
                                String.format("%d-%d-%.0f-%d-%d", n, f, a * 100, t, iter)
                        );
                        // wait for run to finish before starting a new one
                        Await.ready(system.whenTerminated(), Duration.create(10, TimeUnit.MINUTES));
                    }
                }
            }
        }
    }
}
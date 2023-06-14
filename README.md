# Project: Obstruction-Free Consensus and Paxos

you can read the TOR for this project here: [project.pdf](project.pdf). 

## Problem statement

Assume a system with _N_ _processes_ (or agents).
Each process proposes some value _v_ from _{0, 1}_ to others.
The problem of deciding the same value from proposed ones for every process is called **consensus**.
The goal of consensus algorithm is to solve this problem: 
make all processes agree on the same value _v_,
that was before proposed by one of N processes.

There is a simple analogy of consensus formulation.
The processes can be thought of as generals that are trying to atack enemy's castle.
All the generals need to atack the castle simultaneously to increase the chances for success.
However, general's armies are far apart and that is of strategical importance, generals don't move their armies.
Meaning, they can only communicate by sending messages.
Some general want to atack on Monday (i.e. _v = 1_) and some don't (i.e. _v = 0_).
Consensus algorithms makes it so eventually all generals agree, whether they should all atack on Monday.

The above can be formally described as safety requirements for algorithm:
- _Agreement_: Every process decides the same value;
- _Validity_: Only the values proposed before can be decided.

During execution a process p is allowed to _abort_: report that p failed to decide.
In that case, process p can try to propose second time.

During executions some processes may _crash_ (I.e. some armies are defeated).
If the process never crushes, it is called correct.
In this project we want the algorithm to provide _Obstruction Free Termination_:

- All correct process eventually decide or abort;
- If some correct process decides, every other correct process eventually decides (not aborts);
- If at some point exactly one correct process keeps proposing, it decides.

Additionally, an _asynchronous_ system is assumed:
if process p sends a message to process q, q eventually receives this message.

## Implementation description

In this project, obstruction free consensus is solved with **Synod OFCons** algorithm.
This algorithm assumes that at least half of the processes is correct.
Let _f_ be the number of crash prone processes. 
Each of the crash prone processes has a chance _alpha_ to crash for every event on the process.

The implementation simulates leader election:
After some timeout _tle_, all but one correct processes p stop trying to propose after abort.
By obstruction freedom, that means the algorithm always terminates successfully.

Process interactions is implemented with AKKA on Java.
I used typed akka API instead of the traditional one.

Each process in the system is an AKKA actor.
The following AKKA actors are implemented:
- `Process`: an Actor that implements consensus algorithm.
When `Process` Actor receives `Launch` message, 
it chooses a random value between 0 and 1 and starts proposing it to other `Process` Actors.
When `Process` Actor receives `Hold` message, it stops proposing after abort.
When `Process` Actor receives `Crash` message with parameter `a`, 
it attempts to crash after each event with probability `a`.
Crashed Actor don't send or reply to any messages.
If `Process` actor decides on value v, it sends `Deliver` message to the `Gatherer` Actor.


- `Gatherer`: waits for all correct `Process` Actors to `Deliver`.
After that, terminates the system.


- `Main`: creates N `Process` Actors and a single `Gatherer` actor.
sends `Crash` message to f `Process` actors
and after timeout _tle_ send `Hold` message to all but one correct actors.
Terminates with `Gatherer`.

You can run the benchmarking application with:

``gradle run``

## Performance analysis

The code was run on Intel Core i9-12900H processor with default JVM configuration.

The algorithm is executed for each combination of parameters exactly 5 times:
- _N_ - number of `Process` actors in {3, 25, 50, 75, 100};
- _f_ - number of crash prone `Process` Actors is defined to be the possible maximum for a given _N_,
that is less than half of _N_;
- _a_ - probability to crash for crash prone `Process` Actors in {0, 0.01, 0.1, 1};
- _tle_ time delay before `Main` elects a leader is in {0.5s, 1s, 1.5s, 2s}.

All `Deliver` and `Launch` events are logged in [paxos.log](/target/paxos.log) file. In a following format:

``INFO %timestamp %N-%f-%a-%tle-%iter - Process %pId %eventType %value``

The log is then parsed to a [csv](/jupyter/df_5avg.csv) in Python
and plotted in [plots.pdf](/jupyter/plots.pdf) file for a further analysis.

This yields the following result:
![plots.png](/jupyter/plots.png)

Each line represents correlation between average latency of 5 runs and the number of actors _N_.
Each of the four plots are for each leader election timeout _tle_ parameter, which is labeled on top of the boxes.
Each plot contains four lines for crash probabilities _a_ labeled in left right corner of each plot.
The dotted line represents leader election timeout _tle_.

## Conclusions 

- Starting with the obvious: the increase of number of actors _N_ increases the average latency.
More processes means more contention and more messages. 
In fact, the number of messages in a system grows quadratically of _N_ for Synod algorithm.
Additionally, increase of _N_ likely increases the number of propose calls for leader.


- At a first glance, crash probabilities _a_ do not have statistically significant effect on average latency.
On one hand, crashed actors do not send messages,
meaning the more crashed actors the less contention in the system.
On the other hand, with fewer crashes, the quorums are reached faster.
That is up to debate, but maybe with different parameters of _tle_ and _N_, and crash probability exponentially decreasing,
it will have more noticeable effect on latency.


- Contrary to the previous observation: when processes don't crash (i.e. _a = 0_),
on _N = 100_ actors, the system finishes before leader election.
However, when processes do crash (i.e. _a > 0_), the average latency is longer than _tle_.
That shows that with crash prone processes and large enough _N_, it is harder to reach consensus. 


- It is clear that with longer leader election timeouts, the average latency increases.
Additionally, for larger number of actors _N_
consensus is reached very fast after the leader was elected. 
That indicates a good use of obstruction freedom property by the algorithm.
For smaller _N_, the consensus can be reached before leader election.
Cynical block-chain implementation using Akka and Scala
=======================================================

The name is a bit on the pun, since I forked this project off my previous naivechain-scala project.

There were naivechain is meant to be a simple as possible, cynical-chain tries to actually be useful by adding proof-of-work.
This also means the code is more elaborate and tries to follow Scala best-practises.

It uses Akka and Akka-http. Peer to peer communication between nodes is straight akka remoting, and akka-http is used for a simple rest interface.

Nodes spin up a peer-to-peer network: connecting a node to another node will cause it to connect to all the nodes in the network.

Building, running, etc
----------------------

The app can be run using docker-compose.

Start by building the docker container

    sbt docker:publishLocal

Then run

    docker-compose up

This will start 4 nodes, which will first connect to node1 (the seed node) and then proceed to build a P2P network.

Then mine a block:

    curl -X POST -d "My data for the block" http://localhost:9000/mineBlock

All nodes should show the block:

    curl http://localhost:9000/blocks
    curl http://localhost:9001/blocks
    curl http://localhost:9002/blocks
    curl http://localhost:9003/blocks

Testing and code coverage
-------------------------

Code coverage is done using scoverage.

Run

    sbt coverage test

to measure, and then

    sbt coverageReport

to generate a report.


